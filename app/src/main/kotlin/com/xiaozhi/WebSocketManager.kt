package com.xiaozhi

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.JsonObject
import okhttp3.*
import okio.ByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WebSocketManager(
    private val url: String,
    private val token: String,
    private val deviceId: String,
    private val clientId: String
) {
    companion object {
        private const val TAG = "WS"
        private const val HEARTBEAT_INTERVAL = 60_000L
        private const val RECONNECT_DELAY_MS = 5_000L
        private const val MAX_RECONNECT_ATTEMPTS = 20
        private const val PROTOCOL_VERSION = 2

        @Volatile var wsManager: WebSocketManager? = null
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(2, 1, TimeUnit.MINUTES))
        .build()

    private var webSocket: WebSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile var isConnected = false
        private set
    var sessionId: String? = null
        private set

    private var serverSampleRate = 24000
    private var serverFrameDuration = 60

    private var callback: Callback? = null

    private var userName: String? = null
    private var userEmail: String? = null
    private var userAvatar: String? = null

    private val shouldReconnect = AtomicBoolean(true)
    private var reconnectAttempts = 0
    private val reconnectRunnable = Runnable { attemptReconnect() }

    interface Callback {
        fun onConnected()
        fun onDisconnected(code: Int, reason: String?)
        fun onJsonMessage(json: JsonObject)
        fun onAudioData(opusData: ByteArray, sampleRate: Int, frameDuration: Int)
    }

    fun setCallback(cb: Callback) { callback = cb }

    fun setUserInfo(name: String?, email: String?, avatar: String?) {
        userName = name
        userEmail = email
        userAvatar = avatar
        if (isConnected) sendUserInfoMessage()
    }

    private fun sendUserInfoMessage() {
        val user = JsonObject()
        if (!userName.isNullOrEmpty()) user.addProperty("name", userName)
        if (!userEmail.isNullOrEmpty()) user.addProperty("email", userEmail)
        if (!userAvatar.isNullOrEmpty()) user.addProperty("avatar", userAvatar)
        if (user.size() > 0) {
            val info = JsonObject().apply {
                addProperty("type", "user_info")
                add("data", user)
            }
            webSocket?.send(info.toString())
        }
    }

    fun connect() {
        mainHandler.removeCallbacks(reconnectRunnable)
        shouldReconnect.set(true)

        val builder = Request.Builder().url(url)
            .header("Authorization", if (token.startsWith("Bearer ")) token else "Bearer $token")
            .header("Protocol-Version", PROTOCOL_VERSION.toString())
            .header("Device-Id", deviceId)
            .header("Client-Id", clientId)

        webSocket = client.newWebSocket(builder.build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isConnected = true
                reconnectAttempts = 0
                Log.i(TAG, "Connected")
                val hello = JsonObject().apply {
                    addProperty("type", "hello")
                    addProperty("version", PROTOCOL_VERSION)
                    addProperty("transport", "websocket")
                    add("features", JsonObject().apply { addProperty("mcp", true) })
                    add("audio_params", JsonObject().apply {
                        addProperty("format", "opus")
                        addProperty("sample_rate", 16000)
                        addProperty("channels", 1)
                        addProperty("frame_duration", 60)
                    })
                }
                ws.send(hello.toString())
                sendUserInfoMessage()
                mainHandler.post { callback?.onConnected() }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val obj = com.google.gson.Gson().fromJson(text, JsonObject::class.java)
                    val type = obj.get("type")?.asString ?: ""
                    if (type == "hello") {
                        sessionId = obj.get("session_id")?.asString
                        obj.getAsJsonObject("audio_params")?.let {
                            serverSampleRate = it.get("sample_rate")?.asInt ?: serverSampleRate
                            serverFrameDuration = it.get("frame_duration")?.asInt ?: serverFrameDuration
                        }
                        Log.i(TAG, "Server hello: sessionId=$sessionId, sampleRate=$serverSampleRate")
                    }
                    mainHandler.post { callback?.onJsonMessage(obj) }
                } catch (e: Exception) {
                    Log.e(TAG, "JSON error", e)
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                val opusPayload = extractOpus(data)
                opusPayload?.let { callback?.onAudioData(it, serverSampleRate, serverFrameDuration) }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.w(TAG, "WS closed: $code $reason")
                mainHandler.post { callback?.onDisconnected(code, reason) }
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e(TAG, "WS failure", t)
                mainHandler.post { callback?.onDisconnected(-1, t.message) }
                scheduleReconnect()
            }
        })
    }

    private fun extractOpus(data: ByteArray): ByteArray? {
        if (data.size < 16) return null
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        buffer.short   // version
        buffer.short   // type
        buffer.int     // reserved
        buffer.int     // timestamp
        val payloadSize = buffer.int
        if (data.size < 16 + payloadSize) return null
        return data.copyOfRange(16, 16 + payloadSize)
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect.get()) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts reached")
            return
        }
        reconnectAttempts++
        val delay = RECONNECT_DELAY_MS * reconnectAttempts
        mainHandler.postDelayed(reconnectRunnable, delay)
    }

    private fun attemptReconnect() {
        if (isConnected || !shouldReconnect.get()) return
        Log.i(TAG, "Attempting reconnect...")
        connect()
    }

    fun sendText(json: String) {
        try {
            webSocket?.send(json) ?: Log.w(TAG, "sendText ignored: webSocket is null")
        } catch (e: Exception) {
            Log.e(TAG, "sendText failed", e)
        }
    }

    fun sendAudio(opusData: ByteArray, timestamp: Long = System.currentTimeMillis() % 0x100000000) {
        if (!isConnected) return
        val header = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(PROTOCOL_VERSION.toShort())
            putShort(0.toShort())
            putInt(0)
            putInt(timestamp.toInt())
            putInt(opusData.size)
        }.array()
        val packet = ByteArray(16 + opusData.size).apply {
            System.arraycopy(header, 0, this, 0, 16)
            System.arraycopy(opusData, 0, this, 16, opusData.size)
        }
        try {
            webSocket?.send(ByteString.of(*packet))
        } catch (e: Exception) {
            Log.e(TAG, "sendAudio failed", e)
        }
    }

    fun disconnect() {
        shouldReconnect.set(false)
        mainHandler.removeCallbacks(reconnectRunnable)
        webSocket?.close(1000, "User")
        isConnected = false
    }

    // ========== BỔ SUNG ==========
    fun sendMcpResponse(response: JsonObject) {
        val wrapper = JsonObject().apply {
            addProperty("type", "mcp")
            add("payload", response)
        }
        sendText(wrapper.toString())
    }
}