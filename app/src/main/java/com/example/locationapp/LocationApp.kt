package com.example.locationapp

import android.app.Application
import android.util.Log
import android.widget.Toast

class LocationApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("LocationApp", "崩溃: ${throwable.message}", throwable)
            try {
                android.os.Looper.prepare()
                Toast.makeText(this, "错误: ${throwable.message}", Toast.LENGTH_LONG).show()
                android.os.Looper.loop()
            } catch (_: Exception) {}
        }
    }
}
