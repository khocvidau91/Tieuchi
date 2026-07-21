package com.xiaozhi.smarthome

import android.util.Log
import com.google.gson.JsonParser
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class HaWebSocketClient(
    url: String,
    private val token: String,
    private val onStateChanged: (HaEntity) -> Unit
) : WebSocketClient(URI.create(url)) {

    companion object { private const val TAG = "HaWebSocket" }

    override fun onOpen(handshake: ServerHandshake?) {
        Log.i(TAG, "HA WebSocket opened")
        // Subscribe to state_changed events
        send("""{"id":1,"type":"subscribe_events","event_type":"state_changed"}""")
    }

    override fun onMessage(message: String?) {
        if (message == null) return
        try {
            val json = JsonParser.parseString(message).asJsonObject
            if (json.has("event") && json["event"].asJsonObject.has("data")) {
                val data = json["event"].asJsonObject["data"].asJsonObject
                val entityId = data["entity_id"]?.asString ?: return
                val newStateObj = data["new_state"]?.asJsonObject ?: return
                val state = newStateObj["state"]?.asString ?: "unknown"
                val attributes = newStateObj["attributes"]?.asJsonObject ?: return
                val name = attributes["friendly_name"]?.asString ?: entityId
                val domain = entityId.split(".").firstOrNull() ?: ""
                val haEntity = HaEntity(
                    entityId = entityId,
                    name = name,
                    state = state,
                    domain = domain,
                    attributes = attributes.asMap().mapValues { it.value.toString() } // Convert JsonElement to String for simplicity
                )
                onStateChanged(haEntity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket message error", e)
        }
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        Log.w(TAG, "HA WebSocket closed: $code $reason")
    }

    override fun onError(ex: Exception?) {
        Log.e(TAG, "HA WebSocket error", ex)
    }
}