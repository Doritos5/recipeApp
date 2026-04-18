package com.example.recipeapp.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.recipeapp.model.recipes.Recipe

@Dao
interface RecipeDao {
    // Read all
    @Query("SELECT * FROM recipes")
    fun getAllRecipes(): LiveData<List<Recipe>>

    // Read by author (for My Recipes)
    @Query("SELECT * FROM recipes WHERE authorId = :authorId")
    fun getRecipesByAuthor(authorId: String): LiveData<List<Recipe>>

    @Query("SELECT * FROM recipes")
    suspend fun getAllRecipesList(): List<Recipe>

    // Read single recipe by ID
    @Query("SELECT * FROM recipes WHERE id = :recipeId LIMIT 1")
    fun getRecipeById(recipeId: String): LiveData<Recipe?>

    // Read single recipe by ID (suspend, non-LiveData)
    @Query("SELECT * FROM recipes WHERE id = :recipeId LIMIT 1")
    suspend fun getRecipeByIdOnce(recipeId: String): Recipe?

    // Create (Batch from API)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recipes: List<Recipe>)

    // Create (Single - for user generated content)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recipe: Recipe)

    // Update
    @Update
    suspend fun update(recipe: Recipe)

    // Delete by entity
    @Delete
    suspend fun delete(recipe: Recipe)

    // Delete by ID (safer — avoids entity field mismatch issues)
    @Query("DELETE FROM recipes WHERE id = :recipeId")
    suspend fun deleteById(recipeId: String)

    // Delete all recipes by author (used on logout to clear user data)
    @Query("DELETE FROM recipes WHERE authorId = :authorId")
    suspend fun deleteByAuthor(authorId: String)
}