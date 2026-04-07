package com.example.recipeapp.model.places

data class NearbyPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float,
    val address: String
)

