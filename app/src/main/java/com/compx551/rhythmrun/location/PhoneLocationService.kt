package com.compx551.rhythmrun.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.compx551.rhythmrun.MainActivity
import com.compx551.rhythmrun.data.local.PhoneRoomDatabase
import com.compx551.rhythmrun.data.repository.RoomRunRepository
import com.compx551.rhythmrun.domain.model.LocationFixRecord
import com.compx551.rhythmrun.domain.model.RunDataBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Records phone fixes for a phone-started run, including while the screen is locked. */
class PhoneLocationService : Service(), LocationListener {
    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val repository by lazy { RoomRunRepository(PhoneRoomDatabase.getInstance(applicationContext)) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionId: String? = null
    private var listening = false
    private var lastSequence = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_RESUME -> {
                val id = intent.getStringExtra(EXTRA_SESSION_ID)?.takeIf(String::isNotBlank)
                    ?: return START_NOT_STICKY
                sessionId = id
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, notification())
                registerLocation()
            }
            ACTION_PAUSE -> unregisterLocation()
            ACTION_STOP -> {
                unregisterLocation()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun registerLocation() {
        if (listening) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Phone location permission is unavailable")
            stopSelf()
            return
        }
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            providers.filter(locationManager::isProviderEnabled).forEach { provider ->
                locationManager.requestLocationUpdates(provider, 1_000L, 0f, this, Looper.getMainLooper())
                listening = true
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to register phone location", error)
            unregisterLocation()
            stopSelf()
        }
    }

    private fun unregisterLocation() {
        if (listening) locationManager.removeUpdates(this)
        listening = false
    }

    override fun onLocationChanged(location: Location) {
        val id = sessionId ?: return
        if (location.time <= 0L || System.currentTimeMillis() - location.time > 10_000L ||
            location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0 ||
            (location.latitude == 0.0 && location.longitude == 0.0)
        ) return
        val fix = LocationFixRecord(
            sessionId = id,
            sequence = maxOf(System.currentTimeMillis(), lastSequence + 1).also { lastSequence = it },
            timestampEpochMillis = location.time,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMetres = location.accuracy.takeIf { location.hasAccuracy() }?.toDouble(),
            available = true,
        )
        scope.launch {
            repository.persistBatch(RunDataBatch(locationFixes = listOf(fix)))
            withContext(Dispatchers.Main) { locationListener?.invoke(fix) }
        }
    }

    override fun onDestroy() {
        unregisterLocation()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Running route", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun notification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder.setSmallIcon(com.compx551.rhythmrun.R.mipmap.ic_launcher)
            .setContentTitle("RhythmRun is recording your route")
            .setContentText("Phone location continues while the screen is locked")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "PhoneLocationService"
        private const val CHANNEL_ID = "running_route"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS = "phone_route_sessions"
        private const val SESSIONS = "ids"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val ACTION_START = "com.compx551.rhythmrun.location.START"
        private const val ACTION_RESUME = "com.compx551.rhythmrun.location.RESUME"
        private const val ACTION_PAUSE = "com.compx551.rhythmrun.location.PAUSE"
        private const val ACTION_STOP = "com.compx551.rhythmrun.location.STOP"

        @Volatile var locationListener: ((LocationFixRecord) -> Unit)? = null

        fun isPhoneRouteSession(context: Context, sessionId: String): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(SESSIONS, emptySet())?.contains(sessionId) == true

        fun start(context: Context, sessionId: String) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(
                SESSIONS, prefs.getStringSet(SESSIONS, emptySet()).orEmpty().toMutableSet() + sessionId,
            ).apply()
            command(context, ACTION_START, sessionId)
        }

        fun resume(context: Context, sessionId: String) = command(context, ACTION_RESUME, sessionId)
        fun pause(context: Context, sessionId: String) = command(context, ACTION_PAUSE, sessionId)
        fun stop(context: Context, sessionId: String) = command(context, ACTION_STOP, sessionId)

        private fun command(context: Context, action: String, sessionId: String) {
            val intent = Intent(context, PhoneLocationService::class.java).apply {
                this.action = action
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            if (action == ACTION_START || action == ACTION_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
