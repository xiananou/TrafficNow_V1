// app/src/main/java/com/example/trafficnow/MainActivity.kt
package com.example.trafficnow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

// Google Places API 数据模型
data class PlacesResponse(
    val results: List<PlaceResult>,
    val status: String
)

data class PlaceResult(
    val place_id: String,
    val name: String,
    val formatted_address: String,
    val geometry: PlaceGeometry
)

data class PlaceGeometry(
    val location: PlaceLocation
)

data class PlaceLocation(
    val lat: Double,
    val lng: Double
)

// Google Directions API 数据模型
data class DirectionsResponse(
    val routes: List<DirectionRoute>,
    val status: String
)

data class DirectionRoute(
    val legs: List<RouteLeg>,
    val overview_polyline: PolylineOverview,
    val summary: String
)

data class RouteLeg(
    val distance: DistanceInfo,
    val duration: DurationInfo,
    val start_address: String,
    val end_address: String,
    val steps: List<RouteStep>
)

data class DistanceInfo(
    val text: String,
    val value: Int
)

data class DurationInfo(
    val text: String,
    val value: Int
)

data class RouteStep(
    val distance: DistanceInfo,
    val duration: DurationInfo,
    val html_instructions: String,
    val travel_mode: String
)

data class PolylineOverview(
    val points: String
)

// Google Maps API 服务接口
interface GoogleMapsService {
    @GET("place/textsearch/json")
    suspend fun searchPlaces(
        @Query("query") query: String,
        @Query("key") apiKey: String,
        @Query("location") location: String? = null,
        @Query("radius") radius: Int = 50000
    ): PlacesResponse

    @GET("directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("mode") mode: String = "driving",
        @Query("key") apiKey: String,
        @Query("alternatives") alternatives: Boolean = true,
        @Query("traffic_model") trafficModel: String = "best_guess",
        @Query("departure_time") departureTime: String = "now"
    ): DirectionsResponse
}

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
    private lateinit var googleMapsService: GoogleMapsService

    private var currentLocation: Location? = null
    private var destinationLocation: LatLng? = null
    private var isTrafficVisible = true
    private var currentMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var currentPolyline: Polyline? = null
    private var currentRouteData: DirectionRoute? = null
    private val transitStops = mutableListOf<TransitStop>()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private val MAPS_API_KEY = BuildConfig.GOOGLE_MAPS_API_KEY
        private val TRANSIT_API_KEY = BuildConfig.TRANSIT_API_KEY
        private const val TRANSITLAND_BASE_URL = "https://transit.land/api/v2/"
        private const val GOOGLE_MAPS_BASE_URL = "https://maps.googleapis.com/maps/api/"

        // TransitLand API 经常有问题，主要使用Google Places API作为替代
        private const val USE_TRANSITLAND_API = false
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
        setupGoogleMapsService()
        setupTransitLandService()
        checkLocationPermission()
    }

    // 设置Google Maps服务
    private fun setupGoogleMapsService() {
        val retrofit = Retrofit.Builder()
            .baseUrl(GOOGLE_MAPS_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        googleMapsService = retrofit.create(GoogleMapsService::class.java)
    }

    // TransitLand API setup
    private fun setupTransitLandService() {
        val retrofit = Retrofit.Builder()
            .baseUrl(TRANSITLAND_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        transitLandService = retrofit.create(TransitLandService::class.java)

        // 注意：TransitLand API 经常返回403错误，主要依赖Google Places API
        Log.d("TransitLand", "TransitLand service setup completed (backup only)")
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
        try {
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            bottomSheetBehavior.peekHeight = 200
            bottomSheetBehavior.isHideable = true

            Log.d("BottomSheet", "Bottom sheet setup successful")
        } catch (e: Exception) {
            Log.e("BottomSheet", "Failed to setup bottom sheet", e)
            Toast.makeText(this, "Bottom sheet setup failed", Toast.LENGTH_SHORT).show()
        }
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
            Log.d("MapClick", "Map clicked at: ${latLng.latitude}, ${latLng.longitude}")
            searchNearbyTransitStops(latLng.latitude, latLng.longitude)
        }

        // Set marker click listener
        googleMap.setOnMarkerClickListener { marker ->
            Log.d("MarkerClick", "Marker clicked: ${marker.title}")
            if (marker.tag is TransitStop) {
                showTransitStopInfo(marker.tag as TransitStop)
                return@setOnMarkerClickListener true
            }
            false
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

        // 清除当前路线
        clearRoute()

        // 清除目的地标记
        destinationMarker?.remove()
        destinationMarker = null
        destinationLocation = null

        // 隐藏底部表单
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        // 重新获取当前位置
        getCurrentLocation()

        currentLocation?.let { location ->
            searchNearbyTransitStops(location.latitude, location.longitude)
        }
    }

    // 更新的搜索目的地方法
    private fun searchDestination(destination: String) {
        if (destination.isBlank()) {
            Toast.makeText(this, "Please enter a destination", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Searching for $destination...", Toast.LENGTH_SHORT).show()
                }

                // 1. 首先搜索地点
                val place = searchPlace(destination)
                if (place == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Location not found: $destination", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 2. 保存目的地位置
                destinationLocation = LatLng(place.geometry.location.lat, place.geometry.location.lng)

                // 3. 在地图上添加目的地标记
                withContext(Dispatchers.Main) {
                    addDestinationMarker(place)
                }

                // 4. 获取路线
                val routes = if (currentLocation != null) {
                    getActualRoutes(currentLocation!!, place)
                } else {
                    emptyList()
                }

                withContext(Dispatchers.Main) {
                    if (routes.isNotEmpty()) {
                        routeAdapter.updateRoutes(routes)
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        Toast.makeText(this@MainActivity, "Found ${routes.size} route(s)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "No routes found", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Search failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Search failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 搜索地点
    private suspend fun searchPlace(query: String): PlaceResult? {
        return try {
            // 如果有当前位置，优先搜索附近的地点
            val locationBias = currentLocation?.let {
                "${it.latitude},${it.longitude}"
            }

            val response = googleMapsService.searchPlaces(
                query = query,
                apiKey = MAPS_API_KEY,
                location = locationBias,
                radius = 50000 // 50km范围内搜索
            )

            if (response.status == "OK" && response.results.isNotEmpty()) {
                Log.d("PlaceSearch", "Found ${response.results.size} places for '$query'")
                response.results.first() // 返回第一个结果
            } else {
                Log.w("PlaceSearch", "No places found for '$query', status: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e("PlaceSearch", "Failed to search place: $query", e)
            null
        }
    }

    // 获取实际路线
    private suspend fun getActualRoutes(origin: Location, destination: PlaceResult): List<Route> {
        val routes = mutableListOf<Route>()
        val originStr = "${origin.latitude},${origin.longitude}"
        val destStr = "${destination.geometry.location.lat},${destination.geometry.location.lng}"

        try {
            // 获取驾车路线
            val drivingResponse = googleMapsService.getDirections(
                origin = originStr,
                destination = destStr,
                mode = "driving",
                apiKey = MAPS_API_KEY,
                alternatives = true
            )

            if (drivingResponse.status == "OK" && drivingResponse.routes.isNotEmpty()) {
                drivingResponse.routes.take(2).forEach { route -> // 最多2条驾车路线
                    val leg = route.legs.firstOrNull()
                    if (leg != null) {
                        routes.add(Route(
                            mode = "Driving",
                            duration = leg.duration.text,
                            distance = leg.distance.text,
                            description = route.summary.ifEmpty { "Via main roads" },
                            type = RouteType.DRIVING
                        ))
                    }
                }
            }

            // 获取步行路线
            val walkingResponse = googleMapsService.getDirections(
                origin = originStr,
                destination = destStr,
                mode = "walking",
                apiKey = MAPS_API_KEY
            )

            if (walkingResponse.status == "OK" && walkingResponse.routes.isNotEmpty()) {
                val route = walkingResponse.routes.first()
                val leg = route.legs.firstOrNull()
                if (leg != null) {
                    routes.add(Route(
                        mode = "Walking",
                        duration = leg.duration.text,
                        distance = leg.distance.text,
                        description = "Walking route",
                        type = RouteType.WALKING
                    ))
                }
            }

            // 获取公交路线
            val transitResponse = googleMapsService.getDirections(
                origin = originStr,
                destination = destStr,
                mode = "transit",
                apiKey = MAPS_API_KEY
            )

            if (transitResponse.status == "OK" && transitResponse.routes.isNotEmpty()) {
                val route = transitResponse.routes.first()
                val leg = route.legs.firstOrNull()
                if (leg != null) {
                    routes.add(Route(
                        mode = "Transit",
                        duration = leg.duration.text,
                        distance = leg.distance.text,
                        description = "Public transportation",
                        type = RouteType.TRANSIT
                    ))
                }
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to get routes", e)
        }

        return routes
    }

    // 在地图上添加目的地标记
    private fun addDestinationMarker(place: PlaceResult) {
        val position = LatLng(place.geometry.location.lat, place.geometry.location.lng)

        // 移除之前的目的地标记
        destinationMarker?.remove()

        // 添加新的目的地标记
        destinationMarker = googleMap.addMarker(
            MarkerOptions()
                .position(position)
                .title(place.name)
                .snippet(place.formatted_address)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )

        // 调整地图视图包含起点和终点
        currentLocation?.let { current ->
            val bounds = LatLngBounds.Builder()
                .include(LatLng(current.latitude, current.longitude))
                .include(position)
                .build()

            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, 100)
            )
        } ?: run {
            // 如果没有当前位置，就移动到目的地
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(position, 15f)
            )
        }
    }

    private fun selectRoute(route: Route) {
        Toast.makeText(this, "Selected ${route.mode} route", Toast.LENGTH_SHORT).show()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        // 获取并绘制真实路线
        if (currentLocation != null && destinationLocation != null) {
            drawRealRoute(route)
        }
    }

    // 绘制真实路线
    private fun drawRealRoute(selectedRoute: Route) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val origin = currentLocation!!
                val destination = destinationLocation!!

                val originStr = "${origin.latitude},${origin.longitude}"
                val destStr = "${destination.latitude},${destination.longitude}"

                // 根据选择的路线类型获取对应的路线
                val mode = when (selectedRoute.type) {
                    RouteType.DRIVING -> "driving"
                    RouteType.WALKING -> "walking"
                    RouteType.TRANSIT -> "transit"
                }

                val response = googleMapsService.getDirections(
                    origin = originStr,
                    destination = destStr,
                    mode = mode,
                    apiKey = MAPS_API_KEY,
                    alternatives = false // 只获取最佳路线
                )

                if (response.status == "OK" && response.routes.isNotEmpty()) {
                    val route = response.routes.first()
                    currentRouteData = route

                    withContext(Dispatchers.Main) {
                        drawRouteOnMap(route, selectedRoute.type)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Failed to get route details", Toast.LENGTH_SHORT).show()
                        // 如果API失败，画直线作为后备
                        drawSimpleRoute(selectedRoute.type)
                    }
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to draw route", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Route drawing failed", Toast.LENGTH_SHORT).show()
                    drawSimpleRoute(selectedRoute.type)
                }
            }
        }
    }

    // 在地图上绘制路线
    private fun drawRouteOnMap(route: DirectionRoute, routeType: RouteType) {
        try {
            // 移除之前的路线
            currentPolyline?.remove()

            // 解码polyline points
            val decodedPath = decodePolyline(route.overview_polyline.points)

            if (decodedPath.isNotEmpty()) {
                // 创建polyline选项
                val polylineOptions = PolylineOptions()
                    .addAll(decodedPath)
                    .width(8f)
                    .color(getRouteColor(routeType))
                    .geodesic(true)

                // 添加到地图
                currentPolyline = googleMap.addPolyline(polylineOptions)

                // 调整地图视图包含整个路线
                val boundsBuilder = LatLngBounds.Builder()
                decodedPath.forEach { point ->
                    boundsBuilder.include(point)
                }

                val bounds = boundsBuilder.build()
                val padding = 100 // 边距

                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, padding)
                )

                Log.d("RouteDrawing", "Successfully drew route with ${decodedPath.size} points")
            } else {
                Log.w("RouteDrawing", "No points decoded from polyline")
                drawSimpleRoute(routeType)
            }

        } catch (e: Exception) {
            Log.e("RouteDrawing", "Failed to draw route on map", e)
            drawSimpleRoute(routeType)
        }
    }

    // 解码Google Polyline编码的路径
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0

            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0

            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)

            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }

        return poly
    }

    // 简单路线绘制（后备方案）
    private fun drawSimpleRoute(routeType: RouteType) {
        currentLocation?.let { location ->
            val start = LatLng(location.latitude, location.longitude)
            val end = destinationLocation ?: return

            // 移除之前的路线
            currentPolyline?.remove()

            val polylineOptions = PolylineOptions()
                .add(start, end)
                .width(8f)
                .color(getRouteColor(routeType))
                .pattern(listOf(Dash(10f), Gap(5f))) // 虚线表示这是简化路线

            currentPolyline = googleMap.addPolyline(polylineOptions)

            // 调整视图
            val bounds = LatLngBounds.Builder()
                .include(start)
                .include(end)
                .build()

            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, 100)
            )
        }
    }

    // 清除路线的方法
    private fun clearRoute() {
        currentPolyline?.remove()
        currentPolyline = null
        currentRouteData = null
    }

    private fun getRouteColor(routeType: RouteType): Int {
        return when (routeType) {
            RouteType.DRIVING -> ContextCompat.getColor(this, R.color.primary_color) // 蓝色
            RouteType.WALKING -> ContextCompat.getColor(this, R.color.secondary_color) // 绿色
            RouteType.TRANSIT -> ContextCompat.getColor(this, R.color.accent_color) // 橙色
        }
    }

    // 改进的搜索附近公交站点方法
    private fun searchNearbyTransitStops(latitude: Double, longitude: Double) {
        Log.d("TransitSearch", "Searching transit stops at: $latitude, $longitude")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val stops = fetchTransitStopsWithFallback(latitude, longitude)
                withContext(Dispatchers.Main) {
                    Log.d("TransitSearch", "Displaying ${stops.size} transit stops")
                    displayTransitStops(stops)
                    if (stops.isNotEmpty()) {
                        val message = if (stops.any { it.id.startsWith("mock") }) {
                            "Showing demo transit stops"
                        } else {
                            "Found ${stops.size} nearby transit stops"
                        }
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("TransitSearch", "Failed to fetch transit stops", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Using demo transit data", Toast.LENGTH_SHORT).show()
                    // 显示模拟数据以便测试
                    val mockStops = getMockTransitStops(latitude, longitude)
                    displayTransitStops(mockStops)
                }
            }
        }
    }

    // 带有多种后备方案的获取公交站点方法
    private suspend fun fetchTransitStopsWithFallback(lat: Double, lon: Double): List<TransitStop> {
        // 策略1: 使用Google Places API (最可靠)
        try {
            Log.d("TransitAPI", "Attempting Google Places API...")
            val googleStops = fetchStopsFromGooglePlaces(lat, lon)
            if (googleStops.isNotEmpty()) {
                Log.d("TransitAPI", "Success with Google Places API: ${googleStops.size} stops")
                return googleStops
            }
        } catch (e: Exception) {
            Log.w("TransitAPI", "Google Places API failed: ${e.message}")
        }

        // 策略2: 返回高质量模拟数据
        Log.d("TransitAPI", "Using demo data as fallback")
        return getMockTransitStops(lat, lon)
    }

    // Google Places API 获取公交站点
    private suspend fun fetchStopsFromGooglePlaces(lat: Double, lon: Double): List<TransitStop> {
        val searchTypes = listOf(
            "transit_station",
            "bus_station",
            "subway_station",
            "train_station"
        )

        val allStops = mutableListOf<TransitStop>()

        for (searchType in searchTypes.take(2)) { // 限制搜索避免配额问题
            try {
                val response = googleMapsService.searchPlaces(
                    query = searchType.replace("_", " "),
                    apiKey = MAPS_API_KEY,
                    location = "$lat,$lon",
                    radius = 800 // 缩小搜索范围提高相关性
                )

                if (response.status == "OK") {
                    val stops = response.results.take(4).map { place ->
                        TransitStop(
                            id = place.place_id,
                            name = place.name,
                            latitude = place.geometry.location.lat,
                            longitude = place.geometry.location.lng,
                            type = determineStationType(place.name, searchType),
                            arrivals = generateRealisticArrivals(searchType)
                        )
                    }
                    allStops.addAll(stops)
                }

                delay(200) // 避免API限制

            } catch (e: Exception) {
                Log.w("GooglePlaces", "Failed to search $searchType: ${e.message}")
            }
        }

        return allStops.distinctBy { it.name }.take(6)
    }

    // 根据名称和查询确定站点类型
    private fun determineStationType(name: String, query: String): String {
        val lowerName = name.lowercase()
        val lowerQuery = query.lowercase()

        return when {
            lowerName.contains("bus") || lowerQuery.contains("bus") -> "Bus Stop"
            lowerName.contains("metro") || lowerName.contains("subway") || lowerQuery.contains("subway") -> "Metro Station"
            lowerName.contains("train") || lowerQuery.contains("train") -> "Train Station"
            lowerName.contains("station") -> "Station"
            else -> "Transit Stop"
        }
    }

    // 生成更真实的到达时间
    private fun generateRealisticArrivals(stationType: String): List<String> {
        val random = kotlin.random.Random

        return when {
            stationType.contains("bus") -> {
                listOf(
                    "Bus ${random.nextInt(10, 99)} - ${random.nextInt(2, 8)} min",
                    "Bus ${random.nextInt(10, 99)} - ${random.nextInt(8, 15)} min",
                    "Express ${random.nextInt(100, 200)} - ${random.nextInt(12, 25)} min"
                )
            }
            stationType.contains("subway") || stationType.contains("metro") -> {
                listOf(
                    "${listOf("Blue", "Red", "Green", "Orange").random()} Line - ${random.nextInt(1, 5)} min",
                    "${listOf("Blue", "Red", "Green", "Orange").random()} Line - ${random.nextInt(5, 12)} min",
                    "${listOf("Blue", "Red", "Green", "Orange").random()} Line - ${random.nextInt(10, 18)} min"
                )
            }
            stationType.contains("train") -> {
                listOf(
                    "Train ${random.nextInt(100, 999)} - ${random.nextInt(5, 15)} min",
                    "Express ${random.nextInt(100, 999)} - ${random.nextInt(15, 30)} min",
                    "Local ${random.nextInt(100, 999)} - ${random.nextInt(20, 40)} min"
                )
            }
            else -> generateMockArrivals()
        }
    }

    // 生成模拟到达时间
    private fun generateMockArrivals(): List<String> {
        val routes = listOf("Bus 42", "Bus 18", "Metro Blue", "Metro Red", "Bus 25", "Bus 67")
        val times = listOf("2 min", "5 min", "8 min", "12 min", "15 min", "18 min")

        return (1..3).map {
            "${routes.random()} - ${times.random()}"
        }
    }

    // 模拟数据生成方法
    private fun getMockTransitStops(lat: Double, lon: Double): List<TransitStop> {
        Log.d("TransitSearch", "Generating mock transit stops")
        return listOf(
            TransitStop(
                id = "mock_1",
                name = "Main Street Bus Stop",
                latitude = lat + 0.002,
                longitude = lon + 0.001,
                type = "Bus Stop",
                arrivals = listOf("Bus 42 - 5 min", "Bus 18 - 12 min", "Bus 25 - 18 min")
            ),
            TransitStop(
                id = "mock_2",
                name = "City Center Metro Station",
                latitude = lat - 0.001,
                longitude = lon + 0.003,
                type = "Metro Station",
                arrivals = listOf("Blue Line - 3 min", "Red Line - 8 min", "Green Line - 14 min")
            ),
            TransitStop(
                id = "mock_3",
                name = "Park Avenue Bus Stop",
                latitude = lat + 0.001,
                longitude = lon - 0.002,
                type = "Bus Stop",
                arrivals = listOf("Bus 67 - 7 min", "Bus 89 - 15 min", "Express 101 - 22 min")
            ),
            TransitStop(
                id = "mock_4",
                name = "Downtown Transit Hub",
                latitude = lat - 0.0015,
                longitude = lon - 0.001,
                type = "Transit Hub",
                arrivals = listOf("Bus 33 - 4 min", "Tram A - 9 min", "Bus 55 - 16 min")
            ),
            TransitStop(
                id = "mock_5",
                name = "University Train Station",
                latitude = lat + 0.0025,
                longitude = lon + 0.002,
                type = "Train Station",
                arrivals = listOf("Train 401 - 6 min", "Train 302 - 13 min", "Express 201 - 25 min")
            ),
            TransitStop(
                id = "mock_6",
                name = "Shopping Mall Bus Terminal",
                latitude = lat - 0.002,
                longitude = lon + 0.0015,
                type = "Bus Terminal",
                arrivals = listOf("Bus 77 - 2 min", "Bus 123 - 9 min", "Night Bus N1 - 20 min")
            )
        )
    }

    private fun displayTransitStops(stops: List<TransitStop>) {
        Log.d("TransitDisplay", "Displaying ${stops.size} transit stops on map")

        // 统计站点类型
        val typeCount = mutableMapOf<String, Int>()

        stops.forEach { stop ->
            val position = LatLng(stop.latitude, stop.longitude)

            // 获取站点图标
            val icon = getTransitStopIcon(stop)
            val stopTypeInfo = analyzeStopType(stop)
            val stopType = stopTypeInfo.first

            // 统计类型
            typeCount[stopType] = typeCount.getOrDefault(stopType, 0) + 1

            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("${getStopTypeEmoji(stopType)} ${stop.name}")
                    .snippet("${stop.type} • Tap for arrivals")
                    .icon(icon)
            )
            marker?.tag = stop

            Log.d("TransitDisplay", "Added ${stopType} marker for ${stop.name}")
        }

        // 更新内部列表
        transitStops.clear()
        transitStops.addAll(stops)

        // 显示站点类型统计
        if (stops.isNotEmpty()) {
            val summary = typeCount.entries.joinToString(" • ") {
                "${getStopTypeEmoji(it.key)}${it.value}"
            }
            Toast.makeText(this, "Found: $summary", Toast.LENGTH_LONG).show()
        }
    }

    // 获取站点图标和颜色
    private fun getTransitStopIcon(stop: TransitStop): BitmapDescriptor {
        val stopTypeInfo = analyzeStopType(stop)

        Log.d("TransitIcon", "${stop.name} (${stop.type}) -> ${stopTypeInfo.first} (${stopTypeInfo.second})")

        return BitmapDescriptorFactory.defaultMarker(stopTypeInfo.second)
    }

    // 分析站点类型并返回 (类型名称, 颜色)
    private fun analyzeStopType(stop: TransitStop): Pair<String, Float> {
        val name = stop.name.lowercase()
        val type = stop.type.lowercase()

        return when {
            // 地铁/轻轨 - 橙色
            name.contains("metro") || name.contains("subway") ||
                    type.contains("metro") || type.contains("subway") ||
                    name.contains("underground") -> {
                "Metro/Subway" to BitmapDescriptorFactory.HUE_ORANGE
            }

            // 火车站 - 红色
            name.contains("train") || name.contains("railway") ||
                    type.contains("train") || type.contains("railway") ||
                    name.contains("central station") -> {
                "Train Station" to BitmapDescriptorFactory.HUE_RED
            }

            // 公交站 - 绿色
            name.contains("bus") || type.contains("bus") ||
                    name.contains("stop") || type.contains("stop") -> {
                "Bus Stop" to BitmapDescriptorFactory.HUE_GREEN
            }

            // 交通枢纽 - 蓝色
            name.contains("hub") || name.contains("terminal") ||
                    name.contains("interchange") || type.contains("hub") ||
                    type.contains("terminal") || name.contains("center") -> {
                "Transit Hub" to BitmapDescriptorFactory.HUE_AZURE
            }

            // 轻轨/电车 - 青色
            name.contains("tram") || name.contains("light rail") ||
                    type.contains("tram") || name.contains("lrt") -> {
                "Tram/Light Rail" to BitmapDescriptorFactory.HUE_CYAN
            }

            // 机场/港口 - 洋红色
            name.contains("airport") || name.contains("port") ||
                    name.contains("ferry") || type.contains("airport") -> {
                "Airport/Ferry" to BitmapDescriptorFactory.HUE_MAGENTA
            }

            // 包含"station"的通常是重要站点 - 橙色
            name.contains("station") && !name.contains("bus") -> {
                "Station" to BitmapDescriptorFactory.HUE_ORANGE
            }

            // 其他 - 紫色
            else -> {
                "Other Transit" to BitmapDescriptorFactory.HUE_VIOLET
            }
        }
    }

    // 获取站点类型的emoji图标
    private fun getStopTypeEmoji(stopType: String): String {
        return when (stopType) {
            "Metro/Subway" -> "🚇"
            "Train Station" -> "🚆"
            "Bus Stop" -> "🚌"
            "Transit Hub" -> "🚉"
            "Tram/Light Rail" -> "🚋"
            "Airport/Ferry" -> "✈️"
            "Station" -> "🚉"
            else -> "🚏"
        }
    }

    private fun showTransitStopInfo(stop: TransitStop) {
        Log.d("TransitInfo", "Showing info for ${stop.name} with ${stop.arrivals.size} arrivals")

        // 更新公交信息适配器
        transitAdapter.updateArrivals(stop.arrivals)

        // 显示底部表单
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

        // 显示详细信息
        val message = "Transit info for ${stop.name}\n${stop.arrivals.size} upcoming arrivals"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        // 可选：移动地图到选中的站点
        val position = LatLng(stop.latitude, stop.longitude)
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 16f))
    }

    // Time formatting helper function
    private fun formatDepartureTime(departureTime: String): String {
        return try {
            // 支持多种时间格式
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss"
            )

            var departureDate: Date? = null
            for (format in formats) {
                try {
                    val formatter = SimpleDateFormat(format, Locale.getDefault())
                    departureDate = formatter.parse(departureTime)
                    break
                } catch (e: Exception) {
                    continue
                }
            }

            if (departureDate == null) {
                Log.w("TransitLand", "Could not parse departure time: $departureTime")
                return "Time unavailable"
            }

            val now = Date()
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
        } catch (e: Exception) {
            Log.w("TransitLand", "Failed to format departure time: $departureTime", e)
            "Time unavailable"
        }
    }

    // 网络连接检查
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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