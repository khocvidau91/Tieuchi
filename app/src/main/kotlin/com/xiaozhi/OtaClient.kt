package com.xiaozhi

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.util.concurrent.TimeUnit

class OtaClient(
    private val context: Context,
    private val deviceId: String,
    private val clientId: String,
    private val otaUrl: String = "https://api.tenclass.net/xiaozhi/ota/"
) {
    companion object {
        private const val TAG = "OtaClient"
        private const val ACTIVATE_URL = "https://api.tenclass.net/xiaozhi/ota/activate"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    var otaResponse: OtaResponse? = null
        private set

    @Throws(IOException::class)
    fun checkOta(): Boolean {
        val body = JsonObject().apply {
            addProperty("chip_model", "Android")
            addProperty("firmware_version", "1.0")
            addProperty("device_name", AppState.getDeviceName(context))
        }

        val request = Request.Builder()
            .url(otaUrl)
            .post(RequestBody.create("application/json".toMediaType(), body.toString()))
            .header("Device-Id", deviceId)
            .header("Client-Id", clientId)
            .build()

        // Sử dụng use để tự động đóng response
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val json = response.body?.string() ?: return false
            otaResponse = gson.fromJson(json, OtaResponse::class.java)
            Log.d(TAG, "OTA response: $json")
            return true
        }
    }

    fun pollActivation(): Boolean {
        val request = Request.Builder()
            .url(ACTIVATE_URL)
            .post(RequestBody.create("application/json".toMediaType(), "{}"))
            .header("Device-Id", deviceId)
            .header("Client-Id", clientId)
            .build()

        while (true) {
            try {
                // Sử dụng use để tự động đóng response
                val code = client.newCall(request).execute().use { response ->
                    response.code
                }
                when (code) {
                    200 -> return true
                    202 -> Thread.sleep(3000)
                    else -> Thread.sleep(5000)
                }
            } catch (e: Exception) {
                try {
                    Thread.sleep(3000)
                } catch (_: InterruptedException) {}
            }
        }
    }

    data class OtaResponse(
        val websocket: WebSocketConfig?,
        val activation: Activation?
    ) {
        data class WebSocketConfig(
            val url: String?,
            val token: String?
        )
        data class Activation(
            val code: String?
        )
    }
}