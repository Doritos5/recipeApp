package com.example.recipeapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.recipeapp.R

class NearbySupermarketDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_nearby_supermarket_detail, container, false)

        val name = arguments?.getString("placeName").orEmpty()
        val address = arguments?.getString("placeAddress").orEmpty()
        val distance = arguments?.getFloat("placeDistance") ?: 0f
        val latitude = arguments?.getDouble("placeLatitude") ?: 0.0
        val longitude = arguments?.getDouble("placeLongitude") ?: 0.0

        view.findViewById<View>(R.id.backBtn).setOnClickListener {
            findNavController().navigateUp()
        }

        view.findViewById<TextView>(R.id.placeNameTv).text = name
        view.findViewById<TextView>(R.id.placeAddressTv).text =
            if (address.isBlank()) getString(R.string.nearby_place_no_address) else address
        view.findViewById<TextView>(R.id.placeDistanceTv).text =
            if (distance >= 1000f) {
                String.format("%.1f km", distance / 1000f)
            } else {
                String.format("%.0f m", distance)
            }
        view.findViewById<TextView>(R.id.placeCoordinatesTv).text =
            "Lat: %.5f, Lng: %.5f".format(latitude, longitude)

        return view
    }
}