package com.example.recipeapp.auth

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sealed class representing the result of an authentication operation.
 */
sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * UserAuthManager wraps FirebaseAuth and provides suspend functions
 * for Sign Up, Login, Logout, profile updates, and current user queries.
 */
@Singleton
class UserAuthManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {

    /** Returns the current Firebase user's UID, or null if not logged in. */
    fun getCurrentUserId(): String? = firebaseAuth.currentUser?.uid

    /** Returns the current FirebaseUser, or null if not logged in. */
    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser

    /** Returns true if a user is currently signed in. */
    fun isLoggedIn(): Boolean = firebaseAuth.currentUser != null

    /** Returns the display name of the current user. */
    fun getDisplayName(): String? = firebaseAuth.currentUser?.displayName

    /** Returns the email of the current user. */
    fun getEmail(): String? = firebaseAuth.currentUser?.email

    /** Returns the profile image URL of the current user. */
    fun getProfileImageUrl(): String? = firebaseAuth.currentUser?.photoUrl?.toString()

    /**
     * Creates a new account with email and password.
     * @return [AuthResult.Success] with the FirebaseUser, or [AuthResult.Error] with a message.
     */
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

    /**
     * Signs in with email and password.
     * @return [AuthResult.Success] with the FirebaseUser, or [AuthResult.Error] with a message.
     */
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

    /**
     * Updates the current user's display name.
     */
    suspend fun updateDisplayName(name: String) {
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("No user logged in")
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(name)
            .build()
        user.updateProfile(profileUpdates).await()
    }

    /**
     * Updates the current user's profile photo URL.
     */
    suspend fun updateProfileImage(photoUrl: String) {
        val user = firebaseAuth.currentUser ?: throw IllegalStateException("No user logged in")
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setPhotoUri(Uri.parse(photoUrl))
            .build()
        user.updateProfile(profileUpdates).await()
    }

    /**
     * Signs out the current user.
     */
    fun logout() {
        firebaseAuth.signOut()
    }
}

