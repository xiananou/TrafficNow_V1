// app/src/main/java/com/example/trafficnow/MainActivity.kt
package com.example.trafficnow

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import com.example.trafficnow.adapters.RouteAdapter
import com.example.trafficnow.adapters.TransitAdapter
import com.example.trafficnow.models.*
import com.example.trafficnow.utils.MotionDetector

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var toggleTrafficButton: FloatingActionButton
    private lateinit var refreshButton: FloatingActionButton
    private lateinit var currentLocationButton: FloatingActionButton
    private lateinit var bottomSheet: View
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var routeOptionsRecyclerView: RecyclerView
    private lateinit var transitInfoRecyclerView: RecyclerView
    private lateinit var routeAdapter: RouteAdapter
    private lateinit var transitAdapter: TransitAdapter
    private lateinit var motionDetector: MotionDetector
    private lateinit var transitLandService: TransitLandService

    private var currentLocation: Location? = null
    private var isTrafficVisible = true
    private var currentMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private val transitStops = mutableListOf<TransitStop>()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val MAPS_API_KEY = "AIzaSyDn7hIph289VPpjEC7DOEY-picJJR6I1Qs"
        private const val TRANSIT_API_KEY = "xUAFiEJw8XJIFftacPwmd0GELBwhfBB9"
        private const val TRANSITLAND_BASE_URL = "https://transit.land/api/v2/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupMap(savedInstanceState)
        setupLocationClient()
        setupMotionDetector()
        setupBottomSheet()
        setupClickListeners()
        setupTransitLandService()
        checkLocationPermission()
    }

    // TransitLand API setup
    private fun setupTransitLandService() {
        val retrofit = Retrofit.Builder()
            .baseUrl(TRANSITLAND_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        transitLandService = retrofit.create(TransitLandService::class.java)
    }

    private fun initializeViews() {
        mapView = findViewById(R.id.mapView)
        searchEditText = findViewById(R.id.searchEditText)
        searchButton = findViewById(R.id.searchButton)
        toggleTrafficButton = findViewById(R.id.toggleTrafficButton)
        refreshButton = findViewById(R.id.refreshButton)
        currentLocationButton = findViewById(R.id.currentLocationButton)
        bottomSheet = findViewById(R.id.bottomSheet)
        routeOptionsRecyclerView = findViewById(R.id.routeOptionsRecyclerView)
        transitInfoRecyclerView = findViewById(R.id.transitInfoRecyclerView)

        // Setup RecyclerViews
        routeAdapter = RouteAdapter { route -> selectRoute(route) }
        routeOptionsRecyclerView.layoutManager = LinearLayoutManager(this)
        routeOptionsRecyclerView.adapter = routeAdapter

        transitAdapter = TransitAdapter()
        transitInfoRecyclerView.layoutManager = LinearLayoutManager(this)
        transitInfoRecyclerView.adapter = transitAdapter
    }

    private fun setupMap(savedInstanceState: Bundle?) {
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
    }

    private fun setupLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun setupMotionDetector() {
        motionDetector = MotionDetector(this) { motionState ->
            handleMotionStateChange(motionState)
        }
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun setupClickListeners() {
        searchButton.setOnClickListener {
            val destination = searchEditText.text.toString().trim()
            if (destination.isNotEmpty()) {
                searchDestination(destination)
            }
        }

        toggleTrafficButton.setOnClickListener {
            toggleTrafficLayer()
        }

        refreshButton.setOnClickListener {
            refreshData()
        }

        currentLocationButton.setOnClickListener {
            moveToCurrentLocation()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Configure map settings
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isCompassEnabled = true
        googleMap.isTrafficEnabled = isTrafficVisible

        // Set map click listener
        googleMap.setOnMapClickListener { latLng ->
            searchNearbyTransitStops(latLng.latitude, latLng.longitude)
        }

        // Set marker click listener
        googleMap.setOnMarkerClickListener { marker ->
            if (marker.tag is TransitStop) {
                showTransitStopInfo(marker.tag as TransitStop)
            }
            true
        }

        getCurrentLocation()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            googleMap.isMyLocationEnabled = true

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLocation = it
                    val currentLatLng = LatLng(it.latitude, it.longitude)

                    // Move camera to current location
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))

                    // Add current location marker
                    currentMarker?.remove()
                    currentMarker = googleMap.addMarker(
                        MarkerOptions()
                            .position(currentLatLng)
                            .title("Current Location")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                    )

                    // Search for nearby transit stops
                    searchNearbyTransitStops(it.latitude, it.longitude)
                }
            }
        }
    }

    private fun moveToCurrentLocation() {
        currentLocation?.let { location ->
            val currentLatLng = LatLng(location.latitude, location.longitude)
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
        } ?: run {
            getCurrentLocation()
        }
    }

    private fun toggleTrafficLayer() {
        isTrafficVisible = !isTrafficVisible
        googleMap.isTrafficEnabled = isTrafficVisible

        val message = if (isTrafficVisible) "Traffic layer enabled" else "Traffic layer disabled"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun refreshData() {
        Toast.makeText(this, "Refreshing data...", Toast.LENGTH_SHORT).show()
        getCurrentLocation()

        currentLocation?.let { location ->
            searchNearbyTransitStops(location.latitude, location.longitude)
        }
    }

    private fun searchDestination(destination: String) {
        // Simulate destination search (in real app, use Google Places API)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val routes = searchRoutes(destination)
                withContext(Dispatchers.Main) {
                    if (routes.isNotEmpty()) {
                        routeAdapter.updateRoutes(routes)
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    } else {
                        Toast.makeText(this@MainActivity, "No routes found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun searchRoutes(destination: String): List<Route> {
        // Simulate API call - replace with actual Google Directions API
        delay(1000)

        return listOf(
            Route("Driving", "25 min", "12.5 km", "Fast route via highway", RouteType.DRIVING),
            Route("Walking", "45 min", "3.2 km", "Scenic route through park", RouteType.WALKING),
            Route("Transit", "35 min", "8.1 km", "Bus + Metro", RouteType.TRANSIT)
        )
    }

    private fun selectRoute(route: Route) {
        Toast.makeText(this, "Selected ${route.mode} route", Toast.LENGTH_SHORT).show()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        // In real app, draw route on map using Google Directions API
        drawSimulatedRoute(route)
    }

    private fun drawSimulatedRoute(route: Route) {
        // Simulate drawing a route - in real app use Google Directions API
        currentLocation?.let { location ->
            val start = LatLng(location.latitude, location.longitude)
            val end = LatLng(location.latitude + 0.01, location.longitude + 0.01)

            destinationMarker?.remove()
            destinationMarker = googleMap.addMarker(
                MarkerOptions()
                    .position(end)
                    .title("Destination")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )

            val polylineOptions = PolylineOptions()
                .add(start, end)
                .width(8f)
                .color(getRouteColor(route.type))

            googleMap.addPolyline(polylineOptions)
        }
    }

    private fun getRouteColor(routeType: RouteType): Int {
        return when (routeType) {
            RouteType.DRIVING -> ContextCompat.getColor(this, android.R.color.holo_blue_dark)
            RouteType.WALKING -> ContextCompat.getColor(this, android.R.color.holo_green_dark)
            RouteType.TRANSIT -> ContextCompat.getColor(this, android.R.color.holo_orange_dark)
        }
    }

    private fun searchNearbyTransitStops(latitude: Double, longitude: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val stops = fetchNearbyTransitStops(latitude, longitude)
                withContext(Dispatchers.Main) {
                    displayTransitStops(stops)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Transit data unavailable", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Time formatting helper function - moved here before usage
    private fun formatDepartureTime(departureTime: String): String {
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val departureDate = formatter.parse(departureTime)
            val now = Date()

            if (departureDate != null) {
                val diffInMillis = departureDate.time - now.time
                val diffInMinutes = (diffInMillis / (1000 * 60)).toInt()

                when {
                    diffInMillis <= 0 -> "Arriving now"
                    diffInMinutes < 60 -> "${diffInMinutes} min"
                    else -> {
                        val hours = diffInMinutes / 60
                        val minutes = diffInMinutes % 60
                        "${hours}h ${minutes}m"
                    }
                }
            } else {
                "Unknown time"
            }
        } catch (e: Exception) {
            Log.w("TransitLand", "Failed to parse departure time: $departureTime")
            "Time unavailable"
        }
    }

    private suspend fun fetchNearbyTransitStops(lat: Double, lon: Double): List<TransitStop> {
        return try {
            Log.d("TransitLand", "Fetching transit stops for location: $lat, $lon")
            val stopsResponse = transitLandService.getNearbyStops(lat, lon, 1000, TRANSIT_API_KEY)

            Log.d("TransitLand", "Received ${stopsResponse.stops.size} stops")
            stopsResponse.stops.take(10).map { stop -> // Limit to 10 stops
                // Get departures for each stop (optional to avoid too many API calls)
                val arrivals = try {
                    val departuresResponse = transitLandService.getDepartures(stop.id, TRANSIT_API_KEY)
                    departuresResponse.departures.take(3).map { departure ->
                        "${departure.route.route_short_name} - ${formatDepartureTime(departure.departure_time)}"
                    }
                } catch (e: Exception) {
                    Log.w("TransitLand", "Failed to get departures for stop ${stop.id}: ${e.message}")
                    listOf("Real-time info unavailable")
                }

                TransitStop(
                    id = stop.id,
                    name = stop.stop_name,
                    latitude = stop.stop_lat,
                    longitude = stop.stop_lon,
                    type = "Transit",
                    arrivals = arrivals
                )
            }
        } catch (e: Exception) {
            Log.e("TransitLand", "Failed to fetch transit stops: ${e.message}")
            // Return mock data as fallback
            listOf(
                TransitStop("1", "Main St Station", lat + 0.002, lon + 0.001, "Bus",
                    listOf("Bus 42 - 5 min", "Bus 18 - 12 min")),
                TransitStop("2", "City Center", lat - 0.001, lon + 0.003, "Metro",
                    listOf("Blue Line - 3 min", "Red Line - 8 min")),
                TransitStop("3", "Park Ave", lat + 0.001, lon - 0.002, "Bus",
                    listOf("Bus 25 - 7 min", "Bus 67 - 15 min"))
            )
        }
    }

    private fun displayTransitStops(stops: List<TransitStop>) {
        // Clear existing transit markers
        transitStops.clear()

        stops.forEach { stop ->
            val position = LatLng(stop.latitude, stop.longitude)
            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(stop.name)
                    .snippet(stop.type)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
            marker?.tag = stop
            transitStops.add(stop)
        }
    }

    private fun showTransitStopInfo(stop: TransitStop) {
        transitAdapter.updateArrivals(stop.arrivals)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        Toast.makeText(this, "Transit info for ${stop.name}", Toast.LENGTH_SHORT).show()
    }

    private fun handleMotionStateChange(motionState: MotionState) {
        when (motionState) {
            MotionState.WALKING -> {
                // Adjust map settings for walking
                Toast.makeText(this, "Walking detected", Toast.LENGTH_SHORT).show()
            }
            MotionState.DRIVING -> {
                // Adjust map settings for driving
                Toast.makeText(this, "Driving detected", Toast.LENGTH_SHORT).show()
            }
            MotionState.STATIONARY -> {
                // User is stationary
            }
        }
    }

    // Lifecycle methods for MapView
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        motionDetector.start()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        motionDetector.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        motionDetector.stop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}