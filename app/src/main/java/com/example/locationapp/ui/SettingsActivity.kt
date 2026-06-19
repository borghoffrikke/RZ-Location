package com.example.locationapp.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.locationapp.LocationService
import com.example.locationapp.MqttClientManager
import com.example.locationapp.PreferencesManager

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val prefs = PreferencesManager.getInstance(this)
            val root = ScrollView(this)
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }

            fun lb(t: String) = android.widget.TextView(this).apply { text = t; textSize = 16f; setPadding(0, 16, 0, 4) }
            fun ed(h: String, v: String, it: Int = 0x00000001) = EditText(this).apply { hint = h; setText(v); inputType = it; textSize = 14f; minHeight = 80 }

            c.addView(lb("MQTT Broker"))
            val broker = ed("ssl://host:8883", prefs.mqttBrokerUrl); c.addView(broker)

            c.addView(lb("用户名"))
            val user = ed("用户名", prefs.mqttUsername); c.addView(user)

            c.addView(lb("密码"))
            val pass = ed("密码", prefs.mqttPassword, 0x00000081); c.addView(pass)

            c.addView(lb("设备 ID"))
            val dev = ed("设备ID", prefs.deviceId); c.addView(dev)

            c.addView(lb("电话"))
            val phone = ed("13800138000", prefs.phoneNumber, 0x00000003); c.addView(phone)

            c.addView(lb("定位间隔(秒)"))
            val interval = ed("5", prefs.locationIntervalSec.toString(), 0x00000002); c.addView(interval)

            // 地图开关
            val mapRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            mapRow.addView(android.widget.TextView(this).apply { text = "启用地图 (华为开/红米关)"; textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            val mapSw = android.widget.Switch(this).apply { isChecked = prefs.mapEnabled }
            mapRow.addView(mapSw); c.addView(mapRow)

            // 位置可见开关
            val visRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            visRow.addView(android.widget.TextView(this).apply { text = "发送本机位置"; textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
            val visSw = android.widget.Switch(this).apply { isChecked = prefs.isLocationVisible }
            visRow.addView(visSw); c.addView(visRow)

            val save = Button(this).apply { text = "保存" }
            save.setOnClickListener {
                val nb = broker.text.toString().trim()
                val reconnect = nb != prefs.mqttBrokerUrl
                prefs.mqttBrokerUrl = nb
                prefs.mqttUsername = user.text.toString().trim()
                prefs.mqttPassword = pass.text.toString()
                val nd = dev.text.toString().trim(); if (nd.isNotBlank()) prefs.deviceId = nd
                prefs.phoneNumber = phone.text.toString().trim()
                interval.text.toString().toIntOrNull()?.let { prefs.locationIntervalSec = it }
                prefs.mapEnabled = mapSw.isChecked
                prefs.isLocationVisible = visSw.isChecked
                MqttClientManager.getInstance(this).refreshVisibility()

                if (reconnect) {
                    val m = MqttClientManager.getInstance(this)
                    m.disconnect()
                    m.connect()
                }
                LocationService.stop(this)
                LocationService.start(this)
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
                finish()
            }
            c.addView(save)

            root.addView(c)
            setContentView(root)
        } catch (e: Exception) {
            Toast.makeText(this, "错误: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
