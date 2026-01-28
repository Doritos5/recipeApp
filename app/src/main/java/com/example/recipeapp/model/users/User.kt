package com.example.recipeapp.model.users

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class User(
    @PrimaryKey
    val uid: String = "",
    val name: String = "",
    @ColumnInfo(name = "last_name")
    val lastName: String = "",
    val email: String = "",
    val imageUrl: String? = null,
)
