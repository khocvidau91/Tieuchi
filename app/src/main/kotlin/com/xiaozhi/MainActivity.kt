package com.xiaozhi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.request.ImageRequest
import coil.ImageLoader
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.gson.JsonObject
import com.xiaozhi.ai.Brain
import com.xiaozhi.services.FileWatcherService
import com.xiaozhi.ui.theme.XiaoZhiTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

// ========================== UI State ==========================
enum class AIState {
    IDLE, LISTENING, SPEAKING, PROCESSING
}

data class MainUiState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isMenuOpen: Boolean = false,
    val currentEmotion: String = "neutral",
    val isAlbumArtVisible: Boolean = false,
    val albumArtUrl: String? = null,
    val currentSongTitle: String = "",
    val isVideoVisible: Boolean = false,
    val isActivationCardVisible: Boolean = false,
    val activationPinCode: String = "------",
    val aiState: AIState = AIState.IDLE,
    val batteryPercent: Int = 100
)

// ========================== Main Activity ==========================
class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_PERMISSIONS = 100
        private val PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        private const val MAX_IMAGE_WIDTH = 1024
        private const val JPEG_QUALITY = 75
        private const val VOICE_TIMEOUT_MS = 30_000L

        lateinit var instance: MainActivity
            private set
    }

    // Core components
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    var wsManager: WebSocketManager? = null
    internal set
    private var audioManager: XiaoZhiAudioManager? = null
    private var otaClient: OtaClient? = null
    private var mcpHandler: McpHandler? = null
    private lateinit var videoPlayerManager: VideoPlayerManager
    private lateinit var playerView: PlayerView
    private lateinit var previewViewForEye: PreviewView
    private var eyeManager: EyeManager? = null
    private var sensorFusion: SensorFusion? = null
    private lateinit var brain: Brain
    private lateinit var systemController: SystemController

    // State machine
    private var facePresent = false
    private var micActive = false
    private var voiceTimeout = false
    private var voiceTimeoutRunnable: Runnable? = null
    private var lastMicOffTime = 0L

    private var deviceId: String? = null
    private var clientId: String? = null
    private var activated: Boolean = false
    @Volatile var isMusicPlaying: Boolean = false
    @Volatile var isVideoPlaying: Boolean = false
    @Volatile var isSpeaking: Boolean = false
    private var autoListening: Boolean = true
    private var waitingForResponse: Boolean = false
    private var lastUserMessage: String? = null
    private var pendingTextStt: Boolean = false
    private var lastServerResponseTime = 0L
    private var audioStartTimestamp = 0L

    private var pendingOpenOverlay = false
    private var pendingOverlayMode = 0
    private var pendingStartAudioAfterReconnect = false

    private lateinit var googleSignInClient: GoogleSignInClient
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private var isWakeWordEnabled = false
    private var isAppInitialized = false
    private var isForeground = false

    private var uiState by mutableStateOf(MainUiState())
    private fun updateUiState(block: MainUiState.() -> MainUiState) {
        uiState = uiState.block()
    }

    private var lastSentMessage = ""
    private var lastSentTime = 0L
    private val DEBOUNCE_MS = 500L

    // Waveform data từ Visualizer
    private val _waveformAmplitudes = MutableStateFlow<List<Float>>(emptyList())
    val waveformAmplitudes: StateFlow<List<Float>> = _waveformAmplitudes.asStateFlow()

    // ========== VÒNG LẶP ĐIỀU KHIỂN TRUNG TÂM ==========
    private val stateMonitorRunnable = object : Runnable {
        override fun run() {
            if (!isForeground) {
                mainHandler.postDelayed(this, 1000)
                return
            }

            autoListening = AppState.isAutoListeningEnabled(this@MainActivity)
            facePresent = eyeManager?.isFacePresent() ?: false

            Log.d("STATE_MONITOR", "facePresent=$facePresent, micActive=$micActive, isSpeaking=$isSpeaking, " +
                    "music=$isMusicPlaying, video=$isVideoPlaying, auto=$autoListening")

            if (isSpeaking || isMusicPlaying || isVideoPlaying) {
                mainHandler.postDelayed(this, 1000)
                return
            }

            if (facePresent && !micActive && autoListening) {
                val now = System.currentTimeMillis()
                if (now - lastMicOffTime > 2000) {
                    Log.d("STATE_MONITOR", "Face present and mic off -> start mic")
                    startSendingAudioToServer()
                } else {
                    Log.d("STATE_MONITOR", "Mic just turned off, waiting before re-enabling")
                }
            }

            mainHandler.postDelayed(this, 1000)
        }
    }

    // ========== CÁC REQUEST & RECEIVER ==========
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
        }
    }

    private val captureImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val imagePath = result.data?.getStringExtra("image_path")
            if (imagePath != null) {
                mcpHandler?.onCaptureResult(true, imagePath)
            } else {
                mcpHandler?.onCaptureResult(false, "")
            }
        } else {
            mcpHandler?.onCaptureResult(false, "")
        }
    }

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            AppState.setAvatarUri(this, it.toString())
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                firebaseAuth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        val user = firebaseAuth.currentUser
                        user?.let {
                            AppState.setLoggedIn(this, true)
                            AppState.setUserName(this, it.displayName)
                            AppState.setUserEmail(this, it.email)
                            AppState.setUserAvatar(this, it.photoUrl?.toString())
                        }
                        sendUserInfo()
                        Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, getString(R.string.login_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Google sign in failed", e)
                Toast.makeText(this, getString(R.string.login_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val overlaySendTextReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra("text") ?: return
            updateUiState { copy(inputText = text) }
            sendTextMessage()
        }
    }

    private val musicStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isMusicPlaying = false
            isSpeaking = false
        }
    }

    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Wake word detected")
            if (!micActive && !isSpeaking && !isMusicPlaying && !isVideoPlaying) {
                startSendingAudioToServer()
            }
        }
    }

    private val keyboardReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { }
    }

    private val waveformReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MusicPlaybackService.ACTION_WAVEFORM) {
                val array = intent.getFloatArrayExtra(MusicPlaybackService.EXTRA_WAVEFORM)
                if (array != null) {
                    _waveformAmplitudes.value = array.toList()
                }
            }
        }
    }

    private val requestRecordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Quyền ghi âm được cấp, sóng nhạc sẽ hoạt động", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Không thể hiển thị sóng nhạc do thiếu quyền ghi âm", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        Log.d(TAG, "onCreate started")
        setStatus(getString(R.string.status_ready))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = AndroidColor.TRANSPARENT
            navigationBarColor = AndroidColor.parseColor("#0B0E14")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
            }
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> { /* ok */ }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        systemController = SystemController(this)

        previewViewForEye = PreviewView(this).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            visibility = PreviewView.INVISIBLE
        }
        eyeManager = EyeManager(this, previewViewForEye).apply {
            bindToLifecycle(lifecycle)
        }

        eyeManager?.onFacePresenceChanged = { isLooking, trackingId ->
            Log.d("FACE_DEBUG", "isLooking=$isLooking, trackingId=$trackingId")
            facePresent = isLooking
        }

        eyeManager?.onBlinkDetected = {
            wsManager?.sendText(JsonObject().apply {
                addProperty("type", "blink")
                addProperty("session_id", wsManager?.sessionId ?: "")
            }.toString())
        }
        eyeManager?.onNewFaceAppeared = { trackingId ->
            Log.d(TAG, getString(R.string.new_face) + ": $trackingId")
        }

        playerView = PlayerView(this)
        videoPlayerManager = VideoPlayerManager(this, playerView)
        videoPlayerManager.onPlaybackEnded = {
            updateUiState { copy(isVideoVisible = false) }
        }

        sensorFusion = SensorFusion(this)
        sensorFusion?.onSensorDataReady = { sensorJson ->
            val ws = wsManager
            if (ws != null && ws.isConnected) {
                ws.sessionId?.let { sensorJson.addProperty("session_id", it) }
                ws.sendText(sensorJson.toString())
            }
        }

        brain = Brain(
            onEmotionStable = { emotion, confidence ->
                runOnMainThread { setStatus("😊 $emotion (${(confidence*100).toInt()}%)") }
            },
            context = this
        )
        brain.start()
        brain.monitorStarlightCore(this)

        lifecycleScope.launch {
            brain.quantumEnergyFlow.collect { percent ->
                updateUiState { copy(batteryPercent = percent) }
            }
        }

        startService(Intent(this, FileWatcherService::class.java))

        audioManager = (application as MyApplication).audioManager

        isWakeWordEnabled = AppState.isWakeWordEnabled(this)
        if (isWakeWordEnabled) startHotwordService()

        autoListening = AppState.isAutoListeningEnabled(this)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            wakeWordReceiver,
            IntentFilter("com.xiaozhi.WAKE_WORD_TRIGGERED")
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            keyboardReceiver,
            IntentFilter("com.xiaozhi.OPEN_KEYBOARD")
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            overlaySendTextReceiver,
            IntentFilter("com.xiaozhi.OVERLAY_SEND_TEXT")
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            waveformReceiver,
            IntentFilter(MusicPlaybackService.ACTION_WAVEFORM)
        )

        if (hasPermissions() && !isAppInitialized) {
            initApp()
            isAppInitialized = true
        } else if (!hasPermissions()) {
            requestPermissions()
        }

        startHotwordServiceIfNeeded()

        if (intent.getBooleanExtra("open_overlay", false)) {
            val mode = intent.getIntExtra(OverlayActivity.EXTRA_MODE, OverlayActivity.MODE_LISTENING)
            if (wsManager?.isConnected == true) {
                openOverlay(mode)
            } else {
                pendingOpenOverlay = true
                pendingOverlayMode = mode
            }
        }

        setContent {
            XiaoZhiTheme {
                val waveform by waveformAmplitudes.collectAsStateWithLifecycle()
                MainScreen(
                    uiState = uiState,
                    onInputTextChange = { updateUiState { copy(inputText = it) } },
                    onSendTextMessage = { sendTextMessage() },
                    onToggleMic = {
                        if (micActive) {
                            stopSendingAudio()
                        } else {
                            startSendingAudioToServer()
                        }
                    },
                    onMenuOpenChange = { updateUiState { copy(isMenuOpen = it) } },
                    onSelectImage = { selectImageLauncher.launch("image/*") },
                    onSettingsClick = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onToggleMcpMusic = {
                        val current = AppState.isMcpMusicEnabled(this)
                        AppState.setMcpMusicEnabled(this, !current)
                        mcpHandler?.musicEnabled = !current
                        Toast.makeText(
                            this,
                            if (!current) getString(R.string.mcp_music_on) else getString(R.string.mcp_music_off),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onToggleMcpVideo = {
                        val current = AppState.isMcpVideoEnabled(this)
                        AppState.setMcpVideoEnabled(this, !current)
                        mcpHandler?.videoEnabled = !current
                        Toast.makeText(
                            this,
                            if (!current) getString(R.string.mcp_video_on) else getString(R.string.mcp_video_off),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onLoginLogout = {
                        if (firebaseAuth.currentUser != null) {
                            firebaseAuth.signOut()
                            googleSignInClient.signOut().addOnCompleteListener {
                                AppState.setLoggedIn(this, false)
                                AppState.setUserName(this, null)
                                AppState.setUserEmail(this, null)
                                AppState.setUserAvatar(this, null)
                                Toast.makeText(this, getString(R.string.logged_out), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        }
                    },
                    onCopyActivationCode = {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("activation", uiState.activationPinCode))
                        Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                    },
                    videoPlayerManager = videoPlayerManager,
                    playerView = playerView,
                    previewViewForEye = previewViewForEye,
                    isMicActive = micActive,
                    isMusicPlaying = isMusicPlaying,
                    isVideoPlaying = isVideoPlaying,
                    currentEmotionGif = uiState.currentEmotion,
                    currentSongTitle = uiState.currentSongTitle,
                    onPrevious = { safeMediaPrevious() },
                    onPlayPause = { safeMediaPlayPause() },
                    onNext = { safeMediaNext() },
                    onRequestRecordAudioPermission = { requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    waveformAmplitudes = waveform
                )
            }
        }
        Log.d(TAG, "Content set successfully")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_overlay", false)) {
            val mode = intent.getIntExtra(OverlayActivity.EXTRA_MODE, OverlayActivity.MODE_LISTENING)
            if (wsManager?.isConnected == true) {
                openOverlay(mode)
            } else {
                pendingOpenOverlay = true
                pendingOverlayMode = mode
            }
        }
    }

    private fun openOverlay(mode: Int) {
        val intent = Intent(this, OverlayActivity::class.java).apply {
            putExtra(OverlayActivity.EXTRA_MODE, mode)
        }
        startActivity(intent)
        pendingOpenOverlay = false
    }

    private fun safeMediaPlayPause() {
        try { mediaPlayPause() } catch (e: SecurityException) { Toast.makeText(this, "Không thể điều khiển media nền", Toast.LENGTH_LONG).show() } catch (e: Exception) { }
    }
    private fun safeMediaNext() { try { mediaNext() } catch (e: SecurityException) { Toast.makeText(this, "Không thể chuyển bài tiếp", Toast.LENGTH_SHORT).show() } catch (e: Exception) { } }
    private fun safeMediaPrevious() { try { mediaPrevious() } catch (e: SecurityException) { Toast.makeText(this, "Không thể chuyển bài trước", Toast.LENGTH_SHORT).show() } catch (e: Exception) { } }

    private fun sendTextMessage() {
        val msg = uiState.inputText.trim()
        if (msg.isEmpty()) return

        val now = System.currentTimeMillis()
        if (msg == lastSentMessage && now - lastSentTime < DEBOUNCE_MS) {
            Log.d(TAG, "Debounce: skip duplicate message: $msg")
            return
        }
        lastSentMessage = msg
        lastSentTime = now

        val ws = wsManager ?: run {
            Toast.makeText(this, getString(R.string.server_not_connected), Toast.LENGTH_SHORT).show()
            return
        }
        if (!ws.isConnected) {
            Toast.makeText(this, getString(R.string.server_not_connected), Toast.LENGTH_SHORT).show()
            return
        }
        val sessionId = ws.sessionId ?: run {
            Toast.makeText(this, getString(R.string.session_id_missing), Toast.LENGTH_SHORT).show()
            return
        }
        ws.sendText(JsonObject().apply {
            addProperty("session_id", sessionId)
            addProperty("type", "listen")
            addProperty("state", "detect")
            addProperty("text", msg)
            addProperty("emotion", uiState.currentEmotion)
        }.toString())
        showUserMessage(msg)
        pendingTextStt = true
        lastUserMessage = normalizeString(msg)
        updateUiState { copy(inputText = "") }
        waitingForResponse = true
        setStatus(getString(R.string.status_processing))
        updateUiState { copy(aiState = AIState.PROCESSING) }
        stopSendingAudio()
    }

    fun showUserMessage(text: String) {
        val newMessage = Message(Message.TYPE_USER, text)
        updateUiState { copy(messages = messages + newMessage) }
    }

    fun showAssistantMessage(text: String) {
        val newMessage = Message(Message.TYPE_ASSISTANT, text)
        updateUiState { copy(messages = messages + newMessage) }
        val intent = Intent("com.xiaozhi.OVERLAY_ADD_MESSAGE")
        intent.putExtra("text", text)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun showAlbumArt(thumbnailUrl: String) {
        updateUiState { copy(isAlbumArtVisible = true, albumArtUrl = thumbnailUrl) }
    }
    fun hideAlbumArt() { updateUiState { copy(isAlbumArtVisible = false, albumArtUrl = null) } }
    fun hideVideo() { updateUiState { copy(isVideoVisible = false) } }
    fun setEmotionGif(emotion: String) { updateUiState { copy(currentEmotion = emotion) } }
    fun setStatus(statusText: String) { Log.d(TAG, "Status: $statusText") }

    private fun startVoiceTimeout() {
        cancelVoiceTimeout()
        val timeoutRunnable = Runnable {
            Log.d(TAG, "Voice timeout")
            showAssistantMessage(getString(R.string.voice_timeout))
            handleVoiceTimeout()
        }
        mainHandler.postDelayed(timeoutRunnable, VOICE_TIMEOUT_MS)
        voiceTimeoutRunnable = timeoutRunnable
    }

    private fun cancelVoiceTimeout() {
        voiceTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        voiceTimeoutRunnable = null
    }

    private fun handleVoiceTimeout() {
        stopSendingAudio()
        voiceTimeout = true
        setStatus(getString(R.string.status_ready))
        updateUiState { copy(aiState = AIState.IDLE) }
        Log.d(TAG, "Voice timeout handled - mic stopped, face detection still active")
    }

    private fun startSendingAudioToServer() {
        Log.d(TAG, "startSendingAudioToServer called")
        if (micActive) {
            Log.d(TAG, "Already sending audio, skip")
            return
        }
        val ws = wsManager
        if (ws == null || !ws.isConnected || ws.sessionId == null) {
            Log.d(TAG, "WebSocket not ready (connected=${ws?.isConnected}, sessionId=${ws?.sessionId}), pending start")
            pendingStartAudioAfterReconnect = true
            return
        }
        val json = JsonObject().apply {
            addProperty("session_id", ws.sessionId)
            addProperty("type", "listen")
            addProperty("state", "start")
            addProperty("mode", "auto")
            addProperty("emotion", uiState.currentEmotion)
        }
        try { ws.sendText(json.toString()) } catch (e: Exception) { setStatus(getString(R.string.send_start_error)); return }
        audioStartTimestamp = System.currentTimeMillis()
        lastServerResponseTime = 0L
        val success = audioManager?.startRecordingForServer { opusData ->
            if (ws.isConnected) {
                val timestamp = System.currentTimeMillis() % 0x10000000L
                try { ws.sendAudio(opusData, timestamp) } catch (e: Exception) { mainHandler.post { stopSendingAudio() } }
            } else { mainHandler.post { stopSendingAudio() } }
        }
        if (success == true) {
            micActive = true
            voiceTimeout = false
            pendingStartAudioAfterReconnect = false
            setStatus(getString(R.string.status_listening))
            updateUiState { copy(aiState = AIState.LISTENING) }
            startVoiceTimeout()
            Log.d(TAG, "startSendingAudioToServer: audio recording started, facePresent=$facePresent")
        } else {
            setStatus(getString(R.string.cannot_record))
        }
    }

    private fun stopSendingAudio() {
        if (!micActive) return
        Log.d(TAG, "Stopping audio sending...")
        micActive = false
        lastMicOffTime = System.currentTimeMillis()
        audioManager?.stopRecording()
        val ws = wsManager
        if (ws != null && ws.isConnected && ws.sessionId != null) {
            val json = JsonObject().apply {
                addProperty("session_id", ws.sessionId)
                addProperty("type", "listen")
                addProperty("state", "stop")
            }
            try { ws.sendText(json.toString()) } catch (_: Exception) {}
        }
        cancelVoiceTimeout()
        setStatus(getString(R.string.status_ready))
        updateUiState { copy(aiState = AIState.IDLE) }
    }

    // ========== CÁC HÀM HỖ TRỢ ==========
    fun startVideo(url: String, title: String) {
        stopSendingAudio()
        videoPlayerManager.playUrl(url)
        isVideoPlaying = true
        updateUiState { copy(isVideoVisible = true) }
        stopMusicService()
        setStatus("🎬 $title")
    }
    fun stopVideo() { videoPlayerManager.stop(); isVideoPlaying = false; updateUiState { copy(isVideoVisible = false) }; setStatus(getString(R.string.status_ready)) }
    fun pauseVideo() = videoPlayerManager.pause()
    fun resumeVideo() = videoPlayerManager.resume()

    fun startMusicService(url: String, title: String, thumbnail: String) {
        // Kiểm tra quyền RECORD_AUDIO cho visualizer
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            Toast.makeText(this, "Cấp quyền ghi âm để thấy sóng nhạc", Toast.LENGTH_LONG).show()
        }
        stopSendingAudio()
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY
            putExtra(MusicPlaybackService.EXTRA_URL, url)
            putExtra(MusicPlaybackService.EXTRA_TITLE, title)
            putExtra(MusicPlaybackService.EXTRA_THUMBNAIL, thumbnail)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        isMusicPlaying = true
        setStatus("🎵 $title")
        showAlbumArt(thumbnail)
        updateUiState { copy(currentSongTitle = title) }
    }

    fun stopMusicService() {
        startService(Intent(this, MusicPlaybackService::class.java).apply { action = MusicPlaybackService.ACTION_STOP })
        updateUiState { copy(currentSongTitle = "") }
    }

    fun startCamera(url: String, name: String) {
        stopSendingAudio()
        videoPlayerManager.playUrl(url)
        isVideoPlaying = true
        updateUiState { copy(isVideoVisible = true) }
        stopMusicService()
        setStatus("📷 $name")
    }
    fun stopCamera() { videoPlayerManager.stop(); isVideoPlaying = false; updateUiState { copy(isVideoVisible = false) }; setStatus(getString(R.string.status_ready)) }
    fun takeCameraSnapshot(callback: (Bitmap?) -> Unit) { callback(null) }
    fun saveSnapshot(bitmap: Bitmap): String { return "" }
    fun openCameraForCapture() { captureImageLauncher.launch(Intent(this, CameraCaptureActivity::class.java)) }
    fun showImageMessage(imagePath: String) {
        val newMessage = Message(Message.TYPE_USER, imagePath, isImage = true)
        updateUiState { copy(messages = messages + newMessage) }
    }
    fun analyzeFileForMCP(file: File): String? { return null }
    fun sendImageForAnalysis(imagePath: String) { }

    fun mediaPlay() = systemController.mediaPlay()
    fun mediaPause() = systemController.mediaPause()
    fun mediaNext() = systemController.mediaNext()
    fun mediaPrevious() = systemController.mediaPrevious()
    fun mediaPlayPause() = systemController.mediaPlayPause()
    suspend fun setWifiEnabled(enabled: Boolean): Boolean = systemController.setWifiEnabled(enabled)
    suspend fun isWifiEnabled(): Boolean = systemController.isWifiEnabled()
    suspend fun setBluetoothEnabled(enabled: Boolean): Boolean = systemController.setBluetoothEnabled(enabled)
    suspend fun isBluetoothEnabled(): Boolean = systemController.isBluetoothEnabled()
    suspend fun setAirplaneModeEnabled(enabled: Boolean): Boolean = systemController.setAirplaneModeEnabled(enabled)
    suspend fun isAirplaneModeEnabled(): Boolean = systemController.isAirplaneModeEnabled()
    fun setVolume(stream: String, percent: Int): Boolean = systemController.setVolume(android.media.AudioManager.STREAM_MUSIC, percent)
    fun getVolume(stream: String): Int = systemController.getVolume(android.media.AudioManager.STREAM_MUSIC)
    suspend fun setBrightness(percent: Int): Boolean = systemController.setBrightness(percent)
    suspend fun getBrightness(): Int = systemController.getBrightness()
    fun lockScreen() = systemController.lockScreen()
    fun getBatteryLevel() = systemController.getBatteryLevel()
    fun isCharging() = systemController.isCharging()
    fun getFreeStorage() = systemController.getFreeStorage()
    fun getTotalStorage() = systemController.getTotalStorage()
    fun getInstalledApps() = systemController.getInstalledApps()
    fun openApp(packageName: String) = systemController.openApp(packageName)
    fun uninstallApp(packageName: String) = systemController.uninstallApp(packageName)
    fun findPackageByName(appName: String): String? = systemController.findPackageByName(appName)
    fun dialPhoneNumber(number: String): Boolean = systemController.dialPhoneNumber(number)
    fun listDirectory(path: String) = systemController.listDirectory(path)
    fun createDirectory(path: String) = systemController.createDirectory(path)
    fun getFileInfo(path: String) = systemController.getFileInfo(path)
    fun deleteFileSystem(path: String): Boolean = systemController.deleteFileSystem(path)
    fun getExternalStorageRoot() = systemController.getExternalStorageRoot()
    fun sendNotification(title: String, content: String) = systemController.sendNotification(title, content)
    fun clearNotifications() = systemController.clearAllNotifications()
    suspend fun scanWifiNetworks() = systemController.scanWifiNetworks()
    suspend fun connectToWifi(ssid: String, password: String?) = systemController.connectToWifi(ssid, password)
    fun startBluetoothScan(callback: (List<Map<String, String>>) -> Unit) = systemController.startBluetoothScan(callback)
    suspend fun getPairedBluetoothDevices() = systemController.getPairedBluetoothDevices()
    suspend fun connectToBluetooth(address: String) = systemController.connectToBluetooth(address)
    suspend fun disconnectBluetooth() = systemController.disconnectBluetooth()
    suspend fun getConnectedBluetoothDevice() = systemController.getConnectedBluetoothDevice()

    private fun normalizeString(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    private fun handleServerJson(json: JsonObject) {
        lastServerResponseTime = System.currentTimeMillis()
        when (json.get("type")?.asString) {
            "tts" -> {
                when (json.get("state")?.asString) {
                    "start" -> {
                        stopSendingAudio()
                        setStatus(getString(R.string.status_speaking))
                        isSpeaking = true
                        waitingForResponse = false
                        pendingTextStt = false
                        updateUiState { copy(aiState = AIState.SPEAKING) }
                    }
                    "stop" -> {
                        isSpeaking = false
                        updateUiState { copy(aiState = AIState.IDLE) }
                        setStatus(getString(R.string.status_ready))
                    }
                    "sentence_start" -> {
                        val text = json.get("text")?.asString ?: ""
                        if (text.isNotEmpty() && !isMusicRelatedErrorTTS(text)) {
                            showAssistantMessage(text)
                        }
                    }
                }
            }
            "stt" -> {
                val rawText = json.get("text")?.asString ?: return
                cancelVoiceTimeout()
                startVoiceTimeout()
                if (pendingTextStt) {
                    pendingTextStt = false
                    waitingForResponse = true
                    setStatus(getString(R.string.status_processing))
                    updateUiState { copy(aiState = AIState.PROCESSING) }
                    return
                }
                val text = normalizeString(rawText)
                if (text != lastUserMessage) {
                    lastUserMessage = text
                    showUserMessage(rawText)
                    waitingForResponse = true
                    setStatus(getString(R.string.status_processing))
                    updateUiState { copy(aiState = AIState.PROCESSING) }
                }
            }
            "llm" -> {
                json.get("emotion")?.asString?.let { setEmotionGif(it) }
            }
            "mcp" -> mcpHandler?.handleMcpMessage(json.getAsJsonObject("payload"))
            "hello" -> {}
        }
    }

    private fun isMusicRelatedErrorTTS(text: String) = text.lowercase().contains("xin lỗi") || text.lowercase().contains("không có khả năng phát nhạc") || text.lowercase().contains("chưa thể phát")
    private fun sendUserInfo() {
        val user = firebaseAuth.currentUser
        wsManager?.setUserInfo(user?.displayName, user?.email, user?.photoUrl?.toString())
    }

    private fun requestPermissions() {
        val missingPermissions = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions, REQUEST_PERMISSIONS)
        }
    }
    private fun hasPermissions() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        AppState.setAppForeground(this, true)
        isForeground = true
        val filter = IntentFilter("com.xiaozhi.MUSIC_STOPPED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(musicStoppedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(musicStoppedReceiver, filter)
        }
        if (!isAppInitialized && hasPermissions()) {
            initApp()
            isAppInitialized = true
        } else if (wsManager?.isConnected == false) {
            wsManager?.connect()
        }
        if (isWakeWordEnabled && !isHotwordServiceRunning()) startHotwordService()
        sensorFusion?.start()
        mainHandler.removeCallbacks(stateMonitorRunnable)
        mainHandler.post(stateMonitorRunnable)
    }

    override fun onPause() {
        super.onPause()
        AppState.setAppForeground(this, false)
        isForeground = false
        try { unregisterReceiver(musicStoppedReceiver) } catch (_: IllegalArgumentException) {}
        sensorFusion?.stop()
        mainHandler.removeCallbacks(stateMonitorRunnable)
    }

    private fun isHotwordServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (HotwordForegroundService::class.java.name == service.service.className) return true
        }
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (hasPermissions() && !isAppInitialized) {
                initApp()
                isAppInitialized = true
            } else {
                Toast.makeText(this, getString(R.string.permission_still_missing), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initApp() {
        deviceId = AppState.getDeviceId(this)
        clientId = AppState.getClientId(this)
        activated = AppState.isActivated(this)

        val otaUrl = AppState.getOtaUrl(this)
        otaClient = OtaClient(this, deviceId!!, clientId!!, otaUrl)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (otaClient?.checkOta() != true) {
                    setStatus(getString(R.string.ota_failed))
                    return@launch
                }
            } catch (e: IOException) {
                setStatus(getString(R.string.connection_error))
                return@launch
            }

            val ota = otaClient?.otaResponse
            if (!activated && ota?.activation != null) {
                val code = ota.activation.code
                withContext(Dispatchers.Main) {
                    updateUiState {
                        copy(
                            isActivationCardVisible = true,
                            activationPinCode = code ?: "------"
                        )
                    }
                    setStatus(getString(R.string.awaiting_activation))
                }
                val success = otaClient?.pollActivation() ?: false
                if (success) {
                    activated = true
                    AppState.setActivated(this@MainActivity, true)
                    withContext(Dispatchers.Main) {
                        updateUiState { copy(isActivationCardVisible = false) }
                        setStatus(getString(R.string.status_ready))
                    }
                }
            }

            val wsUrl = AppState.getWssUrl(this@MainActivity) ?: ota?.websocket?.url
            val wsToken = ota?.websocket?.token
            if (wsUrl == null || wsToken == null) {
                setStatus(getString(R.string.websocket_config_error))
                return@launch
            }

            wsManager = WebSocketManager(wsUrl, wsToken, deviceId!!, clientId!!)
            WebSocketManager.wsManager = wsManager
            AppState.setWsToken(this@MainActivity, wsToken)
            wsManager?.setUserInfo(
                firebaseAuth.currentUser?.displayName,
                firebaseAuth.currentUser?.email,
                firebaseAuth.currentUser?.photoUrl?.toString()
            )
            wsManager?.setCallback(object : WebSocketManager.Callback {
                override fun onConnected() {
                    setStatus(getString(R.string.status_connected))
                    mcpHandler?.setChatWebSocketManager(wsManager!!)
                    mcpHandler?.flushPendingResults()
                    if (pendingOpenOverlay) {
                        openOverlay(pendingOverlayMode)
                    }
                    if (pendingStartAudioAfterReconnect) {
                        pendingStartAudioAfterReconnect = false
                        startSendingAudioToServer()
                    }
                }
                override fun onDisconnected(code: Int, reason: String?) {
                    setStatus(getString(R.string.status_disconnected))
                    waitingForResponse = false
                    if (micActive) stopSendingAudio()
                    updateUiState { copy(aiState = AIState.IDLE) }
                }
                override fun onJsonMessage(json: JsonObject) { handleServerJson(json) }
                override fun onAudioData(opusData: ByteArray, sampleRate: Int, frameDuration: Int) {
                    audioManager?.playAudio(opusData)
                }
            })
            wsManager?.connect()

            mcpHandler = McpHandler(this@MainActivity).apply {
                setChatWebSocketManager(wsManager!!)
                musicEnabled = AppState.isMcpMusicEnabled(this@MainActivity)
                videoEnabled = AppState.isMcpVideoEnabled(this@MainActivity)
            }
            setStatus(getString(R.string.status_connecting))
        }
    }

    private fun startHotwordService() {
        if (!isWakeWordEnabled) return
        val intent = Intent(this, HotwordForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
    private fun stopHotwordService() { stopService(Intent(this, HotwordForegroundService::class.java)) }
    private fun startHotwordServiceIfNeeded() {
        if (isWakeWordEnabled && !isHotwordServiceRunning()) startHotwordService()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSendingAudio()
        eyeManager?.release()
        sensorFusion?.stop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(wakeWordReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(keyboardReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(overlaySendTextReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(waveformReceiver)
        stopMusicService()
        videoPlayerManager.release()
        wsManager?.disconnect()
        brain.shutdownTts()
        brain.cancel()
        mainScope.cancel()
    }

    private fun runOnMainThread(action: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run() else mainHandler.post(action)
    }
}

// ========================== UI Composables ==========================
@Composable
fun QuantumSpaceBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "quantum_flow")
    val gridOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = Color(0xFF05070B))
        val strokeWidth = 1.dp.toPx()
        val colorGrid = Color(0xFF00E5FF).copy(alpha = 0.05f)

        var x = gridOffset
        while (x < size.width) {
            drawLine(color = colorGrid, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = strokeWidth)
            x += 60.dp.toPx()
        }

        var y = gridOffset
        while (y < size.height) {
            drawLine(color = colorGrid, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = strokeWidth)
            y += 60.dp.toPx()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onInputTextChange: (String) -> Unit,
    onSendTextMessage: () -> Unit,
    onToggleMic: () -> Unit,
    onMenuOpenChange: (Boolean) -> Unit,
    onSelectImage: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleMcpMusic: () -> Unit,
    onToggleMcpVideo: () -> Unit,
    onLoginLogout: () -> Unit,
    onCopyActivationCode: () -> Unit,
    videoPlayerManager: VideoPlayerManager,
    playerView: PlayerView,
    previewViewForEye: PreviewView,
    isMicActive: Boolean,
    isMusicPlaying: Boolean,
    isVideoPlaying: Boolean,
    currentEmotionGif: String,
    currentSongTitle: String,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRequestRecordAudioPermission: () -> Unit,
    waveformAmplitudes: List<Float>
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val strMic = stringResource(R.string.mic)
    val strInputHint = stringResource(R.string.input_hint)
    val strSend = stringResource(R.string.send)
    val strActivationTitle = stringResource(R.string.activation_title)
    val strActivationInstruction = stringResource(R.string.activation_instruction)
    val strActivationWaiting = stringResource(R.string.activation_waiting)
    val strCopyActivation = stringResource(R.string.copy_activation)
    val strSettings = stringResource(R.string.settings)
    val strLoginGoogle = stringResource(R.string.login_google)
    val strLogout = stringResource(R.string.logout)
    val strToggleMusic = stringResource(R.string.toggle_mcp_music)
    val strToggleVideo = stringResource(R.string.toggle_mcp_video)

    val gifImageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(GifDecoder.Factory()) }
            .build()
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            delay(50)
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        if (uiState.messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        QuantumSpaceBackground()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray)
                                    .clickable { onMenuOpenChange(true) },
                                contentAlignment = Alignment.Center
                            ) {
                                val avatarUri = AppState.getEffectiveAvatarUri(context)
                                if (avatarUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(avatarUri),
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.AccountCircle, contentDescription = "Avatar", tint = Color.White, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (AppState.isLoggedIn(context)) FirebaseAuth.getInstance().currentUser?.displayName ?: "XiaoZhi AI" else "XiaoZhi AI",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.BatteryFull,
                                        contentDescription = "Battery",
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${uiState.batteryPercent}%",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val orbTransition = rememberInfiniteTransition(label = "orb")
                            val orbScale by orbTransition.animateFloat(
                                initialValue = 1.0f,
                                targetValue = 1.4f,
                                animationSpec = infiniteRepeatable(animation = tween(1200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                                label = "scale"
                            )
                            val orbColor = when (uiState.aiState) {
                                AIState.LISTENING -> Color(0xFF10B981)
                                AIState.SPEAKING -> Color(0xFF3B82F6)
                                else -> Color(0xFFF59E0B)
                            }
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .scale(orbScale)
                                        .background(orbColor.copy(alpha = 0.3f), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(orbColor, CircleShape)
                                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(uiState.aiState.name, color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, titleContentColor = Color.White)
                )
            },
            bottomBar = {
                val infiniteTransition = rememberInfiniteTransition(label = "neon")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 0.9f,
                    animationSpec = infiniteRepeatable(animation = tween(1500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                    label = "glow"
                )
                val quantumColor = when (uiState.aiState) {
                    AIState.LISTENING -> Color(0xFF10B981)
                    AIState.SPEAKING -> Color(0xFF0074FF)
                    AIState.PROCESSING -> Color(0xFFD946EF)
                    AIState.IDLE -> Color(0xFF00E5FF)
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.radialGradient(
                                colors = listOf(quantumColor, quantumColor.copy(alpha = 0.1f)),
                                radius = 300f
                            ),
                            shape = RoundedCornerShape(32.dp)
                        ),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xBF090D14)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onToggleMic,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(if (isMicActive) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = strMic, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(
                            value = uiState.inputText,
                            onValueChange = onInputTextChange,
                            placeholder = { Text(strInputHint, color = Color(0xFF94A3B8)) },
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White,
                                focusedPlaceholderColor = Color(0xFF94A3B8),
                                unfocusedPlaceholderColor = Color(0xFF94A3B8)
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    onSendTextMessage()
                                    keyboardController?.hide()
                                }
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                onSendTextMessage()
                                keyboardController?.hide()
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = strSend, tint = Color.White)
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(16.dp)
                ) {
                    val pagerState = rememberPagerState(pageCount = { 4 })
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 16.dp
                    ) { page ->
                        when (page) {
                            0 -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    val emotionFileMap = mapOf(
                                        "happy" to "happy.gif", "laughing" to "laughing.gif", "funny" to "funny.gif",
                                        "sad" to "sad.gif", "angry" to "angry.gif", "crying" to "crying.gif",
                                        "loving" to "loving.gif", "embarrassed" to "embarrassed.gif",
                                        "surprised" to "surprised.gif", "shocked" to "shocked.gif",
                                        "thinking" to "thinking.gif", "winking" to "winking.gif",
                                        "cool" to "cool.gif", "relaxed" to "relaxed.gif",
                                        "delicious" to "delicious.gif", "kissy" to "kissy.gif",
                                        "confident" to "confident.gif", "sleepy" to "sleepy.gif",
                                        "silly" to "silly.gif", "confused" to "confused.gif"
                                    )
                                    val fileName = emotionFileMap[currentEmotionGif] ?: "neutral.gif"
                                    val gifBytes = remember(fileName) {
                                        try {
                                            context.assets.open("emotions/$fileName").use { it.readBytes() }
                                        } catch (e: Exception) { null }
                                    }
                                    if (gifBytes != null) {
                                        val painter = rememberAsyncImagePainter(
                                            model = ImageRequest.Builder(context).data(gifBytes).crossfade(false).build(),
                                            imageLoader = gifImageLoader
                                        )
                                        Image(painter = painter, contentDescription = "AI Emotion", modifier = Modifier.size(140.dp), contentScale = ContentScale.Fit)
                                    } else {
                                        Spacer(modifier = Modifier.size(140.dp))
                                    }
                                }
                            }
                            1 -> {
                                CompactMusicWidget(
                                    isPlaying = isMusicPlaying,
                                    currentTrack = currentSongTitle,
                                    onPlayPause = onPlayPause,
                                    onNext = onNext,
                                    onPrevious = onPrevious,
                                    onRequestRecordAudioPermission = onRequestRecordAudioPermission,
                                    waveformAmplitudes = waveformAmplitudes
                                )
                            }
                            2 -> {
                                if (uiState.isVideoVisible) {
                                    AndroidView(
                                        factory = { playerView },
                                        modifier = Modifier.fillMaxSize(),
                                        update = { view ->
                                            if (videoPlayerManager.playerView != view) {
                                                videoPlayerManager.attachPlayerView(view)
                                            }
                                            videoPlayerManager.onPlaybackEnded = { }
                                        }
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(80.dp))
                                    }
                                }
                            }
                            3 -> {
                                QuantumClockTab()
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    reverseLayout = false,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(uiState.messages) { message ->
                        ChatMessageItem(message)
                    }
                }

                if (uiState.isActivationCardVisible) {
                    ActivationCard(
                        activationPinCode = uiState.activationPinCode,
                        strActivationTitle = strActivationTitle,
                        strActivationInstruction = strActivationInstruction,
                        strActivationWaiting = strActivationWaiting,
                        strCopyActivation = strCopyActivation,
                        onCopy = onCopyActivationCode
                    )
                }
            }
        }

        AndroidView(factory = { previewViewForEye }, modifier = Modifier.size(0.dp))

        AnimatedVisibility(
            visible = uiState.isMenuOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                if (dragAmount > 100f) onMenuOpenChange(false)
                            }
                        )
                    }
                    .clickable { onMenuOpenChange(false) }
            ) {
                Card(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .padding(8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xCC0A0F1A)
                    ),
                    elevation = CardDefaults.cardElevation(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFF00E5FF), Color(0xFF9D4EDD)),
                                        radius = 50f
                                    ),
                                    shape = CircleShape
                                )
                                .clickable { onSelectImage() },
                            contentAlignment = Alignment.Center
                        ) {
                            val avatarUri = AppState.getEffectiveAvatarUri(context)
                            if (avatarUri != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(avatarUri),
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = AppState.getUserName(context) ?: "XiaoZhi AI",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = AppState.getUserEmail(context) ?: "quantum@universe.ai",
                            color = Color(0xFF00E5FF).copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, Color(0xFF00E5FF), Color.Transparent)
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        MenuItemQuantum(
                            icon = Icons.Default.Settings,
                            text = strSettings,
                            iconColor = Color(0xFF00E5FF)
                        ) {
                            onMenuOpenChange(false)
                            onSettingsClick()
                        }
                        MenuItemQuantum(
                            icon = Icons.Default.MusicNote,
                            text = strToggleMusic,
                            iconColor = Color(0xFF9D4EDD)
                        ) {
                            onToggleMcpMusic()
                        }
                        MenuItemQuantum(
                            icon = Icons.Default.Videocam,
                            text = strToggleVideo,
                            iconColor = Color(0xFF00E5FF)
                        ) {
                            onToggleMcpVideo()
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, Color(0xFF9D4EDD), Color.Transparent)
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        MenuItemQuantum(
                            icon = Icons.Default.Person,
                            text = if (FirebaseAuth.getInstance().currentUser != null) strLogout else strLoginGoogle,
                            iconColor = Color(0xFFF59E0B)
                        ) {
                            onMenuOpenChange(false)
                            onLoginLogout()
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = "QUANTUM v2.0 | ENTANGLED",
                            color = Color(0xFF475569),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }
            }
        }

        BackHandler(enabled = uiState.isMenuOpen) {
            onMenuOpenChange(false)
        }
    }
}

@Composable
fun MenuItemQuantum(
    icon: ImageVector,
    text: String,
    iconColor: Color,
    onClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isHovered) 1.02f else 1f, label = "scale")
    val glowAlpha by animateFloatAsState(targetValue = if (isHovered) 0.3f else 0f, label = "glow")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .background(
                color = if (isHovered) Color(0x3300E5FF) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(iconColor.copy(alpha = 0.2f), Color.Transparent),
                        radius = 20f
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = if (isHovered) 1f else 0.8f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (isHovered) {
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ChatMessageItem(message: Message) {
    val isUser = message.type == Message.TYPE_USER
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0x331D4ED8) else Color(0x261E293B)
            ),
            modifier = Modifier
                .widthIn(max = 290.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = if (isUser)
                            listOf(Color(0xFF2563EB).copy(alpha = 0.5f), Color(0xFF00E5FF).copy(alpha = 0.2f))
                        else
                            listOf(Color(0xFF475569).copy(alpha = 0.3f), Color(0xFF94A3B8).copy(alpha = 0.1f))
                    ),
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 16.dp
                    )
                )
        ) {
            if (message.isImage) {
                Image(
                    painter = rememberAsyncImagePainter(File(message.content)),
                    contentDescription = "Image",
                    modifier = Modifier
                        .size(240.dp)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = message.content,
                    color = if (isUser) Color(0xFFE0F2FE) else Color(0xFFE2E8F0),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.5.sp)
                )
            }
        }
    }
}

@Composable
fun ActivationCard(
    activationPinCode: String,
    strActivationTitle: String,
    strActivationInstruction: String,
    strActivationWaiting: String,
    strCopyActivation: String,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(strActivationTitle, color = Color(0xFFF8FAFC), style = MaterialTheme.typography.titleMedium)
            Text(
                text = activationPinCode,
                color = Color(0xFFFBBF24),
                fontSize = MaterialTheme.typography.displayMedium.fontSize,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Text(strActivationInstruction, color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
            Text(strActivationWaiting, color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            Button(
                onClick = onCopy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(strCopyActivation)
            }
        }
    }
}

@Composable
fun CompactMusicWidget(
    isPlaying: Boolean,
    currentTrack: String,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRequestRecordAudioPermission: () -> Unit,
    waveformAmplitudes: List<Float>
) {
    val context = LocalContext.current
    val hasRecordPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!hasRecordPermission) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                Text(
                    text = "Cần quyền ghi âm để hiển thị sóng nhạc",
                    color = Color.White,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRequestRecordAudioPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF)
                    )
                ) {
                    Text("Cấp quyền", color = Color.Black)
                }
            }
        } else {
            // Vẽ sóng nhạc bằng Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                val width = size.width
                val height = size.height
                val barCount = 40
                val step = width / barCount
                val barWidth = (step - 2).coerceAtLeast(1f)

                val data = if (isPlaying && waveformAmplitudes.isNotEmpty()) waveformAmplitudes else List(barCount) { 0.1f }

                val indices = (0 until barCount).map { i ->
                    val index = (i * data.size / barCount).coerceIn(0, data.size - 1)
                    data[index]
                }

                indices.forEachIndexed { i, amp ->
                    val barHeight = height * (0.1f + amp * 0.7f).coerceIn(0.05f, 0.9f)
                    val x = i * step
                    drawLine(
                        start = Offset(x, height - barHeight),
                        end = Offset(x, height),
                        color = Color(0xFF00E5FF).copy(alpha = if (isPlaying) 0.8f else 0.3f),
                        strokeWidth = barWidth
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = currentTrack.ifEmpty { "🌀 Kết nối vũ trụ" },
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Text(
            text = if (isPlaying) "● ĐANG PHÁT" else "○ DỪNG",
            color = if (isPlaying) Color(0xFF00E5FF) else Color(0xFF94A3B8),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color(0xFFCBD5E1))
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E5FF))
                    .clickable { onPlayPause() }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.Black,
                    modifier = Modifier.align(Alignment.Center).size(28.dp)
                )
            }

            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color(0xFFCBD5E1))
            }
        }
    }
}

@Composable
fun QuantumClockTab() {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val infiniteTransition = rememberInfiniteTransition(label = "quantum_clock")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )
    val quantumState by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "quantum_state"
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - (System.currentTimeMillis() % 1000))
            currentTime = System.currentTimeMillis()
        }
    }

    val calendar = remember(currentTime) { java.util.Calendar.getInstance().apply { timeInMillis = currentTime } }
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = calendar.get(java.util.Calendar.MINUTE)
    val second = calendar.get(java.util.Calendar.SECOND)
    val timeString = String.format("%02d:%02d:%02d", hour, minute, second)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val strokeWidth = 2.dp.toPx()
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - strokeWidth / 2
            drawArc(
                color = Color(0xFF00E5FF).copy(alpha = 0.3f),
                startAngle = rotationAngle,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth)
            )
            drawArc(
                color = Color(0xFFD946EF).copy(alpha = 0.3f),
                startAngle = rotationAngle + 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 3
            for (i in 0..2) {
                val progress = (quantumState + i * 0.33f) % 1f
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.1f * (1f - progress)),
                    radius = radius * (0.8f + progress * 0.6f),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = timeString,
                color = Color(0xFF00E5FF).copy(alpha = pulseAlpha),
                fontSize = 48.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                modifier = Modifier.shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    clip = false,
                    spotColor = Color(0xFF00E5FF),
                    ambientColor = Color(0xFF00E5FF)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "⚛ QUANTUM TIME ⚛",
                color = Color(0xFFD946EF).copy(alpha = pulseAlpha * 0.8f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    second % 2 == 0 -> "◉ ENTANGLED"
                    else -> "◎ OBSERVING"
                },
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}