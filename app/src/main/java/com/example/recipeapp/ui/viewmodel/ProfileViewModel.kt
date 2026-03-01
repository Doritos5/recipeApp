package com.example.recipeapp.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recipeapp.auth.UserAuthManager
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

/**
 * Represents the state of the user profile screen.
 */
sealed class ProfileState {
    object Idle : ProfileState()
    object Loading : ProfileState()
    data class Loaded(
        val displayName: String,
        val email: String,
        val profileImageUrl: String?
    ) : ProfileState()
    data class Updated(val message: String) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

/**
 * ProfileViewModel handles user profile display and editing.
 *
 * Profile image strategy:
 * - Always saved LOCALLY in Room User table (imageUrl field) — this always works.
 * - Optionally also uploaded to Firebase Storage — if it works, great; if not, local image is used.
 * - Firebase Auth photoUrl is updated with whichever URL we have.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authManager: UserAuthManager,
    private val userDao: UserDao,
    private val storageService: FirebaseStorageService
) : ViewModel() {

    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Idle)
    val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

    /**
     * Loads the current user's profile.
     * Priority: Room local DB first (for image), then Firebase Auth (for name/email).
     */
    fun loadProfile() {
        val firebaseUser = authManager.getCurrentUser()
        if (firebaseUser == null) {
            _profileState.value = ProfileState.Error("Not logged in")
            return
        }

        viewModelScope.launch {
            // Try to get local user from Room
            val localUser = withContext(Dispatchers.IO) {
                userDao.getUserById(firebaseUser.uid)
            }

            // Use local image if available, otherwise fall back to Firebase Auth photoUrl
            val imageUrl = localUser?.imageUrl
                ?: firebaseUser.photoUrl?.toString()

            _profileState.value = ProfileState.Loaded(
                displayName = firebaseUser.displayName ?: localUser?.name ?: "",
                email = firebaseUser.email ?: localUser?.email ?: "",
                profileImageUrl = imageUrl
            )
        }
    }

    /**
     * Updates the display name in Firebase Auth + local Room.
     */
    fun updateDisplayName(name: String) {
        val userId = authManager.getCurrentUserId() ?: return
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            try {
                // Update Firebase Auth
                authManager.updateDisplayName(name)

                // Update local Room
                withContext(Dispatchers.IO) {
                    val existing = userDao.getUserById(userId)
                    if (existing != null) {
                        userDao.updateUser(existing.copy(name = name))
                    } else {
                        userDao.createUser(User(
                            uid = userId,
                            name = name,
                            email = authManager.getEmail() ?: ""
                        ))
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

    /**
     * Saves the profile image locally in Room.
     * TODO: When Firebase Storage is available (Blaze plan), also upload to Firebase Storage
     *       using storageService.uploadProfileImage() and store the remote URL.
     */
    fun updateProfileImage(imageUri: Uri, context: android.content.Context) {
        val userId = authManager.getCurrentUserId() ?: return
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            try {
                val localUriString = imageUri.toString()

                // Save local URI to Room (instant, always works)
                withContext(Dispatchers.IO) {
                    val existing = userDao.getUserById(userId)
                    if (existing != null) {
                        userDao.updateUser(existing.copy(imageUrl = localUriString))
                    } else {
                        userDao.createUser(User(
                            uid = userId,
                            name = authManager.getDisplayName() ?: "",
                            email = authManager.getEmail() ?: "",
                            imageUrl = localUriString
                        ))
                    }
                }
                Log.d("PROFILE", "Profile image saved locally: $localUriString")

                // TODO: Upload to Firebase Storage when Blaze plan is enabled:
                //  val remoteUrl = storageService.uploadProfileImage(context, imageUri, userId)
                //  authManager.updateProfileImage(remoteUrl)
                //  withContext(Dispatchers.IO) {
                //      val user = userDao.getUserById(userId)
                //      user?.let { userDao.updateUser(it.copy(imageUrl = remoteUrl)) }
                //  }

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

