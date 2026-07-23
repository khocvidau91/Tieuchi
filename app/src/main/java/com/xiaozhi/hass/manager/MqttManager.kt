package com.xiaozhi.hass.manager

import android.content.Context
import android.util.Log
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.util.concurrent.Executors

class MqttManager(private val context: Context) {
    companion object {
        private const val TAG = "MqttManager"
    }

    private var mqttClient: MqttAndroidClient? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var isConnected = false
    private var messageCallback: ((topic: String, payload: String) -> Unit)? = null

    fun connect(
        serverUri: String,
        clientId: String,
        username: String? = null,
        password: String? = null,
        onConnected: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        if (mqttClient != null) {
            disconnect()
        }
        mqttClient = MqttAndroidClient(context, serverUri, clientId)
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 60
            username?.let { userName = it }
            password?.let { password = it }
        }

        mqttClient?.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                isConnected = false
                Log.w(TAG, "MQTT connection lost", cause)
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                val payload = String(message.payload)
                Log.d(TAG, "MQTT message arrived: $topic -> $payload")
                executor.execute {
                    messageCallback?.invoke(topic, payload)
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // not used
            }
        })

        mqttClient?.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                isConnected = true
                Log.i(TAG, "MQTT connected")
                onConnected()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                isConnected = false
                Log.e(TAG, "MQTT connection failed", exception)
                onFailure(exception ?: Exception("Unknown error"))
            }
        })
    }

    fun disconnect() {
        mqttClient?.disconnect()
        mqttClient = null
        isConnected = false
    }

    fun publish(topic: String, payload: String, qos: Int = 1, retained: Boolean = false) {
        if (!isConnected || mqttClient == null) {
            Log.w(TAG, "MQTT not connected, cannot publish")
            return
        }
        try {
            val message = MqttMessage(payload.toByteArray()).apply {
                this.qos = qos
                isRetained = retained
            }
            mqttClient?.publish(topic, message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish MQTT", e)
        }
    }

    fun subscribe(topic: String, qos: Int = 1) {
        if (!isConnected || mqttClient == null) {
            Log.w(TAG, "MQTT not connected, cannot subscribe")
            return
        }
        try {
            mqttClient?.subscribe(topic, qos)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe", e)
        }
    }

    fun setMessageCallback(callback: (topic: String, payload: String) -> Unit) {
        messageCallback = callback
    }

    fun isConnected(): Boolean = isConnected
}
