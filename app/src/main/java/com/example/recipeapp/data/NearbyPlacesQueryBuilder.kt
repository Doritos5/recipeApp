package com.example.recipeapp.data

import java.util.Locale

object NearbyPlacesQueryBuilder {
    fun buildSupermarketQuery(latitude: Double, longitude: Double, radiusMeters: Int): String {
        val lat = String.format(Locale.US, "%.6f", latitude)
        val lon = String.format(Locale.US, "%.6f", longitude)
        val radius = radiusMeters.coerceIn(200, 50000)

        return """
            [out:json];
            (
              node["shop"="supermarket"](around:$radius,$lat,$lon);
              way["shop"="supermarket"](around:$radius,$lat,$lon);
              relation["shop"="supermarket"](around:$radius,$lat,$lon);
            );
            out center tags;
        """.trimIndent()
    }
}

