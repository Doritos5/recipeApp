package com.example.recipeapp.data

import android.location.Location
import android.util.Log
import com.example.recipeapp.model.places.NearbyPlace
import com.example.recipeapp.model.places.OverpassElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
                "https://overpass.kumi.systems/api/interpreter",
                "https://overpass-api.de/api/interpreter"
            )

            var lastError: Exception? = null

            repeat(2) { attempt ->
                Log.d(TAG, "Nearby supermarkets fetch attempt #${attempt + 1}")

                for (endpoint in endpoints) {
                    try {
                        Log.d(TAG, "Trying Overpass endpoint: $endpoint")

                        val response = withTimeout(10_000L) {
                            overpassApi.search(endpoint, query)
                        }

                        val places = response.elements
                            .mapNotNull { element ->
                                element.toNearbyPlace(latitude, longitude)
                            }
                            .sortedBy { it.distanceMeters }

                        Log.d(TAG, "Loaded ${places.size} nearby supermarkets from $endpoint")
                        return@withContext places
                    } catch (e: TimeoutCancellationException) {
                        lastError = e
                        Log.e(TAG, "Timeout from $endpoint", e)
                    } catch (e: HttpException) {
                        lastError = e
                        Log.e(TAG, "HTTP error from $endpoint: ${e.code()} ${e.message()}", e)
                    } catch (e: IOException) {
                        lastError = e
                        Log.e(TAG, "Network error from $endpoint: ${e.message}", e)
                    } catch (e: Exception) {
                        lastError = e
                        Log.e(TAG, "Unexpected error from $endpoint: ${e.message}", e)
                    }

                    delay(500)
                }

                if (attempt == 0) {
                    delay(900)
                }
            }

            throw IllegalStateException(
                "Couldn't load nearby supermarkets right now. Please try again."
            )
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