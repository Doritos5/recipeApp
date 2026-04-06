package com.example.recipeapp.ui

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
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
import com.example.recipeapp.ui.util.TagChipUtils
import com.example.recipeapp.util.ImageUtils
import com.example.recipeapp.util.TagValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditRecipeFragment : Fragment() {

    private val recipeViewModel: RecipeViewModel by activityViewModels()
    private val args: EditRecipeFragmentArgs by navArgs()

    private lateinit var backBtn: ImageView

    private lateinit var titleEt: TextInputEditText
    private lateinit var instructionsEt: TextInputEditText
    private lateinit var imageEt: TextInputEditText
    private lateinit var tagsEt: TextInputEditText
    private lateinit var ingredientsEt: TextInputEditText

    private lateinit var saveBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var pickImageBtn: Button
    private lateinit var addTagBtn: MaterialButton
    private lateinit var tagsChipGroup: ChipGroup
    private lateinit var recipeImageView: ImageView
    private lateinit var uploadProgressBar: ProgressBar

    private var currentRecipe: Recipe? = null
    private var selectedImageUri: Uri? = null
    private val currentTags = mutableListOf<String>()

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
        backBtn = view.findViewById(R.id.backBtn)

        titleEt = view.findViewById(R.id.editRecipeTitleEt)
        instructionsEt = view.findViewById(R.id.editRecipeInstructionsEt)
        imageEt = view.findViewById(R.id.editRecipeImageEt)
        tagsEt = view.findViewById(R.id.editRecipeTagsEt)
        ingredientsEt = view.findViewById(R.id.editRecipeIngredientsEt)

        saveBtn = view.findViewById(R.id.editRecipeSaveBtn)
        cancelBtn = view.findViewById(R.id.editRecipeCancelBtn)
        pickImageBtn = view.findViewById(R.id.editRecipePickImageBtn)
        addTagBtn = view.findViewById(R.id.addTagBtn)
        tagsChipGroup = view.findViewById(R.id.tagsChipGroup)
        recipeImageView = view.findViewById(R.id.editRecipeImageView)
        uploadProgressBar = view.findViewById(R.id.editRecipeProgressBar)
    }

    private fun setupListeners() {
        backBtn.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        cancelBtn.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        pickImageBtn.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        addTagBtn.setOnClickListener {
            val rawInput = tagsEt.text.toString().trim()

            if (rawInput.isNotEmpty()) {
                TagChipUtils.splitInputTags(rawInput).forEach { tag ->
                    TagChipUtils.addTagIfValid(tag, currentTags, tagsChipGroup)
                }
                tagsEt.text?.clear()
            }
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

    private fun renderExistingTags(tags: List<String>) {
        TagChipUtils.renderTags(currentTags, tagsChipGroup, tags)
    }

    private fun toInstructionSteps(raw: String): List<String> {
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun loadRecipe() {
        val recipeId = args.recipeId

        recipeViewModel.getRecipeById(recipeId).observe(viewLifecycleOwner) { recipe ->
            if (recipe != null && currentRecipe == null) {
                currentRecipe = recipe
                titleEt.setText(recipe.title)
                instructionsEt.setText(recipe.instructions.joinToString("\n"))
                ingredientsEt.setText(recipe.ingredients ?: "")
                renderExistingTags(recipe.tags)

                val displayUrl = recipe.imageRemoteUrl ?: recipe.imageUrl
                if (!displayUrl.isNullOrEmpty()) {
                    recipeImageView.visibility = View.VISIBLE
                    when {
                        displayUrl.startsWith("data:image") -> {
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
                            Toast.makeText(
                                context,
                                "Error: ${state.message}",
                                Toast.LENGTH_LONG
                            ).show()
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
        val ingredients = ingredientsEt.text.toString().trim()
        val tags = currentTags.joinToString(",")

        if (title.isEmpty()) {
            titleEt.error = "Please enter a title"
            return
        }

        val hasUrl = imageUrl.isNotEmpty()
        val hasDeviceImage = selectedImageUri != null

        val resolvedImageUrl = when {
            hasDeviceImage -> selectedImageUri.toString()
            hasUrl -> imageUrl
            else -> original.imageUrl
        }

        val updatedRecipe = original.copy(
            title = title,
            instructions = toInstructionSteps(instructions),
            ingredients = ingredients,
            imageUrl = resolvedImageUrl,
            imageRemoteUrl = if (hasDeviceImage) null else original.imageRemoteUrl,
            tags = TagValidator.sanitizeTags(tags)
        )

        recipeViewModel.updateRecipe(updatedRecipe, selectedImageUri, requireContext())
    }
}