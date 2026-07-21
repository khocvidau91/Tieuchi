package com.xiaozhi

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// --------------------------------------------------------------------
// McpHandler – xử lý MCP (Model Context Protocol) cho AI
// SỬ DỤNG YT-DLP BINARY TỰ BUILD
// --------------------------------------------------------------------
class McpHandler(
    private val activity: MainActivity
) {
    companion object {
        private const val TAG = "McpHandler"
        private const val MCP_TIMEOUT_MS = 10_000L
        private const val TOOLS_LIST_CACHE_DURATION_MS = 2_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var chatWsManager: WebSocketManager? = null

    @Volatile
    var musicEnabled: Boolean = true

    @Volatile
    var videoEnabled: Boolean = true

    private val pendingRequests = ConcurrentHashMap<Int, PendingMcpRequest>()
    private val pendingResultsQueue = ConcurrentLinkedQueue<Pair<Int, String>>()
    private data class PendingMcpRequest(val id: Int, val toolName: String, val timestamp: Long = System.currentTimeMillis())

    private var cachedToolsListJson: String? = null
    private var lastToolsListSentTime: Long = 0

    @Volatile
    var pendingAnalyzeRequestId: Int = -1

    init {
        Log.i(TAG, "McpHandler initialized (music/video via yt-dlp binary)")
        // Khởi tạo YtDlpClient trong nền (không chặn)
        scope.launch {
            YtDlpClient.initialize(activity)
        }
    }

    fun setChatWebSocketManager(wsManager: WebSocketManager) {
        chatWsManager = wsManager
        flushPendingResults()
    }

    // ==================== XỬ LÝ MCP MESSAGE CHÍNH ====================
    fun handleMcpMessage(payload: JsonObject?) {
        if (payload == null) return
        scope.launch {
            try {
                val jsonrpc = payload.get("jsonrpc")?.asString ?: ""
                if (jsonrpc != "2.0") return@launch

                val method = payload.get("method")?.asString ?: ""
                val params = payload.getAsJsonObject("params")
                val id = payload.get("id")?.asInt ?: -1

                when (method) {
                    "initialize" -> {
                        val result = JsonObject().apply {
                            addProperty("protocolVersion", "2024-11-05")
                            add("capabilities", JsonObject().apply { add("tools", JsonObject()) })
                            add("serverInfo", JsonObject().apply {
                                addProperty("name", "XiaoZhi Android")
                                addProperty("version", "1.0")
                            })
                        }
                        sendResponse(id, result)
                    }
                    "tools/list" -> {
                        val now = System.currentTimeMillis()
                        if (now - lastToolsListSentTime < TOOLS_LIST_CACHE_DURATION_MS) {
                            Log.d(TAG, "Ignoring duplicate tools/list request")
                            return@launch
                        }
                        lastToolsListSentTime = now
                        if (cachedToolsListJson == null) {
                            val tools = buildToolsList()
                            val result = JsonObject().apply {
                                add("tools", tools)
                                addProperty("nextCursor", "")
                            }
                            cachedToolsListJson = result.toString()
                        }
                        val cachedPayload = JsonObject().apply {
                            addProperty("jsonrpc", "2.0")
                            add("result", JsonParser.parseString(cachedToolsListJson))
                            addProperty("id", id)
                        }
                        sendMcpPayload(cachedPayload)
                    }
                    "tools/call" -> {
                        withTimeoutOrNull(MCP_TIMEOUT_MS) {
                            handleToolsCall(id, params)
                        } ?: run {
                            sendError(id, -32000, "Yêu cầu MCP quá thời gian")
                            pendingRequests.remove(id)
                        }
                    }
                    else -> {
                        if (id != -1) sendError(id, -32601, "Method not found")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleMcpMessage error", e)
            }
        }
    }

    // ==================== XỬ LÝ TOOLS CALL ====================
    private suspend fun handleToolsCall(id: Int, params: JsonObject?) {
        if (params == null) {
            sendError(id, -32602, "Invalid params")
            return
        }
        val toolName = params.get("name")?.asString ?: ""
        val arguments = params.getAsJsonObject("arguments") ?: JsonObject()

        // Kiểm tra bật/tắt MCP music/video
        if (toolName.startsWith("self.music.") && !musicEnabled) {
            sendError(id, -32001, "MCP nhạc đang tắt, hãy bật trong cài đặt")
            return
        }
        if (toolName.startsWith("self.video.") && !videoEnabled) {
            sendError(id, -32001, "MCP video đang tắt, hãy bật trong cài đặt")
            return
        }

        when (toolName) {
            // ==================== MUSIC (YT-DLP BINARY) ====================
            "self.music.play_song" -> {
                val song = arguments.get("song_name")?.asString ?: ""
                val artist = arguments.get("artist_name")?.asString ?: ""
                if (song.isEmpty()) {
                    sendError(id, -32602, "Thiếu song_name")
                    return
                }
                activity.runOnUiThread { activity.setStatus("🔍 Đang tìm: $song $artist") }
                scope.launch {
                    try {
                        // Đảm bảo YtDlpClient đã được khởi tạo
                        YtDlpClient.initialize(activity)
                        val searchQuery = "$song $artist".trim()
                        // SỬA: thêm activity làm tham số đầu tiên
                        val video = YtDlpClient.searchFirst(activity, searchQuery)
                        if (video != null) {
                            // SỬA: thêm activity làm tham số đầu tiên
                            val stream = YtDlpClient.getAudioStream(activity, video.webpageUrl)
                            if (stream.success && stream.url != null) {
                                activity.startMusicService(stream.url, video.title, video.thumbnail)
                                activity.showAssistantMessage("Đang phát: ${video.title}")
                                sendToolCallResult(id, true, "Đã tìm thấy và phát: ${video.title}")
                            } else {
                                sendToolCallResult(id, false, stream.error ?: "Không lấy được link phát")
                            }
                        } else {
                            sendToolCallResult(id, false, "Không tìm thấy bài hát '$song'")
                        }
                    } catch (e: Exception) {
                        sendToolCallResult(id, false, "Lỗi: ${e.message}")
                    }
                }
            }
            "self.music.stop" -> {
                activity.stopMusicService()
                activity.runOnUiThread {
                    activity.hideAlbumArt()
                    activity.setStatus("Sẵn sàng")
                    activity.isMusicPlaying = false
                }
                sendToolCallResult(id, true, "Đã dừng nhạc")
            }

            // ==================== VIDEO (YT-DLP BINARY) ====================
            "self.video.play" -> {
                val query = arguments.get("query")?.asString ?: ""
                if (query.isEmpty()) {
                    sendError(id, -32602, "Thiếu query")
                    return
                }
                activity.runOnUiThread { activity.setStatus("🔍 Đang tìm video: $query") }
                scope.launch {
                    try {
                        YtDlpClient.initialize(activity)
                        // SỬA: thêm activity làm tham số đầu tiên
                        val video = YtDlpClient.searchFirst(activity, query)
                        if (video != null) {
                            // SỬA: thêm activity làm tham số đầu tiên
                            val stream = YtDlpClient.getVideoStream(activity, video.webpageUrl)
                            if (stream.success && stream.url != null) {
                                activity.startVideo(stream.url, video.title)
                                sendToolCallResult(id, true, "Đang phát video: ${video.title}")
                            } else {
                                sendToolCallResult(id, false, stream.error ?: "Không lấy được link video")
                            }
                        } else {
                            sendToolCallResult(id, false, "Không tìm thấy video cho '$query'")
                        }
                    } catch (e: Exception) {
                        sendToolCallResult(id, false, "Lỗi: ${e.message}")
                    }
                }
            }
            "self.video.stop" -> {
                activity.runOnUiThread { activity.stopVideo() }
                sendToolCallResult(id, true, "Đã dừng video")
            }
            "self.video.pause" -> {
                activity.runOnUiThread { activity.pauseVideo() }
                sendToolCallResult(id, true, "Đã tạm dừng video")
            }
            "self.video.resume" -> {
                activity.runOnUiThread { activity.resumeVideo() }
                sendToolCallResult(id, true, "Đã tiếp tục video")
            }

            // ==================== CAMERA IP ====================
            "self.camera.list" -> {
                val cameras = CameraManager.getCameras(activity)
                if (cameras.isEmpty()) sendToolCallResult(id, true, "Chưa có camera nào được thêm.")
                else sendToolCallResult(id, true, "Các camera: ${cameras.joinToString(", ") { it.name }}")
            }
            "self.camera.view" -> {
                var camera: CameraInfo? = null
                val cameraName = arguments.get("camera_name")?.asString
                if (!cameraName.isNullOrEmpty()) {
                    camera = CameraManager.getCameras(activity).find { it.name.equals(cameraName, ignoreCase = true) }
                }
                if (camera == null) camera = CameraManager.getDefaultCamera(activity)
                if (camera == null) {
                    sendToolCallResult(id, false, "Không tìm thấy camera '$cameraName' và chưa có camera mặc định.")
                    return
                }
                val url = camera.getFullUrl()
                activity.runOnUiThread { activity.startCamera(url, camera.name) }
                sendToolCallResult(id, true, "Đang hiển thị camera ${camera.name}")
            }
            "self.camera.stop" -> {
                activity.runOnUiThread { activity.stopCamera() }
                sendToolCallResult(id, true, "Đã dừng xem camera")
            }
            "self.camera.snapshot" -> {
                activity.takeCameraSnapshot { bitmap ->
                    if (bitmap != null) sendToolCallResult(id, true, "Đã chụp ảnh: ${activity.saveSnapshot(bitmap)}")
                    else sendToolCallResult(id, false, "Không thể chụp ảnh")
                }
            }

            // ==================== CAMERA ĐIỆN THOẠI ====================
            "self.camera.capture" -> {
                activity.openCameraForCapture()
                // Kết quả sẽ gửi qua onCaptureResult
            }
            "self.camera.analyze" -> {
                pendingAnalyzeRequestId = id
                activity.openCameraForCapture()
            }

            // ==================== PHÂN TÍCH FILE ====================
            "self.file.analyze" -> {
                val path = arguments.get("path")?.asString
                if (path.isNullOrEmpty()) {
                    sendError(id, -32602, "Thiếu đường dẫn file")
                    return
                }
                val file = File(path)
                if (!file.exists()) {
                    sendToolCallResult(id, false, "File không tồn tại: $path")
                    return
                }
                val result = activity.analyzeFileForMCP(file)
                if (result == null) {
                    sendToolCallResult(id, false, "Không thể phân tích file hoặc định dạng không hỗ trợ")
                } else {
                    sendToolCallResult(id, true, result)
                }
            }

            // ==================== QUẢN LÝ ỨNG DỤNG ====================
            "self.app.list" -> {
                val apps = activity.getInstalledApps()
                val sb = StringBuilder()
                var count = 0
                for (app in apps) {
                    if (count++ >= 50) break
                    val name = app["name"] ?: ""
                    val pkg = app["package"] ?: ""
                    sb.append("$name ($pkg)\n")
                }
                if (apps.size > 50) sb.append("... và ${apps.size - 50} ứng dụng khác")
                sendToolCallResult(id, true, sb.toString())
            }
            "self.app.open" -> {
                var pkg = arguments.get("package")?.asString
                val name = arguments.get("name")?.asString
                if (pkg.isNullOrEmpty() && !name.isNullOrEmpty()) {
                    pkg = activity.findPackageByName(name)
                    if (pkg == null) {
                        sendToolCallResult(id, false, "Không tìm thấy ứng dụng có tên '$name'")
                        return
                    }
                }
                if (pkg.isNullOrEmpty()) {
                    sendError(id, -32602, "Thiếu package hoặc name")
                    return
                }
                if (activity.openApp(pkg)) {
                    sendToolCallResult(id, true, "Đã mở $pkg")
                } else {
                    sendToolCallResult(id, false, "Không thể mở $pkg")
                }
            }
            "self.app.uninstall" -> {
                val pkg = arguments.get("package")?.asString ?: ""
                if (pkg.isEmpty()) { sendError(id, -32602, "Thiếu package"); return }
                activity.uninstallApp(pkg)
                sendToolCallResult(id, true, "Đã mở trình gỡ cài đặt cho $pkg")
            }

            // ==================== ĐIỆN THOẠI ====================
            "self.phone.dial" -> {
                val number = arguments.get("number")?.asString ?: ""
                if (number.isEmpty()) {
                    sendError(id, -32602, "Thiếu số điện thoại")
                    return
                }
                if (activity.dialPhoneNumber(number)) {
                    sendToolCallResult(id, true, "Đã mở quay số $number")
                } else {
                    sendToolCallResult(id, false, "Không thể quay số $number")
                }
            }

            // ==================== HỆ THỐNG ====================
            "self.system.volume.set" -> {
                val stream = arguments.get("stream")?.asString ?: "music"
                val percent = arguments.get("percent")?.asInt ?: -1
                if (percent !in 0..100) { sendError(id, -32602, "percent phải từ 0-100"); return }
                activity.setVolume(stream, percent)
                sendToolCallResult(id, true, "Đã đặt âm lượng $stream = $percent%")
            }
            "self.system.volume.get" -> {
                val stream = arguments.get("stream")?.asString ?: "music"
                val vol = activity.getVolume(stream)
                sendToolCallResult(id, true, "Âm lượng $stream hiện tại = $vol%")
            }
            "self.system.brightness.set" -> {
                val percent = arguments.get("percent")?.asInt ?: -1
                if (percent !in 0..100) { sendError(id, -32602, "percent phải từ 0-100"); return }
                if (!activity.setBrightness(percent)) sendError(id, -32000, "Cần cấp quyền WRITE_SETTINGS")
                else sendToolCallResult(id, true, "Đã đặt độ sáng = $percent%")
            }
            "self.system.brightness.get" -> {
                sendToolCallResult(id, true, "Độ sáng hiện tại = ${activity.getBrightness()}%")
            }
            "self.system.wifi.set" -> {
                val enabled = arguments.get("enabled")?.asBoolean ?: false
                val success = activity.setWifiEnabled(enabled)
                sendToolCallResult(id, success, if (enabled) "Đã bật WiFi" else "Đã tắt WiFi")
            }
            "self.system.wifi.get" -> {
                sendToolCallResult(id, true, "WiFi đang ${if (activity.isWifiEnabled()) "bật" else "tắt"}")
            }
            "self.system.bluetooth.set" -> {
                val enabled = arguments.get("enabled")?.asBoolean ?: false
                val success = activity.setBluetoothEnabled(enabled)
                sendToolCallResult(id, success, if (enabled) "Đã bật Bluetooth" else "Đã tắt Bluetooth")
            }
            "self.system.bluetooth.get" -> {
                sendToolCallResult(id, true, "Bluetooth đang ${if (activity.isBluetoothEnabled()) "bật" else "tắt"}")
            }
            "self.system.wifi.scan" -> {
                val networks = activity.scanWifiNetworks()
                if (networks.isEmpty()) sendToolCallResult(id, false, "Không tìm thấy mạng WiFi hoặc WiFi chưa bật")
                else {
                    val result = networks.joinToString("\n") { "${it["ssid"]} (${it["signalStrength"]} dBm)" }
                    sendToolCallResult(id, true, "Các mạng WiFi xung quanh:\n$result")
                }
            }
            "self.system.wifi.connect" -> {
                val ssid = arguments.get("ssid")?.asString ?: ""
                val password = arguments.get("password")?.asString
                if (ssid.isEmpty()) { sendError(id, -32602, "Thiếu ssid"); return }
                val success = activity.connectToWifi(ssid, password)
                sendToolCallResult(id, success, if (success) "Đã kết nối đến $ssid" else "Kết nối thất bại")
            }
            "self.system.bluetooth.scan" -> {
                val devices = activity.getPairedBluetoothDevices()
                if (devices.isEmpty()) sendToolCallResult(id, false, "Không tìm thấy thiết bị Bluetooth hoặc Bluetooth chưa bật")
                else {
                    val result = devices.joinToString("\n") { "${it["name"]} (${it["address"]})" }
                    sendToolCallResult(id, true, "Thiết bị Bluetooth đã ghép đôi:\n$result")
                }
            }
            "self.system.bluetooth.connect" -> {
                val address = arguments.get("address")?.asString ?: ""
                if (address.isEmpty()) { sendError(id, -32602, "Thiếu địa chỉ Bluetooth"); return }
                val success = activity.connectToBluetooth(address)
                sendToolCallResult(id, success, if (success) "Đã kết nối Bluetooth tới $address" else "Kết nối thất bại")
            }
            "self.system.bluetooth.disconnect" -> {
                val success = activity.disconnectBluetooth()
                sendToolCallResult(id, success, if (success) "Đã ngắt kết nối Bluetooth" else "Ngắt kết nối thất bại")
            }
            "self.system.bluetooth.connected" -> {
                val device = activity.getConnectedBluetoothDevice()
                sendToolCallResult(id, true, if (device != null) "Đang kết nối với $device" else "Không có thiết bị Bluetooth nào đang kết nối")
            }
            "self.system.airplane.set" -> {
                val enabled = arguments.get("enabled")?.asBoolean ?: false
                val success = activity.setAirplaneModeEnabled(enabled)
                sendToolCallResult(id, success, if (enabled) "Đã bật chế độ máy bay" else "Đã tắt chế độ máy bay")
            }
            "self.system.airplane.get" -> {
                sendToolCallResult(id, true, "Chế độ máy bay đang ${if (activity.isAirplaneModeEnabled()) "bật" else "tắt"}")
            }
            "self.system.lock" -> {
                activity.lockScreen()
                sendToolCallResult(id, true, "Đã khoá màn hình")
            }
            "self.system.battery" -> {
                val level = activity.getBatteryLevel()
                val charging = if (activity.isCharging()) "đang sạc" else "không sạc"
                sendToolCallResult(id, true, "Pin: $level%, $charging")
            }
            "self.system.storage" -> {
                val free = activity.getFreeStorage() / (1024 * 1024 * 1024)
                val total = activity.getTotalStorage() / (1024 * 1024 * 1024)
                sendToolCallResult(id, true, "Bộ nhớ: $free GB trống / $total GB tổng")
            }

            // ==================== QUẢN LÝ FILE ====================
            "self.file.list" -> {
                val path = arguments.get("path")?.asString ?: activity.getExternalStorageRoot()
                val files = activity.listDirectory(path)
                if (files == null) sendToolCallResult(id, false, "Không thể đọc $path")
                else {
                    val names = files.take(30).joinToString { (it["name"] as? String) ?: "" }
                    sendToolCallResult(id, true, "$path: $names")
                }
            }
            "self.file.delete" -> {
                val path = arguments.get("path")?.asString ?: ""
                if (path.isEmpty()) { sendError(id, -32602, "Thiếu path"); return }
                val success = activity.deleteFileSystem(path)
                sendToolCallResult(id, success, if (success) "Đã xóa $path" else "Không thể xóa $path")
            }
            "self.file.mkdir" -> {
                val path = arguments.get("path")?.asString ?: ""
                if (path.isEmpty()) { sendError(id, -32602, "Thiếu path"); return }
                val success = activity.createDirectory(path)
                sendToolCallResult(id, success, if (success) "Đã tạo thư mục $path" else "Không thể tạo $path")
            }
            "self.file.info" -> {
                val path = arguments.get("path")?.asString ?: ""
                if (path.isEmpty()) { sendError(id, -32602, "Thiếu path"); return }
                val info = activity.getFileInfo(path)
                if (info == null) sendToolCallResult(id, false, "Không tìm thấy $path")
                else {
                    val name = info["name"] as? String
                    val isDir = if (info["isDirectory"] == true) "Thư mục" else "Tệp"
                    val size = (info["size"] as? Long) ?: 0L
                    sendToolCallResult(id, true, "$isDir: $name, kích thước: ${size / 1024} KB")
                }
            }

            // ==================== ĐIỀU KHIỂN MEDIA (hệ thống) ====================
            "self.media.play" -> { activity.mediaPlay(); sendToolCallResult(id, true, "Đã phát") }
            "self.media.pause" -> { activity.mediaPause(); sendToolCallResult(id, true, "Đã tạm dừng") }
            "self.media.next" -> { activity.mediaNext(); sendToolCallResult(id, true, "Đã chuyển bài tiếp theo") }
            "self.media.prev" -> { activity.mediaPrevious(); sendToolCallResult(id, true, "Đã chuyển bài trước") }
            "self.media.playpause" -> { activity.mediaPlayPause(); sendToolCallResult(id, true, "Đã chuyển trạng thái phát/tạm dừng") }

            // ==================== THÔNG BÁO ====================
            "self.notification.send" -> {
                val title = arguments.get("title")?.asString ?: "XiaoZhi AI"
                val content = arguments.get("content")?.asString ?: "Thông báo từ AI"
                activity.sendNotification(title, content)
                sendToolCallResult(id, true, "Đã gửi thông báo: $title")
            }
            "self.notification.clear" -> {
                activity.clearNotifications()
                sendToolCallResult(id, true, "Đã xóa toàn bộ thông báo")
            }
            "self.notification.read" -> {
                val pkg = NotifyListener.lastPackage
                val title = NotifyListener.lastTitle
                val text = NotifyListener.lastText
                if (pkg.isNotEmpty()) {
                    sendToolCallResult(id, true, "[$pkg] $title: $text")
                } else {
                    sendToolCallResult(id, true, "Không có thông báo mới")
                }
            }

            // ==================== HOME ASSISTANT (STUB) ====================
            "self.smarthome.list_devices" -> {
                sendToolCallResult(id, false, "Tính năng Home Assistant chưa được cấu hình trong ứng dụng này.")
            }
            "self.smarthome.turn_on" -> {
                sendToolCallResult(id, false, "Tính năng Home Assistant chưa được cấu hình.")
            }
            "self.smarthome.turn_off" -> {
                sendToolCallResult(id, false, "Tính năng Home Assistant chưa được cấu hình.")
            }
            "self.smarthome.toggle" -> {
                sendToolCallResult(id, false, "Tính năng Home Assistant chưa được cấu hình.")
            }
            "self.smarthome.set_brightness" -> {
                sendToolCallResult(id, false, "Tính năng Home Assistant chưa được cấu hình.")
            }
            "self.smarthome.set_temperature" -> {
                sendToolCallResult(id, false, "Tính năng Home Assistant chưa được cấu hình.")
            }
            "self.smarthome.set_hvac_mode" -> {
                sendToolCallResult(id, false, "Tính năng Home Assistant chưa được cấu hình.")
            }

            // ==================== TOOL BỔ SUNG ====================
            "google_maps_search" -> {
                val address = arguments.get("address")?.asString ?: ""
                if (address.isEmpty()) {
                    sendError(id, -32602, "Missing address")
                    return
                }
                try {
                    val encoded = URLEncoder.encode(address, "UTF-8")
                    val url = "https://www.google.com/maps/search/?api=1&query=$encoded"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(intent)
                    sendToolCallResult(id, true, "Opened Google Maps for $address")
                } catch (e: Exception) {
                    sendError(id, -32000, "Failed to open maps: ${e.message}")
                }
            }
            "google_search" -> {
                val query = arguments.get("query")?.asString ?: ""
                if (query.isEmpty()) {
                    sendError(id, -32602, "Missing query")
                    return
                }
                try {
                    val encoded = URLEncoder.encode(query, "UTF-8")
                    val url = "https://www.google.com/search?q=$encoded"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(intent)
                    sendToolCallResult(id, true, "Opened Google Search for $query")
                } catch (e: Exception) {
                    sendError(id, -32000, "Failed to open search: ${e.message}")
                }
            }
            "create_alarm" -> {
                val hour = arguments.get("hour")?.asInt ?: run { sendError(id, -32602, "Missing hour"); return }
                val minute = arguments.get("minute")?.asInt ?: run { sendError(id, -32602, "Missing minute"); return }
                val message = arguments.get("message")?.asString ?: "Báo thức"
                val skipUi = arguments.get("skip_ui")?.asBoolean ?: true
                try {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, message)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(intent)
                    sendToolCallResult(id, true, "Alarm set for $hour:$minute")
                } catch (e: Exception) {
                    sendError(id, -32000, "Failed to set alarm: ${e.message}")
                }
            }
            "press_back" -> {
                val intent = Intent("com.xiaozhi.ACCESSIBILITY_ACTION").apply {
                    putExtra("action", android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    setPackage(activity.packageName)
                }
                activity.sendBroadcast(intent)
                sendToolCallResult(id, true, "Pressed back")
            }
            "press_home" -> {
                val intent = Intent("com.xiaozhi.ACCESSIBILITY_ACTION").apply {
                    putExtra("action", android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                    setPackage(activity.packageName)
                }
                activity.sendBroadcast(intent)
                sendToolCallResult(id, true, "Pressed home")
            }
            "scroll_screen" -> {
                val direction = arguments.get("direction")?.asString ?: "down"
                val intent = Intent("com.xiaozhi.ACCESSIBILITY_SCROLL").apply {
                    putExtra("direction", direction)
                    setPackage(activity.packageName)
                }
                activity.sendBroadcast(intent)
                sendToolCallResult(id, true, "Scrolled $direction")
            }
            "click_text" -> {
                val text = arguments.get("text")?.asString ?: run { sendError(id, -32602, "Missing text"); return }
                val intent = Intent("com.xiaozhi.CLICK_TEXT").apply {
                    putExtra("text", text)
                    setPackage(activity.packageName)
                }
                activity.sendBroadcast(intent)
                sendToolCallResult(id, true, "Clicked on $text")
            }
            "click_at" -> {
                val x = arguments.get("x")?.asFloat ?: run { sendError(id, -32602, "Missing x"); return }
                val y = arguments.get("y")?.asFloat ?: run { sendError(id, -32602, "Missing y"); return }
                val intent = Intent("com.xiaozhi.CLICK_AT").apply {
                    putExtra("x", x)
                    putExtra("y", y)
                    setPackage(activity.packageName)
                }
                activity.sendBroadcast(intent)
                sendToolCallResult(id, true, "Clicked at ($x, $y)")
            }
            "type_text" -> {
                val text = arguments.get("text")?.asString ?: run { sendError(id, -32602, "Missing text"); return }
                val intent = Intent("com.xiaozhi.INPUT_TEXT").apply {
                    putExtra("text", text)
                    setPackage(activity.packageName)
                }
                activity.sendBroadcast(intent)
                sendToolCallResult(id, true, "Typed: $text")
            }

            else -> sendError(id, -32601, "Unknown tool: $toolName")
        }
    }

    // ==================== GỬI KẾT QUẢ TOOL CALL ====================
    fun sendToolCallResult(id: Int, success: Boolean, message: String) {
        val payload = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", id)
            if (success) {
                val result = JsonObject().apply {
                    val content = JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", message)
                        })
                    }
                    add("content", content)
                    addProperty("isError", false)
                }
                add("result", result)
            } else {
                add("error", JsonObject().apply {
                    addProperty("code", -32000)
                    addProperty("message", message)
                })
            }
        }
        sendMcpPayload(payload)
    }

    private fun sendResponse(id: Int, result: JsonObject) {
        sendMcpPayload(JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("result", result)
            addProperty("id", id)
        })
    }

    private fun sendError(id: Int, code: Int, message: String) {
        sendMcpPayload(JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("error", JsonObject().apply {
                addProperty("code", code)
                addProperty("message", message)
            })
            addProperty("id", id)
        })
    }

    private fun sendMcpPayload(payload: JsonObject) {
        val ws = chatWsManager
        if (ws != null && ws.isConnected) {
            val mcpMessage = JsonObject().apply {
                addProperty("type", "mcp")
                add("payload", payload)
            }
            ws.sendText(mcpMessage.toString())
            Log.d(TAG, "Sent MCP payload: $mcpMessage")
        } else {
            pendingResultsQueue.add(Pair(payload.get("id").asInt, payload.toString()))
            Log.w(TAG, "Chat WS not ready, queuing MCP result for id=${payload.get("id").asInt}")
        }
    }

    fun flushPendingResults() {
        val ws = chatWsManager ?: return
        if (!ws.isConnected) return
        while (true) {
            val pair = pendingResultsQueue.poll() ?: break
            val (id, jsonStr) = pair
            val mcpMessage = JsonObject().apply {
                addProperty("type", "mcp")
                add("payload", JsonParser.parseString(jsonStr))
            }
            ws.sendText(mcpMessage.toString())
            Log.i(TAG, "Flushed pending MCP result for id=$id")
        }
    }

    // ==================== DANH SÁCH TOOLS (MCP) ====================
    private fun buildToolsList(): JsonArray {
        val tools = JsonArray()

        // Music
        tools.add(JsonObject().apply {
            addProperty("name", "self.music.play_song")
            addProperty("description", "Phát bài hát theo yêu cầu (tìm kiếm trên YouTube và phát trực tiếp, dùng binary yt-dlp)")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("song_name", JsonObject().apply { addProperty("type", "string"); addProperty("description", "Tên bài hát") })
                    add("artist_name", JsonObject().apply { addProperty("type", "string"); addProperty("description", "Tên ca sĩ (không bắt buộc)") })
                })
                add("required", JsonArray().apply { add("song_name") })
            })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.music.stop")
            addProperty("description", "Dừng phát nhạc")
            add("inputSchema", JsonObject().apply { addProperty("type", "object") })
        })

        // Video
        listOf("play", "stop", "pause", "resume").forEach { action ->
            tools.add(JsonObject().apply {
                addProperty("name", "self.video.$action")
                addProperty("description", when (action) {
                    "play" -> "Tìm và phát video trên YouTube (dùng binary yt-dlp)"
                    "stop" -> "Dừng video"
                    "pause" -> "Tạm dừng video"
                    else -> "Tiếp tục video"
                })
                add("inputSchema", JsonObject().apply {
                    addProperty("type", "object")
                    if (action == "play") {
                        add("properties", JsonObject().apply {
                            add("query", JsonObject().apply { addProperty("type", "string"); addProperty("description", "Từ khóa tìm kiếm video") })
                        })
                        add("required", JsonArray().apply { add("query") })
                    } else add("properties", JsonObject())
                })
            })
        }

        // Camera IP
        listOf("list", "view", "stop", "snapshot").forEach { action ->
            tools.add(JsonObject().apply {
                addProperty("name", "self.camera.$action")
                addProperty("description", when (action) {
                    "list" -> "Liệt kê camera đã thêm"
                    "view" -> "Xem luồng camera"
                    "stop" -> "Dừng xem camera"
                    else -> "Chụp ảnh từ camera đang xem"
                })
                add("inputSchema", JsonObject().apply {
                    addProperty("type", "object")
                    if (action == "view") {
                        add("properties", JsonObject().apply {
                            add("camera_name", JsonObject().apply { addProperty("type", "string"); addProperty("description", "Tên camera (không bắt buộc)") })
                        })
                    } else add("properties", JsonObject())
                })
            })
        }

        // Camera phone
        tools.add(JsonObject().apply {
            addProperty("name", "self.camera.capture")
            addProperty("description", "Chụp ảnh từ camera điện thoại và hiển thị trong chat")
            add("inputSchema", JsonObject().apply { addProperty("type", "object") })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.camera.analyze")
            addProperty("description", "Chụp ảnh và phân tích nội dung ảnh (OCR, nhận diện vật thể)")
            add("inputSchema", JsonObject().apply { addProperty("type", "object") })
        })

        // File analysis
        tools.add(JsonObject().apply {
            addProperty("name", "self.file.analyze")
            addProperty("description", "Phân tích nội dung file (ảnh, PDF, văn bản) và trả về dữ liệu để AI xử lý")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("path", JsonObject().apply { addProperty("type", "string") })
                })
                add("required", JsonArray().apply { add("path") })
            })
        })

        // App tools
        tools.add(JsonObject().apply {
            addProperty("name", "self.app.list")
            addProperty("description", "Liệt kê toàn bộ ứng dụng đã cài đặt")
            add("inputSchema", JsonObject().apply { addProperty("type", "object") })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.app.open")
            addProperty("description", "Mở ứng dụng theo tên gói (package) hoặc tên hiển thị")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("package", JsonObject().apply { addProperty("type", "string") })
                    add("name", JsonObject().apply { addProperty("type", "string") })
                })
            })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.app.uninstall")
            addProperty("description", "Gỡ cài đặt ứng dụng")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("package", JsonObject().apply { addProperty("type", "string") })
                })
                add("required", JsonArray().apply { add("package") })
            })
        })

        // Phone
        tools.add(JsonObject().apply {
            addProperty("name", "self.phone.dial")
            addProperty("description", "Quay số điện thoại (mở ứng dụng gọi với số đã nhập)")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("number", JsonObject().apply { addProperty("type", "string") })
                })
                add("required", JsonArray().apply { add("number") })
            })
        })

        // System
        mapOf(
            "self.system.volume.set" to "Đặt âm lượng (stream: music/ring/alarm/notification, percent 0-100)",
            "self.system.volume.get" to "Lấy âm lượng hiện tại",
            "self.system.brightness.set" to "Đặt độ sáng (0-100)",
            "self.system.brightness.get" to "Lấy độ sáng hiện tại",
            "self.system.wifi.set" to "Bật/tắt WiFi (enabled: true/false)",
            "self.system.wifi.get" to "Trạng thái WiFi",
            "self.system.bluetooth.set" to "Bật/tắt Bluetooth",
            "self.system.bluetooth.get" to "Trạng thái Bluetooth",
            "self.system.airplane.set" to "Bật/tắt chế độ máy bay",
            "self.system.airplane.get" to "Trạng thái chế độ máy bay",
            "self.system.lock" to "Khoá màn hình",
            "self.system.battery" to "Thông tin pin",
            "self.system.storage" to "Thông tin bộ nhớ"
        ).forEach { (name, desc) ->
            tools.add(JsonObject().apply {
                addProperty("name", name)
                addProperty("description", desc)
                add("inputSchema", JsonObject().apply {
                    addProperty("type", "object")
                    add("properties", JsonObject().apply {
                        if (name.contains(".set")) {
                            when {
                                name.contains("volume") -> {
                                    add("stream", JsonObject().apply { addProperty("type", "string"); addProperty("description", "music/ring/alarm/notification") })
                                    add("percent", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "0-100") })
                                }
                                name.contains("brightness") -> add("percent", JsonObject().apply { addProperty("type", "integer") })
                                name.contains("wifi") || name.contains("bluetooth") || name.contains("airplane") -> {
                                    add("enabled", JsonObject().apply { addProperty("type", "boolean") })
                                }
                            }
                        }
                    })
                    if (name.contains(".set") && !name.contains("volume")) add("required", JsonArray().apply { add("enabled") })
                    else if (name.contains("volume.set")) add("required", JsonArray().apply { add("percent") })
                })
            })
        }

        // File
        listOf("list", "delete", "mkdir", "info").forEach { action ->
            tools.add(JsonObject().apply {
                addProperty("name", "self.file.$action")
                addProperty("description", when (action) {
                    "list" -> "Liệt kê nội dung thư mục"
                    "delete" -> "Xóa tệp/thư mục"
                    "mkdir" -> "Tạo thư mục mới"
                    else -> "Thông tin tệp/thư mục"
                })
                add("inputSchema", JsonObject().apply {
                    addProperty("type", "object")
                    add("properties", JsonObject().apply {
                        add("path", JsonObject().apply { addProperty("type", "string") })
                    })
                    add("required", JsonArray().apply { add("path") })
                })
            })
        }

        // Media control
        listOf("play", "pause", "next", "prev", "playpause").forEach { action ->
            tools.add(JsonObject().apply {
                addProperty("name", "self.media.$action")
                addProperty("description", when (action) {
                    "play" -> "Phát media đang chạy"
                    "pause" -> "Tạm dừng"
                    "next" -> "Bài tiếp theo"
                    "prev" -> "Bài trước"
                    else -> "Chuyển đổi phát/tạm dừng"
                })
                add("inputSchema", JsonObject().apply { addProperty("type", "object") })
            })
        }

        // Notification
        tools.add(JsonObject().apply {
            addProperty("name", "self.notification.send")
            addProperty("description", "Gửi thông báo")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("title", JsonObject().apply { addProperty("type", "string") })
                    add("content", JsonObject().apply { addProperty("type", "string") })
                })
            })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.notification.clear")
            addProperty("description", "Xóa tất cả thông báo")
            add("inputSchema", JsonObject().apply { addProperty("type", "object") })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.notification.read")
            addProperty("description", "Đọc thông báo cuối cùng")
            add("inputSchema", JsonObject().apply { addProperty("type", "object") })
        })

        // WiFi advanced
        tools.add(JsonObject().apply {
            addProperty("name", "self.system.wifi.scan")
            addProperty("description", "Quét các mạng WiFi xung quanh")
            add("inputSchema", JsonObject().apply { addProperty("type", "object") })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.system.wifi.connect")
            addProperty("description", "Kết nối đến mạng WiFi (hỗ trợ mở và WPA2)")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("ssid", JsonObject().apply { addProperty("type", "string") })
                    add("password", JsonObject().apply { addProperty("type", "string") })
                })
                add("required", JsonArray().apply { add("ssid") })
            })
        })
        // Bluetooth advanced
        tools.add(JsonObject().apply {
            addProperty("name", "self.system.bluetooth.scan")
            addProperty("description", "Quét các thiết bị Bluetooth (đã ghép đôi)")
            add("inputSchema", JsonObject().apply { addProperty("type", "object") })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.system.bluetooth.connect")
            addProperty("description", "Kết nối đến thiết bị Bluetooth theo địa chỉ MAC")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("address", JsonObject().apply { addProperty("type", "string") })
                })
                add("required", JsonArray().apply { add("address") })
            })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.system.bluetooth.disconnect")
            addProperty("description", "Ngắt kết nối Bluetooth hiện tại")
            add("inputSchema", JsonObject().apply { addProperty("type", "object") })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.system.bluetooth.connected")
            addProperty("description", "Lấy thiết bị Bluetooth đang kết nối")
            add("inputSchema", JsonObject().apply { addProperty("type", "object") })
        })

        // Home Assistant Tools (stub)
        tools.add(JsonObject().apply {
            addProperty("name", "self.smarthome.list_devices")
            addProperty("description", "Liệt kê các thiết bị smart home đã kết nối với Home Assistant (chưa khả dụng)")
            add("inputSchema", JsonObject().apply { addProperty("type", "object") })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.smarthome.turn_on")
            addProperty("description", "Bật một thiết bị smart home (chưa khả dụng)")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("entity", JsonObject().apply { addProperty("type", "string") })
                })
                add("required", JsonArray().apply { add("entity") })
            })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.smarthome.turn_off")
            addProperty("description", "Tắt một thiết bị smart home (chưa khả dụng)")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("entity", JsonObject().apply { addProperty("type", "string") })
                })
                add("required", JsonArray().apply { add("entity") })
            })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.smarthome.toggle")
            addProperty("description", "Bật hoặc tắt một thiết bị smart home (chưa khả dụng)")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("entity", JsonObject().apply { addProperty("type", "string") })
                })
                add("required", JsonArray().apply { add("entity") })
            })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.smarthome.set_brightness")
            addProperty("description", "Đặt độ sáng cho đèn (chỉ hoạt động với thiết bị đèn) – chưa khả dụng")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("entity", JsonObject().apply { addProperty("type", "string"); addProperty("description", "ID của thiết bị đèn") })
                    add("brightness", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Độ sáng từ 0-255") })
                })
                add("required", JsonArray().apply { add("entity"); add("brightness") })
            })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.smarthome.set_temperature")
            addProperty("description", "Đặt nhiệt độ cho thiết bị điều hòa – chưa khả dụng")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("entity", JsonObject().apply { addProperty("type", "string"); addProperty("description", "ID của thiết bị điều hòa (climate)") })
                    add("temperature", JsonObject().apply { addProperty("type", "number"); addProperty("description", "Nhiệt độ mong muốn") })
                })
                add("required", JsonArray().apply { add("entity"); add("temperature") })
            })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "self.smarthome.set_hvac_mode")
            addProperty("description", "Đặt chế độ hoạt động cho điều hòa (chưa khả dụng)")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("entity", JsonObject().apply { addProperty("type", "string"); addProperty("description", "ID của thiết bị điều hòa (climate)") })
                    add("hvac_mode", JsonObject().apply { addProperty("type", "string"); addProperty("description", "Chế độ hoạt động (off, heat, cool, auto, dry, fan_only)") })
                })
                add("required", JsonArray().apply { add("entity"); add("hvac_mode") })
            })
        })

        // Additional tools
        tools.add(JsonObject().apply {
            addProperty("name", "google_maps_search")
            addProperty("description", "Search for a location or address on Google Maps.")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("address", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "The address or location to search for")
                    })
                })
                add("required", JsonArray().apply { add("address") })
            })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "google_search")
            addProperty("description", "Search for information on Google and open the results in a browser.")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("query", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "The search query")
                    })
                })
                add("required", JsonArray().apply { add("query") })
            })
        })
        tools.add(JsonObject().apply {
            addProperty("name", "create_alarm")
            addProperty("description", "Creates a new alarm at a specified time.")
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("hour", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Hour (0-23)") })
                    add("minute", JsonObject().apply { addProperty("type", "integer"); addProperty("description", "Minute (0-59)") })
                    add("message", JsonObject().apply { addProperty("type", "string"); addProperty("description", "Alarm label") })
                    add("skip_ui", JsonObject().apply { addProperty("type", "boolean"); addProperty("description", "Skip UI (default true)") })
                })
                add("required", JsonArray().apply { add("hour"); add("minute") })
            })
        })
        listOf(
            "press_back" to "Press the back button",
            "press_home" to "Press the home button",
            "scroll_screen" to "Scroll the screen (up/down/left/right)",
            "click_text" to "Click on a UI element containing specific text",
            "click_at" to "Click at specific screen coordinates",
            "type_text" to "Type text into the focused input field"
        ).forEach { (name, desc) ->
            tools.add(JsonObject().apply {
                addProperty("name", name)
                addProperty("description", desc)
                add("inputSchema", JsonObject().apply {
                    addProperty("type", "object")
                    add("properties", JsonObject().apply {
                        when (name) {
                            "scroll_screen" -> add("direction", JsonObject().apply { addProperty("type", "string"); addProperty("description", "up/down/left/right") })
                            "click_text" -> add("text", JsonObject().apply { addProperty("type", "string"); addProperty("description", "Text to click") })
                            "click_at" -> {
                                add("x", JsonObject().apply { addProperty("type", "number"); addProperty("description", "X coordinate") })
                                add("y", JsonObject().apply { addProperty("type", "number"); addProperty("description", "Y coordinate") })
                            }
                            "type_text" -> add("text", JsonObject().apply { addProperty("type", "string"); addProperty("description", "Text to type") })
                        }
                    })
                    if (name != "press_back" && name != "press_home") {
                        add("required", JsonArray().apply {
                            when (name) {
                                "scroll_screen" -> add("direction")
                                "click_text" -> add("text")
                                "click_at" -> { add("x"); add("y") }
                                "type_text" -> add("text")
                            }
                        })
                    }
                })
            })
        }

        return tools
    }

    // ==================== XỬ LÝ KẾT QUẢ CHỤP ẢNH ====================
    fun onCaptureResult(success: Boolean, imagePath: String) {
        if (pendingAnalyzeRequestId != -1) {
            if (success) {
                val file = File(imagePath)
                val result = activity.analyzeFileForMCP(file)
                if (result != null) {
                    sendToolCallResult(pendingAnalyzeRequestId, true, result)
                } else {
                    sendToolCallResult(pendingAnalyzeRequestId, false, "Không thể phân tích ảnh vừa chụp")
                }
            } else {
                sendToolCallResult(pendingAnalyzeRequestId, false, "Chụp ảnh thất bại")
            }
            pendingAnalyzeRequestId = -1
        } else {
            if (success) {
                activity.showImageMessage(imagePath)
                activity.sendImageForAnalysis(imagePath)
            }
        }
    }

    fun disconnectMusicServer() {
        // Không cần vì không dùng Music WebSocket nữa
    }
}