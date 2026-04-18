package com.example.recipeapp.data

import com.example.recipeapp.model.places.NominatimResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApi {
    @GET("reverse")
    suspend fun reverse(
        @Query("format") format: String = "jsonv2",
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("zoom") zoom: Int = 18,
        @Query("addressdetails") addressDetails: Int = 1
    ): NominatimResponse
}

