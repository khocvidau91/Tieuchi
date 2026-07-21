package com.xiaozhi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class SensorFusion(context: Context) {
    companion object {
        private const val TAG = "SensorFusion"
        private const val SEND_INTERVAL_MS = 5000L // 5 giây thay vì 3s
        private const val SIGNIFICANT_MOTION_THRESHOLD = 0.5f
        // Ngưỡng thay đổi
        private const val ACCEL_THRESHOLD = 0.5f
        private const val GYRO_THRESHOLD = 0.1f
        private const val MAGNET_THRESHOLD = 5.0f
        private const val PROXIMITY_THRESHOLD = 0.5f
        private const val LIGHT_THRESHOLD = 10.0f
        private const val PRESSURE_THRESHOLD = 0.5f
        private const val LOCATION_THRESHOLD_METERS = 10.0f
    }

    var onSensorDataReady: ((JsonObject) -> Unit)? = null

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager?

    private var proximitySensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null
    private var magnetometerSensor: Sensor? = null
    private var pressureSensor: Sensor? = null
    private var temperatureSensor: Sensor? = null
    private var humiditySensor: Sensor? = null
    private var stepCounterSensor: Sensor? = null

    private var proximityDistance: Float? = null
    private var lightLux: Float? = null
    private var acceleration = FloatArray(3)
    private var gyroscope = FloatArray(3)
    private var magnetometer = FloatArray(3)
    private var pressure: Float? = null
    private var temperature: Float? = null
    private var humidity: Float? = null
    private var steps: Int? = null

    private var executor: ScheduledExecutorService? = null
    private var lastLocation: Location? = null
    private var lastSentData: JsonObject? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            try {
                lastLocation = location
            } catch (e: Exception) {
                Log.e(TAG, "Error updating location", e)
            }
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            when (event.sensor.type) {
                Sensor.TYPE_PROXIMITY -> proximityDistance = event.values[0]
                Sensor.TYPE_LIGHT -> lightLux = event.values[0]
                Sensor.TYPE_ACCELEROMETER -> System.arraycopy(event.values, 0, acceleration, 0, 3)
                Sensor.TYPE_GYROSCOPE -> System.arraycopy(event.values, 0, gyroscope, 0, 3)
                Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(event.values, 0, magnetometer, 0, 3)
                Sensor.TYPE_PRESSURE -> pressure = event.values[0]
                Sensor.TYPE_AMBIENT_TEMPERATURE -> temperature = event.values[0]
                Sensor.TYPE_RELATIVE_HUMIDITY -> humidity = event.values[0]
                Sensor.TYPE_STEP_COUNTER -> steps = event.values[0].toInt()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        executor?.shutdown()
        executor = Executors.newSingleThreadScheduledExecutor()

        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        proximitySensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        lightSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerometerSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscopeSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        magnetometerSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        pressureSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        temperatureSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        humiditySensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        stepCounterSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 10000L, 5f, locationListener
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Unexpected security exception for location", e)
            }
        } else {
            Log.i(TAG, "Location permission not granted – skipping GPS updates")
        }

        executor?.scheduleAtFixedRate({
            val newData = buildSensorJson()
            if (newData.size() > 0) {
                val shouldSend = if (lastSentData == null) true else hasSignificantChange(newData, lastSentData!!)
                if (shouldSend) {
                    lastSentData = newData
                    Log.d(TAG, "Sensor data changed significantly, sending: $newData")
                    onSensorDataReady?.invoke(newData)
                } else {
                    Log.v(TAG, "No significant sensor change, skip sending")
                }
            }
        }, 0, SEND_INTERVAL_MS, TimeUnit.MILLISECONDS)

        Log.d(TAG, "Sensor fusion started")
    }

    private fun hasSignificantChange(newData: JsonObject, oldData: JsonObject): Boolean {
        val newSensors = newData.getAsJsonObject("sensors") ?: return true
        val oldSensors = oldData.getAsJsonObject("sensors") ?: return true

        // Kiểm tra accelerometer
        val newAcc = newSensors.getAsJsonObject("accelerometer")
        val oldAcc = oldSensors.getAsJsonObject("accelerometer")
        if (newAcc != null && oldAcc != null) {
            val dx = newAcc.get("x").asFloat - oldAcc.get("x").asFloat
            val dy = newAcc.get("y").asFloat - oldAcc.get("y").asFloat
            val dz = newAcc.get("z").asFloat - oldAcc.get("z").asFloat
            if (sqrt(dx*dx + dy*dy + dz*dz) > ACCEL_THRESHOLD) return true
        }

        // Kiểm tra gyroscope
        val newGyro = newSensors.getAsJsonObject("gyroscope")
        val oldGyro = oldSensors.getAsJsonObject("gyroscope")
        if (newGyro != null && oldGyro != null) {
            val dx = newGyro.get("x").asFloat - oldGyro.get("x").asFloat
            val dy = newGyro.get("y").asFloat - oldGyro.get("y").asFloat
            val dz = newGyro.get("z").asFloat - oldGyro.get("z").asFloat
            if (sqrt(dx*dx + dy*dy + dz*dz) > GYRO_THRESHOLD) return true
        }

        // Kiểm tra magnetometer
        val newMag = newSensors.getAsJsonObject("magnetometer")
        val oldMag = oldSensors.getAsJsonObject("magnetometer")
        if (newMag != null && oldMag != null) {
            val dx = newMag.get("x").asFloat - oldMag.get("x").asFloat
            val dy = newMag.get("y").asFloat - oldMag.get("y").asFloat
            val dz = newMag.get("z").asFloat - oldMag.get("z").asFloat
            if (sqrt(dx*dx + dy*dy + dz*dz) > MAGNET_THRESHOLD) return true
        }

        // Kiểm tra proximity
        val newProx = newSensors.getAsJsonObject("proximity")?.get("distance_cm")?.asFloat
        val oldProx = oldSensors.getAsJsonObject("proximity")?.get("distance_cm")?.asFloat
        if (newProx != null && oldProx != null && kotlin.math.abs(newProx - oldProx) > PROXIMITY_THRESHOLD) return true

        // Kiểm tra light
        val newLight = newSensors.getAsJsonObject("light")?.get("lux")?.asFloat
        val oldLight = oldSensors.getAsJsonObject("light")?.get("lux")?.asFloat
        if (newLight != null && oldLight != null && kotlin.math.abs(newLight - oldLight) > LIGHT_THRESHOLD) return true

        // Kiểm tra pressure
        val newPress = newSensors.getAsJsonObject("pressure")?.get("hPa")?.asFloat
        val oldPress = oldSensors.getAsJsonObject("pressure")?.get("hPa")?.asFloat
        if (newPress != null && oldPress != null && kotlin.math.abs(newPress - oldPress) > PRESSURE_THRESHOLD) return true

        // Kiểm tra location
        val newLoc = newSensors.getAsJsonObject("location")
        val oldLoc = oldSensors.getAsJsonObject("location")
        if (newLoc != null && oldLoc != null) {
            val lat1 = newLoc.get("latitude").asDouble
            val lon1 = newLoc.get("longitude").asDouble
            val lat2 = oldLoc.get("latitude").asDouble
            val lon2 = oldLoc.get("longitude").asDouble
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            if (results[0] > LOCATION_THRESHOLD_METERS) return true
        }

        return false
    }

    fun stop() {
        sensorManager.unregisterListener(sensorListener)
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (ex: SecurityException) {
            // ignore
        }
        executor?.shutdown()
        executor = null
        lastSentData = null
        Log.d(TAG, "Sensor fusion stopped")
    }

    private fun buildSensorJson(): JsonObject {
        val root = JsonObject()
        root.addProperty("type", "context")
        val sensors = JsonObject()

        proximityDistance?.let {
            val proximity = JsonObject()
            proximity.addProperty("distance_cm", it)
            proximity.addProperty("is_near", it < (proximitySensor?.maximumRange ?: 5f))
            sensors.add("proximity", proximity)
        }

        lightLux?.let {
            val light = JsonObject()
            light.addProperty("lux", it)
            val level = when {
                it < 10 -> "dark"
                it < 500 -> "indoors"
                it < 5000 -> "overcast"
                else -> "sunlight"
            }
            light.addProperty("level", level)
            sensors.add("light", light)
        }

        val accelMagnitude = sqrt(
            (acceleration[0] * acceleration[0] + acceleration[1] * acceleration[1] + acceleration[2] * acceleration[2]).toDouble()
        ).toFloat()
        val accel = JsonObject().apply {
            addProperty("x", acceleration[0])
            addProperty("y", acceleration[1])
            addProperty("z", acceleration[2])
            addProperty("magnitude", accelMagnitude)
            addProperty("is_moving", accelMagnitude > SIGNIFICANT_MOTION_THRESHOLD)
        }
        sensors.add("accelerometer", accel)

        val gyro = JsonObject().apply {
            addProperty("x", gyroscope[0])
            addProperty("y", gyroscope[1])
            addProperty("z", gyroscope[2])
        }
        sensors.add("gyroscope", gyro)

        val mag = JsonObject().apply {
            addProperty("x", magnetometer[0])
            addProperty("y", magnetometer[1])
            addProperty("z", magnetometer[2])
        }
        sensors.add("magnetometer", mag)

        pressure?.let { sensors.add("pressure", JsonObject().apply { addProperty("hPa", it) }) }
        temperature?.let { sensors.add("temperature", JsonObject().apply { addProperty("celsius", it) }) }
        humidity?.let { sensors.add("humidity", JsonObject().apply { addProperty("percent", it) }) }
        steps?.let { sensors.add("step_counter", JsonObject().apply { addProperty("steps", it) }) }

        lastLocation?.let {
            val loc = JsonObject()
            loc.addProperty("latitude", it.latitude)
            loc.addProperty("longitude", it.longitude)
            loc.addProperty("speed", it.speed)
            sensors.add("location", loc)
        }

        root.add("sensors", sensors)
        root.addProperty("timestamp", System.currentTimeMillis())
        return root
    }
}