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
 * 1. Upload to Firebase Storage → get a remote https:// URL
 * 2. Save that URL to Cloud Firestore (users/{uid}.imageUrl)  ← cloud-persistent
 * 3. Update Firebase Auth photoUrl
 * 4. Cache in local Room DB
 * Falls back to saving locally if Firebase Storage is unavailable (Spark plan / offline).
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authManager: UserAuthManager,
    private val userDao: UserDao,
    private val storageService: FirebaseStorageService,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Idle)
    val profileState: StateFlow<ProfileState> = _profileState.asStateFlow()

    /**
     * Loads the current user's profile.
     * Image priority: Room local cache → Firestore → Firebase Auth photoUrl.
     */
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

            // If Room has no image, fetch from Firestore (the cloud source of truth)
            var imageUrl = localUser?.imageUrl
            if (imageUrl.isNullOrEmpty()) {
                val firestoreData = userRepository.getUser(firebaseUser.uid)
                val firestoreUrl = firestoreData?.get("imageUrl") as? String
                if (!firestoreUrl.isNullOrEmpty()) {
                    imageUrl = firestoreUrl
                    // Cache in Room for next time
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

            _profileState.value = ProfileState.Loaded(
                displayName = firebaseUser.displayName ?: localUser?.name ?: "",
                email = firebaseUser.email ?: localUser?.email ?: "",
                profileImageUrl = imageUrl
            )
        }
    }

    /**
     * Updates the display name in Firebase Auth + Firestore + local Room.
     */
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
     * Saves the profile image to Firestore as a compressed Base64 data URI.
     * This works on the free Spark plan — no Firebase Storage needed.
     *
     * Flow:
     *  1. Compress image → Base64 string (stored directly in Firestore users/{uid}.imageUrl)
     *  2. Save to Firestore  ← cloud source of truth, visible on every device
     *  3. Update Firebase Auth photoUrl (best-effort, may fail for long strings)
     *  4. Cache in local Room DB
     */
    fun updateProfileImage(imageUri: Uri, context: android.content.Context) {
        val userId = authManager.getCurrentUserId() ?: return
        viewModelScope.launch {
            _profileState.value = ProfileState.Loading
            try {
                // 1. Compress image to a Base64 data URI on IO thread
                val base64DataUri = withContext(Dispatchers.IO) {
                    storageService.compressProfileImageToBase64(context, imageUri)
                }
                Log.d("PROFILE", "Profile image compressed to Base64 (${base64DataUri.length} chars)")

                // 2. Save Base64 URL to Firestore (cloud source of truth — works on Spark plan)
                userRepository.createOrUpdateUser(
                    uid = userId,
                    email = authManager.getEmail(),
                    name = authManager.getDisplayName(),
                    imageUrl = base64DataUri
                )
                Log.d("PROFILE", "Profile image Base64 saved to Firestore")

                // 3. Update Firebase Auth photoUrl (best-effort — Auth has a URL length limit)
                try {
                    authManager.updateProfileImage(base64DataUri)
                } catch (e: Exception) {
                    Log.w("PROFILE", "Could not update Firebase Auth photoUrl (data URI too long): ${e.message}")
                }

                // 4. Cache in local Room
                withContext(Dispatchers.IO) {
                    val existing = userDao.getUserById(userId)
                    if (existing != null) {
                        userDao.updateUser(existing.copy(imageUrl = base64DataUri))
                    } else {
                        userDao.createUser(User(
                            uid = userId,
                            name = authManager.getDisplayName() ?: "",
                            email = authManager.getEmail() ?: "",
                            imageUrl = base64DataUri
                        ))
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

