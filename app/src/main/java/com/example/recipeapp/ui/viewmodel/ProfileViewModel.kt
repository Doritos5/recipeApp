package com.example.recipeapp.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recipeapp.auth.UserAuthManager
import com.example.recipeapp.data.UserRepository
import com.example.recipeapp.model.users.User
import com.example.recipeapp.model.users.UserDao
import com.example.recipeapp.storage.FirebaseStorageService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class ProfileState {
    object Idle : ProfileState()
    object Loading : ProfileState()

    data class Loaded(
        val displayName: String,
        val email: String,
        val profileImageUrl: String?,
        val userId: String,
        val username: String,
        val firstName: String,
        val lastName: String
    ) : ProfileState()

    data class Updated(val message: String) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authManager: UserAuthManager,
    private val userDao: UserDao,
    private val storageService: FirebaseStorageService,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Idle)
    val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

    fun loadProfile() {
        val firebaseUser = authManager.getCurrentUser()
        if (firebaseUser == null) {
            _profileState.value = ProfileState.Error("Not logged in")
            return
        }

        viewModelScope.launch {
            val localUser = withContext(Dispatchers.IO) {
                userDao.getUserById(firebaseUser.uid)
            }

            val firestoreData = userRepository.getUser(firebaseUser.uid)

            var imageUrl = localUser?.imageUrl
            if (imageUrl.isNullOrEmpty()) {
                val firestoreUrl = firestoreData?.get("imageUrl") as? String
                if (!firestoreUrl.isNullOrEmpty()) {
                    imageUrl = firestoreUrl
                    withContext(Dispatchers.IO) {
                        val existing = userDao.getUserById(firebaseUser.uid)
                        if (existing != null) {
                            userDao.updateUser(existing.copy(imageUrl = firestoreUrl))
                        }
                    }
                } else {
                    imageUrl = firebaseUser.photoUrl?.toString()
                }
            }

            val firestoreFirstName = firestoreData?.get("firstName") as? String ?: ""
            val firestoreLastName = firestoreData?.get("lastName") as? String ?: localUser?.lastName.orEmpty()
            val firestoreUsername = firestoreData?.get("username") as? String ?: localUser?.username.orEmpty()
            val firestoreName = firestoreData?.get("name") as? String ?: localUser?.name.orEmpty()

            val resolvedDisplayName = when {
                !firebaseUser.displayName.isNullOrBlank() -> firebaseUser.displayName!!
                firestoreName.isNotBlank() -> firestoreName
                !localUser?.name.isNullOrBlank() -> localUser?.name.orEmpty()
                else -> ""
            }

            val resolvedFirstName = if (firestoreFirstName.isNotBlank()) {
                firestoreFirstName
            } else {
                resolvedDisplayName.split(" ").firstOrNull().orEmpty()
            }

            val resolvedLastName = if (firestoreLastName.isNotBlank()) {
                firestoreLastName
            } else {
                resolvedDisplayName.split(" ").drop(1).joinToString(" ")
            }

            val resolvedUsername = if (firestoreUsername.isNotBlank()) {
                firestoreUsername
            } else {
                resolvedDisplayName
            }

            _profileState.value = ProfileState.Loaded(
                displayName = resolvedDisplayName,
                email = firebaseUser.email ?: localUser?.email ?: "",
                profileImageUrl = imageUrl,
                userId = firebaseUser.uid,
                username = resolvedUsername,
                firstName = resolvedFirstName,
                lastName = resolvedLastName
            )
        }
    }

    fun updateDisplayName(name: String) {
        val userId = authManager.getCurrentUserId() ?: return
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            try {
                authManager.updateDisplayName(name)

                userRepository.createOrUpdateUser(
                    uid = userId,
                    email = authManager.getEmail(),
                    name = name
                )

                withContext(Dispatchers.IO) {
                    val existing = userDao.getUserById(userId)
                    if (existing != null) {
                        userDao.updateUser(existing.copy(name = name))
                    } else {
                        userDao.createUser(
                            User(
                                uid = userId,
                                name = name,
                                email = authManager.getEmail() ?: ""
                            )
                        )
                    }
                }

                Log.d("PROFILE", "Display name updated to: $name")
                loadProfile()
                _profileState.value = ProfileState.Updated("Display name updated!")
            } catch (e: Exception) {
                _profileState.value = ProfileState.Error(e.message ?: "Failed to update name")
            }
        }
    }

    fun updateProfileDetails(
        username: String,
        firstName: String,
        lastName: String,
        currentPassword: String?,
        newPassword: String?
    ) {
        val userId = authManager.getCurrentUserId() ?: return

        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            try {
                val currentUser = authManager.getCurrentUser()
                    ?: throw IllegalStateException("No user logged in")

                val trimmedUsername = username.trim()
                val trimmedFirstName = firstName.trim()
                val trimmedLastName = lastName.trim()
                val trimmedCurrentPassword = currentPassword?.trim().orEmpty()
                val trimmedNewPassword = newPassword?.trim().orEmpty()

                val fullName = listOf(trimmedFirstName, trimmedLastName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { trimmedUsername.ifBlank { currentUser.displayName.orEmpty() } }

                val passwordChanged = trimmedNewPassword.isNotBlank()

                if (passwordChanged) {
                    if (trimmedCurrentPassword.isBlank()) {
                        throw IllegalStateException("Please enter your current password")
                    }
                    authManager.reauthenticate(trimmedCurrentPassword)

                    if (trimmedNewPassword.length < 6) {
                        throw IllegalStateException("New password must be at least 6 characters")
                    }

                    authManager.updatePassword(trimmedNewPassword)
                }

                if (fullName.isNotBlank() && fullName != currentUser.displayName.orEmpty()) {
                    authManager.updateDisplayName(fullName)
                }

                userRepository.createOrUpdateUser(
                    uid = userId,
                    email = currentUser.email.orEmpty(),
                    name = fullName,
                    firstName = trimmedFirstName,
                    lastName = trimmedLastName,
                    username = trimmedUsername
                )

                withContext(Dispatchers.IO) {
                    val existing = userDao.getUserById(userId)
                    if (existing != null) {
                        userDao.updateUser(
                            existing.copy(
                                name = fullName,
                                lastName = trimmedLastName,
                                username = trimmedUsername,
                                email = currentUser.email.orEmpty()
                            )
                        )
                    } else {
                        userDao.createUser(
                            User(
                                uid = userId,
                                name = fullName,
                                lastName = trimmedLastName,
                                username = trimmedUsername,
                                email = currentUser.email.orEmpty()
                            )
                        )
                    }
                }

                loadProfile()
                _profileState.value = ProfileState.Updated("Profile updated!")
            } catch (e: Exception) {
                Log.e("PROFILE", "Failed to update profile details: ${e.message}", e)
                _profileState.value = ProfileState.Error(
                    e.message ?: "Failed to update profile details"
                )
            }
        }
    }

    fun updateProfileImage(imageUri: Uri, context: android.content.Context) {
        val userId = authManager.getCurrentUserId() ?: return
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            try {
                val base64DataUri = withContext(Dispatchers.IO) {
                    storageService.compressProfileImageToBase64(context, imageUri)
                }
                Log.d("PROFILE", "Profile image compressed to Base64 (${base64DataUri.length} chars)")

                userRepository.createOrUpdateUser(
                    uid = userId,
                    email = authManager.getEmail(),
                    name = authManager.getDisplayName(),
                    imageUrl = base64DataUri
                )

                try {
                    authManager.updateProfileImage(base64DataUri)
                } catch (e: Exception) {
                    Log.w(
                        "PROFILE",
                        "Could not update Firebase Auth photoUrl (data URI too long): ${e.message}"
                    )
                }

                withContext(Dispatchers.IO) {
                    val existing = userDao.getUserById(userId)
                    if (existing != null) {
                        userDao.updateUser(existing.copy(imageUrl = base64DataUri))
                    } else {
                        userDao.createUser(
                            User(
                                uid = userId,
                                name = authManager.getDisplayName() ?: "",
                                email = authManager.getEmail() ?: "",
                                imageUrl = base64DataUri
                            )
                        )
                    }
                }

                loadProfile()
                _profileState.value = ProfileState.Updated("Profile image updated!")
            } catch (e: Exception) {
                Log.e("PROFILE", "Failed to save profile image: ${e.message}", e)
                _profileState.value = ProfileState.Error(e.message ?: "Failed to update image")
            }
        }
    }

    fun resetState() {
        loadProfile()
    }
}