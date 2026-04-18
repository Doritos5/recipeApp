package com.example.recipeapp.model.recipes

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comments")
data class Comment(
    @PrimaryKey
    val id: String = "",
    val recipeId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userImageUrl: String? = null,
    val text: String = "",
    val timestamp: Long = 0L
)
