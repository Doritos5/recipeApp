package com.example.recipeapp.ui

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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.recipeapp.R
import com.example.recipeapp.ui.viewmodel.AuthViewModel
import com.example.recipeapp.ui.viewmodel.ProfileState
import com.example.recipeapp.ui.viewmodel.ProfileViewModel
import com.example.recipeapp.util.ImageUtils
import com.google.android.material.textfield.TextInputEditText
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * ProfileFragment displays and allows editing of the user's profile.
 * Contains logout functionality.
 */
@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private val profileViewModel: ProfileViewModel by activityViewModels()
    private val authViewModel: AuthViewModel by activityViewModels()

    private lateinit var profileImageView: ImageView
    private lateinit var pickImageBtn: Button
    private lateinit var emailEt: TextInputEditText
    private lateinit var displayNameEt: TextInputEditText
    private lateinit var updateBtn: Button
    private lateinit var logoutBtn: Button
    private lateinit var progressBar: ProgressBar

    private var selectedImageUri: Uri? = null

    // --- Image Picker Launcher ---
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedUri = ImageUtils.copyImageToInternalStorage(requireContext(), it)
            if (savedUri != null) {
                selectedImageUri = savedUri
                profileImageView.setImageURI(savedUri)
            } else {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        initViews(view)
        setupListeners()
        observeProfileState()

        // Load profile data
        profileViewModel.loadProfile()

        return view
    }

    private fun initViews(view: View) {
        profileImageView = view.findViewById(R.id.profileImageView)
        pickImageBtn = view.findViewById(R.id.profilePickImageBtn)
        emailEt = view.findViewById(R.id.profileEmailEt)
        displayNameEt = view.findViewById(R.id.profileDisplayNameEt)
        updateBtn = view.findViewById(R.id.profileUpdateBtn)
        logoutBtn = view.findViewById(R.id.profileLogoutBtn)
        progressBar = view.findViewById(R.id.profileProgressBar)
    }

    private fun setupListeners() {
        pickImageBtn.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        updateBtn.setOnClickListener {
            val newName = displayNameEt.text.toString().trim()

            // Update display name if changed
            if (newName.isNotEmpty()) {
                profileViewModel.updateDisplayName(newName)
            }

            // Upload new profile image if selected
            if (selectedImageUri != null) {
                profileViewModel.updateProfileImage(selectedImageUri!!, requireContext())
            }

            if (newName.isEmpty() && selectedImageUri == null) {
                Toast.makeText(context, "Nothing to update", Toast.LENGTH_SHORT).show()
            }
        }

        logoutBtn.setOnClickListener {
            // Sign out via AuthViewModel
            authViewModel.logout()
            // Navigate to login, clearing entire back stack
            findNavController().navigate(
                R.id.loginFragment,
                null,
                androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.nav_graph, true)
                    .build()
            )
        }
    }

    private fun observeProfileState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.profileState.collect { state ->
                    when (state) {
                        is ProfileState.Idle -> {
                            progressBar.visibility = View.GONE
                        }
                        is ProfileState.Loading -> {
                            progressBar.visibility = View.VISIBLE
                            updateBtn.isEnabled = false
                        }
                        is ProfileState.Loaded -> {
                            progressBar.visibility = View.GONE
                            updateBtn.isEnabled = true

                            emailEt.setText(state.email)
                            displayNameEt.setText(state.displayName)

                            // Load profile image (handle local file:// and remote https://)
                            val imageUrl = state.profileImageUrl
                            if (!imageUrl.isNullOrEmpty()) {
                                if (imageUrl.startsWith("file://") || imageUrl.startsWith("content://")) {
                                    // Local image — load directly via URI
                                    profileImageView.setImageURI(Uri.parse(imageUrl))
                                } else {
                                    // Remote URL — load via Picasso
                                    Picasso.get()
                                        .load(imageUrl)
                                        .placeholder(R.drawable.icon_account)
                                        .error(R.drawable.icon_account)
                                        .into(profileImageView)
                                }
                            } else {
                                profileImageView.setImageResource(R.drawable.icon_account)
                            }
                        }
                        is ProfileState.Updated -> {
                            progressBar.visibility = View.GONE
                            updateBtn.isEnabled = true
                            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                            selectedImageUri = null
                        }
                        is ProfileState.Error -> {
                            progressBar.visibility = View.GONE
                            updateBtn.isEnabled = true
                            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}

