package com.example.recipeapp

import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    // --- Upload State (StateFlow for Add/Edit Recipe flow) ---
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    /** Returns the current user's UID or null */
    fun getCurrentUserId(): String? = authManager.getCurrentUserId()

    /** Returns the current user's display name, falling back to Recipe User */
    fun getCurrentUserName(): String = authManager.getDisplayName() ?: "Recipe User"

    /** Returns true if a user is logged in */
    fun isLoggedIn(): Boolean = authManager.isLoggedIn()

    // ========================
    // Recipe List Operations
    // ========================

    /**
     * Returns LiveData of recipes for the current user only (My Recipes).
     */
    fun getMyRecipes(userId: String): LiveData<List<Recipe>> {
        return repository.getMyRecipes(userId)
    }

    /**
     * Returns LiveData of a single recipe by ID (for Edit screen).
     */
    fun getRecipeById(recipeId: String): LiveData<Recipe?> {
        return repository.getRecipeById(recipeId)
    }

    fun reloadRecipes() {
        viewModelScope.launch {
            repository.refreshRecipes()
        }
    }

    /**
     * Refreshes only the current user's recipes from Firestore.
     */
    fun reloadMyRecipes(userId: String) {
        viewModelScope.launch {
            repository.refreshMyRecipes(userId)
        }
    }

    // ========================
    // Add Recipe
    // ========================

    /**
     * Add a new recipe with optional image upload.
     * Updates [uploadState] to Loading → Success/Error.
     */
    fun addNewRecipe(recipe: Recipe, imageUri: Uri?, context: android.content.Context) {
        val userId = authManager.getCurrentUserId()

        viewModelScope.launch {
            _uploadState.value = UploadState.Loading
            try {
                if (userId != null) {
                    val savedRecipe = repository.addRecipeWithImage(
                        recipe = recipe,
                        imageUri = imageUri,
                        userId = userId,
                        context = context
                    )
                    _uploadState.value = UploadState.Success(savedRecipe)
                } else {
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

    // ========================
    // Update Recipe
    // ========================

    /**
     * Update an existing recipe with optional new image upload.
     * Updates [uploadState] to Loading → Success/Error.
     */
    fun updateRecipe(recipe: Recipe, imageUri: Uri?, context: android.content.Context) {
        val userId = authManager.getCurrentUserId()

        viewModelScope.launch {
            _uploadState.value = UploadState.Loading
            try {
                if (userId != null) {
                    val updatedRecipe = repository.updateRecipeWithImage(
                        recipe = recipe,
                        imageUri = imageUri,
                        userId = userId,
                        context = context
                    )
                    _uploadState.value = UploadState.Success(updatedRecipe)
                } else {
                    val localRecipe = recipe.copy(authorId = "local_user")
                    repository.addLocal(localRecipe) // insert with REPLACE
                    _uploadState.value = UploadState.Success(localRecipe)
                }
                Log.d("RECIPE_TEST", "Recipe updated successfully: ${recipe.title}")
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error(e.message ?: "Failed to update recipe.")
                Log.e("RECIPE_TEST", "Error updating recipe: ${e.message}")
            }
        }
    }

    // ========================
    // Delete Recipe
    // ========================

    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch {
            try {
                repository.delete(recipe)
                Log.d("RECIPE_TEST", "Recipe deleted: ${recipe.title}")
            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Error deleting recipe: ${e.message}")
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

    /** Resets the upload state back to Idle (call after handling Success/Error in UI). */
    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
    }

    // ========================
    // LIKE & COMMENT
    // ========================

    fun toggleLike(recipeId: String) {
        val userId = authManager.getCurrentUserId() ?: return
        viewModelScope.launch {
            repository.toggleLike(recipeId, userId)
        }
    }

    fun addComment(recipeId: String, text: String, userName: String) {
        val userId = authManager.getCurrentUserId() ?: return
        viewModelScope.launch {
            repository.addComment(recipeId, userId, text, userName)
            // Refresh comments after adding to ensure list updates
            repository.refreshComments(recipeId)
        }
    }

    fun editComment(recipeId: String, commentId: String, newText: String) {
        if (!isLoggedIn()) return
        viewModelScope.launch {
            repository.editComment(recipeId, commentId, newText)
        }
    }

    fun deleteComment(recipeId: String, commentId: String) {
        if (!isLoggedIn()) return
        viewModelScope.launch {
            repository.deleteComment(recipeId, commentId)
        }
    }

    fun getCommentsForRecipe(recipeId: String): LiveData<List<com.example.recipeapp.model.recipes.Comment>> {
        return repository.getCommentsForRecipe(recipeId)
    }

    fun refreshComments(recipeId: String) {
        viewModelScope.launch {
            repository.refreshComments(recipeId)
        }
    }

    fun refreshLikeStatus(recipeId: String) {
        val userId = authManager.getCurrentUserId() ?: return
        viewModelScope.launch {
            repository.refreshLikeStatus(recipeId, userId)
        }
    }

    fun checkIfLiked(recipeId: String): LiveData<Boolean> {
        val userId = authManager.getCurrentUserId() ?: return androidx.lifecycle.MutableLiveData(false)
        return repository.isLiked(recipeId, userId)
    }
}