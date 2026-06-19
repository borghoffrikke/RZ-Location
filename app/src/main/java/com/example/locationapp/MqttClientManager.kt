package com.example.locationapp

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class DeviceInfo(
    val deviceId: String, val label: String = "",
    val lat: Double = 0.0, val lng: Double = 0.0, val ts: Long = 0L,
    val online: Boolean = false, val visible: Boolean = true
)

class MqttClientManager private constructor(private val ctx: Context) {

    companion object {
        private const val TAG = "MQTT"
        @Volatile private var instance: MqttClientManager? = null
        fun getInstance(ctx: Context) = instance ?: synchronized(this) {
            instance ?: MqttClientManager(ctx.applicationContext).also { instance = it }
        }
    }

    private val prefs = PreferencesManager.getInstance(ctx)
    private val devices = ConcurrentHashMap<String, DeviceInfo>()
    private val connecting = AtomicBoolean(false)
    private val running = AtomicBoolean(true)
    private var worker: Thread? = null

    @Volatile var client: MqttClient? = null
    @Volatile var isConnected = false
    var onDevicesChanged: ((Map<String, DeviceInfo>) -> Unit)? = null
    var onConnectionChanged: ((Boolean) -> Unit)? = null

    private fun tLoc() = "location/group/${prefs.groupId}"
    private fun tSta() = "status/group/${prefs.groupId}"

    fun connect() {
        if (connecting.getAndSet(true)) return
        running.set(true)
        worker = Thread({
            while (running.get()) {
                try {
                    doConnect()
                    while (running.get() && isConnected) {
                        try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "循环异常: ${e.message}")
                }
                if (running.get()) {
                    try { Thread.sleep(3000) } catch (_: InterruptedException) { break }
                }
            }
        }, "mqtt-worker").apply { isDaemon = true; start() }
    }

    private fun doConnect() {
        try {
            try { client?.apply { if (isConnected) disconnect(); close() } } catch (_: Exception) {}
            client = null; isConnected = false

            val brokerUrl = prefs.mqttBrokerUrl
            val clientId = prefs.deviceId
            Log.i(TAG, "连接 $brokerUrl id=$clientId")

            val c = MqttClient(brokerUrl, clientId, MemoryPersistence())
            client = c

            val willPayload = JSONObject()
            willPayload.put("deviceId", clientId)
            willPayload.put("online", false)
            willPayload.put("label", clientId.take(8))
            willPayload.put("visible", prefs.isLocationVisible)

            val opts = MqttConnectOptions()
            opts.isCleanSession = true
            opts.connectionTimeout = 30
            opts.keepAliveInterval = 120
            opts.isAutomaticReconnect = false
            if (prefs.mqttUsername.isNotBlank()) {
                opts.userName = prefs.mqttUsername
                opts.password = prefs.mqttPassword.toCharArray()
            }
            if (brokerUrl.startsWith("ssl://")) {
                opts.socketFactory = javax.net.ssl.SSLSocketFactory.getDefault()
            }
            opts.setWill(tSta(), willPayload.toString().toByteArray(), 1, false)

            c.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "断开: ${cause?.message}")
                    try { isConnected = false; onConnectionChanged?.invoke(false) } catch (_: Exception) {}
                }
                override fun messageArrived(topic: String?, msg: MqttMessage?) {
                    try { msg?.let { handle(String(it.payload)) } } catch (_: Exception) {}
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            c.connect(opts)
            isConnected = true
            connecting.set(false)
            try { onConnectionChanged?.invoke(true) } catch (_: Exception) {}

            publishStatus()
            // 老年版不订阅（只发不收，节省资源）
            if (!BuildConfig.SENIOR_MODE) {
                c.subscribe(tLoc(), 1)
                c.subscribe(tSta(), 1)
            }
            Log.i(TAG, "已连接${if (BuildConfig.SENIOR_MODE) " (老年版-仅发送)" else ""} group=${prefs.groupId}")
        } catch (e: Exception) {
            Log.e(TAG, "连接失败: ${e.message}")
            try { isConnected = false; onConnectionChanged?.invoke(false) } catch (_: Exception) {}
            connecting.set(false)
        }
    }

    fun disconnect() {
        running.set(false)
        worker?.interrupt()
        try { client?.disconnect(); client?.close() } catch (_: Exception) {}
        client = null; isConnected = false
    }

    /** 位置发布回调 — 供 UI 层记录日志 */
    var onPublishLog: ((String) -> Unit)? = null

    fun publishLocation(lat: Double, lng: Double) {
        if (!isConnected) { onPublishLog?.invoke("MQTT未连接,跳过"); return }
        if (!prefs.isLocationVisible) { onPublishLog?.invoke("不可见,跳过"); return }
        try {
            val p = JSONObject()
            p.put("deviceId", prefs.deviceId)
            p.put("lat", lat); p.put("lng", lng)
            p.put("ts", System.currentTimeMillis())
            client?.publish(tLoc(), MqttMessage(p.toString().toByteArray()).apply { qos = 1 })
            onPublishLog?.invoke("位置已发送: $lat, $lng")
        } catch (e: Exception) { onPublishLog?.invoke("发送失败: ${e.message}") }
    }

    private fun publishStatus() {
        try {
            val p = JSONObject()
            p.put("deviceId", prefs.deviceId)
            p.put("online", true)
            p.put("label", prefs.deviceId.take(8))
            p.put("visible", prefs.isLocationVisible)
            client?.publish(tSta(), MqttMessage(p.toString().toByteArray()).apply { qos = 1 })
        } catch (_: Exception) {}
    }

    fun refreshVisibility() { publishStatus() }

    fun handle(payload: String) {
        try {
            val j = JSONObject(payload)
            val id = j.optString("deviceId", "")
            if (id.isBlank() || id == prefs.deviceId) return
            if (j.has("lat")) {
                val ex = devices[id]
                devices[id] = (ex ?: DeviceInfo(id)).copy(
                    lat = j.getDouble("lat"), lng = j.getDouble("lng"),
                    ts = j.optLong("ts"), online = true
                )
            } else {
                val ex = devices[id]
                devices[id] = (ex ?: DeviceInfo(id)).copy(
                    label = j.optString("label"),
                    online = j.optBoolean("online"),
                    visible = j.optBoolean("visible", true)
                )
            }
            try { onDevicesChanged?.invoke(HashMap(devices)) } catch (_: Exception) {}
        } catch (_: Exception) {}
    }
}
