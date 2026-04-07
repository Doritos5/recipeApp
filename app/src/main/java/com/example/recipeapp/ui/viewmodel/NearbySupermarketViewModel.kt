package com.example.recipeapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recipeapp.data.NearbyPlacesRepository
import com.example.recipeapp.model.places.NearbyPlace
import dagger.hilt.android.lifecycle.HiltViewModel
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

    fun markPermissionDenied() {
        _state.value = NearbySupermarketState.PermissionDenied
    }

    fun markLocationDisabled() {
        _state.value = NearbySupermarketState.LocationDisabled
    }

    fun setError(message: String) {
        _state.value = NearbySupermarketState.Error(message)
    }

    fun markLoading() {
        _state.value = NearbySupermarketState.Loading
    }

    fun loadNearbySupermarkets(latitude: Double, longitude: Double, radiusMeters: Int) {
        viewModelScope.launch {
            _state.value = NearbySupermarketState.Loading
            try {
                val places = repository.fetchNearbySupermarkets(latitude, longitude, radiusMeters)
                _state.value = if (places.isEmpty()) {
                    NearbySupermarketState.NoResults
                } else {
                    NearbySupermarketState.Loaded(places)
                }
            } catch (e: Exception) {
                _state.value = NearbySupermarketState.Error(
                    e.message ?: "Failed to fetch nearby supermarkets"
                )
            }
        }
    }
}
