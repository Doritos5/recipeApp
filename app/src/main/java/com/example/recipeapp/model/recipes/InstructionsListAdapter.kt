package com.example.recipeapp.model.recipes

import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

class InstructionsListAdapter : JsonDeserializer<List<String>>, JsonSerializer<List<String>> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): List<String> {
        return when {
            json.isJsonArray -> {
                json.asJsonArray.mapNotNull { element ->
                    if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                        element.asString.trim().takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                }
            }
            json.isJsonPrimitive && json.asJsonPrimitive.isString -> {
                json.asString
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
            else -> emptyList()
        }
    }

    override fun serialize(
        src: List<String>?,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        val array = JsonArray()
        src.orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { array.add(JsonPrimitive(it)) }
        return array
    }
}

