package com.autospeed.volume

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

class SpeedVolumeService : Service() {
    private lateinit var client: FusedLocationProviderClient
    private lateinit var audio: AudioManager
    private val channelId = "autospeed_active"
    private var lastVolume = -1
    private val prefs by lazy { AppPrefs.prefs(this) }

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        client = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        startForeground(101, notification("در انتظار دریافت سرعت GPS"))
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            prefs.edit().putBoolean(AppPrefs.IS_RUNNING, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L).build()
        try {
            client.removeLocationUpdates(callback).addOnCompleteListener {
                try { client.requestLocationUpdates(request, callback, Looper.getMainLooper()) }
                catch (_: SecurityException) { stopSelf() }
            }
        } catch (_: SecurityException) { stopSelf() }
        return START_STICKY
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location: Location = result.lastLocation ?: return
            val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
            val min = prefs.getInt(AppPrefs.MIN_VOLUME, 20).coerceIn(0, 95)
            val max = prefs.getInt(AppPrefs.MAX_VOLUME, 75).coerceIn(min + 1, 100)
            val referenceSpeed = prefs.getInt(AppPrefs.MAX_SPEED, 120).coerceIn(1, 300)
            val percent = (min + (speedKmh / referenceSpeed).coerceIn(0f, 1f) * (max - min)).toInt()
            val maxStream = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val volume = (percent / 100f * maxStream).toInt().coerceIn(0, maxStream)
            if (volume != lastVolume) {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                lastVolume = volume
            }
            prefs.edit()
                .putInt(AppPrefs.CURRENT_SPEED, speedKmh.toInt())
                .putInt(AppPrefs.CURRENT_PERCENT, percent)
                .putBoolean(AppPrefs.IS_RUNNING, true)
                .apply()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(101, notification("سرعت ${speedKmh.toInt()} km/h • صدا $percent٪"))
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(channelId, "AutoSpeed Volume", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("AutoSpeed Volume")
            .setContentText(text)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        if (::client.isInitialized) client.removeLocationUpdates(callback)
        prefs.edit().putBoolean(AppPrefs.IS_RUNNING, false).apply()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
