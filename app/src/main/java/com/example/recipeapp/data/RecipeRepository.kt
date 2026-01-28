package com.example.recipeapp.data

import android.util.Log
import androidx.lifecycle.LiveData
import com.example.recipeapp.model.recipes.Recipe
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class RecipeRepository(private val recipeDao: RecipeDao) {

    val allRecipes: LiveData<List<Recipe>> = recipeDao.getAllRecipes()

    // Initialize Firestore
    private val db = FirebaseFirestore.getInstance()

    suspend fun refreshRecipes() {
        withContext(Dispatchers.IO) {
            try {
                // 1. Fetch from Public API
                val apiResponse = RetrofitClient.api.getRecipes().execute()

                if (apiResponse.isSuccessful) {
                    // Get the raw list from internet
                    val rawList = apiResponse.body()?.meals

                    // Add "API" as the authorId for all these recipes
                    val processedList = rawList?.map { recipe ->
                        recipe.copy(
                            authorId = "API",
                            description = "" // TheMealDB has no description
                        )
                    }

                    // Save the processed list to DB
                    processedList?.let {
                        recipeDao.insertAll(it)
                        Log.d("RECIPE_TEST", "Saved ${it.size} recipes with authorId='API'")
                    }
                }

                // 2. Fetch from Firebase (User Generated) - stays the same
                val snapshot = db.collection("recipes").get().await()
                val firebaseList = snapshot.toObjects(Recipe::class.java)
                if (firebaseList.isNotEmpty()) {
                    recipeDao.insertAll(firebaseList)
                }

            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Error fetching data: ${e.message}")
            }
        }
    }

    // Add a recipe (Save to Local DB + Save to Firebase)
    suspend fun add(recipe: Recipe) {
        // 1. Save locally immediately (for fast UI)
        recipeDao.insert(recipe)

        // 2. Save to Firebase (Background)
        try {
            db.collection("recipes")
                .document(recipe.id) // Use the recipe ID as the document ID
                .set(recipe)
                .await()
            Log.d("RECIPE_TEST", "Recipe saved to Firebase successfully!")
        } catch (e: Exception) {
            Log.e("RECIPE_TEST", "Failed to save to Firebase: ${e.message}")
        }
    }

    // Delete (Remove from Local + Remove from Firebase)
    suspend fun delete(recipe: Recipe) {
        recipeDao.delete(recipe)

        try {
            db.collection("recipes").document(recipe.id).delete().await()
            Log.d("RECIPE_TEST", "Recipe deleted from Firebase")
        } catch (e: Exception) {
            Log.e("RECIPE_TEST", "Failed to delete from Firebase")
        }
    }
}