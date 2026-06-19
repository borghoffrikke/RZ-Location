package com.example.locationapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class LocationService : Service() {

    companion object {
        private const val TAG = "LocSvc"
        private const val NID = 1001
        private const val CH = "loc_ch"

        fun start(ctx: Context) {
            try {
                val i = Intent(ctx, LocationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (e: Exception) { Log.e(TAG, "start: ${e.message}") }
        }
        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, LocationService::class.java)) } catch (_: Exception) {}
        }
    }

    private lateinit var lm: LocationManager

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            if (loc.hasAccuracy() && loc.accuracy > 50f) return
            try {
                MqttClientManager.getInstance(this@LocationService).publishLocation(loc.latitude, loc.longitude)
            } catch (_: Exception) {}
        }
        @Deprecated("") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    override fun onBind(i: Intent?): IBinder? = null

    @Suppress("MissingPermission")
    override fun onStartCommand(i: Intent?, f: Int, sid: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(NotificationChannel(CH, "位置共享", NotificationManager.IMPORTANCE_LOW))
            }
            val pi = PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            startForeground(NID, NotificationCompat.Builder(this, CH)
                .setContentTitle("定位助手").setContentText("位置共享运行中")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).setContentIntent(pi).build())

            if (!::lm.isInitialized) lm = getSystemService(LOCATION_SERVICE) as LocationManager
            startLoc()
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand: ${e.message}", e)
        }
        return START_STICKY
    }

    @Suppress("MissingPermission")
    private fun startLoc() {
        try {
            val ms = PreferencesManager.getInstance(this).locationIntervalSec * 1000L
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, ms, 1f, listener)
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, ms * 2L, 5f, listener)
            }
        } catch (e: Exception) { Log.e(TAG, "startLoc: ${e.message}") }
    }

    override fun onDestroy() {
        try { lm.removeUpdates(listener) } catch (_: Exception) {}
        super.onDestroy()
    }
}
