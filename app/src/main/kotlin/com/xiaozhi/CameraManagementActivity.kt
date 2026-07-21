package com.xiaozhi

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*

// ===== CameraInfo data class (ĐÃ ĐƯỢC MỞ RỘNG LƯỢNG TỬ HÓA) =====
data class CameraInfo(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    var url: String = "",
    val type: String = "rtsp",
    val thumbnail: String? = null,
    val username: String? = null,
    val password: String? = null,
    val ip: String? = null,
    val port: Int? = 554,
    val channel: Int? = 1,
    val subtype: Int? = 0,
    var isDefault: Boolean = false,

    // ===== ⚛️ TRƯỜNG LƯỢNG TỬ MỚI – MỞ RỘNG TỐI ĐA =====
    val quantumEntanglementId: String = UUID.randomUUID().toString(),
    var quantumStability: Float = 1.0f,
    var quantumDecay: Float = 0.0f,
    var quantumFrequency: Int = 440,
    var quantumDimension: Int = 0,
    var entangledWithDeviceId: String? = null,
    var gravitySyncPeriodMs: Long = 5000L,
    var lastQuantumPulseTime: Long = System.currentTimeMillis(),
    var quantumLatency: Float = 0f,
    var informationDensity: Float = 1.0f,
    var quantumCorrelation: Float = 0.5f,
    var quantumState: String = "SINGULARITY",
    var quantumEnergyReserve: Float = 100.0f
) {
    // Hàm buildUrl cũ (giữ nguyên để tương thích)
    fun buildUrl(): String {
        if (url.isNotEmpty()) return url
        val userInfo = if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            "$username:$password@"
        } else ""
        val ipAddr = ip ?: return ""
        val portNum = port ?: 554
        val ch = channel ?: 1
        val sub = subtype ?: 0
        return "rtsp://$userInfo$ipAddr:$portNum/Streaming/Channels/$ch$sub"
    }

    fun getFullUrl(): String = if (url.isNotEmpty()) url else buildUrl()

    // ===== PHƯƠNG THỨC LƯỢNG TỬ HÓA =====
    fun getQuantumUrl(isRemoteMode: Boolean): String {
        if (url.isNotEmpty()) return url
        val userInfo = if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            "$username:$password@"
        } else ""
        val host = ip ?: "127.0.0.1"
        val portNum = port ?: 554
        val ch = channel ?: 1
        val sub = subtype ?: 0
        return "rtsp://$userInfo$host:$portNum/Streaming/Channels/$ch$sub"
    }

    fun updateQuantumStability() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastQuantumPulseTime
        if (elapsed > gravitySyncPeriodMs) {
            val decayFactor = (elapsed - gravitySyncPeriodMs).toFloat() / 30000f
            quantumStability = (quantumStability - decayFactor).coerceIn(0f, 1f)
            quantumDecay = (1f - quantumStability)
            quantumEnergyReserve = (quantumEnergyReserve - decayFactor * 10).coerceIn(0f, 100f)
        } else {
            quantumStability = (quantumStability + 0.05f).coerceIn(0f, 1f)
            quantumDecay = 0f
            quantumEnergyReserve = (quantumEnergyReserve + 2f).coerceIn(0f, 100f)
        }
        quantumState = when {
            quantumStability > 0.8f -> "STABLE_ENTANGLEMENT"
            quantumStability > 0.5f -> "WEAK_SIGNAL"
            quantumStability > 0.2f -> "DECAYING"
            else -> "COLLAPSED"
        }
    }

    fun recordQuantumPulse(latencyMs: Long) {
        lastQuantumPulseTime = System.currentTimeMillis()
        quantumLatency = (quantumLatency * 0.7f + latencyMs * 0.3f)
        quantumStability = (quantumStability + 0.2f).coerceIn(0f, 1f)
        quantumDecay = 0f
        quantumEnergyReserve = (quantumEnergyReserve + 5f).coerceIn(0f, 100f)
        quantumFrequency = (440 + (1000 / (latencyMs + 1)).toInt()).coerceIn(200, 2000)
    }

    fun getQuantumReliabilityScore(): Int {
        val stabilityWeight = 0.5f
        val latencyWeight = 0.3f
        val densityWeight = 0.2f
        val latencyNormalized = 1f - (quantumLatency / 1000f).coerceIn(0f, 1f)
        var score = (quantumStability * stabilityWeight +
                latencyNormalized * latencyWeight +
                informationDensity * densityWeight) * 100
        val elapsed = System.currentTimeMillis() - lastQuantumPulseTime
        if (elapsed > gravitySyncPeriodMs * 2) score -= 20
        if (entangledWithDeviceId != null) score += (quantumCorrelation * 10).toInt()
        return score.toInt().coerceIn(0, 100)
    }

    fun createQuantumCopy(newId: String = UUID.randomUUID().toString()): CameraInfo {
        return this.copy(
            id = newId,
            quantumEntanglementId = this.quantumEntanglementId,
            entangledWithDeviceId = this.id,
            quantumCorrelation = 0.9f
        )
    }

    fun isEntangledWith(cameraId: String): Boolean = entangledWithDeviceId == cameraId

    fun getQuantumStateDescription(): String {
        return when {
            quantumStability > 0.8f -> "🔮 Ổn định (${getQuantumReliabilityScore()})"
            quantumStability > 0.5f -> "⚡ Trung bình – ${quantumLatency.toInt()}ms"
            quantumStability > 0.2f -> "🌀 Suy giảm – NL: ${quantumEnergyReserve.toInt()}%"
            else -> "💥 Sụp đổ – Cần kết nối lại"
        }
    }

    fun getQuantumPriority(): Float {
        val reliability = getQuantumReliabilityScore() / 100f
        val dimensionBonus = if (quantumDimension == 0) 0.2f else 0f
        val entangledBonus = if (entangledWithDeviceId != null) 0.1f else 0f
        return (reliability * 0.7f + dimensionBonus + entangledBonus).coerceIn(0f, 1f)
    }
}

// ===== CameraManager object (đã bổ sung hàm scanLocalNetwork) =====
object CameraManager {
    private const val PREFS_NAME = "camera_prefs"
    private const val KEY_CAMERAS = "cameras"
    private const val KEY_DEFAULT_CAMERA_ID = "default_camera_id"
    private val gson = Gson()

    fun getCameras(context: Context): List<CameraInfo> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CAMERAS, "[]") ?: "[]"
        val type = object : TypeToken<List<CameraInfo>>() {}.type
        return gson.fromJson(json, type)
    }

    private fun saveCameras(context: Context, cameras: List<CameraInfo>) {
        val json = gson.toJson(cameras)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CAMERAS, json).apply()
    }

    fun addCamera(context: Context, camera: CameraInfo) {
        val list = getCameras(context).toMutableList()
        list.add(camera)
        saveCameras(context, list)
    }

    fun updateCamera(context: Context, camera: CameraInfo) {
        val list = getCameras(context).toMutableList()
        val index = list.indexOfFirst { it.id == camera.id }
        if (index >= 0) {
            list[index] = camera
            saveCameras(context, list)
        }
    }

    fun removeCamera(context: Context, id: String) {
        val list = getCameras(context).filter { it.id != id }
        saveCameras(context, list)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_DEFAULT_CAMERA_ID, null) == id) {
            prefs.edit().remove(KEY_DEFAULT_CAMERA_ID).apply()
        }
    }

    fun getDefaultCameraId(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEFAULT_CAMERA_ID, null)
    }

    fun setDefaultCameraId(context: Context, cameraId: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEFAULT_CAMERA_ID, cameraId).apply()
    }

    fun getDefaultCamera(context: Context): CameraInfo? {
        val id = getDefaultCameraId(context) ?: return null
        return getCameras(context).find { it.id == id }
    }

    // ==================== CHỨC NĂNG QUÉT MẠNG LAN ====================
    /**
     * Quét toàn bộ dải IP cùng subnet để tìm thiết bị mở cổng RTSP (554)
     * @param context Context
     * @param onProgress Callback báo tiến độ (đã quét, tổng số)
     * @return Danh sách CameraInfo tìm thấy (chỉ có IP, chưa có tên đầy đủ)
     */
    suspend fun scanLocalNetwork(context: Context, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<CameraInfo> {
        return withContext(Dispatchers.IO) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val ip = wifiManager.connectionInfo.ipAddress
            val ipString = String.format("%d.%d.%d.%d", ip and 0xff, (ip shr 8) and 0xff, (ip shr 16) and 0xff, (ip shr 24) and 0xff)
            val subnet = ipString.substringBeforeLast(".")
            val foundCameras = mutableListOf<CameraInfo>()
            val range = 1..254
            var count = 0
            for (i in range) {
                count++
                onProgress(count, range.count())
                val target = "$subnet.$i"
                val isOpen = try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(target, 554), 300)
                        true
                    }
                } catch (e: Exception) {
                    false
                }
                if (isOpen) {
                    val camera = CameraInfo(
                        name = "Camera tại $target",
                        ip = target,
                        port = 554,
                        username = null,
                        password = null
                    )
                    foundCameras.add(camera)
                }
            }
            foundCameras
        }
    }
}

// ===== Activity (giữ nguyên) =====
class CameraManagementActivity : AppCompatActivity() {

    private lateinit var adapter: CameraAdapter
    private val cameras get() = CameraManager.getCameras(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_management)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_camera)
        toolbar.setNavigationOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rv_cameras)
        adapter = CameraAdapter(cameras)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<View>(R.id.fab_add_camera).setOnClickListener {
            showAddEditDialog(null)
        }
    }

    private fun refreshList() {
        adapter.updateData(cameras)
    }

    private fun showAddEditDialog(camera: CameraInfo?) {
        val view = layoutInflater.inflate(R.layout.dialog_add_camera, null)
        val nameEdit = view.findViewById<TextInputEditText>(R.id.camera_name)
        val ipEdit = view.findViewById<TextInputEditText>(R.id.camera_ip)
        val portEdit = view.findViewById<TextInputEditText>(R.id.camera_port)
        val userEdit = view.findViewById<TextInputEditText>(R.id.camera_username)
        val passEdit = view.findViewById<TextInputEditText>(R.id.camera_password)
        val chEdit = view.findViewById<TextInputEditText>(R.id.camera_channel)
        val subtypeEdit = view.findViewById<TextInputEditText>(R.id.camera_subtype)

        if (camera != null) {
            nameEdit.setText(camera.name)
            ipEdit.setText(camera.ip)
            portEdit.setText(camera.port?.toString())
            userEdit.setText(camera.username)
            passEdit.setText(camera.password)
            chEdit.setText(camera.channel?.toString())
            subtypeEdit.setText(camera.subtype?.toString())
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (camera == null) "Thêm camera" else "Sửa camera")
            .setView(view)
            .setPositiveButton("Lưu") { _, _ ->
                val name = nameEdit.text.toString().trim()
                val ip = ipEdit.text.toString().trim()
                val port = portEdit.text.toString().toIntOrNull() ?: 554
                val user = userEdit.text.toString().trim()
                val pass = passEdit.text.toString().trim()
                val channel = chEdit.text.toString().toIntOrNull() ?: 1
                val subtype = subtypeEdit.text.toString().toIntOrNull() ?: 0

                if (name.isEmpty() || ip.isEmpty()) {
                    Toast.makeText(this, "Vui lòng nhập tên và IP", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newCamera = CameraInfo(
                    id = camera?.id ?: UUID.randomUUID().toString(),
                    name = name,
                    ip = ip,
                    port = port,
                    username = user.ifEmpty { null },
                    password = pass.ifEmpty { null },
                    channel = channel,
                    subtype = subtype
                )

                if (camera == null) {
                    CameraManager.addCamera(this, newCamera)
                } else {
                    CameraManager.updateCamera(this, newCamera)
                }
                refreshList()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun confirmDelete(camera: CameraInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Xóa camera")
            .setMessage("Bạn có chắc muốn xóa ${camera.name}?")
            .setPositiveButton("Xóa") { _, _ ->
                CameraManager.removeCamera(this, camera.id)
                refreshList()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    inner class CameraAdapter(private var items: List<CameraInfo>) :
        RecyclerView.Adapter<CameraAdapter.VH>() {

        fun updateData(newItems: List<CameraInfo>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tv_camera_name)
            val url: TextView = view.findViewById(R.id.tv_camera_url)
            val btnDefault: ImageView = view.findViewById(R.id.btn_default)
            val btnEdit: ImageView = view.findViewById(R.id.btn_edit)
            val btnDelete: ImageView = view.findViewById(R.id.btn_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_camera, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val cam = items[position]
            holder.name.text = cam.name
            holder.url.text = cam.getFullUrl()
            val isDefault = CameraManager.getDefaultCameraId(this@CameraManagementActivity) == cam.id
            holder.btnDefault.setImageResource(
                if (isDefault) android.R.drawable.star_big_on else android.R.drawable.star_big_off
            )
            holder.btnDefault.setOnClickListener {
                CameraManager.setDefaultCameraId(this@CameraManagementActivity, cam.id)
                notifyDataSetChanged()
            }
            holder.btnEdit.setOnClickListener { showAddEditDialog(cam) }
            holder.btnDelete.setOnClickListener { confirmDelete(cam) }
        }

        override fun getItemCount() = items.size
    }
}