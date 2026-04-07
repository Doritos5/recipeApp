package com.example.recipeapp.data

import com.example.recipeapp.model.places.OverpassResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Url

interface OverpassApi {
    @FormUrlEncoded
    @POST
    suspend fun search(
        @Url url: String,
        @Field("data") query: String
    ): OverpassResponse
}
