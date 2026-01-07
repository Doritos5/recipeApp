package com.example.recipeapp.data.recipes

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.example.recipeapp.data.users.User

@Entity(
    tableName = "recipe",
    foreignKeys = [
        ForeignKey(
            entity = User::class,          // The parent table
            parentColumns = ["id"],        // The primary key in 'users'
            childColumns = ["owner_id"],     // The foreign key in 'recipe'
            onDelete = ForeignKey.CASCADE  // Deletes recipes if user is deleted
        )
    ]
)
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    @ColumnInfo(name = "owner_id")
    val ownerId: Int,
    val description: String? = null,
    val ingredients: String,
    val instructions: String,
    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false
)
