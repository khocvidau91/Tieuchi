package com.xiaozhi.hass.manager

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DiscoveryManager(private val context: Context) {

    companion object {
        private const val TAG = "DiscoveryManager"
        private const val DISCOVERY_PORT = 5555
        private const val DISCOVERY_MESSAGE = "XIAOZHI_DISCOVERY"
        private const val RESPONSE_MESSAGE = "XIAOZHI_ESP32"
    }

    suspend fun discoverDevices(onDeviceFound: (ip: String, deviceInfo: String) -> Unit) {
        withContext(Dispatchers.IO) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifiManager.connectionInfo.ipAddress
            val ipString = String.format("%d.%d.%d.%d", ip and 0xff, (ip shr 8) and 0xff, (ip shr 16) and 0xff, (ip shr 24) and 0xff)
            val subnet = ipString.substringBeforeLast(".")

            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 3000

                val broadcastAddress = InetAddress.getByName("$subnet.255")
                val sendData = DISCOVERY_MESSAGE.toByteArray()
                val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddress, DISCOVERY_PORT)
                socket.send(sendPacket)

                // Wait for responses
                while (true) {
                    val buffer = ByteArray(1024)
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val response = String(packet.data, 0, packet.length)
                    if (response.startsWith(RESPONSE_MESSAGE)) {
                        val ip = packet.address.hostAddress
                        val deviceInfo = response.substringAfter(":")
                        onDeviceFound(ip, deviceInfo)
                        Log.d(TAG, "Found device at $ip: $deviceInfo")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery error", e)
            }
        }
    }
}
