package com.example.recipeapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.recipeapp.data.AppLocalDb
import com.example.recipeapp.data.RecipeRepository
import com.example.recipeapp.model.recipes.Recipe
import kotlinx.coroutines.launch

class RecipeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RecipeRepository
    val recipes: LiveData<List<Recipe>>

    init {
        val db = AppLocalDb.getDatabase(application)
        val dao = db.recipeDao()
        repository = RecipeRepository(dao)
        recipes = repository.allRecipes
    }

    fun reloadRecipes() {
        viewModelScope.launch {
            repository.refreshRecipes()
        }
    }

    // Call this when the user clicks "Save"
    fun addRecipe(recipe: Recipe) {
        viewModelScope.launch {
            repository.add(recipe)
            Log.d("RECIPE_TEST", "Recipe added successfully: ${recipe.title}")
        }
    }

    // Call this when the user clicks "Delete"
    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch {
            repository.delete(recipe)
            Log.d("RECIPE_TEST", "Recipe deleted: ${recipe.title}")
        }
    }
}