package com.example.recipeapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationListener
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.recipeapp.MainActivity
import com.example.recipeapp.R
import com.example.recipeapp.ui.viewmodel.NearbySupermarketState
import com.example.recipeapp.ui.viewmodel.NearbySupermarketViewModel
import com.example.recipeapp.util.LocationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NearbySupermarketFragment : Fragment() {

    private val viewModel: NearbySupermarketViewModel by activityViewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var statusIcon: View
    private lateinit var retryButton: Button
    private lateinit var subtitleText: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var adapter: NearbySupermarketAdapter

    private var pendingLocationListener: LocationListener? = null

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchLocationAndLoad()
        } else {
            viewModel.markPermissionDenied()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_nearby_supermarket, container, false)

        recyclerView = view.findViewById(R.id.nearbySupermarketList)
        progressBar = view.findViewById(R.id.nearbySupermarketProgress)
        statusText = view.findViewById(R.id.nearbySupermarketStatus)
        statusIcon = view.findViewById(R.id.nearbySupermarketStatusIcon)
        retryButton = view.findViewById(R.id.nearbySupermarketRetry)
        subtitleText = view.findViewById(R.id.nearbySupermarketSubtitle)
        swipeRefreshLayout = view.findViewById(R.id.nearbySupermarketRefresh)

        subtitleText.text = getString(
            R.string.nearby_radius_format,
            DEFAULT_RADIUS_METERS / 1000f
        )

        swipeRefreshLayout.setOnRefreshListener { checkPermissionsAndLoad() }

        adapter = NearbySupermarketAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<View>(R.id.menuIcon).setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        retryButton.setOnClickListener { checkPermissionsAndLoad() }

        observeState()
        checkPermissionsAndLoad()

        return view
    }

    override fun onStop() {
        super.onStop()
        LocationHelper.removeUpdates(requireContext(), pendingLocationListener)
        pendingLocationListener = null
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is NearbySupermarketState.Idle -> {
                            progressBar.visibility = View.GONE
                            statusText.visibility = View.GONE
                            statusIcon.visibility = View.GONE
                            retryButton.visibility = View.GONE
                            swipeRefreshLayout.isRefreshing = false
                        }
                        is NearbySupermarketState.Loading -> {
                            progressBar.visibility = View.VISIBLE
                            statusText.visibility = View.VISIBLE
                            statusIcon.visibility = View.VISIBLE
                            statusText.text = getString(R.string.nearby_loading)
                            retryButton.visibility = View.GONE
                            swipeRefreshLayout.isRefreshing = true
                        }
                        is NearbySupermarketState.Loaded -> {
                            progressBar.visibility = View.GONE
                            statusText.visibility = View.GONE
                            statusIcon.visibility = View.GONE
                            retryButton.visibility = View.GONE
                            swipeRefreshLayout.isRefreshing = false
                            adapter.setPlaces(state.places)
                        }
                        is NearbySupermarketState.NoResults -> {
                            progressBar.visibility = View.GONE
                            statusText.visibility = View.VISIBLE
                            statusIcon.visibility = View.VISIBLE
                            statusText.text = getString(R.string.nearby_no_results)
                            retryButton.visibility = View.VISIBLE
                            swipeRefreshLayout.isRefreshing = false
                            adapter.setPlaces(emptyList())
                        }
                        is NearbySupermarketState.PermissionDenied -> {
                            progressBar.visibility = View.GONE
                            statusText.visibility = View.VISIBLE
                            statusIcon.visibility = View.VISIBLE
                            statusText.text = getString(R.string.nearby_permission_denied)
                            retryButton.visibility = View.VISIBLE
                            swipeRefreshLayout.isRefreshing = false
                            adapter.setPlaces(emptyList())
                        }
                        is NearbySupermarketState.LocationDisabled -> {
                            progressBar.visibility = View.GONE
                            statusText.visibility = View.VISIBLE
                            statusIcon.visibility = View.VISIBLE
                            statusText.text = getString(R.string.nearby_location_disabled)
                            retryButton.visibility = View.VISIBLE
                            swipeRefreshLayout.isRefreshing = false
                            adapter.setPlaces(emptyList())
                        }
                        is NearbySupermarketState.Error -> {
                            progressBar.visibility = View.GONE
                            statusText.visibility = View.VISIBLE
                            statusIcon.visibility = View.VISIBLE
                            statusText.text = state.message
                            retryButton.visibility = View.VISIBLE
                            swipeRefreshLayout.isRefreshing = false
                            adapter.setPlaces(emptyList())
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndLoad() {
        val hasFine = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            fetchLocationAndLoad()
        } else {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchLocationAndLoad() {
        val context = requireContext()
        if (!LocationHelper.isLocationEnabled(context)) {
            viewModel.markLocationDisabled()
            return
        }

        viewModel.markLoading()

        val lastLocation = LocationHelper.getBestLastKnownLocation(context)
        if (lastLocation != null) {
            viewModel.loadNearbySupermarkets(
                latitude = lastLocation.latitude,
                longitude = lastLocation.longitude,
                radiusMeters = DEFAULT_RADIUS_METERS
            )
            return
        }

        LocationHelper.removeUpdates(context, pendingLocationListener)
        pendingLocationListener = LocationHelper.requestSingleUpdate(context) { location ->
            pendingLocationListener = null
            if (location == null) {
                viewModel.setError(getString(R.string.nearby_location_error))
            } else {
                viewModel.loadNearbySupermarkets(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    radiusMeters = DEFAULT_RADIUS_METERS
                )
            }
        }

        if (pendingLocationListener == null) {
            viewModel.setError(getString(R.string.nearby_location_error))
        }
    }

    companion object {
        private const val DEFAULT_RADIUS_METERS = 3000
    }
}
