package com.example.recipeapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.recipeapp.R
import com.example.recipeapp.RecipeViewModel
import com.example.recipeapp.model.recipes.Recipe
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID

class AddRecipeFragment : Fragment() {

    private lateinit var viewModel: RecipeViewModel

    // UI Components
    private lateinit var titleEt: TextInputEditText
    private lateinit var instructionsEt: TextInputEditText
    private lateinit var imageEt: TextInputEditText
    private lateinit var saveBtn: Button
    private lateinit var cancelBtn: Button

    // GPS Components
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLat: Double? = 0.0
    private var currentLong: Double? = 0.0

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
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
        viewModel = ViewModelProvider(this)[RecipeViewModel::class.java]

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        initViews(view)
        setupListeners()

        checkPermissionsAndGetLocation()

        return view
    }

    private fun checkPermissionsAndGetLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
    }

    private fun setupListeners() {
        cancelBtn.setOnClickListener {
            // Just go back
            parentFragmentManager.popBackStack()
        }

        saveBtn.setOnClickListener {
            saveRecipe()
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

        // Create a new recipe object
        val newRecipe = Recipe(
            id = UUID.randomUUID().toString(), // Generate unique ID
            title = title,
            instructions = instructions,
            imageUrl = imageUrl.ifEmpty { null }, // Handle empty URL
            description = "", // Optional description
            ingredients = "", // Optional ingredients
            latitude = currentLat,
            longitude = currentLong,
            authorId = "MyUser" // Hardcoded for now (until we have Login)
        )

        // Save to DB and Firebase
        viewModel.addRecipe(newRecipe)

        Toast.makeText(context, "Recipe Saved!", Toast.LENGTH_SHORT).show()

        // Return to list
        parentFragmentManager.popBackStack()
    }
}