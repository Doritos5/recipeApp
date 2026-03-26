package com.example.recipeapp.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTagsList(tags: List<String>?): String {
        return gson.toJson(tags ?: emptyList<String>())
    }

    @TypeConverter
    fun toTagsList(tagsJson: String?): List<String> {
        return if (tagsJson.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(tagsJson, type)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
