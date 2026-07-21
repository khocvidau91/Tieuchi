package com.xiaozhi.ai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.room.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.xiaozhi.AppState
import com.xiaozhi.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

// ====================================================================
// ⚛️ QUANTUM STATES & EVENTS
// ====================================================================
sealed class AIEvent {
    data class EmotionDetected(val emotion: String, val confidence: Float) : AIEvent()
    data class SensorDataReceived(val sensorType: String, val values: FloatArray) : AIEvent()
    data class VoiceCommandRecognized(val text: String) : AIEvent()
    data class TelemetryPulse(val batteryPercent: Int, val isCharging: Boolean) : AIEvent()
    object SystemCoreOverheated : AIEvent()
    data class GlobalNotificationIntercepted(val appPackage: String, val title: String, val message: String) : AIEvent()
}

object EventBus {
    private val _quantumNeuralFlow = MutableSharedFlow<AIEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val neuralFlow = _quantumNeuralFlow.asSharedFlow()

    suspend fun emit(event: AIEvent) {
        _quantumNeuralFlow.emit(event)
    }
}

// ====================================================================
// 💾 MULTIVERSE SYNAPSE MATRIX
// ====================================================================
data class QuantumSynapse(
    val epochId: String,
    val dimension: String,
    val dataVortex: String,
    val timestamp: Long = System.currentTimeMillis()
)

object QuantumMemory {
    private const val MAX_QUANTUM_CAPACITY = 64
    private val memoryVortex = ConcurrentLinkedQueue<QuantumSynapse>()

    fun imprint(dimension: String, value: String) {
        if (memoryVortex.size >= MAX_QUANTUM_CAPACITY) {
            memoryVortex.poll()
        }
        val epochId = "EP-${Long.MAX_VALUE - System.currentTimeMillis()}"
        memoryVortex.add(QuantumSynapse(epochId, dimension, value))
    }

    fun recallChronicles(): List<QuantumSynapse> = memoryVortex.toList()
    fun collapseAllDimensions() { memoryVortex.clear() }
}

// ====================================================================
// 🔮 WAVEFUNCTION CRITICAL GATE
// ====================================================================
object WavefunctionGate {
    private var collapsedEmotion = "SINGULARITY"
    private var lastQuantumCollapse = 0L
    private var dynamicInterval = 3000L
    private const val CRITICAL_PROBABILITY = 0.80f

    @Synchronized
    fun evaluateSuperposition(emotion: String, probability: Float): Boolean {
        val now = System.currentTimeMillis()
        val timeDelta = now - lastQuantumCollapse

        dynamicInterval = if (emotion != collapsedEmotion && probability > 0.90f) {
            1000L
        } else {
            4000L
        }

        if (timeDelta < dynamicInterval) return false

        if (probability >= CRITICAL_PROBABILITY) {
            collapsedEmotion = emotion
            lastQuantumCollapse = now
            return true
        }
        return false
    }
}

// ====================================================================
// 📡 MCP NOTIFICATION MANAGER
// ====================================================================
object McpNotificationManager {
    fun sendNotification(method: String, params: JsonObject? = null) {
        val ws = MainActivity.instance.wsManager ?: return
        if (!ws.isConnected || ws.sessionId == null) return
        val payload = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", method)
            if (params != null) {
                if (!params.has("session_id") && ws.sessionId != null) params.addProperty("session_id", ws.sessionId)
                add("params", params)
            }
        }
        ws.sendText(payload.toString())
    }
}

// ========================== App Classifier ==========================
data class AppInfo(val name: String, val packageName: String, var category: String = "other", var userPreference: Int = 0)

class AppClassifier private constructor(context: Context) {
    companion object {
        private const val PREFS_NAME = "app_classifier"
        private const val KEY_PREF_PREFIX = "pref_"
        @Volatile private var instance: AppClassifier? = null
        fun getInstance(context: Context): AppClassifier {
            return instance ?: synchronized(this) {
                instance ?: AppClassifier(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appCache = mutableMapOf<String, AppInfo>()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val pm = context.packageManager

    init {
        val allPrefs = prefs.all
        for ((key, value) in allPrefs) {
            if (key.startsWith(KEY_PREF_PREFIX)) {
                val packageName = key.removePrefix(KEY_PREF_PREFIX)
                val pref = (value as? Int) ?: 0
                appCache[packageName] = AppInfo("", packageName, "other", pref)
            }
        }
    }

    fun getAppInfo(packageName: String): AppInfo {
        return appCache[packageName] ?: run {
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                packageName
            }
            val category = guessInitialCategory(packageName, label)
            val info = AppInfo(label, packageName, category, 0)
            appCache[packageName] = info
            info
        }
    }

    private fun guessInitialCategory(packageName: String, appName: String): String {
        val combined = "$packageName $appName".lowercase(Locale.getDefault())
        return when {
            combined.contains("systemui") || combined.contains("android") || combined.contains("com.android") -> "system"
            combined.contains("sms") || combined.contains("messenger") || combined.contains("zalo") || combined.contains("telegram") || combined.contains("whatsapp") -> "social"
            combined.contains("gmail") || combined.contains("outlook") || combined.contains("mail") || combined.contains("calendar") || combined.contains("meet") || combined.contains("teams") || combined.contains("slack") -> "work"
            combined.contains("camera") || combined.contains("imou") || combined.contains("ring") || combined.contains("smartlife") -> "security"
            combined.contains("shopee") || combined.contains("lazada") || combined.contains("tiki") || combined.contains("sendo") -> "shopping"
            combined.contains("game") || combined.contains("pubg") || combined.contains("freefire") || combined.contains("roblox") -> "game"
            combined.contains("tiktok") || combined.contains("facebook") || combined.contains("instagram") || combined.contains("youtube") -> "social_media"
            else -> "other"
        }
    }

    fun updateUserPreference(packageName: String, isRead: Boolean) {
        val info = getAppInfo(packageName)
        val newPref = if (isRead) info.userPreference + 1 else info.userPreference - 1
        info.userPreference = newPref.coerceIn(-10, 10)
        prefs.edit().putInt(KEY_PREF_PREFIX + packageName, info.userPreference).apply()
    }

    fun getPriority(packageName: String): Int {
        val info = getAppInfo(packageName)
        val base = when (info.category) {
            "system" -> -5
            "security" -> 3
            "work" -> 2
            "social" -> 2
            "social_media" -> 1
            "shopping" -> 0
            "game" -> 0
            else -> 1
        }
        val userBoost = info.userPreference.coerceIn(-3, 5)
        return (base + userBoost).coerceIn(-5, 5)
    }

    fun shouldRead(packageName: String): Boolean {
        val info = getAppInfo(packageName)
        if (info.category == "system") return false
        if (info.userPreference <= -5) return false
        return getPriority(packageName) >= 1
    }

    fun getAppName(packageName: String): String = getAppInfo(packageName).name
}

// ====================================================================
// 📦 ROOM ENTITY, DAO, DATABASE
// ====================================================================

@Entity(tableName = "smart_notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "sender") val sender: String = "",
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "priority") val priority: Int = 1,
    @ColumnInfo(name = "category") val category: String = "other",
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    @ColumnInfo(name = "need_reply") val needReply: Boolean = false,
    @ColumnInfo(name = "emotion") val emotion: String = "neutral",
    @ColumnInfo(name = "ignored") val ignored: Boolean = false,
    @ColumnInfo(name = "read_count") val readCount: Int = 0,
    @ColumnInfo(name = "group_count") val groupCount: Int = 1
)

@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(notification: NotificationEntity)

    @Query("SELECT * FROM smart_notifications WHERE timestamp > :since AND priority >= :minPriority AND is_read = 0 AND ignored = 0 ORDER BY timestamp DESC")
    suspend fun getImportantSince(since: Long, minPriority: Int = 1): List<NotificationEntity>

    @Query("DELETE FROM smart_notifications WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("UPDATE smart_notifications SET is_read = 1 WHERE package_name = :packageName")
    suspend fun markReadByPackage(packageName: String)
}

@Database(entities = [NotificationEntity::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notifications.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ========================== DTO cho UI ==========================
data class NotificationItem(
    val id: Long,
    val app: String,
    val sender: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val priority: Int,
    val isRead: Boolean,
    val ignored: Boolean = false,
    val groupCount: Int = 1
)

// ========================== TTS Manager ==========================
class TtsManager(context: Context) {
    private var tts: TextToSpeech? = null
    @Volatile var isReady = false
        private set
    private val queue = ConcurrentLinkedQueue<String>()
    private var isSpeaking = false
    private val lock = Any()
    private val isShutdown = AtomicBoolean(false)

    init {
        tts = TextToSpeech(context) { status ->
            if (isShutdown.get()) return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("vi", "VN")
                isReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        synchronized(lock) { isSpeaking = false; processNext() }
                    }
                    override fun onError(utteranceId: String?) {
                        synchronized(lock) { isSpeaking = false; processNext() }
                    }
                })
                processNext()
                Log.i("TTS", "✅ TTS engine ready (Vietnamese)")
            } else {
                Log.e("TTS", "❌ TTS initialization failed with status: $status")
            }
        }
    }

    private fun processNext() {
        synchronized(lock) {
            if (isShutdown.get()) return
            if (isSpeaking) return
            val next = queue.poll() ?: return
            if (tts == null || !isReady) {
                if (!isShutdown.get()) {
                    queue.add(next)
                }
                return
            }
            isSpeaking = true
            try {
                tts?.speak(next, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
                Log.d("TTS", "🔊 $next")
            } catch (e: Exception) {
                Log.e("TTS", "speak failed", e)
                isSpeaking = false
                processNext()
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (isShutdown.get()) {
            Log.w("TTS", "TTS already shutdown, ignoring speak")
            return
        }
        synchronized(lock) {
            queue.add(text)
            if (isReady && !isSpeaking) {
                processNext()
            }
        }
    }

    fun shutdown() {
        isShutdown.set(true)
        synchronized(lock) {
            queue.clear()
            isSpeaking = false
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        Log.i("TTS", "TTS shutdown")
    }
}

// ========================== Sender Manager ==========================
data class SenderProfile(
    val name: String,
    var importance: Int = 1,
    var lastMessageTime: Long = 0,
    var messageCount: Int = 0,
    var readCount: Int = 0,
    var ignoredCount: Int = 0
)

class SenderManager {
    private val senders = mutableMapOf<String, SenderProfile>()
    private val knownFamily = setOf("mẹ", "bố", "anh", "chị", "em", "con", "bà", "ông", "vợ", "chồng")

    fun extractSender(title: String, text: String, app: String): String {
        val full = "$title $text"
        val patterns = listOf(
            Regex("^([A-Za-zÀ-ỹ]+\\s*[A-Za-zÀ-ỹ]*)\\s*[:：]"),
            Regex("^([A-Za-zÀ-ỹ]+\\s*[A-Za-zÀ-ỹ]*)\\s*[-–—]"),
            Regex("([A-Za-zÀ-ỹ]+\\s*[A-Za-zÀ-ỹ]*)\\s*(đã gửi|gửi|nhắn)"),
            Regex("Tin nhắn từ\\s+([A-Za-zÀ-ỹ]+\\s*[A-Za-zÀ-ỹ]*)")
        )
        for (pattern in patterns) {
            val match = pattern.find(full)
            if (match != null) {
                val sender = match.groupValues[1].trim()
                if (sender.length in 2..30 && !sender.contains(" ")) {
                    return sender
                }
            }
        }
        if (title.length in 2..25 && !title.contains(" ") && !title.contains(".") && !title.contains("@")) {
            return title
        }
        return "Ai đó"
    }

    fun getImportance(sender: String): Int {
        val profile = senders[sender]
        if (profile != null) {
            return when {
                profile.importance >= 3 -> 3
                profile.readCount > profile.ignoredCount + 2 -> 2
                profile.ignoredCount > 3 -> 0
                else -> profile.importance
            }
        }
        val lowerSender = sender.lowercase(Locale.getDefault())
        return when {
            knownFamily.any { lowerSender.contains(it) } -> 3
            lowerSender.matches(Regex("^[0-9]{10,11}$")) -> 1
            else -> 1
        }
    }

    fun updateInteraction(sender: String, isRead: Boolean, isIgnored: Boolean = false) {
        val profile = senders.getOrPut(sender) { SenderProfile(sender) }
        profile.lastMessageTime = System.currentTimeMillis()
        profile.messageCount++
        if (isRead) {
            profile.readCount++
            if (profile.readCount >= 3 && profile.importance < 2) profile.importance = 2
            if (profile.readCount >= 10 && profile.importance < 3) profile.importance = 3
        }
        if (isIgnored) {
            profile.ignoredCount++
            if (profile.ignoredCount >= 3 && profile.importance > 0) profile.importance--
            if (profile.ignoredCount >= 5 && profile.importance > 0) profile.importance = 0
        }
    }
}

// ====================================================================
// 🧠 THE QUANTUM BRAIN CORE
// ====================================================================
class Brain(
    private val onEmotionStable: ((emotion: String, confidence: Float) -> Unit)? = null,
    private val context: Context? = null
) {
    private val brainScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("QuantumBrainCore"))
    private val isBrainActive = AtomicBoolean(false)

    private val _quantumEnergyFlow = MutableStateFlow(100)
    val quantumEnergyFlow = _quantumEnergyFlow.asStateFlow()

    private var lastBatteryLevel = -1
    private var lastChargingState = false

    // Thành phần xử lý thông báo
    private val db: AppDatabase? = context?.let { AppDatabase.getInstance(it) }
    private var ttsManager: TtsManager? = null
    private lateinit var appClassifier: AppClassifier
    private val senderManager = SenderManager()
    private val mutex = Any()

    private val processedMessageKeys = ConcurrentHashMap<String, Long>()
    private val duplicateWindowMs = 2000L
    private val systemDuplicateWindowMs = 300_000L

    private val packageLastReadTime = ConcurrentHashMap<String, Long>()
    private val packageCooldownMs = 30_000L

    data class PendingMessageGroup(
        val app: String,
        val packageName: String,
        val sender: String,
        var lastTitle: String,
        var lastText: String,
        var timestamp: Long,
        var count: Int
    )
    private val pendingGroups = ConcurrentHashMap<String, PendingMessageGroup>()
    private val groupIntervalMs = 3000L

    init {
        context?.let {
            ttsManager = TtsManager(it)
            appClassifier = AppClassifier.getInstance(it)
        }
        brainScope.launch {
            while (isActive) {
                delay(2000)
                flushPendingGroups()
            }
        }
        brainScope.launch {
            while (isActive) {
                delay(10_000)
                val now = System.currentTimeMillis()
                processedMessageKeys.entries.removeIf { now - it.value > duplicateWindowMs }
                packageLastReadTime.entries.removeIf { now - it.value > packageCooldownMs }
            }
        }
    }

    private fun isDuplicate(packageName: String, title: String, text: String): Boolean {
        val cleanTitle = title.replace(Regex("\\d+"), "")
        val cleanText = text.replace(Regex("\\d+"), "")
        val key = "$packageName|$cleanTitle|$cleanText"
        val now = System.currentTimeMillis()
        val lastTime = processedMessageKeys[key]
        val isSystem = packageName.contains("system", ignoreCase = true) ||
                       packageName.contains("android", ignoreCase = true) ||
                       packageName.contains("com.android", ignoreCase = true)
        val timeout = if (isSystem) systemDuplicateWindowMs else duplicateWindowMs
        if (lastTime != null && now - lastTime < timeout) {
            return true
        }
        processedMessageKeys[key] = now
        return false
    }

    private fun isPackageOnCooldown(packageName: String): Boolean {
        val lastTime = packageLastReadTime[packageName]
        val now = System.currentTimeMillis()
        return lastTime != null && now - lastTime < packageCooldownMs
    }

    private fun markPackageRead(packageName: String) {
        packageLastReadTime[packageName] = System.currentTimeMillis()
    }

    // ===== FLUSH PENDING GROUPS (đã sửa + thêm toggle toàn cục) =====
    private fun flushPendingGroups() {
        if (pendingGroups.isEmpty()) return

        // 🔥 KIỂM TRA CỜ TOÀN CỤC
        val ctx = context
        val globalEnabled = ctx?.let { AppState.isNotificationReadingEnabled(it) } ?: true

        if (!globalEnabled) {
            // Nếu tắt, vẫn lưu vào DB nhưng KHÔNG đọc
            val groupsToSave = pendingGroups.toMap()
            pendingGroups.clear()
            for ((_, group) in groupsToSave) {
                saveToDatabase(
                    group.app, group.packageName, group.sender,
                    group.lastTitle, group.lastText, group.timestamp,
                    priority = 1, groupCount = group.count, ignored = true
                )
            }
            return
        }

        // Phần xử lý cũ (giữ nguyên)
        val groupsToSend = pendingGroups.toMap()
        pendingGroups.clear()
        for ((_, group) in groupsToSend) {
            val appName = group.app
            val packageName = group.packageName
            val sender = group.sender
            if (isPackageOnCooldown(packageName)) continue
            val userPref = if (::appClassifier.isInitialized) appClassifier.getPriority(packageName) else 1
            val analysis = analyzeContent(packageName, group.lastTitle, group.lastText, sender)
            val finalPriority = (userPref + analysis.priorityBoost).coerceIn(0, 5)

            // Kiểm tra whitelist
            val isInWhitelist = ctx?.let { AppState.isNotificationEnabledForApp(it, packageName) } ?: false
            val shouldReadNow = analysis.forceRead || isInWhitelist

            if (!shouldReadNow) {
                saveToDatabase(appName, packageName, sender, group.lastTitle, group.lastText, group.timestamp, finalPriority, group.count, ignored = true)
                continue
            }

            val speakText = buildSmartMessage(appName, group, analysis, finalPriority)
            if (speakText.isBlank()) continue
            Log.d("Brain", "✅ Đọc: ${analysis.reason} -> $speakText")
            markPackageRead(packageName)
            saveToDatabase(appName, packageName, sender, group.lastTitle, group.lastText, group.timestamp, finalPriority, group.count)
            if (sender.isNotEmpty()) {
                senderManager.updateInteraction(sender, isRead = true)
            }
            ttsManager?.speak(speakText)
            sendToServer(appName, packageName, group, finalPriority, sender)
        }
    }

    private fun extractCaller(title: String, text: String): String {
        var raw = title.replace(Regex("^Cuộc gọi[:\\s]*"), "").trim()
        if (raw.isEmpty()) {
            raw = text.replace(Regex("^Cuộc gọi[:\\s]*"), "").trim()
        }
        if (raw.isEmpty()) return "số lạ"
        raw = raw.replace(Regex("[-–—]$"), "").trim()
        if (raw.matches(Regex("^[0-9]{10,11}$"))) {
            return "số $raw"
        }
        return raw
    }

    private fun analyzeContent(packageName: String, title: String, text: String, sender: String): ContentAnalysis {
        val full = "$title $text".lowercase(Locale.getDefault())
        val isBatteryStart = full.contains("đã bắt đầu sạc") || full.contains("bắt đầu sạc") || full.contains("sạc siêu nhanh")
        val isBatteryLow = full.contains("pin yếu") || (full.contains("pin") && full.contains("%") && full.contains("còn lại") && !full.contains("sạc"))
        val isBatteryFull = full.contains("pin đã đầy") || full.contains("sạc đầy") || (full.contains("100%") && full.contains("pin"))
        if (isBatteryStart) {
            return ContentAnalysis(true, "Bắt đầu sạc", 1, sender)
        }
        if (isBatteryLow) {
            return ContentAnalysis(true, "Pin yếu", 3, sender)
        }
        if (isBatteryFull) {
            return ContentAnalysis(true, "Pin đầy", 2, sender)
        }
        val isChargingUpdate = full.contains("đang sạc") && full.contains("%") && full.contains("phút nữa sẽ đầy")
        if (isChargingUpdate) {
            return ContentAnalysis(false, "Cập nhật pin (bỏ qua)", -10, sender)
        }
        val isSpam = packageName.contains("systemui", ignoreCase = true) ||
                     full.contains("thông báo khác") ||
                     full.contains("dịch vụ xây dựng gradle") ||
                     full.contains("xây dựng thành công") ||
                     full.contains("xây dựng thất bại") ||
                     full.contains("đang xây dựng") ||
                     full.contains("đang chạy ngầm") ||
                     (packageName.contains("android") && full.contains("cập nhật"))
        if (isSpam) {
            return ContentAnalysis(false, "Thông báo hệ thống (Bỏ qua)", -10, sender)
        }
        val isCall = packageName.contains("dialer") || packageName.contains("phone") ||
                     title.contains("Cuộc gọi") || text.contains("Cuộc gọi") ||
                     full.contains("incoming call") || full.contains("cuộc gọi đến") ||
                     full.contains("đang gọi")
        if (isCall) {
            val caller = extractCaller(title, text)
            return ContentAnalysis(true, "Cuộc gọi đến", 3, caller, needReply = false)
        }
        if (full.contains("otp") || full.contains("mã xác thực") || full.contains("verification code")) {
            return ContentAnalysis(true, "Mã OTP", 1, sender)
        }
        if (full.contains("gấp") || full.contains("khẩn") || full.contains("urgent") ||
            full.contains("deadline") || full.contains("cháy") || full.contains("cứu")) {
            return ContentAnalysis(true, "Khẩn cấp", 2, sender)
        }
        if (full.contains("?") || full.contains("không") || full.contains("chưa") || full.contains("khi nào")) {
            return ContentAnalysis(true, "Cần phản hồi", 1, sender, needReply = true)
        }
        return ContentAnalysis(true, "Thông thường", 0, sender)
    }

    private fun buildSmartMessage(appName: String, group: PendingMessageGroup, analysis: ContentAnalysis, priority: Int): String {
        val count = group.count
        val sender = group.sender
        if (count == 1) {
            return when {
                analysis.reason == "Bắt đầu sạc" -> {
                    val percent = group.lastText.replace(Regex("[^0-9]"), "").take(2)
                    "Đã cắm sạc, pin hiện tại ${percent}%"
                }
                analysis.reason == "Pin yếu" -> {
                    val percent = group.lastText.replace(Regex("[^0-9]"), "").take(2)
                    "Pin yếu, chỉ còn ${percent}%, hãy sạc pin"
                }
                analysis.reason == "Pin đầy" -> "Pin đã đầy, có thể rút sạc"
                analysis.reason == "Cuộc gọi đến" -> {
                    if (analysis.sender == "số lạ") "Có một cuộc gọi nhỡ từ số lạ"
                    else "Cuộc gọi từ ${analysis.sender}"
                }
                analysis.reason == "Mã OTP" -> "Mã xác thực của bạn: ${group.lastTitle} ${group.lastText}".take(100)
                analysis.reason == "Khẩn cấp" -> "Tin nhắn khẩn cấp từ ${sender.takeIf { it.isNotEmpty() } ?: appName}: ${group.lastTitle}. ${group.lastText}"
                analysis.needReply -> "Tin nhắn cần phản hồi từ ${sender.takeIf { it.isNotEmpty() } ?: appName}: ${group.lastTitle}. ${group.lastText}"
                sender.isNotEmpty() -> "$sender vừa nhắn trên $appName: ${group.lastTitle}. ${group.lastText}"
                else -> "Bạn có 1 tin nhắn mới trên $appName: ${group.lastTitle}. ${group.lastText}"
            }
        }
        return if (sender.isNotEmpty()) {
            "Bạn có $count tin nhắn mới từ $sender trên $appName"
        } else {
            "Bạn có $count tin nhắn mới trên $appName"
        }
    }

    // ===== ROOM DATABASE OPERATIONS =====
    private fun saveToDatabase(
        appName: String,
        packageName: String,
        sender: String,
        title: String,
        text: String,
        timestamp: Long,
        priority: Int = 1,
        groupCount: Int = 1,
        ignored: Boolean = false
    ) {
        val (category, needReply) = classifyNotification(title, text, packageName)
        val emotion = detectEmotion(title, text)
        CoroutineScope(Dispatchers.IO).launch {
            val dbInstance = db ?: return@launch
            val entity = NotificationEntity(
                appName = appName,
                packageName = packageName,
                title = title,
                text = text,
                sender = sender,
                timestamp = timestamp,
                priority = priority,
                category = category,
                needReply = needReply,
                emotion = emotion,
                ignored = ignored,
                groupCount = groupCount
            )
            dbInstance.notificationDao().insert(entity)
        }
    }

    suspend fun getImportantNotificationsSince(since: Long): List<NotificationItem> = withContext(Dispatchers.IO) {
        val dbInstance = db ?: return@withContext emptyList()
        val entities = dbInstance.notificationDao().getImportantSince(since, 1)
        entities.map { entity ->
            NotificationItem(
                id = entity.id,
                app = entity.appName,
                sender = entity.sender,
                title = entity.title,
                text = entity.text,
                timestamp = entity.timestamp,
                priority = entity.priority,
                isRead = entity.isRead,
                ignored = entity.ignored,
                groupCount = entity.groupCount
            )
        }
    }

    fun cleanOldNotifications(days: Int) {
        val cutoff = System.currentTimeMillis() - days * 24L * 3600 * 1000
        CoroutineScope(Dispatchers.IO).launch {
            db?.notificationDao()?.deleteOlderThan(cutoff)
        }
    }

    fun markUserRead(packageName: String) {
        if (::appClassifier.isInitialized) appClassifier.updateUserPreference(packageName, isRead = true)
        CoroutineScope(Dispatchers.IO).launch {
            db?.notificationDao()?.markReadByPackage(packageName)
        }
    }

    fun markUserIgnored(packageName: String) {
        if (::appClassifier.isInitialized) appClassifier.updateUserPreference(packageName, isRead = false)
    }

    private fun sendToServer(appName: String, packageName: String, group: PendingMessageGroup, priority: Int, sender: String) {
        val ws = MainActivity.instance.wsManager ?: return
        if (ws.isConnected && ws.sessionId != null) {
            val params = JsonObject().apply {
                addProperty("app", appName)
                addProperty("package", packageName)
                addProperty("sender", sender)
                addProperty("count", group.count)
                addProperty("priority", priority)
                addProperty("title", group.lastTitle)
                addProperty("text", group.lastText)
                addProperty("timestamp", group.timestamp)
            }
            val mcpMessage = JsonObject().apply {
                addProperty("type", "mcp")
                add("payload", JsonObject().apply {
                    addProperty("jsonrpc", "2.0")
                    addProperty("method", "notifications/new_notification")
                    add("params", params)
                })
                addProperty("session_id", ws.sessionId)
            }
            ws.sendText(mcpMessage.toString())
        }
    }

    private fun classifyNotification(title: String, text: String, packageName: String): Pair<String, Boolean> {
        val full = "$title $text".lowercase(Locale.getDefault())
        return when {
            packageName.contains("zalo") || packageName.contains("messenger") -> "social" to true
            packageName.contains("gmail") || packageName.contains("outlook") -> "work" to false
            packageName.contains("imou") || packageName.contains("camera") -> "security" to false
            full.contains("otp") -> "otp" to false
            else -> "other" to false
        }
    }

    private fun detectEmotion(title: String, text: String): String {
        val full = "$title $text".lowercase(Locale.getDefault())
        return when {
            full.contains("cảm ơn") -> "grateful"
            full.contains("buồn") -> "sad"
            full.contains("vui") -> "happy"
            else -> "neutral"
        }
    }

    // ========== Core Brain Functions ==========
    fun start() {
        if (!isBrainActive.compareAndSet(false, true)) return
        Log.i("🧠 Lượng Tử Core", "🌌 KHỞI CHẠY LÕI NÃO LƯỢNG TỬ ĐA VŨ TRỤ...")
        brainScope.launch {
            EventBus.neuralFlow.collect { event ->
                when (event) {
                    is AIEvent.EmotionDetected -> collapseEmotionWavefunction(event.emotion, event.confidence)
                    is AIEvent.SensorDataReceived -> fuseQuantumSensors(event.sensorType, event.values)
                    is AIEvent.VoiceCommandRecognized -> interpretHighOrderLinguistics(event.text)
                    is AIEvent.TelemetryPulse -> balanceEnergySingularity(event.batteryPercent, event.isCharging)
                    is AIEvent.SystemCoreOverheated -> {
                        Log.w("🧠 Lượng Tử Core", "🚨 Hệ thống quá nhiệt lượng tử!")
                        QuantumMemory.collapseAllDimensions()
                    }
                    is AIEvent.GlobalNotificationIntercepted -> handleNotification(event)
                }
            }
        }
    }

    private fun handleNotification(event: AIEvent.GlobalNotificationIntercepted) {
        val ctx = context ?: return
        if (isDuplicate(event.appPackage, event.title, event.message)) return
        var sender = ""
        if (event.appPackage.contains("zalo", ignoreCase = true) ||
            event.appPackage.contains("messenger", ignoreCase = true) ||
            event.appPackage.contains("whatsapp", ignoreCase = true)) {
            sender = senderManager.extractSender(event.title, event.message, event.appPackage)
        }
        val key = "${event.appPackage}|$sender"
        val now = System.currentTimeMillis()
        synchronized(pendingGroups) {
            val existing = pendingGroups[key]
            if (existing != null && (now - existing.timestamp) < groupIntervalMs) {
                existing.count++
                existing.lastTitle = event.title
                existing.lastText = event.message
                existing.timestamp = now
            } else {
                pendingGroups[key] = PendingMessageGroup(
                    app = getAppName(event.appPackage),
                    packageName = event.appPackage,
                    sender = sender,
                    lastTitle = event.title,
                    lastText = event.message,
                    timestamp = now,
                    count = 1
                )
            }
        }
    }

    private fun getAppName(packageName: String): String {
        return if (::appClassifier.isInitialized) appClassifier.getAppName(packageName) else packageName
    }

    private fun collapseEmotionWavefunction(emotion: String, confidence: Float) {
        Log.v("🧠 Lượng Tử", "Đang quét ma trận xác suất cảm xúc: $emotion (${confidence * 100}%)")
        if (WavefunctionGate.evaluateSuperposition(emotion, confidence)) {
            Log.d("🧠 Lượng Tử", "🔮 Sụp đổ sóng nhận thức thành công: [$emotion]")
            QuantumMemory.imprint("QUANTUM_EMOTION", emotion)
            val mentalContext = JsonArray()
            QuantumMemory.recallChronicles().takeLast(5).forEach {
                mentalContext.add("[Dim:${it.dimension}]->${it.dataVortex}")
            }
            McpNotificationManager.sendNotification("notifications/emotion_changed", JsonObject().apply {
                addProperty("emotion", emotion)
                addProperty("confidence", confidence)
                addProperty("quantum_matrix_status", "ENTANGLED")
                add("quantum_mental_context", mentalContext)
            })
            onEmotionStable?.invoke(emotion, confidence)
        }
    }

    private fun fuseQuantumSensors(type: String, values: FloatArray) {
        Log.v("🧠 Cảm Biến", "Hợp nhất xung vật lý: $type -> [${values.joinToString()}]")
        if (type == "GYROSCOPE" && values.isNotEmpty() && values[0] > 9.0f) {
            QuantumMemory.imprint("PHYSICAL_DISTURBANCE", "HYPER_WARP_SHAKE")
        }
    }

    private fun interpretHighOrderLinguistics(command: String) {
        Log.i("🧠 Ngôn Ngữ", "Giải mã cấu trúc lệnh thoại vũ trụ: '$command'")
        QuantumMemory.imprint("VOICE_COMMAND", command)
    }

    private fun balanceEnergySingularity(percent: Int, charging: Boolean) {
        _quantumEnergyFlow.value = percent
        if (percent != lastBatteryLevel || charging != lastChargingState) {
            Log.d("🧠 Năng Lượng", "🔋 Trạng thái năng lượng Lượng tử: $percent% | Đang nạp: $charging")
            McpNotificationManager.sendNotification("notifications/battery_low", JsonObject().apply {
                addProperty("percent", percent)
                addProperty("quantum_efficiency", if (charging) "OVERCLOCKED" else "STABLE")
            })
            lastBatteryLevel = percent
            lastChargingState = charging
        }
    }

    fun monitorStarlightCore(context: Context) {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)
        batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            if (pct >= 0) {
                brainScope.launch { EventBus.emit(AIEvent.TelemetryPulse(pct, isCharging)) }
            }
        }
    }

    fun shutdownTts() {
        ttsManager?.shutdown()
        Log.w("🧠 Lượng Tử Core", "💤 Đóng các kênh phát âm thanh, chuyển lõi não về trạng thái ngủ đông")
    }

    fun cancel() {
        if (isBrainActive.compareAndSet(true, false)) {
            Log.w("🧠 Lượng Tử Core", "💥 Kích hoạt lệnh tự hủy...")
            QuantumMemory.collapseAllDimensions()
            brainScope.cancel()
        }
    }

    private data class ContentAnalysis(val forceRead: Boolean, val reason: String, val priorityBoost: Int, val sender: String, val needReply: Boolean = false)
}