package com.example.recipeapp.ui.state

import com.example.recipeapp.model.recipes.Recipe

/**
 * Sealed class representing the state of an upload operation.
 * Used by ViewModels to communicate UI state for async operations.
 */
sealed class UploadState {
    /** No operation in progress. */
    object Idle : UploadState()

    /** An upload/save operation is currently in progress. */
    object Loading : UploadState()

    /** The operation completed successfully. */
    data class Success(val recipe: Recipe) : UploadState()

    /** The operation failed with an error message. */
    data class Error(val message: String) : UploadState()
}

