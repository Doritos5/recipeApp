package com.example.recipeapp.model.recipes

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

// Dor comment - we need to have ' = "" ' for every parameter because when firebase gets data from internet
// it firsts create empty object and than put the data into it. if we will remove the default value we
// will get: Error fetching data: Could not deserialize object. Class com.example.recipeapp.model.recipes.Recipe does not define a no-argument constructor.
@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey
    @SerializedName("idMeal")
    val id: String = "",

    @SerializedName("strMeal")
    val title: String = "",

    @SerializedName("strInstructions")
    val instructions: String? = null,

    @SerializedName("strMealThumb")
    val imageUrl: String? = null,

    val description: String? = "",
    val ingredients: String? = "",
    val authorId: String? = null,
    val latitude: Double? = 0.0,
    val longitude: Double? = 0.0
)