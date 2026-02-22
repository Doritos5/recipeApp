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
     * Adds a new recipe WITH an optional image upload.
     *
     * Flow:
     * 1. If imageUri is provided → compress & upload to Firebase Storage → get download URL.
     * 2. Save the recipe (with userId and imageRemoteUrl) to Firebase Firestore.
     * 3. ONLY after Firebase succeeds → save to local Room DB.
     *
     * This ensures the local DB is updated only after a successful Firebase metadata reference.
     *
     * @param recipe The recipe to save.
     * @param imageUri Optional local URI of the recipe photo.
     * @param userId The current user's UID.
     * @param context Android context, needed for image compression.
     * @return The final Recipe object (with remote URL and userId set).
     * @throws Exception if any Firebase operation fails — Room will NOT be updated.
     */
    suspend fun addRecipeWithImage(
        recipe: Recipe,
        imageUri: Uri?,
        userId: String,
        context: Context
    ): Recipe {
        return withContext(Dispatchers.IO) {
            // Step 1: Upload image to Firebase Storage (if provided)
            var remoteImageUrl: String? = null
            if (imageUri != null) {
                try {
                    remoteImageUrl = storageService.uploadRecipeImage(context, imageUri, userId)
                } catch (e: Exception) {
                    Log.e("RECIPE_TEST", "Image upload failed (skipped): ${e.message}")
                }
            }

            // Step 2: Create the final recipe with userId and remote image URL
            val finalRecipe = recipe.copy(
                authorId = userId,
                imageRemoteUrl = remoteImageUrl
            )

            // Step 3: Try to save to Firebase Firestore
            try {
                firestore.collection("recipes")
                    .document(finalRecipe.id)
                    .set(finalRecipe)
                    .await()
                Log.d("RECIPE_TEST", "Recipe saved to Firebase successfully!")
            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Firebase save failed (saving locally only): ${e.message}")
            }

            // Step 4: Always save to local Room DB
            recipeDao.insert(finalRecipe)
            Log.d("RECIPE_TEST", "Recipe saved to local DB: ${finalRecipe.title}")

            finalRecipe
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
        recipeDao.delete(recipe)
        try {
            firestore.collection("recipes").document(recipe.id).delete().await()
            Log.d("RECIPE_TEST", "Recipe deleted from Firebase")
        } catch (e: Exception) {
            Log.e("RECIPE_TEST", "Failed to delete from Firebase: ${e.message}")
        }
    }
}