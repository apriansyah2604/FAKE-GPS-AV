package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.*
import java.util.Random

class MockLocationService : Service() {

    companion object {
        const val CHANNEL_ID = "mock_location_service_channel"
        const val NOTIFICATION_ID = 1337

        // Intent Actions
        const val ACTION_START = "com.example.service.START"
        const val ACTION_STOP = "com.example.service.STOP"
        const val ACTION_UPDATE = "com.example.service.UPDATE"

        // Intent Extras
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_JITTER = "jitter"
        const val EXTRA_DRIFT = "drift"
        const val EXTRA_ACCURACY_VAR = "accuracy_variance"

        // Service State Shared Flow or static state for UI observation
        @Volatile
        var isRunning = false
            private set

        @Volatile
        var currentMockLat = 0.0
            private set

        @Volatile
        var currentMockLng = 0.0
            private set
    }

    private lateinit var locationManager: LocationManager
    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Active Mock Configuration
    private var targetLatitude = 0.0
    private var targetLongitude = 0.0
    private var isJitterEnabled = false
    private var isDriftEnabled = false
    private var isAccuracyVarEnabled = false

    private val random = Random()
    private var driftAngle = 0.0
    private var currentBearing = 0f
    private var currentAltitude = 120.0 // Standard meters above sea level

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                targetLatitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                targetLongitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                isJitterEnabled = intent.getBooleanExtra(EXTRA_JITTER, false)
                isDriftEnabled = intent.getBooleanExtra(EXTRA_DRIFT, false)
                isAccuracyVarEnabled = intent.getBooleanExtra(EXTRA_ACCURACY_VAR, false)

                startForegroundService()
                startMockingLoop()
            }
            ACTION_UPDATE -> {
                targetLatitude = intent.getDoubleExtra(EXTRA_LATITUDE, targetLatitude)
                targetLongitude = intent.getDoubleExtra(EXTRA_LONGITUDE, targetLongitude)
                isJitterEnabled = intent.getBooleanExtra(EXTRA_JITTER, isJitterEnabled)
                isDriftEnabled = intent.getBooleanExtra(EXTRA_DRIFT, isDriftEnabled)
                isAccuracyVarEnabled = intent.getBooleanExtra(EXTRA_ACCURACY_VAR, isAccuracyVarEnabled)

                // Update Notification instantly
                updateNotification()
            }
            ACTION_STOP -> {
                stopMocking()
            }
        }

        return START_STICKY
    }

    private fun startForegroundService() {
        isRunning = true
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MockLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stealthBadge = if (isJitterEnabled || isDriftEnabled || isAccuracyVarEnabled) " [STEALTH ACTIVE]" else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fake GPS Stealth Active$stealthBadge")
            .setContentText(String.format("Menirukan lokasi ke: %.5f, %.5f", targetLatitude, targetLongitude))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Hentikan Mocking",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startMockingLoop() {
        serviceJob?.cancel()

        // Set up test providers
        setupMockProvider(LocationManager.GPS_PROVIDER)
        setupMockProvider(LocationManager.NETWORK_PROVIDER)

        serviceJob = serviceScope.launch {
            while (isActive) {
                try {
                    mockSingleUpdate()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1000) // update every second
            }
        }
    }

    private fun mockSingleUpdate() {
        var finalLat = targetLatitude
        var finalLng = targetLongitude
        var finalAccuracy = 3.0f // m

        // 1. GPS Jitter (random 1-2 meters atmospheric micro-vibrations)
        if (isJitterEnabled) {
            val latJitter = (random.nextDouble() - 0.5) * 0.000015
            val lngJitter = (random.nextDouble() - 0.5) * 0.000015
            finalLat += latJitter
            finalLng += lngJitter
        }

        // 2. Slow Walking Drift (makes coordinate dynamic like standard physical drift)
        if (isDriftEnabled) {
            driftAngle += 0.2
            if (driftAngle > 2 * Math.PI) driftAngle -= 2 * Math.PI
            
            val latDrift = Math.sin(driftAngle) * 0.000025
            val lngDrift = Math.cos(driftAngle) * 0.000025
            finalLat += latDrift
            finalLng += lngDrift

            currentBearing = ((driftAngle * 180 / Math.PI).toFloat() + 360f) % 360f
        }

        // 3. Accuracy fluctuation (trees, clouds, buildings)
        if (isAccuracyVarEnabled) {
            finalAccuracy = 4.0f + random.nextFloat() * 7.5f
        }

        currentMockLat = finalLat
        currentMockLng = finalLng

        // Push updates to system location managers
        pushMockLocation(LocationManager.GPS_PROVIDER, finalLat, finalLng, finalAccuracy)
        pushMockLocation(LocationManager.NETWORK_PROVIDER, finalLat, finalLng, finalAccuracy)
    }

    private fun pushMockLocation(provider: String, lat: Double, lng: Double, accuracy: Float) {
        try {
            val mockLocation = Location(provider).apply {
                latitude = lat
                longitude = lng
                altitude = currentAltitude + (random.nextDouble() - 0.5) * 2.0 // fluctuate altitude slightly
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                this.accuracy = accuracy
                
                if (isDriftEnabled) {
                    speed = 1.0f + random.nextFloat() * 0.5f // walking speed ~1-1.5 m/s
                    bearing = currentBearing
                } else {
                    speed = 0f
                    bearing = 0f
                }

                // Add extras if required
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    verticalAccuracyMeters = accuracy / 2
                    bearingAccuracyDegrees = 10f
                    speedAccuracyMetersPerSecond = 0.5f
                }
            }

            locationManager.setTestProviderLocation(provider, mockLocation)
        } catch (e: SecurityException) {
            // Security Exception can happen if the app was revoked from Developer options while running
            stopMocking()
        } catch (e: IllegalArgumentException) {
            // Can occur if the test provider is missing, we re-setup
            setupMockProvider(provider)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupMockProvider(provider: String) {
        try {
            try {
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {}

            locationManager.addTestProvider(
                provider,
                false, // requiresNetwork
                false, // requiresSatellite
                false, // requiresCell
                false, // hasMonetaryCost
                true,  // supportsAltitude
                true,  // supportsSpeed
                true,  // supportsBearing
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(provider, true)
        } catch (e: SecurityException) {
            // Fail gracefully, user needs to authorize this app in Settings > Developer Options
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeMockProviders() {
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {}
        try {
            locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {}
    }

    private fun stopMocking() {
        isRunning = false
        serviceJob?.cancel()
        removeMockProviders()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopMocking()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Layanan Fake GPS Stealth",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status pemalsuan lokasi background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
