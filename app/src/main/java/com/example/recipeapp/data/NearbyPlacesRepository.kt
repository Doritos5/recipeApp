package com.example.recipeapp.data

import android.location.Location
import android.util.Log
import com.example.recipeapp.model.places.NearbyPlace
import com.example.recipeapp.model.places.NominatimResponse
import com.example.recipeapp.model.places.OverpassElement
import java.util.Locale
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
    private val overpassApi: OverpassApi,
    private val nominatimApi: NominatimApi
) {
    private val reverseCache = mutableMapOf<String, String>()

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

                        val places = mutableListOf<NearbyPlace>()
                        for (element in response.elements) {
                            val place = element.toNearbyPlace(latitude, longitude)
                            if (place != null) {
                                places.add(place)
                            }
                        }

                        val sortedPlaces = places.sortedBy { it.distanceMeters }

                        Log.d(TAG, "Loaded ${sortedPlaces.size} nearby supermarkets from $endpoint")
                        return@withContext sortedPlaces
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

    private suspend fun OverpassElement.toNearbyPlace(
        userLat: Double,
        userLon: Double
    ): NearbyPlace? {
        val elementLat = lat ?: center?.lat
        val elementLon = lon ?: center?.lon
        if (elementLat == null || elementLon == null) return null

        val name = tags?.get("name")?.trim().orEmpty()
        if (name.isEmpty()) return null

        val address = resolveAddressWithFallback(tags.orEmpty(), elementLat, elementLon)
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

    private suspend fun resolveAddressWithFallback(
        tags: Map<String, String>,
        lat: Double,
        lon: Double
    ): String {
        val tagAddress = buildAddress(tags)
        if (tagAddress.isNotBlank()) return tagAddress

        val key = String.format(Locale.US, "%.5f,%.5f", lat, lon)
        reverseCache[key]?.let { return it }

        val reverse = try {
            val response = withTimeout(4_000L) {
                nominatimApi.reverse(lat = lat, lon = lon)
            }
            formatNominatimAddress(response)
        } catch (_: Exception) {
            ""
        }

        if (reverse.isNotBlank()) {
            reverseCache[key] = reverse
        }

        return reverse
    }

    private fun formatNominatimAddress(response: NominatimResponse): String {
        val displayName = response.displayName?.trim().orEmpty()
        if (displayName.isNotBlank()) return displayName

        val address = response.address
        val street = listOf(address?.houseNumber, address?.road)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")

        val place = firstNonBlank(
            address?.city,
            address?.town,
            address?.village,
            address?.suburb
        )

        val line2 = listOf(place, address?.state, address?.postcode, address?.country)
            .filter { !it.isNullOrBlank() }
            .joinToString(", ")

        return listOf(street, line2)
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }

    private fun buildAddress(tags: Map<String, String>): String {
        val full = tags["addr:full"].orEmpty().trim()
        if (full.isNotEmpty()) return full

        val street = tags["addr:street"].orEmpty().trim()
        val houseNumber = tags["addr:housenumber"].orEmpty().trim()
        val houseAlt = tags["addr:streetnumber"].orEmpty().trim()
        val house = houseNumber.ifEmpty { houseAlt }

        val place = firstNonBlank(
            tags["addr:city"],
            tags["addr:town"],
            tags["addr:village"],
            tags["addr:hamlet"],
            tags["addr:suburb"],
            tags["addr:neighbourhood"],
            tags["addr:place"],
            tags["addr:district"],
            tags["addr:municipality"]
        )

        val postcode = tags["addr:postcode"].orEmpty().trim()
        val state = tags["addr:state"].orEmpty().trim()
        val country = tags["addr:country"].orEmpty().trim()

        val line1 = listOf(house, street)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifEmpty { tags["addr:place"].orEmpty().trim() }

        val line2Parts = listOf(place, state, postcode, country).filter { it.isNotBlank() }
        val line2 = line2Parts.joinToString(", ")

        return listOf(line1, line2)
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }

    private fun firstNonBlank(vararg values: String?): String {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
    }

    companion object {
        private const val TAG = "NEARBY_PLACES"
    }
}