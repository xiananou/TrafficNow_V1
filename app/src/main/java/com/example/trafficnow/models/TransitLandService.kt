package com.example.trafficnow.models

import retrofit2.http.GET
import retrofit2.http.Query

interface TransitLandService {
    @GET("stops")
    suspend fun getNearbyStops(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("radius") radius: Int = 1000, // meters
        @Query("apikey") apiKey: String
    ): TransitLandStopsResponse

    @GET("departures")
    suspend fun getDepartures(
        @Query("stop") stopId: String,
        @Query("apikey") apiKey: String
    ): TransitLandDeparturesResponse
}