package com.example.recipeapp.data.users

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    @ColumnInfo(name = "last_name")
    val lastName: String,
    val email: String,
    val password: String,
    val imageUrl: String? = null,
)
