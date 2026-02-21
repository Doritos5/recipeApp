package com.example.recipeapp

import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recipeapp.auth.AuthResult
import com.example.recipeapp.auth.UserAuthManager
import com.example.recipeapp.data.RecipeRepository
import com.example.recipeapp.model.recipes.Recipe
import com.example.recipeapp.ui.state.UploadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecipeViewModel @Inject constructor(
    private val repository: RecipeRepository,
    private val authManager: UserAuthManager
) : ViewModel() {

    // --- Recipe List (LiveData, observed by RecipesListFragment) ---
    val recipes: LiveData<List<Recipe>> = repository.allRecipes

    // --- Upload State (StateFlow for Add Recipe flow) ---
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    // --- Auth State ---
    private val _authState = MutableStateFlow<AuthResult?>(null)
    val authState: StateFlow<AuthResult?> = _authState.asStateFlow()

    /** Returns the current user's UID or null */
    fun getCurrentUserId(): String? = authManager.getCurrentUserId()

    /** Returns true if a user is logged in */
    fun isLoggedIn(): Boolean = authManager.isLoggedIn()

    // ========================
    // Recipe Operations
    // ========================

    fun reloadRecipes() {
        viewModelScope.launch {
            repository.refreshRecipes()
        }
    }

    /**
     * Add a new recipe with optional image upload.
     * Updates [uploadState] to Loading → Success/Error.
     *
     * Room DB is updated ONLY after Firebase metadata is successfully created.
     *
     * @param recipe The recipe to save (id should already be generated).
     * @param imageUri Optional URI of the selected/captured image.
     * @param context Android context for image compression.
     */
    fun addNewRecipe(recipe: Recipe, imageUri: Uri?, context: android.content.Context) {
        val userId = authManager.getCurrentUserId()

        viewModelScope.launch {
            _uploadState.value = UploadState.Loading
            try {
                if (userId != null) {
                    // User is logged in → full flow: upload image + Firestore + Room
                    val savedRecipe = repository.addRecipeWithImage(
                        recipe = recipe,
                        imageUri = imageUri,
                        userId = userId,
                        context = context
                    )
                    _uploadState.value = UploadState.Success(savedRecipe)
                } else {
                    // User is NOT logged in → save locally only (no Firebase)
                    // The recipe already has imageUrl set (local URI or web URL) from the fragment
                    val localRecipe = recipe.copy(authorId = "local_user")
                    repository.addLocal(localRecipe)
                    _uploadState.value = UploadState.Success(localRecipe)
                }
                Log.d("RECIPE_TEST", "Recipe added successfully: ${recipe.title}")
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error(e.message ?: "Failed to save recipe.")
                Log.e("RECIPE_TEST", "Error adding recipe: ${e.message}")
            }
        }
    }

    /**
     * Legacy add (no image upload). Kept for backward compatibility.
     */
    fun addRecipe(recipe: Recipe) {
        viewModelScope.launch {
            repository.add(recipe)
            Log.d("RECIPE_TEST", "Recipe added successfully: ${recipe.title}")
        }
    }

    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch {
            repository.delete(recipe)
            Log.d("RECIPE_TEST", "Recipe deleted: ${recipe.title}")
        }
    }

    /** Resets the upload state back to Idle (call after handling Success/Error in UI). */
    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
    }

    // ========================
    // Auth Operations
    // ========================

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = null // reset
            val result = authManager.signUp(email, password)
            _authState.value = result
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = null // reset
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