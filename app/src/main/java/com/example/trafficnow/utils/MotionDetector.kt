// app/src/main/java/com/example/trafficnow/utils/MotionDetector.kt
package com.example.trafficnow.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.trafficnow.models.MotionState

class MotionDetector(
    private val context: Context,
    private val onMotionStateChanged: (MotionState) -> Unit
) {
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastUpdate: Long = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let { handleSensorChange(it) }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        accelerometer?.let { sensor ->
            sensorManager?.registerListener(
                sensorEventListener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(sensorEventListener)
    }

    private fun handleSensorChange(event: SensorEvent) {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastUpdate > 100) {
            val timeDiff = currentTime - lastUpdate
            lastUpdate = currentTime

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val speed = Math.abs(x + y + z - lastX - lastY - lastZ) / timeDiff * 10000

            val motionState = when {
                speed > 800 -> MotionState.DRIVING
                speed > 200 -> MotionState.WALKING
                else -> MotionState.STATIONARY
            }

            onMotionStateChanged(motionState)

            lastX = x
            lastY = y
            lastZ = z
        }
    }
}