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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.UUID

@AndroidEntryPoint
class AddRecipeFragment : Fragment() {

    private val viewModel: RecipeViewModel by activityViewModels()

    // UI Components
    private lateinit var titleEt: TextInputEditText
    private lateinit var instructionsEt: TextInputEditText
    private lateinit var imageEt: TextInputEditText
    private lateinit var saveBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var pickImageBtn: Button
    private lateinit var recipeImageView: ImageView
    private lateinit var uploadProgressBar: ProgressBar

    // Selected image URI from gallery
    private var selectedImageUri: Uri? = null

    // GPS Components
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLat: Double? = 0.0
    private var currentLong: Double? = 0.0

    // --- Image Picker Launcher ---
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Copy the image to app's internal storage so it persists across restarts
            val savedUri = copyImageToInternalStorage(it)
            if (savedUri != null) {
                selectedImageUri = savedUri
                recipeImageView.setImageURI(savedUri)
                recipeImageView.visibility = View.VISIBLE
                // Clear the URL field since the user chose a device image instead
                imageEt.text?.clear()
            } else {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Location Permission Launcher ---
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
        val view = inflater.inflate(R.layout.fragment_add_recipe, container, false)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        initViews(view)
        setupListeners()
        observeUploadState()
        checkPermissionsAndGetLocation()

        return view
    }

    private fun checkPermissionsAndGetLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
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
    }

    private fun setupListeners() {
        cancelBtn.setOnClickListener {
            // Just go back
            parentFragmentManager.popBackStack()
        }

        pickImageBtn.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // When the user types a URL, clear the device-picked image
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
     * Observes the upload StateFlow from the ViewModel.
     * Shows progress on Loading, navigates back on Success, shows error on Error.
     */
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
                            Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_LONG)
                                .show()
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

        // Determine image source:
        // Option 1: User typed a URL → use it directly, no upload needed
        // Option 2: User picked an image from device → store local URI + attempt upload
        val hasUrl = imageUrl.isNotEmpty()
        val hasDeviceImage = selectedImageUri != null

        // If the user picked a device image, store the local URI as imageUrl (fallback for display).
        // If the user typed a URL, use that instead.
        val resolvedImageUrl = when {
            hasDeviceImage -> selectedImageUri.toString()  // local content:// URI
            hasUrl -> imageUrl                              // web URL
            else -> null
        }

        // Create a new recipe object
        val newRecipe = Recipe(
            id = UUID.randomUUID().toString(),
            title = title,
            instructions = instructions,
            imageUrl = resolvedImageUrl,
            description = "",
            ingredients = "",
            latitude = currentLat,
            longitude = currentLong
        )

        if (hasDeviceImage) {
            // Upload device image → save to Firebase → save to Room
            viewModel.addNewRecipe(newRecipe, selectedImageUri, requireContext())
        } else {
            // No device image to upload (URL or no image at all) → save directly
            viewModel.addNewRecipe(newRecipe, null, requireContext())
        }
    }

    /**
     * Copies an image from a temporary content:// URI to the app's internal storage.
     * Each image gets a unique filename so different recipes never share the same file.
     *
     * @param sourceUri The temporary content:// URI from the gallery picker.
     * @return A permanent file:// URI pointing to the saved copy, or null if copy failed.
     */
    private fun copyImageToInternalStorage(sourceUri: Uri): Uri? {
        return try {
            val context = requireContext()
            // Create a dedicated directory for recipe images
            val imagesDir = File(context.filesDir, "recipe_images")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            // Create a unique filename for this image
            val fileName = "recipe_${UUID.randomUUID()}.jpg"
            val destFile = File(imagesDir, fileName)

            // Copy the bytes from the content:// URI to the local file
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Return a file:// URI pointing to the permanent copy
            Uri.fromFile(destFile)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}