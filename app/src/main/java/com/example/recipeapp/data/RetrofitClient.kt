package com.example.recipeapp.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * RetrofitClient is a Singleton object used to manage network requests.
 * It initializes and provides the implementation of the RecipeApi interface.
 */
object RetrofitClient {
    // The base URL of the API service
    private const val BASE_URL = "https://www.themealdb.com/"

    /**
     * The API implementation, created lazily to save resources.
     * 'by lazy' ensures it is only initialized when first accessed.
     */
    val api: RecipeApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL) // Set the server root URL
            .addConverterFactory(GsonConverterFactory.create()) // Use GSON to convert JSON to Kotlin objects
            .build() // Create the Retrofit instance
            .create(RecipeApi::class.java) // Create the implementation of our API interface
    }
}