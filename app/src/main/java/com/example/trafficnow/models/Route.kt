package com.example.trafficnow.models

data class Route(
    val mode: String,
    val duration: String,
    val distance: String,
    val description: String,
    val type: RouteType
)