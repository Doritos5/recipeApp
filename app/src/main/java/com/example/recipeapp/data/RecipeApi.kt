package com.example.recipeapp.data

import com.example.recipeapp.model.recipes.RecipeResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface RecipeApi {

    @GET("api/json/v1/1/search.php")
    fun getRecipes(
        @Query("s") query: String = "" // "s" is the query itself. like s=chicken
    ): Call<RecipeResponse>
}