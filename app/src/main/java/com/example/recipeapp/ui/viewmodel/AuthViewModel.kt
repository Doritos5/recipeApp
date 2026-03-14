package com.example.recipeapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recipeapp.auth.AuthResult
import com.example.recipeapp.auth.UserAuthManager
import com.example.recipeapp.data.UserRepository
import com.example.recipeapp.model.users.User
import com.example.recipeapp.model.users.UserDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * AuthViewModel handles all authentication operations.
 * Used by LoginFragment, SignUpFragment, and ProfileFragment (for logout).
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: UserAuthManager,
    private val userRepository: UserRepository,
    private val userDao: UserDao
) : ViewModel() {

    // --- Auth State ---
    private val _authState = MutableStateFlow<AuthResult?>(null)
    val authState: StateFlow<AuthResult?> = _authState.asStateFlow()

    /** Returns the current user's UID or null */
    fun getCurrentUserId(): String? = authManager.getCurrentUserId()

    /** Returns true if a user is logged in */
    fun isLoggedIn(): Boolean = authManager.isLoggedIn()

    fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        username: String
    ) {
        viewModelScope.launch {
            _authState.value = null
            val result = authManager.signUp(email, password)

            // On successful signup, create the full user document in Firestore + Room
            if (result is AuthResult.Success) {
                val uid = result.user.uid
                try {
                    // Write the full document to Firestore (cloud source of truth) with all user data
                    userRepository.createOrUpdateUser(
                        uid = uid,
                        email = email,
                        firstName = firstName,
                        lastName = lastName,
                        username = username,
                        password = password,
                        imageUrl = null,
                        isNewUser = true
                    )
                    // Also seed local Room cache
                    withContext(Dispatchers.IO) {
                        userDao.createUser(
                            User(
                                uid = uid,
                                email = email,
                                name = firstName,
                                lastName = lastName,
                                username = username
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Non-fatal: auth succeeded, just log
                    android.util.Log.w("AUTH", "Could not create Firestore user doc: ${e.message}")
                }
            }

            _authState.value = result
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = null
            val result = authManager.login(email, password)
            _authState.value = result
        }
    }

    fun logout() {
        authManager.logout()
        _authState.value = null
    }

    /** Resets the auth state (call after handling the result in UI). */
    fun resetAuthState() {
        _authState.value = null
    }
}
