package com.example.recipeapp.model.recipes

import androidx.room.Entity

@Entity(tableName = "likes", primaryKeys = ["recipeId", "userId"])
data class Like(
    val recipeId: String = "",
    val userId: String = ""
)
