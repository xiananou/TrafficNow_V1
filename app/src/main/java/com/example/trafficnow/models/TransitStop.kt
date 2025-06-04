// app/src/main/java/com/example/trafficnow/models/TransitStop.kt
package com.example.trafficnow.models

data class TransitStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val arrivals: List<String>
)