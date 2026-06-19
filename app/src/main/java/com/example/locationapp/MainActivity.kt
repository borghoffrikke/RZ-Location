package com.example.locationapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.locationapp.ui.SettingsActivity
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var mqtt: MqttClientManager
    private lateinit var log: TextView
    private lateinit var statusBar: TextView
    private var mapView: MapView? = null
    private var mapEnabled = false
    private val markers = mutableMapOf<String, Marker>()
    private var currentDevices = emptyMap<String, DeviceInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            prefs = PreferencesManager.getInstance(this)
            mqtt = MqttClientManager.getInstance(this)
            log = findViewById(R.id.logText)
            statusBar = findViewById(R.id.statusBar)

            l("启动 — ${Build.MANUFACTURER} ${Build.MODEL}")
            if (BuildConfig.SENIOR_MODE) l("老年版: 仅发送定位")

            // 设置按钮 (两个版本都需要)
            findViewById<Button>(R.id.btnSettings).setOnClickListener {
                try { startActivity(Intent(this, SettingsActivity::class.java)) } catch (_: Exception) {}
            }

            // 呼叫按钮 (两个版本都需要)
            findViewById<Button>(R.id.btnCall).setOnClickListener {
                val n = prefs.phoneNumber
                if (n.isBlank()) return@setOnClickListener
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    try { startActivity(Intent(Intent.ACTION_CALL).apply { data = Uri.parse("tel:$n") }) } catch (_: Exception) {}
                } else phonePerm.launch(Manifest.permission.CALL_PHONE)
            }

            // 普通版专属（地图）
            if (!BuildConfig.SENIOR_MODE) {
                mapEnabled = prefs.mapEnabled
                findViewById<Button>(R.id.btnMap).setOnClickListener { if (mapEnabled) hideMap() else showMap() }
            } else {
                // 老年版：隐藏地图按钮
                findViewById<Button>(R.id.btnMap).visibility = View.GONE
            }

            // MQTT 回调（老年版不需要设备列表）
            mqtt.onConnectionChanged = { c ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val msg = if (c) "MQTT 已连接" else "MQTT 断开"
                    l(msg); status(msg)
                }
            }
            if (!BuildConfig.SENIOR_MODE) {
                mqtt.onDevicesChanged = { devs ->
                    currentDevices = devs
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        val on = devs.values.count { it.online }
                        l("设备: $on 在线"); status("在线: $on 台")
                        for ((id, d) in devs) {
                            val coords = if (d.lat != 0.0) String.format("%.6f,%.6f", d.lat, d.lng) else "等待GPS"
                            l("  $id $coords")
                        }
                        if (mapEnabled) { updateMarkers(devs); updateDevicePanel(devs) }
                    }
                }
            }

            if (!hasLocPerm()) reqLocPerm() else { l("权限 ✓"); autoStart() }
            if (!BuildConfig.SENIOR_MODE && mapEnabled) showMap()
        } catch (e: Exception) {
            setContentView(TextView(this).apply { text = "失败: ${e.message}"; setPadding(32, 64, 32, 32); textSize = 16f })
        }
    }

    private fun autoStart() {
        try { LocationService.start(this); l("定位已启动 (等待GPS...)") } catch (_: Exception) {}
        try { mqtt.connect() } catch (_: Exception) {}
        // 老年版：显示位置发送日志 + GPS 等待提示
        if (BuildConfig.SENIOR_MODE) {
            mqtt.onPublishLog = { msg -> runOnUiThread { if (!isFinishing) l(msg) } }
            // 10 秒后如果还没收到位置，提示去室外
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                val txt = log.text.toString()
                if (!txt.contains("位置已发送") && !txt.contains("MQTT未连接")) {
                    l("⚠ 未收到GPS信号，请到室外/窗边")
                }
            }, 10000)
        }
    }

    // ─── 地图（仅普通版）─────────────────────────────────

    private fun showMap() {
        try {
            if (mapView != null) return
            Configuration.getInstance().apply {
                osmdroidBasePath = filesDir
                osmdroidTileCache = cacheDir.resolve("osmdroid").also { it.mkdirs() }
                userAgentValue = "Mozilla/5.0 LocationApp"
            }
            val mv = MapView(this).apply {
                id = View.generateViewId()
                layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1)
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                setMultiTouchControls(true); controller.setZoom(14.0); controller.setCenter(GeoPoint(35.0, 105.0))
                maxZoomLevel = 18.0; minZoomLevel = 3.0
            }
            findViewById<android.widget.FrameLayout>(R.id.contentArea).addView(mv)
            findViewById<View>(R.id.logScroll).visibility = View.GONE
            mapView = mv
            MyLocationNewOverlay(GpsMyLocationProvider(this), mv).apply { enableMyLocation(); enableFollowLocation() }
                .also { mv.overlays.add(it) }
            mv.invalidate()
            mapEnabled = true; prefs.mapEnabled = true
            findViewById<View>(R.id.devicePanel).visibility = View.VISIBLE
            updateMarkers(currentDevices); updateDevicePanel(currentDevices)
        } catch (_: Exception) { hideMap() }
    }

    private fun hideMap() {
        try { mapView?.let { findViewById<android.widget.FrameLayout>(R.id.contentArea).removeView(it) }; mapView?.onDetach() } catch (_: Exception) {}
        markers.clear(); mapView = null; mapEnabled = false; prefs.mapEnabled = false
        findViewById<View>(R.id.logScroll).visibility = View.VISIBLE
        findViewById<View>(R.id.devicePanel).visibility = View.GONE
    }

    private fun updateMarkers(devices: Map<String, DeviceInfo>) {
        val mv = mapView ?: return
        val active = mutableSetOf<String>()
        for ((id, d) in devices) {
            if (!d.online || d.lat == 0.0 && d.lng == 0.0) continue
            active.add(id)
            markers.getOrPut(id) { Marker(mv).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); mv.overlays.add(0, this) } }
                .position = GeoPoint(d.lat, d.lng)
        }
        for (r in markers.keys - active) { markers[r]?.let { mv.overlays.remove(it) }; markers.remove(r) }
        mv.invalidate()
    }

    private fun updateDevicePanel(devices: Map<String, DeviceInfo>) {
        val panel = findViewById<LinearLayout>(R.id.deviceList)
        panel.removeAllViews()
        devices.values.filter { it.online }.forEach { d ->
            val row = LinearLayout(this).apply {
                setPadding(8, 6, 8, 6)
                setOnClickListener {
                    if (d.lat != 0.0 || d.lng != 0.0) {
                        mapView?.controller?.animateTo(GeoPoint(d.lat, d.lng))
                        mapView?.controller?.setZoom(17.0)
                    }
                }
            }
            row.addView(TextView(this).apply {
                text = "🟢 ${d.label.ifBlank { d.deviceId.take(8) }} ${if (d.lat != 0.0) String.format("📍%.4f,%.4f", d.lat, d.lng) else "⏳GPS"}"
                textSize = 13f
            })
            panel.addView(row)
        }
    }

    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { super.onPause(); mapView?.onPause() }
    override fun onDestroy() { mapView?.onDetach(); super.onDestroy() }

    private fun l(msg: String) { log.append("$msg\n") }
    private fun status(msg: String) { statusBar.text = msg }

    private fun hasLocPerm(): Boolean {
        val f = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) else PackageManager.PERMISSION_GRANTED
        return f == PackageManager.PERMISSION_GRANTED && b == PackageManager.PERMISSION_GRANTED
    }
    private fun reqLocPerm() {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) p.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        locPerm.launch(p.toTypedArray())
    }
    private val phonePerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g && prefs.phoneNumber.isNotBlank()) startActivity(Intent(Intent.ACTION_CALL).apply { data = Uri.parse("tel:${prefs.phoneNumber}") })
    }
    private val locPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { g ->
        if (g.values.all { it }) { l("权限 ✓"); autoStart() } else l("权限被拒")
    }
}
