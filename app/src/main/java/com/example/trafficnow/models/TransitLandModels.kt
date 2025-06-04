package com.example.trafficnow.models

// TransitLand API response models
data class TransitLandStopsResponse(
    val stops: List<TransitLandStop>
)

data class TransitLandStop(
    val id: String,
    val stop_name: String,
    val stop_lat: Double,
    val stop_lon: Double,
    val served_by_route_ids: List<String>? = null
)

data class TransitLandDeparturesResponse(
    val departures: List<TransitLandDeparture>
)

data class TransitLandDeparture(
    val departure_time: String,
    val route: TransitLandRoute
)

data class TransitLandRoute(
    val route_short_name: String,
    val route_long_name: String
)