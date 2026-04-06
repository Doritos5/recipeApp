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
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.recipeapp.R
import com.example.recipeapp.auth.UserAuthManager
import com.example.recipeapp.ui.viewmodel.ProfileState
import com.example.recipeapp.ui.viewmodel.ProfileViewModel
import com.google.android.material.button.MaterialButton
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private val profileViewModel: ProfileViewModel by activityViewModels()

    private lateinit var backBtn: ImageView
    private lateinit var profileEditBtn: MaterialButton

    private lateinit var profileImageView: ImageView
    private lateinit var emailTv: TextView
    private lateinit var displayNameTv: TextView
    private lateinit var userIdTv: TextView
    private lateinit var progressBar: ProgressBar

    private var currentEmail: String = ""
    private var currentDisplayName: String = ""
    private var currentUserId: String = ""

    @Inject
    lateinit var userAuthManager: UserAuthManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        initViews(view)
        setupListeners()
        observeProfileState()

        profileViewModel.loadProfile()

        return view
    }

    private fun initViews(view: View) {
        backBtn = view.findViewById(R.id.backBtn)
        profileEditBtn = view.findViewById(R.id.profileEditBtn)

        profileImageView = view.findViewById(R.id.profileImageView)
        emailTv = view.findViewById(R.id.profileEmailTv)
        displayNameTv = view.findViewById(R.id.profileDisplayNameTv)
        userIdTv = view.findViewById(R.id.profileUserIdTv)
        progressBar = view.findViewById(R.id.profileProgressBar)
    }

    private fun setupListeners() {
        backBtn.setOnClickListener {
            findNavController().navigateUp()
        }

        profileEditBtn.setOnClickListener {
            if (userAuthManager.isGuest()) {
                Toast.makeText(requireContext(), getString(R.string.guest_profile_edit_blocked), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            findNavController().navigate(R.id.action_profileFragment_to_editProfileFragment)
        }
    }

    private fun observeProfileState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.profileState.collect { state ->
                    if (userAuthManager.isGuest()) {
                        progressBar.visibility = View.GONE
                        displayNameTv.text = getString(R.string.guest_profile_title)
                        emailTv.text = getString(R.string.guest_profile_message)
                        userIdTv.text = "-"
                        profileImageView.setImageResource(R.drawable.icon_account)
                        profileEditBtn.isEnabled = false
                        return@collect
                    }

                    profileEditBtn.isEnabled = true
                    when (state) {
                        is ProfileState.Idle -> {
                            progressBar.visibility = View.GONE
                        }

                        is ProfileState.Loading -> {
                            progressBar.visibility = View.VISIBLE
                        }

                        is ProfileState.Loaded -> {
                            progressBar.visibility = View.GONE

                            currentEmail = state.email
                            currentDisplayName = state.displayName
                            currentUserId = state.userId

                            emailTv.text = if (currentEmail.isNotBlank()) currentEmail else "-"
                            displayNameTv.text =
                                if (currentDisplayName.isNotBlank()) currentDisplayName else "No name"
                            userIdTv.text =
                                if (currentUserId.isNotBlank()) currentUserId else "-"

                            val imageUrl = state.profileImageUrl
                            if (!imageUrl.isNullOrEmpty()) {
                                when {
                                    imageUrl.startsWith("data:image") -> {
                                        try {
                                            val base64 = imageUrl.substringAfter("base64,")
                                            val bytes = Base64.decode(base64, Base64.NO_WRAP)
                                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            profileImageView.setImageBitmap(bitmap)
                                        } catch (e: Exception) {
                                            profileImageView.setImageResource(R.drawable.icon_account)
                                        }
                                    }

                                    imageUrl.startsWith("file://") || imageUrl.startsWith("content://") -> {
                                        profileImageView.setImageURI(Uri.parse(imageUrl))
                                    }

                                    else -> {
                                        Picasso.get()
                                            .load(imageUrl)
                                            .placeholder(R.drawable.icon_account)
                                            .error(R.drawable.icon_account)
                                            .into(profileImageView)
                                    }
                                }
                            } else {
                                profileImageView.setImageResource(R.drawable.icon_account)
                            }
                        }

                        is ProfileState.Updated -> {
                            progressBar.visibility = View.GONE
                            profileViewModel.loadProfile()
                        }

                        is ProfileState.Error -> {
                            progressBar.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }
}