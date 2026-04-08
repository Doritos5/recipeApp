package com.example.recipeapp.data

import android.location.Location
import android.util.Log
import com.example.recipeapp.model.places.NearbyPlace
import com.example.recipeapp.model.places.OverpassElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearbyPlacesRepository @Inject constructor(
    private val overpassApi: OverpassApi
) {
    suspend fun fetchNearbySupermarkets(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): List<NearbyPlace> {
        return withContext(Dispatchers.IO) {
            val query = NearbyPlacesQueryBuilder.buildSupermarketQuery(
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters
            )
            Log.d(TAG, "Fetching supermarkets near $latitude,$longitude radius=$radiusMeters")

            val endpoints = listOf(
                "https://overpass-api.de/api/interpreter",
                "https://overpass.kumi.systems/api/interpreter",
                "https://overpass.nchc.org.tw/api/interpreter"
            )

            var lastError: Exception? = null
            for (endpoint in endpoints) {
                try {
                    val response = overpassApi.search(endpoint, query)
                    return@withContext response.elements
                        .mapNotNull { element ->
                            element.toNearbyPlace(latitude, longitude)
                        }
                        .sortedBy { it.distanceMeters }
                } catch (e: HttpException) {
                    lastError = e
                    if (e.code() != 504) throw e
                } catch (e: IOException) {
                    lastError = e
                }
                delay(400)
            }

            throw lastError ?: IllegalStateException("Failed to fetch nearby supermarkets")
        }
    }

    private fun OverpassElement.toNearbyPlace(
        userLat: Double,
        userLon: Double
    ): NearbyPlace? {
        val elementLat = lat ?: center?.lat
        val elementLon = lon ?: center?.lon
        if (elementLat == null || elementLon == null) return null

        val name = tags?.get("name")?.trim().orEmpty()
        if (name.isEmpty()) return null

        val address = buildAddress(tags.orEmpty())
        val distance = FloatArray(1)
        Location.distanceBetween(userLat, userLon, elementLat, elementLon, distance)

        return NearbyPlace(
            id = "$type:$id",
            name = name,
            latitude = elementLat,
            longitude = elementLon,
            distanceMeters = distance[0],
            address = address
        )
    }

    private fun buildAddress(tags: Map<String, String>): String {
        val full = tags["addr:full"]?.trim().orEmpty()
        if (full.isNotEmpty()) return full

        val street = tags["addr:street"]?.trim().orEmpty()
        val house = tags["addr:housenumber"]?.trim().orEmpty()
        val city = tags["addr:city"]?.trim().orEmpty()

        val line1 = listOf(street, house).filter { it.isNotEmpty() }.joinToString(" ")
        val line2 = city

        return listOf(line1, line2)
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }

    companion object {
        private const val TAG = "NEARBY_PLACES"
    }
}
