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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.recipeapp.MainActivity
import com.example.recipeapp.R
import com.example.recipeapp.model.places.NearbyPlace
import com.example.recipeapp.ui.viewmodel.NearbySupermarketState
import com.example.recipeapp.ui.viewmodel.NearbySupermarketViewModel
import com.example.recipeapp.util.LocationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Polygon

@AndroidEntryPoint
class NearbySupermarketFragment : Fragment() {

    private val viewModel: NearbySupermarketViewModel by activityViewModels()

    private lateinit var mapView: MapView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var retryButton: Button
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var adapter: NearbySupermarketAdapter

    private var latestPlaces: List<NearbyPlace> = emptyList()
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null
    private var pendingLocationListener: LocationListener? = null
    private var isNavigatingToDetails = false
    private var renderedPlaceIds: Set<String> = emptySet()
    private var renderJob: Job? = null

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            fetchLocationAndLoad(forceRefresh = false)
        } else {
            viewModel.markPermissionDenied()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_nearby_supermarket, container, false)

        Configuration.getInstance().userAgentValue = requireContext().packageName

        mapView = view.findViewById(R.id.nearbyMapView)
        recyclerView = view.findViewById(R.id.nearbySupermarketList)
        progressBar = view.findViewById(R.id.nearbySupermarketProgress)
        statusText = view.findViewById(R.id.nearbySupermarketStatus)
        retryButton = view.findViewById(R.id.nearbySupermarketRetry)
        swipeRefreshLayout = view.findViewById(R.id.nearbySupermarketRefresh)

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(true)
        mapView.zoomController.setVisibility(
            CustomZoomButtonsController.Visibility.ALWAYS
        )
        mapView.controller.setZoom(14.5)

        adapter = NearbySupermarketAdapter(emptyList()) { place ->
            openPlaceDetails(place)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<View>(R.id.menuIcon).setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        retryButton.setOnClickListener {
            fetchLocationAndLoad(forceRefresh = true)
        }

        swipeRefreshLayout.setOnRefreshListener {
            fetchLocationAndLoad(forceRefresh = true)
        }

        observeState()
        restoreCachedUiAndMaybeLoad()

        return view
    }

    override fun onResume() {
        super.onResume()
        isNavigatingToDetails = false
        mapView.onResume()
        if (mapView.tileProvider.tileSource == null) {
            mapView.setTileSource(TileSourceFactory.MAPNIK)
        }
        // Force a refresh when returning so tiles and overlays are restored.
        mapView.post { renderMapMarkers(force = true) }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        LocationHelper.removeUpdates(requireContext(), pendingLocationListener)
        pendingLocationListener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        renderJob?.cancel()
        renderJob = null
        // Keep tile provider attached; just clear overlays for the old view.
        mapView.overlays.clear()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is NearbySupermarketState.Idle -> {
                            progressBar.visibility = View.GONE
                            statusText.visibility = View.GONE
                            retryButton.visibility = View.GONE
                            swipeRefreshLayout.isRefreshing = false
                        }

                        is NearbySupermarketState.Loading -> {
                            progressBar.visibility = View.VISIBLE
                            statusText.visibility = View.VISIBLE
                            statusText.text = getString(R.string.nearby_loading)
                            retryButton.visibility = View.GONE
                            swipeRefreshLayout.isRefreshing = true
                        }

                        is NearbySupermarketState.Loaded -> {
                            progressBar.visibility = View.GONE
                            statusText.visibility = View.GONE
                            retryButton.visibility = View.GONE
                            swipeRefreshLayout.isRefreshing = false

                            latestPlaces = state.places
                            adapter.setPlaces(state.places)
                            renderMapMarkers()
                        }

                        is NearbySupermarketState.NoResults -> {
                            progressBar.visibility = View.GONE
                            statusText.visibility = View.VISIBLE
                            statusText.text = getString(R.string.nearby_no_results)
                            retryButton.visibility = View.VISIBLE
                            swipeRefreshLayout.isRefreshing = false

                            latestPlaces = emptyList()
                            adapter.setPlaces(emptyList())
                            renderMapMarkers()
                        }

                        is NearbySupermarketState.PermissionDenied -> {
                            progressBar.visibility = View.GONE
                            statusText.visibility = View.VISIBLE
                            statusText.text = getString(R.string.nearby_permission_denied)
                            retryButton.visibility = View.VISIBLE
                            swipeRefreshLayout.isRefreshing = false
                        }

                        is NearbySupermarketState.LocationDisabled -> {
                            progressBar.visibility = View.GONE
                            statusText.visibility = View.VISIBLE
                            statusText.text = getString(R.string.nearby_location_disabled)
                            retryButton.visibility = View.VISIBLE
                            swipeRefreshLayout.isRefreshing = false
                        }

                        is NearbySupermarketState.Error -> {
                            progressBar.visibility = View.GONE
                            statusText.visibility = View.VISIBLE
                            statusText.text = state.message
                            retryButton.visibility = View.VISIBLE
                            swipeRefreshLayout.isRefreshing = false
                        }
                    }
                }
            }
        }
    }

    private fun restoreCachedUiAndMaybeLoad() {
        lastLatitude = viewModel.getCachedLatitude()
        lastLongitude = viewModel.getCachedLongitude()

        val cachedPlaces = viewModel.getCachedPlaces()
        if (cachedPlaces.isNotEmpty()) {
            latestPlaces = cachedPlaces
            adapter.setPlaces(cachedPlaces)
            renderMapMarkers()
            viewModel.emitCachedIfAvailable()
        } else {
            checkPermissionsAndLoad()
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
            fetchLocationAndLoad(forceRefresh = false)
        } else {
            requestLocationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchLocationAndLoad(forceRefresh: Boolean) {
        val context = requireContext()

        if (!LocationHelper.isLocationEnabled(context)) {
            viewModel.markLocationDisabled()
            return
        }

        val lastLocation = LocationHelper.getBestLastKnownLocation(context)
        if (lastLocation != null) {
            lastLatitude = lastLocation.latitude
            lastLongitude = lastLocation.longitude

            viewModel.loadNearbySupermarkets(
                latitude = lastLocation.latitude,
                longitude = lastLocation.longitude,
                radiusMeters = DEFAULT_RADIUS_METERS,
                forceRefresh = forceRefresh
            )
            return
        }

        if (!forceRefresh && viewModel.emitCachedIfAvailable()) {
            lastLatitude = viewModel.getCachedLatitude()
            lastLongitude = viewModel.getCachedLongitude()
            latestPlaces = viewModel.getCachedPlaces()
            renderMapMarkers()
            return
        }

        viewModel.markLoading()

        LocationHelper.removeUpdates(context, pendingLocationListener)
        pendingLocationListener = LocationHelper.requestSingleUpdate(context) { location ->
            pendingLocationListener = null
            if (location == null) {
                viewModel.setError(getString(R.string.nearby_location_error))
            } else {
                lastLatitude = location.latitude
                lastLongitude = location.longitude

                viewModel.loadNearbySupermarkets(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    radiusMeters = DEFAULT_RADIUS_METERS,
                    forceRefresh = forceRefresh
                )
            }
        }

        if (pendingLocationListener == null) {
            viewModel.setError(getString(R.string.nearby_location_error))
        }
    }

    private fun renderMapMarkers(force: Boolean = false) {
        if (!isAdded || view == null || isNavigatingToDetails) return

        val userLat = lastLatitude
        val userLng = lastLongitude
        if (userLat == null || userLng == null) return

        val placesSnapshot = latestPlaces
        val currentIds = placesSnapshot.map { it.id }.toSet()
        if (!force && currentIds == renderedPlaceIds && mapView.overlays.isNotEmpty()) {
            return
        }

        renderedPlaceIds = currentIds
        renderJob?.cancel()
        renderJob = viewLifecycleOwner.lifecycleScope.launch {
            val renderData = withContext(Dispatchers.Default) {
                val userPoint = GeoPoint(userLat, userLng)
                val circlePoints = Polygon.pointsAsCircle(
                    userPoint,
                    DEFAULT_RADIUS_METERS.toDouble()
                )

                val latDelta = DEFAULT_RADIUS_METERS / 111000.0
                val lonDelta = DEFAULT_RADIUS_METERS /
                    (111000.0 * kotlin.math.cos(Math.toRadians(userLat)))

                val boundingBox = BoundingBox(
                    userLat + latDelta,
                    userLng + lonDelta,
                    userLat - latDelta,
                    userLng - lonDelta
                )

                RenderData(
                    userPoint = userPoint,
                    circlePoints = circlePoints,
                    places = placesSnapshot.take(20),
                    boundingBox = boundingBox
                )
            }

            if (!isAdded || view == null || isNavigatingToDetails) return@launch

            mapView.overlays.clear()

            val radiusCircle = Polygon().apply {
                points = renderData.circlePoints
                fillColor = 0x1A3F51B5
                strokeColor = 0xFF3F51B5.toInt()
                strokeWidth = 3f
            }
            mapView.overlays.add(radiusCircle)

            val userMarker = Marker(mapView).apply {
                position = renderData.userPoint
                title = "You are here"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_my_location_marker)
            }
            mapView.overlays.add(userMarker)

            renderData.places.forEach { place ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(place.latitude, place.longitude)
                    title = place.name
                    subDescription = place.address
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_supermarket_marker)

                    setOnMarkerClickListener { _, _ ->
                        openPlaceDetails(place)
                        true
                    }
                }
                mapView.overlays.add(marker)
            }

            mapView.zoomToBoundingBox(renderData.boundingBox, true, 64)
            mapView.controller.animateTo(renderData.userPoint)
            mapView.invalidate()
        }
    }

    private data class RenderData(
        val userPoint: GeoPoint,
        val circlePoints: List<GeoPoint>,
        val places: List<NearbyPlace>,
        val boundingBox: BoundingBox
    )

    private fun openPlaceDetails(place: NearbyPlace) {
        if (isNavigatingToDetails) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.nearbySupermarketFragment) return

        isNavigatingToDetails = true

        LocationHelper.removeUpdates(requireContext(), pendingLocationListener)
        pendingLocationListener = null

        val bundle = Bundle().apply {
            putString("placeName", place.name)
            putString("placeAddress", place.address)
            putFloat("placeDistance", place.distanceMeters)
            putDouble("placeLatitude", place.latitude)
            putDouble("placeLongitude", place.longitude)
        }

        view?.post {
            navController.navigate(
                R.id.nearbySupermarketDetailFragment,
                bundle
            )
        }
    }

    companion object {
        private const val DEFAULT_RADIUS_METERS = 1500
    }
}