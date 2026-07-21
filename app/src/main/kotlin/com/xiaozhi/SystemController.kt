package com.xiaozhi

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.session.MediaController as AndroidMediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

// ==================== ShizukuManager – dùng reflection để gọi newProcess ====================
object ShizukuManager {
    private const val TAG = "ShizukuManager"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 1001

    private var isShizukuAvailable = false
    private var isPermissionGranted = false
    private var binderReceivedListener: Shizuku.OnBinderReceivedListener? = null
    private var binderDeadListener: Shizuku.OnBinderDeadListener? = null
    private var permissionResultListener: Shizuku.OnRequestPermissionResultListener? = null

    fun init(context: Context) {
        binderReceivedListener = Shizuku.OnBinderReceivedListener {
            isShizukuAvailable = true
            checkAndRequestPermission()
            Log.i(TAG, "Shizuku binder received")
        }
        binderDeadListener = Shizuku.OnBinderDeadListener {
            isShizukuAvailable = false
            isPermissionGranted = false
            Log.w(TAG, "Shizuku binder dead")
        }
        permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE) {
                isPermissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
                Log.i(TAG, "Shizuku permission granted: $isPermissionGranted")
            }
        }

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener!!)
        Shizuku.addBinderDeadListener(binderDeadListener!!)
        Shizuku.addRequestPermissionResultListener(permissionResultListener!!)

        isShizukuAvailable = Shizuku.pingBinder()
        if (isShizukuAvailable) checkAndRequestPermission()
    }

    private fun checkAndRequestPermission() {
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                isPermissionGranted = true
            } else {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission", e)
        }
    }

    fun isReady(): Boolean = isShizukuAvailable && isPermissionGranted

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun openShizuku(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (intent != null) {
                context.startActivity(intent)
            } else {
                val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE"))
                context.startActivity(playIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Shizuku", e)
        }
    }

    private fun isCommandSafe(command: String): Boolean {
        val dangerousPatterns = listOf(
            "reboot", "shutdown", "recovery", "factory", "reset",
            "format", "rm -rf", "su", "chmod 777", "mount -o remount",
            "dd if=", "mkfs", "flash_image", "reboot -p"
        )
        val lowerCommand = command.lowercase()
        return !dangerousPatterns.any { lowerCommand.contains(it) }
    }

    suspend fun exec(command: String): String {
        if (!isReady()) return "ERROR: Shizuku not ready"
        if (!isCommandSafe(command)) {
            Log.w(TAG, "Blocked dangerous command: $command")
            return "ERROR: Dangerous command blocked"
        }
        return try {
            // Dùng reflection để gọi newProcess (an toàn với mọi phiên bản Shizuku)
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as? Process
            if (process != null) {
                val output = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                process.waitFor()
                process.destroy()
                if (error.isNotBlank()) output + "\n" + error else output
            } else "ERROR: newProcess unavailable"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}

// ==================== SystemController ====================
class SystemController(private val context: Context) {

    companion object {
        private const val TAG = "SystemController"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Bluetooth scan queue
    private val bluetoothDevices = ConcurrentLinkedQueue<Map<String, String>>()
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var isScanning = false

    fun isShizukuReady(): Boolean = ShizukuManager.isReady()
    fun isShizukuInstalled(): Boolean = ShizukuManager.isInstalled(context)
    fun installShizuku() { ShizukuManager.openShizuku(context) }
    fun openShizukuSettings() { ShizukuManager.openShizuku(context) }

    // ==================== WiFi cơ bản ====================
    suspend fun setWifiEnabled(enabled: Boolean): Boolean {
        if (ShizukuManager.isReady()) {
            val result = ShizukuManager.exec("cmd wifi set-wifi-enabled ${if (enabled) "enabled" else "disabled"}")
            if (!result.contains("ERROR")) return true
        }
        openWifiSettings()
        return false
    }

    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled

    private fun openWifiSettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { Log.e(TAG, "openWifiSettings error", e) }
    }

    // ==================== Bluetooth cơ bản ====================
    suspend fun setBluetoothEnabled(enabled: Boolean): Boolean {
        if (ShizukuManager.isReady()) {
            var result = ShizukuManager.exec("svc bluetooth ${if (enabled) "enable" else "disable"}")
            if (!result.contains("ERROR") && !result.contains("not found")) {
                delay(500)
                return true
            }
            result = ShizukuManager.exec("cmd bluetooth_manager set-bluetooth-enabled ${if (enabled) "enabled" else "disabled"}")
            if (!result.contains("ERROR")) {
                delay(500)
                return true
            }
        }
        openBluetoothSettings()
        return false
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    private fun openBluetoothSettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { Log.e(TAG, "openBluetoothSettings error", e) }
    }

    // ==================== Chế độ máy bay ====================
    suspend fun setAirplaneModeEnabled(enabled: Boolean): Boolean {
        if (ShizukuManager.isReady()) {
            val value = if (enabled) "1" else "0"
            val result = ShizukuManager.exec("settings put global airplane_mode_on $value && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $enabled")
            if (!result.contains("ERROR")) return true
        }
        openAirplaneSettings()
        return false
    }

    fun isAirplaneModeEnabled(): Boolean = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    private fun openAirplaneSettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { Log.e(TAG, "openAirplaneSettings error", e) }
    }

    // ==================== Độ sáng màn hình ====================
    suspend fun setBrightness(percent: Int): Boolean {
        val clampedPercent = percent.coerceIn(0, 100)
        val value = (clampedPercent * 255 / 100).coerceIn(0, 255)
        if (ShizukuManager.isReady()) {
            val result = ShizukuManager.exec("settings put system screen_brightness $value")
            if (!result.contains("ERROR")) return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        return Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
    }

    suspend fun getBrightness(): Int {
        if (ShizukuManager.isReady()) {
            val result = ShizukuManager.exec("settings get system screen_brightness").trim()
            val intVal = result.toIntOrNull()
            if (intVal != null) return (intVal * 100 / 255).coerceIn(0, 100)
        }
        val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return brightness * 100 / 255
    }

    // ==================== Khóa màn hình ====================
    fun lockScreen() {
        ioScope.launch { ShizukuManager.exec("input keyevent KEYCODE_POWER") }
    }

    // ==================== Pin & bộ nhớ ====================
    fun getBatteryLevel(): Int {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra("level", -1) ?: -1
        val scale = intent?.getIntExtra("scale", -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    fun isCharging(): Boolean {
        val status = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra("status", -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun getFreeStorage(): Long = StatFs(Environment.getDataDirectory().path).availableBlocksLong * StatFs(Environment.getDataDirectory().path).blockSizeLong
    fun getTotalStorage(): Long = StatFs(Environment.getDataDirectory().path).blockCountLong * StatFs(Environment.getDataDirectory().path).blockSizeLong

    // ==================== Âm lượng ====================
    fun setVolume(streamType: Int, percent: Int): Boolean {
        val max = audioManager.getStreamMaxVolume(streamType)
        audioManager.setStreamVolume(streamType, (percent * max / 100).coerceIn(0, max), 0)
        return true
    }

    fun getVolume(streamType: Int): Int = (audioManager.getStreamVolume(streamType) * 100 / audioManager.getStreamMaxVolume(streamType))

    // ==================== Thông báo ====================
    fun sendNotification(title: String, content: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(android.app.NotificationChannel("mcp_notifications_channel", "MCP Commands", android.app.NotificationManager.IMPORTANCE_HIGH))
        }
        manager.notify(System.currentTimeMillis().toInt(), NotificationCompat.Builder(context, "mcp_notifications_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(content).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build())
    }

    fun clearAllNotifications() { (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).cancelAll() }

    // ==================== Quản lý ứng dụng ====================
    fun getInstalledApps(): List<Map<String, String>> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA).mapNotNull { appInfo ->
            try {
                mapOf("name" to pm.getApplicationLabel(appInfo).toString(), "package" to appInfo.packageName, "isSystem" to ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0).toString())
            } catch (e: Exception) { null }
        }
    }

    fun openApp(packageName: String): Boolean {
        return try {
            context.startActivity(context.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) { false }
    }

    fun uninstallApp(packageName: String): Boolean {
        return try { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    }

    fun findPackageByName(appName: String): String? {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val exactMatch = apps.firstOrNull { pm.getApplicationLabel(it).toString().equals(appName, ignoreCase = true) }?.packageName
        if (exactMatch != null) return exactMatch
        return apps.firstOrNull { pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true) }?.packageName
    }

    // ==================== Quản lý file ====================
    fun listDirectory(path: String): List<Map<String, Any>>? {
        val dir = File(path)
        return if (dir.exists() && dir.isDirectory) dir.listFiles()?.map { mapOf("name" to it.name, "isDirectory" to it.isDirectory, "size" to it.length(), "lastModified" to it.lastModified()) } ?: emptyList() else null
    }

    fun deleteFileSystem(path: String): Boolean = File(path).delete()
    fun createDirectory(path: String): Boolean = File(path).mkdirs()

    fun getFileInfo(path: String): Map<String, Any>? {
        val file = File(path)
        return if (file.exists()) mapOf("name" to file.name, "isDirectory" to file.isDirectory, "size" to file.length(), "lastModified" to file.lastModified(), "absolutePath" to file.absolutePath) else null
    }

    fun getExternalStorageRoot(): String = Environment.getExternalStorageDirectory().absolutePath

    // ==================== Điều khiển Media ====================
    private fun getActiveMediaController(): AndroidMediaController? = mediaSessionManager.getActiveSessions(null).firstOrNull()

    private fun sendMediaKey(keyCode: Int): Boolean {
        val controller = getActiveMediaController() ?: return false
        val tc = controller.transportControls
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> tc.play()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> tc.pause()
            KeyEvent.KEYCODE_MEDIA_NEXT -> tc.skipToNext()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> tc.skipToPrevious()
            KeyEvent.KEYCODE_MEDIA_STOP -> tc.stop()
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                return true
            }
            else -> return false
        }
        return true
    }

    fun mediaPlay() = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    fun mediaPause() = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    fun mediaNext() = sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun mediaPrevious() = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    fun mediaPlayPause() = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    // ==================== Quay số ====================
    fun dialPhoneNumber(number: String): Boolean {
        return try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) { false }
    }

    // ==================== Voice Assistant (Shizuku) ====================
    suspend fun setDefaultVoiceAssistant(): Boolean {
        if (!ShizukuManager.isReady()) return false
        val packageName = context.packageName
        var result = ShizukuManager.exec("cmd role add-role-holder android.app.role.ASSISTANT $packageName")
        if (!result.contains("ERROR") && !result.contains("Exception")) {
            delay(500)
            if (isDefaultVoiceAssistant()) return true
        }
        result = ShizukuManager.exec("settings put secure voice_interaction_service $packageName/.XiaoZhiVoiceInteractionService")
        if (!result.contains("ERROR")) {
            delay(500)
            return isDefaultVoiceAssistant()
        }
        return false
    }

    suspend fun isDefaultVoiceAssistant(): Boolean {
        if (!ShizukuManager.isReady()) return false
        val packageName = context.packageName
        var result = ShizukuManager.exec("cmd role get-role-holders android.app.role.ASSISTANT")
        if (result.contains(packageName)) return true
        result = ShizukuManager.exec("settings get secure voice_interaction_service")
        if (result.trim() == "$packageName/.XiaoZhiVoiceInteractionService") return true
        return false
    }

    // ==================== WiFi nâng cao ====================
    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    suspend fun scanWifiNetworks(): List<Map<String, String>> {
        Log.d(TAG, "scanWifiNetworks called")
        if (!isWifiEnabled()) {
            Log.w(TAG, "WiFi is disabled")
            return emptyList()
        }

        if (!isLocationEnabled()) {
            Log.e(TAG, "Location is disabled, cannot scan WiFi")
            return emptyList()
        }

        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) {
            Log.e(TAG, "Missing ACCESS_FINE_LOCATION permission")
            return emptyList()
        }

        val scanStarted = try {
            wifiManager.startScan()
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "startScan security exception", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "startScan failed", e)
            false
        }

        if (!scanStarted) {
            Log.e(TAG, "startScan returned false")
            return emptyList()
        }

        delay(4000)

        val results = try {
            wifiManager.scanResults
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get scanResults", e)
            emptyList()
        }

        Log.d(TAG, "scanResults size: ${results.size}")
        return results.distinctBy { it.SSID }.mapNotNull { result ->
            if (result.SSID.isNullOrBlank()) return@mapNotNull null
            mapOf(
                "ssid" to result.SSID,
                "bssid" to result.BSSID,
                "signalStrength" to result.level.toString(),
                "capabilities" to result.capabilities
            )
        }
    }

    suspend fun connectToWifi(ssid: String, password: String?): Boolean {
        if (ssid.isEmpty()) return false
        if (!isWifiEnabled()) {
            openWifiSettings()
            return false
        }

        if (ShizukuManager.isReady()) {
            val cmd = if (password.isNullOrEmpty()) {
                "cmd wifi connect-network \"$ssid\" open"
            } else {
                "cmd wifi connect-network \"$ssid\" wpa2 \"$password\""
            }
            val result = ShizukuManager.exec(cmd)
            Log.d(TAG, "WiFi connect result: $result")
            if (!result.contains("ERROR") && !result.contains("failed")) {
                return true
            } else {
                Log.w(TAG, "WiFi command failed, fallback to settings")
            }
        }
        openWifiSettings()
        return false
    }

    // ==================== Bluetooth nâng cao ====================
    fun startBluetoothScan(callback: (List<Map<String, String>>) -> Unit) {
        if (!isBluetoothEnabled()) {
            callback(emptyList())
            return
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val hasScanPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            if (!hasScanPerm) {
                Log.e(TAG, "Missing BLUETOOTH_SCAN permission")
                callback(emptyList())
                return
            }
        }
        bluetoothDevices.clear()
        isScanning = true

        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        device?.let {
                            if (!bluetoothDevices.any { it["address"] == device.address }) {
                                bluetoothDevices.add(mapOf(
                                    "address" to it.address,
                                    "name" to (it.name ?: "Unknown")
                                ))
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        stopBluetoothScan()
                        callback(bluetoothDevices.toList())
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(bluetoothReceiver, filter)

        if (bluetoothAdapter?.isDiscovering == true) bluetoothAdapter?.cancelDiscovery()
        bluetoothAdapter?.startDiscovery()
    }

    fun stopBluetoothScan() {
        if (!isScanning) return
        isScanning = false
        bluetoothAdapter?.cancelDiscovery()
        bluetoothReceiver?.let { context.unregisterReceiver(it) }
        bluetoothReceiver = null
    }

    suspend fun getPairedBluetoothDevices(): List<Map<String, String>> {
        val paired = bluetoothAdapter?.bondedDevices
        return paired?.mapNotNull { device ->
            if (device.address.isBlank()) null
            else mapOf("address" to device.address, "name" to (device.name ?: "Unknown"))
        } ?: emptyList()
    }

    suspend fun connectToBluetooth(address: String): Boolean {
        if (ShizukuManager.isReady()) {
            val result = ShizukuManager.exec("cmd bluetooth_manager connect $address")
            if (!result.contains("ERROR")) return true
        }
        openBluetoothSettings()
        return false
    }

    suspend fun disconnectBluetooth(): Boolean {
        if (ShizukuManager.isReady()) {
            val result = ShizukuManager.exec("cmd bluetooth_manager disconnect")
            return !result.contains("ERROR")
        }
        return false
    }

    suspend fun getConnectedBluetoothDevice(): String? {
        if (ShizukuManager.isReady()) {
            val result = ShizukuManager.exec("cmd bluetooth_manager get-connected-device")
            return result.trim().takeIf { it.isNotEmpty() }
        }
        return null
    }
}