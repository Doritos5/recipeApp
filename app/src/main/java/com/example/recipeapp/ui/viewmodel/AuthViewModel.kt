package com.example.recipeapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recipeapp.auth.AuthResult
import com.example.recipeapp.auth.UserAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AuthViewModel handles all authentication operations.
 * Used by LoginFragment, SignUpFragment, and ProfileFragment (for logout).
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: UserAuthManager
) : ViewModel() {

    // --- Auth State ---
    private val _authState = MutableStateFlow<AuthResult?>(null)
    val authState: StateFlow<AuthResult?> = _authState.asStateFlow()

    /** Returns the current user's UID or null */
    fun getCurrentUserId(): String? = authManager.getCurrentUserId()

    /** Returns true if a user is logged in */
    fun isLoggedIn(): Boolean = authManager.isLoggedIn()

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = null
            val result = authManager.signUp(email, password)
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

