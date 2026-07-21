package com.xiaozhi.smarthome

import android.content.Context
import android.util.Log
import com.xiaozhi.AppState
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import com.xiaozhi.smarthome.HomeAssistantApi
import com.xiaozhi.smarthome.HaAuthInterceptor
import com.xiaozhi.smarthome.HaWebSocketClient
import com.xiaozhi.smarthome.HaEntity
import com.xiaozhi.smarthome.DeviceCapability
import com.xiaozhi.smarthome.Room

class HomeAssistantManager private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: HomeAssistantManager? = null
        fun getInstance(context: Context): HomeAssistantManager {
            return instance ?: synchronized(this) {
                instance ?: HomeAssistantManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var api: HomeAssistantApi? = null
    private var webSocketClient: HaWebSocketClient? = null
    private val entityCache = ConcurrentHashMap<String, HaEntity>()
    private val capabilityCache = ConcurrentHashMap<String, DeviceCapability>()

    private var haUrl: String? = null
    private var haToken: String? = null

    suspend fun connect(url: String, token: String): Boolean {
        if (!url.startsWith("http")) return false
        AppState.setHaUrl(context, url)
        AppState.setHaToken(context, token)
        AppState.setHaEnabled(context, true)
        this.haUrl = url
        this.haToken = token

        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(HaAuthInterceptor(token))
            .addInterceptor(logging) // Thêm interceptor logging
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        return try {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (url.endsWith("/")) url else "$url/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            api = retrofit.create(HomeAssistantApi::class.java)

            // Test connection by fetching config
            val configResponse = api?.getConfig()
            if (configResponse?.isSuccessful == true) {
                Log.i("HA", "HA Config fetched successfully")
                startWebSocket(url, token)
                refreshCache()
                true
            } else {
                Log.e("HA", "Failed to fetch HA config. Response: ${configResponse?.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e("HA", "Error connecting to Home Assistant", e)
            false
        }
    }

    private fun startWebSocket(baseUrl: String, token: String) {
        val wsUrl = baseUrl.replace("http", "ws") + "api/websocket"
        Log.d("HA", "Connecting to WebSocket: $wsUrl")
        webSocketClient?.close()
        webSocketClient = HaWebSocketClient(wsUrl, token) { entity ->
            scope.launch {
                entityCache[entity.entityId] = entity
                // TODO: Potentially emit an event to the AI for context updates
            }
        }
        webSocketClient?.connect()
    }

    suspend fun refreshCache() {
        val apiInstance = api ?: return
        val response = try {
            apiInstance.getAllStates()
        } catch (e: Exception) {
            Log.e("HA", "Error fetching all states", e)
            return
        }

        if (response.isSuccessful) {
            response.body()?.forEach { entity ->
                entityCache[entity.entityId] = entity
                capabilityCache[entity.entityId] = DeviceCapability(
                    entityId = entity.entityId,
                    domain = entity.domain,
                    actions = getActionsForDomain(entity.domain)
                )
            }
            Log.i("HA", "Refreshed cache with ${entityCache.size} entities")
        } else {
            Log.e("HA", "Failed to refresh cache: ${response.code()}")
        }
    }

    private fun getActionsForDomain(domain: String): List<String> {
        return when (domain) {
            "light" -> listOf("turn_on", "turn_off", "toggle", "set_brightness", "set_color")
            "switch", "input_boolean" -> listOf("turn_on", "turn_off", "toggle")
            "climate" -> listOf("set_temperature", "set_hvac_mode", "set_fan_mode")
            "media_player" -> listOf("turn_on", "turn_off", "volume_set", "media_play_pause", "media_next_track", "media_previous_track")
            "cover" -> listOf("open_cover", "close_cover", "stop_cover", "set_cover_position")
            else -> listOf("turn_on", "turn_off") // Default actions
        }
    }

    suspend fun callService(domain: String, service: String, entityId: String? = null, data: Map<String, Any> = emptyMap()): Boolean {
        val apiInstance = api ?: return false
        val body = mutableMapOf<String, Any>()
        if (entityId != null) {
            // Handle service calls that target a specific entity
            body["entity_id"] = entityId
        } else {
            // For services that might not target a specific entity, or if entityId is optional
            // You might need to adjust this logic based on HA service call patterns.
            // For simplicity here, we assume most calls target an entity.
        }
        body.putAll(data)

        return try {
            val response = apiInstance.callService(domain, service, body)
            if (!response.isSuccessful) {
                Log.e("HA", "Service call failed: ${response.code()} - ${response.message()}")
            }
            response.isSuccessful
        } catch (e: Exception) {
            Log.e("HA", "Exception calling service $domain.$service", e)
            false
        }
    }

    fun getEntity(entityId: String): HaEntity? = entityCache[entityId]
    fun getAllEntities(): List<HaEntity> = entityCache.values.toList()
    fun getCapability(entityId: String): DeviceCapability? = capabilityCache[entityId]

    suspend fun discoverRooms(): List<Room> {
        // This would typically involve calling the Home Assistant API for areas/rooms
        // Example API endpoint: /api/areas/list
        // For now, returning an empty list as implementation is complex and requires specific HA setup.
        return emptyList()
    }

    fun disconnect() {
        webSocketClient?.close()
        webSocketClient = null
        api = null
        AppState.setHaEnabled(context, false)
        Log.i("HA", "Disconnected from Home Assistant")
    }

    fun isConnected(): Boolean = webSocketClient?.isOpen ?: false
}