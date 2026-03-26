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
import com.example.recipeapp.model.recipes.Recipe
import com.example.recipeapp.ui.state.UploadState
import com.example.recipeapp.util.ImageUtils
import com.example.recipeapp.util.TagValidator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID

@AndroidEntryPoint
class AddRecipeFragment : Fragment() {

    private val viewModel: RecipeViewModel by activityViewModels()

    private lateinit var titleEt: TextInputEditText
    private lateinit var instructionsEt: TextInputEditText
    private lateinit var imageEt: TextInputEditText
    private lateinit var saveBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var pickImageBtn: Button
    private lateinit var recipeImageView: ImageView
    private lateinit var uploadProgressBar: ProgressBar
    private lateinit var tagsEt: TextInputEditText

    private var selectedImageUri: Uri? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLat: Double? = 0.0
    private var currentLong: Double? = 0.0

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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        val view = inflater.inflate(R.layout.fragment_add_recipe, container, false)
        initViews(view)
        setupListeners()
        observeUploadState()
        checkPermissionsAndGetLocation()
        return view
    }

    private fun checkPermissionsAndGetLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getLocation()
        } else {
            requestLocationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun getLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLat = it.latitude
                    currentLong = it.longitude
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun initViews(view: View) {
        titleEt = view.findViewById(R.id.addRecipeTitleEt)
        instructionsEt = view.findViewById(R.id.addRecipeInstructionsEt)
        imageEt = view.findViewById(R.id.addRecipeImageEt)
        saveBtn = view.findViewById(R.id.addRecipeSaveBtn)
        cancelBtn = view.findViewById(R.id.addRecipeCancelBtn)
        pickImageBtn = view.findViewById(R.id.addRecipePickImageBtn)
        recipeImageView = view.findViewById(R.id.addRecipeImageView)
        uploadProgressBar = view.findViewById(R.id.addRecipeProgressBar)
        tagsEt = view.findViewById(R.id.addRecipeTagsEt)
    }

    private fun setupListeners() {
        cancelBtn.setOnClickListener { parentFragmentManager.popBackStack() }
        pickImageBtn.setOnClickListener { imagePickerLauncher.launch("image/*") }
        imageEt.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !imageEt.text.isNullOrEmpty()) {
                selectedImageUri = null
                recipeImageView.visibility = View.GONE
            }
        }
        saveBtn.setOnClickListener { saveRecipe() }
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

    private fun saveRecipe() {
        val title = titleEt.text.toString().trim()
        val instructions = instructionsEt.text.toString().trim()
        val imageUrl = imageEt.text.toString().trim()

        if (title.isEmpty()) {
            titleEt.error = "Please enter a title"
            return
        }

        val hasDeviceImage = selectedImageUri != null

        // If a device image was picked, don't store the local file:// path in imageUrl —
        // the repository will compress it to Base64 and store it in imageRemoteUrl instead.
        // imageUrl is only used for manual https:// URLs typed into the text field.
        val resolvedImageUrl = when {
            hasDeviceImage -> null
            imageUrl.isNotEmpty() -> imageUrl
            else -> null
        }

        val newRecipe = Recipe(
            id = UUID.randomUUID().toString(),
            title = title,
            instructions = instructions,
            imageUrl = resolvedImageUrl,
            description = "",
            ingredients = "",
            latitude = currentLat,
            longitude = currentLong,
            tags = TagValidator.sanitizeTags(tagsEt.text.toString().trim())
        )

        viewModel.addNewRecipe(newRecipe, if (hasDeviceImage) selectedImageUri else null, requireContext())
    }
}
