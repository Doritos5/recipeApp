package com.example.recipeapp.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import com.example.recipeapp.R
import com.example.recipeapp.RecipeViewModel
import com.example.recipeapp.model.recipes.Recipe
import com.example.recipeapp.ui.state.UploadState
import com.example.recipeapp.util.ImageUtils
import com.google.android.material.textfield.TextInputEditText
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * EditRecipeFragment allows the user to update an existing recipe.
 * Receives recipeId via SafeArgs from MyRecipesFragment.
 * Reuses shared logic (image picker, upload state) from AddRecipeFragment pattern.
 */
@AndroidEntryPoint
class EditRecipeFragment : Fragment() {

    private val recipeViewModel: RecipeViewModel by activityViewModels()
    private val args: EditRecipeFragmentArgs by navArgs()

    // UI Components
    private lateinit var titleEt: TextInputEditText
    private lateinit var instructionsEt: TextInputEditText
    private lateinit var imageEt: TextInputEditText
    private lateinit var saveBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var pickImageBtn: Button
    private lateinit var recipeImageView: ImageView
    private lateinit var uploadProgressBar: ProgressBar

    // The recipe being edited
    private var currentRecipe: Recipe? = null

    // Selected new image URI from gallery
    private var selectedImageUri: Uri? = null

    // --- Image Picker Launcher ---
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedUri = ImageUtils.copyImageToInternalStorage(requireContext(), it)
            if (savedUri != null) {
                selectedImageUri = savedUri
                recipeImageView.setImageURI(savedUri)
                recipeImageView.visibility = View.VISIBLE
                imageEt.text?.clear()
            } else {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_edit_recipe, container, false)

        initViews(view)
        setupListeners()
        observeUploadState()
        loadRecipe()

        return view
    }

    private fun initViews(view: View) {
        titleEt = view.findViewById(R.id.editRecipeTitleEt)
        instructionsEt = view.findViewById(R.id.editRecipeInstructionsEt)
        imageEt = view.findViewById(R.id.editRecipeImageEt)
        saveBtn = view.findViewById(R.id.editRecipeSaveBtn)
        cancelBtn = view.findViewById(R.id.editRecipeCancelBtn)
        pickImageBtn = view.findViewById(R.id.editRecipePickImageBtn)
        recipeImageView = view.findViewById(R.id.editRecipeImageView)
        uploadProgressBar = view.findViewById(R.id.editRecipeProgressBar)
    }

    private fun setupListeners() {
        cancelBtn.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        pickImageBtn.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        imageEt.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !imageEt.text.isNullOrEmpty()) {
                selectedImageUri = null
                recipeImageView.visibility = View.GONE
            }
        }

        saveBtn.setOnClickListener {
            saveRecipe()
        }
    }

    /**
     * Loads the recipe by ID (from SafeArgs) and pre-fills the form.
     */
    private fun loadRecipe() {
        val recipeId = args.recipeId

        recipeViewModel.getRecipeById(recipeId).observe(viewLifecycleOwner) { recipe ->
            if (recipe != null && currentRecipe == null) {
                // Only pre-fill once (avoid overwriting user edits on LiveData re-emission)
                currentRecipe = recipe
                titleEt.setText(recipe.title)
                instructionsEt.setText(recipe.instructions ?: "")

                // Show existing image
                val displayUrl = recipe.imageRemoteUrl ?: recipe.imageUrl
                if (!displayUrl.isNullOrEmpty()) {
                    recipeImageView.visibility = View.VISIBLE
                    when {
                        displayUrl.startsWith("data:image") -> {
                            // Base64 image stored in Firestore — don't put it in the text field
                            try {
                                val base64 = displayUrl.substringAfter("base64,")
                                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                recipeImageView.setImageBitmap(bitmap)
                            } catch (e: Exception) {
                                recipeImageView.setImageResource(R.drawable.ic_launcher_foreground)
                            }
                        }
                        displayUrl.startsWith("file://") || displayUrl.startsWith("content://") -> {
                            imageEt.setText(displayUrl)
                            recipeImageView.setImageURI(Uri.parse(displayUrl))
                        }
                        else -> {
                            imageEt.setText(displayUrl)
                            Picasso.get()
                                .load(displayUrl)
                                .placeholder(R.drawable.ic_launcher_foreground)
                                .error(R.drawable.ic_launcher_foreground)
                                .into(recipeImageView)
                        }
                    }
                }
            }
        }
    }

    private fun observeUploadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                recipeViewModel.uploadState.collect { state ->
                    when (state) {
                        is UploadState.Idle -> {
                            uploadProgressBar.visibility = View.GONE
                            saveBtn.isEnabled = true
                        }
                        is UploadState.Loading -> {
                            uploadProgressBar.visibility = View.VISIBLE
                            saveBtn.isEnabled = false
                        }
                        is UploadState.Success -> {
                            uploadProgressBar.visibility = View.GONE
                            saveBtn.isEnabled = true
                            Toast.makeText(context, "Recipe Updated!", Toast.LENGTH_SHORT).show()
                            recipeViewModel.resetUploadState()
                            parentFragmentManager.popBackStack()
                        }
                        is UploadState.Error -> {
                            uploadProgressBar.visibility = View.GONE
                            saveBtn.isEnabled = true
                            Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                            recipeViewModel.resetUploadState()
                        }
                    }
                }
            }
        }
    }

    private fun saveRecipe() {
        val original = currentRecipe ?: return
        val title = titleEt.text.toString().trim()
        val instructions = instructionsEt.text.toString().trim()
        val imageUrl = imageEt.text.toString().trim()

        if (title.isEmpty()) {
            titleEt.error = "Please enter a title"
            return
        }

        val hasUrl = imageUrl.isNotEmpty()
        val hasDeviceImage = selectedImageUri != null

        // When no new image is picked:
        // - if user typed a URL, use that
        // - otherwise keep the existing imageRemoteUrl (Base64 from Firestore) untouched
        val resolvedImageUrl = when {
            hasDeviceImage -> selectedImageUri.toString()
            hasUrl -> imageUrl
            else -> original.imageUrl
        }

        // Build updated recipe, keeping original ID and other fields.
        // Always preserve imageRemoteUrl unless a new device image was picked
        // (the repository will re-compress it; if no new image, the repo keeps the existing one).
        val updatedRecipe = original.copy(
            title = title,
            instructions = instructions,
            imageUrl = resolvedImageUrl,
            imageRemoteUrl = if (hasDeviceImage) null else original.imageRemoteUrl
        )

        recipeViewModel.updateRecipe(updatedRecipe, selectedImageUri, requireContext())
    }
}

