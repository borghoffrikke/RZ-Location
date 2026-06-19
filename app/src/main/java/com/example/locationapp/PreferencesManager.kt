package com.example.locationapp

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferencesManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mqttBrokerUrl: String
        get() = prefs.getString(KEY_BROKER, DEFAULT_BROKER)!!
        set(v) = prefs.edit { putString(KEY_BROKER, v) }

    var mqttUsername: String
        get() = prefs.getString(KEY_USER, "") ?: ""
        set(v) = prefs.edit { putString(KEY_USER, v) }

    var mqttPassword: String
        get() = prefs.getString(KEY_PASS, "") ?: ""
        set(v) = prefs.edit { putString(KEY_PASS, v) }

    var deviceId: String
        get() {
            val id = prefs.getString(KEY_DEV, null)
            if (id != null) return id
            val g = "d-${System.currentTimeMillis().toString(36)}"
            prefs.edit { putString(KEY_DEV, g) }
            return g
        }
        set(v) = prefs.edit { putString(KEY_DEV, v) }

    var phoneNumber: String
        get() = prefs.getString(KEY_PHONE, "") ?: ""
        set(v) = prefs.edit { putString(KEY_PHONE, v) }

    var locationIntervalSec: Int
        get() = prefs.getInt(KEY_INTERVAL, 5)
        set(v) = prefs.edit { putInt(KEY_INTERVAL, v) }

    var groupId: String
        get() = prefs.getString(KEY_GROUP, "1") ?: "1"
        set(v) = prefs.edit { putString(KEY_GROUP, v) }

    var isLocationVisible: Boolean
        get() = prefs.getBoolean(KEY_VISIBLE, true)
        set(v) = prefs.edit { putBoolean(KEY_VISIBLE, v) }

    var mapEnabled: Boolean
        get() = prefs.getBoolean(KEY_MAP, false)
        set(v) = prefs.edit { putBoolean(KEY_MAP, v) }

    companion object {
        private const val PREFS_NAME = "loc_prefs"
        private const val KEY_BROKER = "broker"
        private const val KEY_USER = "user"
        private const val KEY_PASS = "pass"
        private const val KEY_DEV = "dev"
        private const val KEY_PHONE = "phone"
        private const val KEY_INTERVAL = "interval"
        private const val KEY_GROUP = "group"
        private const val KEY_VISIBLE = "visible"
        private const val KEY_MAP = "map"
        private const val DEFAULT_BROKER = "ssl://g16b99c6.ala.cn-hangzhou.emqxsl.cn:8883"

        @Volatile private var instance: PreferencesManager? = null
        fun getInstance(context: Context) = instance ?: synchronized(this) {
            instance ?: PreferencesManager(context).also { instance = it }
        }
    }
}
