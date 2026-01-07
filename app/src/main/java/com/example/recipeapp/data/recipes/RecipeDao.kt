package com.example.recipeapp.data.recipes

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipe")
    fun getAllRecipes(): List<Recipe>

    @Insert
    fun createRecipe(recipe: Recipe)

    @Update
    fun updateRecipe(recipe: Recipe)

    @Delete
    fun deleteRecipe(recipe: Recipe)

    @Query("SELECT * FROM recipe WHERE title = :name")
    fun getRecipeByName(name: String): Recipe?

    @Query("SELECT * FROM recipe WHERE is_favorite = 1")
    fun getFavoriteRecipes(): List<Recipe>

}