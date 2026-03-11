package com.example.recipeapp.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import com.example.recipeapp.model.recipes.Recipe
import com.example.recipeapp.storage.FirebaseStorageService
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val firestore: FirebaseFirestore,
    private val storageService: FirebaseStorageService
) {

    val allRecipes: LiveData<List<Recipe>> = recipeDao.getAllRecipes()

    /**
     * Returns LiveData of recipes created by a specific user (for My Recipes screen).
     */
    fun getMyRecipes(userId: String): LiveData<List<Recipe>> {
        return recipeDao.getRecipesByAuthor(userId)
    }

    /**
     * Returns LiveData of a single recipe by ID.
     */
    fun getRecipeById(recipeId: String): LiveData<Recipe?> {
        return recipeDao.getRecipeById(recipeId)
    }

    /**
     * Returns a single recipe by ID (suspend, non-LiveData).
     */
    suspend fun getRecipeByIdOnce(recipeId: String): Recipe? {
        return withContext(Dispatchers.IO) {
            recipeDao.getRecipeByIdOnce(recipeId)
        }
    }

    /**
     * Refreshes the local Room cache from both the public API and Firebase Firestore.
     */
    suspend fun refreshRecipes() {
        withContext(Dispatchers.IO) {
            // 1. Fetch from Public API
            try {
                val apiResponse = RetrofitClient.api.getRecipes().execute()

                if (apiResponse.isSuccessful) {
                    val rawList = apiResponse.body()?.meals
                    val processedList = rawList?.map { recipe ->
                        recipe.copy(
                            authorId = "API",
                            description = ""
                        )
                    }
                    processedList?.let {
                        recipeDao.insertAll(it)
                        Log.d("RECIPE_TEST", "Saved ${it.size} recipes with authorId='API'")
                    }
                }
            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Error fetching from API: ${e.message}")
            }

            // 2. Fetch from Firebase (User Generated) — only if Firebase is reachable
            try {
                val snapshot = firestore.collection("recipes").get().await()
                val firebaseList = snapshot.toObjects(Recipe::class.java)
                if (firebaseList.isNotEmpty()) {
                    recipeDao.insertAll(firebaseList)
                }
            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Error fetching from Firebase (skipped): ${e.message}")
            }
        }
    }

    /**
     * Refreshes only the current user's recipes from Firestore into Room.
     * Uses data isolation: whereEqualTo("authorId", userId).
     */
    suspend fun refreshMyRecipes(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("recipes")
                    .whereEqualTo("authorId", userId)
                    .get()
                    .await()
                val firebaseList = snapshot.toObjects(Recipe::class.java)
                if (firebaseList.isNotEmpty()) {
                    recipeDao.insertAll(firebaseList)
                }
                Log.d("RECIPE_TEST", "Refreshed ${firebaseList.size} recipes for user $userId")
            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Error refreshing my recipes: ${e.message}")
            }
        }
    }

    /**
     * Adds a new recipe WITH an optional image.
     * Image is saved locally (file:// URI stored in imageUrl).
     * Recipe is saved to both Room and Firestore.
     */
    suspend fun addRecipeWithImage(
        recipe: Recipe,
        imageUri: Uri?,
        userId: String,
        context: Context
    ): Recipe {
        return withContext(Dispatchers.IO) {
            // Compress picked image to Base64 and store in imageRemoteUrl.
            // This works on the free Spark plan — no Firebase Storage needed.
            // The Base64 string is saved directly inside the Firestore document,
            // so it is available on any device that loads the recipe.
            var base64ImageUrl: String? = null
            if (imageUri != null) {
                try {
                    base64ImageUrl = storageService.compressRecipeImageToBase64(context, imageUri)
                    Log.d("RECIPE_TEST", "Recipe image compressed to Base64 (${base64ImageUrl.length} chars)")
                } catch (e: Exception) {
                    Log.w("RECIPE_TEST", "Could not compress recipe image: ${e.message}")
                }
            }

            val finalRecipe = recipe.copy(
                authorId = userId,
                imageRemoteUrl = base64ImageUrl ?: recipe.imageRemoteUrl
            )

            // Save to Firestore
            try {
                firestore.collection("recipes")
                    .document(finalRecipe.id)
                    .set(finalRecipe)
                    .await()
                Log.d("RECIPE_TEST", "Recipe saved to Firebase successfully!")
            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Firebase save failed (saving locally only): ${e.message}")
            }

            // Always save to local Room DB
            recipeDao.insert(finalRecipe)
            Log.d("RECIPE_TEST", "Recipe saved to local DB: ${finalRecipe.title}")

            finalRecipe
        }
    }

    /**
     * Updates an existing recipe with optional new image.
     * Image is saved locally (file:// URI stored in imageUrl).
     */
    suspend fun updateRecipeWithImage(
        recipe: Recipe,
        imageUri: Uri?,
        userId: String,
        context: Context
    ): Recipe {
        return withContext(Dispatchers.IO) {
            // If a new image was picked, compress it to Base64.
            // If no new image was picked, keep the existing imageRemoteUrl.
            var base64ImageUrl: String? = recipe.imageRemoteUrl
            if (imageUri != null) {
                try {
                    base64ImageUrl = storageService.compressRecipeImageToBase64(context, imageUri)
                    Log.d("RECIPE_TEST", "Recipe image re-compressed to Base64 (${base64ImageUrl.length} chars)")
                } catch (e: Exception) {
                    Log.w("RECIPE_TEST", "Could not compress updated recipe image: ${e.message}")
                }
            }

            val updatedRecipe = recipe.copy(
                authorId = userId,
                imageRemoteUrl = base64ImageUrl
            )

            // Update in Firestore
            try {
                firestore.collection("recipes")
                    .document(updatedRecipe.id)
                    .set(updatedRecipe)
                    .await()
                Log.d("RECIPE_TEST", "Recipe updated in Firebase successfully!")
            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Firebase update failed (updating locally only): ${e.message}")
            }

            // Update in local Room DB
            recipeDao.update(updatedRecipe)
            Log.d("RECIPE_TEST", "Recipe updated in local DB: ${updatedRecipe.title}")

            updatedRecipe
        }
    }

    /**
     * Simple add (legacy — no image upload, saves locally first then to Firebase).
     */
    suspend fun add(recipe: Recipe) {
        recipeDao.insert(recipe)
        try {
            firestore.collection("recipes")
                .document(recipe.id)
                .set(recipe)
                .await()
            Log.d("RECIPE_TEST", "Recipe saved to Firebase successfully!")
        } catch (e: Exception) {
            Log.e("RECIPE_TEST", "Failed to save to Firebase: ${e.message}")
        }
    }

    /**
     * Save only to local Room DB (no Firebase interaction).
     * Used when the user is not logged in.
     */
    suspend fun addLocal(recipe: Recipe) {
        withContext(Dispatchers.IO) {
            recipeDao.insert(recipe)
            Log.d("RECIPE_TEST", "Recipe saved locally (offline): ${recipe.title}")
        }
    }

    /**
     * Delete a recipe from both local Room DB and Firebase Firestore.
     */
    suspend fun delete(recipe: Recipe) {
        withContext(Dispatchers.IO) {
            // Delete from local Room DB by ID (safer than entity match)
            recipeDao.deleteById(recipe.id)
            Log.d("RECIPE_TEST", "Recipe deleted from local DB: ${recipe.id}")

            // Delete from Firebase Firestore (only if id is valid)
            if (recipe.id.isNotEmpty()) {
                try {
                    firestore.collection("recipes").document(recipe.id).delete().await()
                    Log.d("RECIPE_TEST", "Recipe deleted from Firebase")
                } catch (e: Exception) {
                    Log.e("RECIPE_TEST", "Failed to delete from Firebase: ${e.message}")
                }
            }
        }
    }
}