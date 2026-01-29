package com.example.recipeapp.model

import com.example.recipeapp.model.recipes.Recipe
import com.example.recipeapp.model.users.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val recipesCollection = firestore.collection("recipes")
    private val usersCollection = firestore.collection("users")

    // Get current user ID
    val currentUserId: String?
        get() = auth.currentUser?.uid

    // Create or Update User
    suspend fun saveUser(user: User) {
        usersCollection.document(user.uid).set(user).await()
    }

    // Save Recipe
    suspend fun saveRecipe(recipe: Recipe) {
        val docRef = if (recipe.id.isEmpty()) {
            recipesCollection.document() // Create new doc
        } else {
            recipesCollection.document(recipe.id) // Update existing
        }
        val recipeWithId = recipe.copy(id = docRef.id)
        docRef.set(recipeWithId).await()
    }

    // Get Recipes for current user
    fun getRecipesFlow(): Flow<List<Recipe>> = callbackFlow {
        val userId = currentUserId ?: return@callbackFlow
        val subscription = recipesCollection
            .whereEqualTo("owner_id", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val recipes = snapshot?.toObjects(Recipe::class.java) ?: emptyList()
                trySend(recipes)
            }
        awaitClose { subscription.remove() }
    }
}
