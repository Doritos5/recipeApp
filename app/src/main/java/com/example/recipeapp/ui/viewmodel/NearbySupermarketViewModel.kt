package com.example.recipeapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recipeapp.data.NearbyPlacesRepository
import com.example.recipeapp.model.places.NearbyPlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NearbySupermarketState {
    object Idle : NearbySupermarketState()
    object Loading : NearbySupermarketState()
    data class Loaded(val places: List<NearbyPlace>) : NearbySupermarketState()
    object NoResults : NearbySupermarketState()
    object PermissionDenied : NearbySupermarketState()
    object LocationDisabled : NearbySupermarketState()
    data class Error(val message: String) : NearbySupermarketState()
}

@HiltViewModel
class NearbySupermarketViewModel @Inject constructor(
    private val repository: NearbyPlacesRepository
) : ViewModel() {

    private val _state = MutableStateFlow<NearbySupermarketState>(NearbySupermarketState.Idle)
    val state: StateFlow<NearbySupermarketState> = _state.asStateFlow()

    private var cachedPlaces: List<NearbyPlace> = emptyList()
    private var cachedLatitude: Double? = null
    private var cachedLongitude: Double? = null
    private var lastLoadedAtMillis: Long? = null
    private var loadJob: Job? = null

    fun markPermissionDenied() {
        _state.value = NearbySupermarketState.PermissionDenied
    }

    fun markLocationDisabled() {
        _state.value = NearbySupermarketState.LocationDisabled
    }

    fun setError(message: String) {
        if (cachedPlaces.isNotEmpty()) {
            _state.value = NearbySupermarketState.Loaded(cachedPlaces)
        } else {
            _state.value = NearbySupermarketState.Error(message)
        }
    }

    fun markLoading() {
        if (cachedPlaces.isEmpty()) {
            _state.value = NearbySupermarketState.Loading
        }
    }

    fun getCachedPlaces(): List<NearbyPlace> = cachedPlaces

    fun getCachedLatitude(): Double? = cachedLatitude

    fun getCachedLongitude(): Double? = cachedLongitude

    fun hasFreshCache(
        latitude: Double,
        longitude: Double,
        maxAgeMillis: Long = 5 * 60 * 1000L
    ): Boolean {
        val loadedAt = lastLoadedAtMillis ?: return false
        val lat = cachedLatitude ?: return false
        val lng = cachedLongitude ?: return false

        val ageOk = System.currentTimeMillis() - loadedAt <= maxAgeMillis
        val locationCloseEnough =
            kotlin.math.abs(lat - latitude) < 0.002 &&
                    kotlin.math.abs(lng - longitude) < 0.002

        return ageOk && locationCloseEnough && cachedPlaces.isNotEmpty()
    }

    fun emitCachedIfAvailable(): Boolean {
        return if (cachedPlaces.isNotEmpty()) {
            _state.value = NearbySupermarketState.Loaded(cachedPlaces)
            true
        } else {
            false
        }
    }

    fun loadNearbySupermarkets(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        forceRefresh: Boolean = false
    ) {
        if (!forceRefresh && hasFreshCache(latitude, longitude)) {
            _state.value = NearbySupermarketState.Loaded(cachedPlaces)
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (cachedPlaces.isEmpty()) {
                _state.value = NearbySupermarketState.Loading
            }

            try {
                val places = repository.fetchNearbySupermarkets(latitude, longitude, radiusMeters)

                cachedLatitude = latitude
                cachedLongitude = longitude
                lastLoadedAtMillis = System.currentTimeMillis()
                cachedPlaces = places

                _state.value = if (places.isEmpty()) {
                    NearbySupermarketState.NoResults
                } else {
                    NearbySupermarketState.Loaded(places)
                }
            } catch (e: Exception) {
                if (cachedPlaces.isNotEmpty()) {
                    _state.value = NearbySupermarketState.Loaded(cachedPlaces)
                } else {
                    _state.value = NearbySupermarketState.Error(
                        e.message ?: "Failed to fetch nearby supermarkets"
                    )
                }
            }
        }
    }
}