package com.example.recipeapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.recipeapp.R
import com.example.recipeapp.RecipeViewModel
import com.example.recipeapp.auth.UserAuthManager
import com.example.recipeapp.model.recipes.Recipe
import com.example.recipeapp.ui.state.UploadState
import com.example.recipeapp.ui.util.TagChipUtils
import com.example.recipeapp.util.ImageUtils
import com.example.recipeapp.util.LocationHelper
import com.example.recipeapp.util.TagValidator
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import androidx.navigation.fragment.findNavController
import android.location.LocationListener

@AndroidEntryPoint
class AddRecipeFragment : Fragment() {

    private val viewModel: RecipeViewModel by activityViewModels()

    private lateinit var backBtn: ImageView
    private lateinit var titleEt: TextInputEditText
    private lateinit var ingredientsEt: TextInputEditText
    private lateinit var instructionsEt: TextInputEditText
    private lateinit var imageEt: TextInputEditText
    private lateinit var saveBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var pickImageBtn: Button
    private lateinit var recipeImageView: ImageView
    private lateinit var uploadProgressBar: ProgressBar
    private lateinit var tagsEt: TextInputEditText
    private lateinit var addTagBtn: MaterialButton
    private lateinit var tagsChipGroup: com.google.android.material.chip.ChipGroup
    private val pendingTags = mutableListOf<String>()

    private var selectedImageUri: Uri? = null

    private var currentLat: Double? = 0.0
    private var currentLong: Double? = 0.0
    private var pendingLocationListener: LocationListener? = null

    @Inject
    lateinit var userAuthManager: UserAuthManager

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

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            getLocation()
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (userAuthManager.isGuest()) {
            Toast.makeText(requireContext(), getString(R.string.guest_add_recipe_blocked), Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return null
        }

        val view = inflater.inflate(R.layout.fragment_add_recipe, container, false)
        initViews(view)
        setupListeners()
        observeUploadState()
        checkPermissionsAndGetLocation()
        return view
    }

    override fun onStop() {
        super.onStop()
        LocationHelper.removeUpdates(requireContext(), pendingLocationListener)
        pendingLocationListener = null
    }

    private fun checkPermissionsAndGetLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getLocation()
        } else {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun getLocation() {
        val context = requireContext()
        if (!LocationHelper.isLocationEnabled(context)) {
            Toast.makeText(context, getString(R.string.nearby_location_disabled), Toast.LENGTH_SHORT).show()
            return
        }

        val lastLocation = LocationHelper.getBestLastKnownLocation(context)
        if (lastLocation != null) {
            currentLat = lastLocation.latitude
            currentLong = lastLocation.longitude
            return
        }

        LocationHelper.removeUpdates(context, pendingLocationListener)
        pendingLocationListener = LocationHelper.requestSingleUpdate(context) { location ->
            pendingLocationListener = null
            if (location != null) {
                currentLat = location.latitude
                currentLong = location.longitude
            }
        }
    }

    private fun initViews(view: View) {
        backBtn = view.findViewById(R.id.backBtn)
        titleEt = view.findViewById(R.id.addRecipeTitleEt)
        ingredientsEt = view.findViewById(R.id.addRecipeIngredientsEt)
        instructionsEt = view.findViewById(R.id.addRecipeInstructionsEt)
        imageEt = view.findViewById(R.id.addRecipeImageEt)
        saveBtn = view.findViewById(R.id.addRecipeSaveBtn)
        cancelBtn = view.findViewById(R.id.addRecipeCancelBtn)
        pickImageBtn = view.findViewById(R.id.addRecipePickImageBtn)
        recipeImageView = view.findViewById(R.id.addRecipeImageView)
        uploadProgressBar = view.findViewById(R.id.addRecipeProgressBar)
        tagsEt = view.findViewById(R.id.addRecipeTagsEt)
        addTagBtn = view.findViewById(R.id.addTagBtn)
        tagsChipGroup = view.findViewById(R.id.tagsChipGroup)
    }

    private fun setupListeners() {
        backBtn.setOnClickListener { parentFragmentManager.popBackStack() }
        cancelBtn.setOnClickListener { parentFragmentManager.popBackStack() }
        pickImageBtn.setOnClickListener { imagePickerLauncher.launch("image/*") }

        imageEt.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !imageEt.text.isNullOrEmpty()) {
                selectedImageUri = null
                recipeImageView.visibility = View.GONE
            }
        }

        addTagBtn.setOnClickListener {
            addTagFromInput()
        }

        saveBtn.setOnClickListener { saveRecipe() }
    }

    private fun addTagFromInput() {
        val rawInput = tagsEt.text?.toString()?.trim().orEmpty()

        if (rawInput.isEmpty()) {
            tagsEt.error = "Please enter a tag"
            return
        }

        var hasAdded = false
        var hasDuplicate = false

        TagChipUtils.splitInputTags(rawInput).forEach { tag ->
            val added = TagChipUtils.addTagIfValid(
                rawTag = tag,
                tags = pendingTags,
                chipGroup = tagsChipGroup,
                onDuplicate = { hasDuplicate = true },
                onInvalid = { tagsEt.error = "Please enter a valid tag" }
            )
            hasAdded = hasAdded || added
        }

        when {
            hasAdded -> {
                tagsEt.text?.clear()
                tagsEt.error = null
            }
            hasDuplicate -> tagsEt.error = "Tag already added"
            else -> tagsEt.error = "Please enter a valid tag"
        }
    }

    private fun observeUploadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uploadState.collect { state ->
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
                            Toast.makeText(context, "Recipe Saved!", Toast.LENGTH_SHORT).show()
                            viewModel.resetUploadState()
                            parentFragmentManager.popBackStack()
                        }
                        is UploadState.Error -> {
                            uploadProgressBar.visibility = View.GONE
                            saveBtn.isEnabled = true
                            Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                            viewModel.resetUploadState()
                        }
                    }
                }
            }
        }
    }

    private fun toInstructionSteps(raw: String): List<String> {
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun saveRecipe() {
        if (userAuthManager.isGuest()) {
            Toast.makeText(requireContext(), getString(R.string.guest_add_recipe_blocked), Toast.LENGTH_SHORT).show()
            return
        }

        val title = titleEt.text.toString().trim()
        val ingredients = ingredientsEt.text.toString().trim()
        val instructions = instructionsEt.text.toString().trim()
        val imageUrl = imageEt.text.toString().trim()

        if (title.isEmpty()) {
            titleEt.error = "Please enter a title"
            return
        }

        if (ingredients.isEmpty()) {
            ingredientsEt.error = "Please enter ingredients"
            return
        }

        if (instructions.isEmpty()) {
            instructionsEt.error = "Please enter instructions"
            return
        }

        val allTags = mutableListOf<String>()
        allTags.addAll(pendingTags)

        val typedTags = TagValidator.sanitizeTags(tagsEt.text.toString().trim())
        allTags.addAll(typedTags)

        val finalTags = pendingTags
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

        val hasDeviceImage = selectedImageUri != null

        val resolvedImageUrl = when {
            hasDeviceImage -> null
            imageUrl.isNotEmpty() -> imageUrl
            else -> null
        }

        val newRecipe = Recipe(
            id = UUID.randomUUID().toString(),
            title = title,
            instructions = toInstructionSteps(instructions),
            imageUrl = resolvedImageUrl,
            description = "",
            ingredients = ingredients,
            authorName = "Recipe User",
            createdAt = System.currentTimeMillis(),
            latitude = currentLat,
            longitude = currentLong,
            tags = finalTags
        )

        viewModel.addNewRecipe(
            newRecipe,
            if (hasDeviceImage) selectedImageUri else null,
            requireContext()
        )
    }
}