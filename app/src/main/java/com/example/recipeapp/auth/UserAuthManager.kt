package com.example.recipeapp.auth

import android.net.Uri
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Singleton
class UserAuthManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {

    fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid
    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser
    fun isLoggedIn(): Boolean = firebaseAuth.currentUser != null
    fun isGuest(): Boolean = firebaseAuth.currentUser == null
    fun getDisplayName(): String? = firebaseAuth.currentUser?.displayName
    fun getEmail(): String? = firebaseAuth.currentUser?.email
    fun getProfileImageUrl(): String? = firebaseAuth.currentUser?.photoUrl?.toString()

    suspend fun signUp(email: String, password: String): AuthResult {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Sign up succeeded but user is null.")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Sign up failed.")
        }
    }

    suspend fun login(email: String, password: String): AuthResult {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Login succeeded but user is null.")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Login failed.")
        }
    }

    suspend fun updateDisplayName(name: String) {
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("No user logged in")
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(name)
            .build()
        user.updateProfile(profileUpdates).await()
    }

    suspend fun updateProfileImage(photoUrl: String) {
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("No user logged in")
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setPhotoUri(Uri.parse(photoUrl))
            .build()
        user.updateProfile(profileUpdates).await()
    }

    suspend fun reauthenticate(currentPassword: String) {
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("No user logged in")
        val email = user.email ?: throw IllegalStateException("Current user has no email")

        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential).await()
    }

    suspend fun updatePassword(newPassword: String) {
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("No user logged in")
        user.updatePassword(newPassword).await()
    }

    fun logout() {
        firebaseAuth.signOut()
    }
}