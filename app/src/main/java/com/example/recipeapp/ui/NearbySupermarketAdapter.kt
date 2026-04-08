package com.example.recipeapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.recipeapp.R
import com.example.recipeapp.model.places.NearbyPlace
import java.util.Locale

class NearbySupermarketAdapter(
    private var places: List<NearbyPlace>
) : RecyclerView.Adapter<NearbySupermarketAdapter.PlaceViewHolder>() {

    class PlaceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.nearbyPlaceName)
        val distance: TextView = view.findViewById(R.id.nearbyPlaceDistance)
        val address: TextView = view.findViewById(R.id.nearbyPlaceAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_nearby_supermarket, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val place = places[position]
        holder.name.text = place.name
        holder.distance.text = formatDistance(place.distanceMeters)
        holder.address.text = place.address.ifBlank { holder.itemView.context.getString(R.string.nearby_place_no_address) }
    }

    override fun getItemCount(): Int = places.size

    fun setPlaces(newPlaces: List<NearbyPlace>) {
        places = newPlaces
        notifyDataSetChanged()
    }

    private fun formatDistance(distanceMeters: Float): String {
        return if (distanceMeters >= 1000f) {
            val km = distanceMeters / 1000f
            String.format(Locale.US, "%.1f km", km)
        } else {
            String.format(Locale.US, "%.0f m", distanceMeters)
        }
    }
}

