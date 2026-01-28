package com.example.recipeapp.model.recipes

import com.google.gson.annotations.SerializedName

data class RecipeResponse(
    @SerializedName("meals")
    val meals: List<Recipe>?
)