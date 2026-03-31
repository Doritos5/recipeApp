package com.example.recipeapp.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.example.recipeapp.ui.viewmodel.ProfileState
import com.example.recipeapp.ui.viewmodel.ProfileViewModel
import com.example.recipeapp.util.ImageUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditProfileFragment : Fragment() {

    private val profileViewModel: ProfileViewModel by activityViewModels()

    private lateinit var backBtn: ImageView
    private lateinit var saveBtn: MaterialButton

    private lateinit var emailEt: TextInputEditText
    private lateinit var currentPasswordEt: TextInputEditText
    private lateinit var newPasswordEt: TextInputEditText
    private lateinit var usernameEt: TextInputEditText
    private lateinit var firstNameEt: TextInputEditText
    private lateinit var lastNameEt: TextInputEditText

    private lateinit var profileImageView: ImageView
    private lateinit var cameraIcon: ImageView
    private lateinit var progressBar: ProgressBar

    private var selectedImageUri: Uri? = null
    private var currentUsername: String = ""
    private var currentFirstName: String = ""
    private var currentLastName: String = ""

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedUri = ImageUtils.copyImageToInternalStorage(requireContext(), it)
            if (savedUri != null) {
                selectedImageUri = savedUri
                profileImageView.setImageURI(savedUri)
                cameraIcon.visibility = View.GONE
            } else {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_edit_profile, container, false)

        initViews(view)
        setupListeners()
        observeProfileState()
        profileViewModel.loadProfile()

        return view
    }

    private fun initViews(view: View) {
        backBtn = view.findViewById(R.id.backBtn)
        saveBtn = view.findViewById(R.id.profileSaveBtn)

        emailEt = view.findViewById(R.id.editProfileEmailEt)
        currentPasswordEt = view.findViewById(R.id.editProfileCurrentPasswordEt)
        newPasswordEt = view.findViewById(R.id.editProfileNewPasswordEt)
        usernameEt = view.findViewById(R.id.editProfileUsernameEt)
        firstNameEt = view.findViewById(R.id.editProfileFirstNameEt)
        lastNameEt = view.findViewById(R.id.editProfileLastNameEt)

        profileImageView = view.findViewById(R.id.editProfileImageView)
        cameraIcon = view.findViewById(R.id.editProfileCameraIcon)
        progressBar = view.findViewById(R.id.editProfileProgressBar)
    }

    private fun setupListeners() {
        backBtn.setOnClickListener {
            findNavController().navigateUp()
        }

        profileImageView.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        cameraIcon.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        saveBtn.setOnClickListener {
            saveProfile()
        }
    }

    private fun observeProfileState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.profileState.collect { state ->
                    when (state) {
                        is ProfileState.Idle -> {
                            progressBar.visibility = View.GONE
                            saveBtn.isEnabled = true
                        }

                        is ProfileState.Loading -> {
                            progressBar.visibility = View.VISIBLE
                            saveBtn.isEnabled = false
                        }

                        is ProfileState.Loaded -> {
                            progressBar.visibility = View.GONE
                            saveBtn.isEnabled = true

                            emailEt.setText(state.email)
                            usernameEt.setText(state.username)
                            firstNameEt.setText(state.firstName)
                            lastNameEt.setText(state.lastName)

                            currentUsername = state.username
                            currentFirstName = state.firstName
                            currentLastName = state.lastName

                            val imageUrl = state.profileImageUrl
                            if (!imageUrl.isNullOrEmpty()) {
                                when {
                                    imageUrl.startsWith("data:image") -> {
                                        try {
                                            val base64 = imageUrl.substringAfter("base64,")
                                            val bytes = Base64.decode(base64, Base64.NO_WRAP)
                                            val bitmap =
                                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            profileImageView.setImageBitmap(bitmap)
                                            cameraIcon.visibility = View.GONE
                                        } catch (e: Exception) {
                                            profileImageView.setImageResource(R.drawable.icon_account)
                                            cameraIcon.visibility = View.VISIBLE
                                        }
                                    }

                                    imageUrl.startsWith("file://") || imageUrl.startsWith("content://") -> {
                                        profileImageView.setImageURI(Uri.parse(imageUrl))
                                        cameraIcon.visibility = View.GONE
                                    }

                                    else -> {
                                        Picasso.get()
                                            .load(imageUrl)
                                            .placeholder(R.drawable.icon_account)
                                            .error(R.drawable.icon_account)
                                            .into(profileImageView)
                                        cameraIcon.visibility = View.GONE
                                    }
                                }
                            } else {
                                profileImageView.setImageResource(R.drawable.icon_account)
                                cameraIcon.visibility = View.VISIBLE
                            }
                        }

                        is ProfileState.Updated -> {
                            progressBar.visibility = View.GONE
                            saveBtn.isEnabled = true
                            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                            selectedImageUri = null
                            findNavController().navigateUp()
                        }

                        is ProfileState.Error -> {
                            progressBar.visibility = View.GONE
                            saveBtn.isEnabled = true
                            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun saveProfile() {
        val username = usernameEt.text?.toString()?.trim().orEmpty()
        val firstName = firstNameEt.text?.toString()?.trim().orEmpty()
        val lastName = lastNameEt.text?.toString()?.trim().orEmpty()
        val currentPassword = currentPasswordEt.text?.toString()?.trim().orEmpty()
        val newPassword = newPasswordEt.text?.toString()?.trim().orEmpty()

        if (username.isBlank() && firstName.isBlank() && lastName.isBlank()) {
            usernameEt.error = "Please enter username or name"
            return
        }

        val profileFieldsChanged =
            username != currentUsername || firstName != currentFirstName || lastName != currentLastName
        val passwordChanged = newPassword.isNotBlank()

        if (passwordChanged && currentPassword.isBlank()) {
            currentPasswordEt.error = "Please enter your current password"
            return
        }

        if (!profileFieldsChanged && !passwordChanged && selectedImageUri == null) {
            Toast.makeText(context, "Nothing to update", Toast.LENGTH_SHORT).show()
            return
        }

        profileViewModel.updateProfileDetails(
            username = username,
            firstName = firstName,
            lastName = lastName,
            currentPassword = currentPassword.ifBlank { null },
            newPassword = newPassword.ifBlank { null }
        )

        if (selectedImageUri != null) {
            profileViewModel.updateProfileImage(selectedImageUri!!, requireContext())
        }
    }
}