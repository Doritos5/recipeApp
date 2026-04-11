package com.example.recipeapp.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import com.example.recipeapp.model.recipes.ApiRecipe
import com.example.recipeapp.model.recipes.Recipe
import com.example.recipeapp.storage.FirebaseStorageService
import com.google.firebase.firestore.DocumentSnapshot
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
    private val storageService: FirebaseStorageService,
    private val commentDao: CommentDao,
    private val likeDao: LikeDao
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

    private fun splitInstructions(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun normalizeRecipe(recipe: Recipe): Recipe {
        val normalizedInstructions = recipe.instructions
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val normalizedAuthorName = recipe.authorName.ifBlank {
            when (recipe.authorId) {
                "API" -> "API"
                else -> "Unknown"
            }
        }

        val normalizedCreatedAt = if (recipe.createdAt > 0L) {
            recipe.createdAt
        } else {
            System.currentTimeMillis()
        }

        return recipe.copy(
            instructions = normalizedInstructions,
            authorName = normalizedAuthorName,
            createdAt = normalizedCreatedAt
        )
    }

    private fun parseInstructions(value: Any?): List<String> {
        return when (value) {
            is String -> splitInstructions(value)
            is List<*> -> value.mapNotNull { it as? String }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }

    private fun recipeFromDocument(doc: DocumentSnapshot): Recipe {
        val tags = (doc.get("tags") as? List<*>)
            ?.mapNotNull { it as? String }
            .orEmpty()

        val recipe = Recipe(
            id = doc.getString("id") ?: doc.id,
            title = doc.getString("title") ?: "",
            instructions = parseInstructions(doc.get("instructions")),
            imageUrl = doc.getString("imageUrl"),
            description = doc.getString("description"),
            ingredients = doc.getString("ingredients"),
            authorId = doc.getString("authorId"),
            authorName = doc.getString("authorName") ?: "",
            createdAt = doc.getLong("createdAt") ?: 0L,
            imageRemoteUrl = doc.getString("imageRemoteUrl"),
            latitude = doc.getDouble("latitude") ?: 0.0,
            longitude = doc.getDouble("longitude") ?: 0.0,
            tags = tags,
            likesCount = doc.getLong("likesCount")?.toInt() ?: 0,
            commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0
        )

        return normalizeRecipe(recipe)
    }

    private fun buildIngredients(api: ApiRecipe): String {
        val ingredients = listOf(
            api.ingredient1 to api.measure1,
            api.ingredient2 to api.measure2,
            api.ingredient3 to api.measure3,
            api.ingredient4 to api.measure4,
            api.ingredient5 to api.measure5,
            api.ingredient6 to api.measure6,
            api.ingredient7 to api.measure7,
            api.ingredient8 to api.measure8,
            api.ingredient9 to api.measure9,
            api.ingredient10 to api.measure10,
            api.ingredient11 to api.measure11,
            api.ingredient12 to api.measure12,
            api.ingredient13 to api.measure13,
            api.ingredient14 to api.measure14,
            api.ingredient15 to api.measure15,
            api.ingredient16 to api.measure16,
            api.ingredient17 to api.measure17,
            api.ingredient18 to api.measure18,
            api.ingredient19 to api.measure19,
            api.ingredient20 to api.measure20
        )

        return ingredients.mapNotNull { (ingredient, measure) ->
            val name = ingredient?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val qty = measure?.trim().orEmpty()
            if (qty.isEmpty()) name else "$qty $name"
        }.joinToString("\n")
    }

    private fun mapApiRecipe(api: ApiRecipe): Recipe {
        return Recipe(
            id = api.id,
            title = api.title,
            instructions = splitInstructions(api.instructions),
            imageUrl = api.imageUrl,
            description = "",
            ingredients = buildIngredients(api),
            authorId = "API",
            authorName = "API",
            createdAt = System.currentTimeMillis(),
            tags = emptyList(),
            likesCount = 0,
            commentsCount = 0
        )
    }

    private suspend fun resolveAuthorName(userId: String, fallback: String): String {
        return try {
            val snapshot = firestore.collection("users").document(userId).get().await()
            val data = snapshot.data
            val name = data?.get("name") as? String
            val username = data?.get("username") as? String
            val firstName = data?.get("firstName") as? String
            val lastName = data?.get("lastName") as? String
            val fullName = listOfNotNull(firstName?.trim(), lastName?.trim())
                .joinToString(" ")
                .trim()
                .ifEmpty { "" }

            listOf(name, fullName, username)
                .firstOrNull { !it.isNullOrBlank() }
                ?: fallback
        } catch (e: Exception) {
            fallback
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
                    val localRecipesMap = recipeDao.getAllRecipesList().associateBy { it.id }
                    val rawList = apiResponse.body()?.meals
                    val processedList = rawList?.map { apiRecipe ->
                        val localInfo = localRecipesMap[apiRecipe.id]
                        val mapped = mapApiRecipe(apiRecipe)
                        val finalMapped = if (localInfo != null) {
                            mapped.copy(
                                likesCount = localInfo.likesCount,
                                commentsCount = localInfo.commentsCount
                            )
                        } else {
                            mapped
                        }
                        normalizeRecipe(finalMapped)
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
                val firebaseList = snapshot.documents.map { recipeFromDocument(it) }
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
                val firebaseList = snapshot.documents.map { recipeFromDocument(it) }
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

            val resolvedAuthorName = resolveAuthorName(userId, recipe.authorName.ifBlank { "Recipe User" })

            val finalRecipe = normalizeRecipe(
                recipe.copy(
                    authorId = userId,
                    authorName = resolvedAuthorName,
                    imageRemoteUrl = base64ImageUrl ?: recipe.imageRemoteUrl
                )
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

            val resolvedAuthorName = resolveAuthorName(userId, recipe.authorName.ifBlank { "Recipe User" })

            val updatedRecipe = normalizeRecipe(
                recipe.copy(
                    authorId = userId,
                    authorName = resolvedAuthorName,
                    imageRemoteUrl = base64ImageUrl
                )
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

    suspend fun toggleLike(recipeId: String, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val likeRef = firestore.collection("recipes").document(recipeId)
                    .collection("likes").document(userId)

                val doc = likeRef.get().await()
                if (doc.exists()) {
                    likeRef.delete().await()
                    likeDao.deleteLike(recipeId, userId)
                } else {
                    likeRef.set(mapOf("timestamp" to System.currentTimeMillis())).await()
                    likeDao.insert(com.example.recipeapp.model.recipes.Like(recipeId = recipeId, userId = userId))
                }

                // Update aggregate count
                val likesSnapshot = firestore.collection("recipes").document(recipeId)
                    .collection("likes").get().await()
                val count = likesSnapshot.size()

                try {
                    firestore.collection("recipes").document(recipeId)
                        .update("likesCount", count).await()
                } catch (e: Exception) {
                    Log.w("RECIPE_TEST", "Failed to update likesCount in Firestore (may be an API recipe): ${e.message}")
                }

                // Update local Room entity
                val localRecipe = recipeDao.getRecipeByIdOnce(recipeId)
                if (localRecipe != null) {
                    val updated = localRecipe.copy(likesCount = count)
                    recipeDao.update(updated)
                }
            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Error toggling like: ${e.message}")
            }
        }
    }

    suspend fun addComment(recipeId: String, userId: String, text: String, userName: String) {
        withContext(Dispatchers.IO) {
            try {
                val resolvedName = resolveAuthorName(userId, userName.ifBlank { "Recipe User" })

                val commentRef = firestore.collection("recipes").document(recipeId)
                    .collection("comments").document()

                val commentData = mapOf(
                    "userId" to userId,
                    "userName" to resolvedName,
                    "text" to text,
                    "timestamp" to System.currentTimeMillis()
                )
                commentRef.set(commentData).await()

                // Update aggregate count
                val commentsSnapshot = firestore.collection("recipes").document(recipeId)
                    .collection("comments").get().await()
                val count = commentsSnapshot.size()

                try {
                    firestore.collection("recipes").document(recipeId)
                        .update("commentsCount", count).await()
                } catch (e: Exception) {
                    Log.w("RECIPE_TEST", "Failed to update commentsCount in Firestore: ${e.message}")
                }

                // Update local Room entity
                val localRecipe = recipeDao.getRecipeByIdOnce(recipeId)
                if (localRecipe != null) {
                    val updated = localRecipe.copy(commentsCount = count)
                    recipeDao.update(updated)
                }
            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Error adding comment: ${e.message}")
            }
        }
    }

    fun getCommentsForRecipe(recipeId: String): LiveData<List<com.example.recipeapp.model.recipes.Comment>> {
        return commentDao.getCommentsForRecipe(recipeId)
    }

    suspend fun editComment(recipeId: String, commentId: String, newText: String) {
        withContext(Dispatchers.IO) {
            try {
                firestore.collection("recipes").document(recipeId)
                    .collection("comments").document(commentId)
                    .update("text", newText).await()
            } catch (e: Exception) {
                Log.w("RECIPE_TEST", "Failed to update comment text in Firestore: ${e.message}")
            }
            try {
                commentDao.updateText(commentId, newText)
            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Error editing comment locally: ${e.message}")
            }
        }
    }

    suspend fun deleteComment(recipeId: String, commentId: String) {
        withContext(Dispatchers.IO) {
            try {
                firestore.collection("recipes").document(recipeId)
                    .collection("comments").document(commentId).delete().await()

                // Update aggregate count
                val commentsSnapshot = firestore.collection("recipes").document(recipeId)
                    .collection("comments").get().await()
                val count = commentsSnapshot.size()

                try {
                    firestore.collection("recipes").document(recipeId)
                        .update("commentsCount", count).await()
                } catch (e: Exception) {
                    Log.w("RECIPE_TEST", "Failed to update commentsCount in Firestore: ${e.message}")
                }

                commentDao.deleteById(commentId)

                // Update local Recipe entity
                val localRecipe = recipeDao.getRecipeByIdOnce(recipeId)
                if (localRecipe != null) {
                    val updated = localRecipe.copy(commentsCount = count)
                    recipeDao.update(updated)
                }
            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Error deleting comment: ${e.message}")
            }
        }
    }

    suspend fun refreshComments(recipeId: String) {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("recipes").document(recipeId)
                    .collection("comments").get().await()

                val commentsList = snapshot.documents.map { doc ->
                    com.example.recipeapp.model.recipes.Comment(
                        id = doc.id,
                        recipeId = recipeId,
                        userId = doc.getString("userId") ?: "",
                        userName = doc.getString("userName") ?: "",
                        text = doc.getString("text") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                }

                // Insert or update comments in Room
                commentsList.let {
                    commentDao.insertAll(it)
                    Log.d("RECIPE_TEST", "Saved ${it.size} comments for recipeId='$recipeId'")
                }

                // Update aggregate count
                val count = commentsList.size
                try {
                    firestore.collection("recipes").document(recipeId)
                        .update("commentsCount", count).await()
                } catch (e: Exception) {
                    Log.w("RECIPE_TEST", "Failed to update commentsCount in Firestore: ${e.message}")
                }

                // Update local Recipe entity
                val localRecipe = recipeDao.getRecipeByIdOnce(recipeId)
                if (localRecipe != null) {
                    val updated = localRecipe.copy(commentsCount = count)
                    recipeDao.update(updated)
                }
            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Error refreshing comments: ${e.message}")
            }
        }
    }

    fun isLiked(recipeId: String, userId: String): LiveData<Boolean> {
        return likeDao.isRecipeLikedByUser(recipeId, userId)
    }

    suspend fun checkIfLiked(recipeId: String, userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val doc = firestore.collection("recipes").document(recipeId)
                    .collection("likes").document(userId).get().await()
                doc.exists()
            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Error checking like status: ${e.message}")
                false
            }
        }
    }

    suspend fun refreshLikeStatus(recipeId: String, userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val isLiked = checkIfLiked(recipeId, userId)
                if (isLiked) {
                    likeDao.insert(com.example.recipeapp.model.recipes.Like(recipeId = recipeId, userId = userId))
                } else {
                    likeDao.deleteLike(recipeId, userId)
                }
            } catch (e: Exception) {
                Log.e("RECIPE_TEST", "Error refreshing like status: ${e.message}")
            }
        }
    }
}
