package com.xiaozhi

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import com.xiaozhi.ui.theme.XiaoZhiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class OverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_ASSISTANT = 0
        const val MODE_LISTENING = 1
        private const val VOICE_TIMEOUT_MS = 8000L
    }

    private var audioManager: XiaoZhiAudioManager? = null
    private var isRecording = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var voiceTimeoutRunnable: Runnable? = null

    // UI State
    private var mode by mutableStateOf(MODE_ASSISTANT)
    private var isVoiceMode by mutableStateOf(true)
    private var inputText by mutableStateOf("")
    private var messages by mutableStateOf(listOf<Message>())
    private var isExpanded by mutableStateOf(false)
    private var sheetOffsetY by mutableStateOf(0f)
    private var waveformAmplitudes by mutableStateOf(List(30) { 0.2f })
    private var currentTime by mutableStateOf("")
    private var batteryLevel by mutableStateOf(0)
    private var isListening by mutableStateOf(false)
    private var isConnected by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.apply {
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }

        mode = intent.getIntExtra(EXTRA_MODE, MODE_ASSISTANT)
        audioManager = (application as MyApplication).audioManager

        // Cập nhật thời gian và pin
        lifecycleScope.launch {
            while (true) {
                currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                batteryLevel = fetchBatteryLevel()
                delay(1000)
            }
        }

        // Theo dõi trạng thái WebSocket (dùng singleton)
        lifecycleScope.launch {
            while (true) {
                val ws = WebSocketManager.wsManager
                isConnected = ws != null && ws.isConnected
                delay(500)
            }
        }

        setContent {
            XiaoZhiTheme {
                OverlayScreen(
                    mode = mode,
                    isVoiceMode = isVoiceMode,
                    inputText = inputText,
                    messages = messages,
                    isExpanded = isExpanded,
                    sheetOffsetY = sheetOffsetY,
                    waveformAmplitudes = waveformAmplitudes,
                    currentTime = currentTime,
                    batteryLevel = batteryLevel,
                    isListening = isListening,
                    isConnected = isConnected,
                    onInputTextChange = { inputText = it },
                    onSendText = {
                        sendTextMessage()
                        inputText = ""
                    },
                    onToggleVoiceMode = {
                        if (isVoiceMode) stopVoiceRecording() else startVoiceRecording()
                        isVoiceMode = !isVoiceMode
                    },
                    onClose = { finish() },
                    onExpandToggle = {
                        isExpanded = !isExpanded
                        if (isExpanded) {
                            window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                        } else {
                            window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                        }
                    },
                    onDrag = { deltaY ->
                        sheetOffsetY = (sheetOffsetY + deltaY).coerceIn(-400f, 400f)
                        if (sheetOffsetY > 300f) finish()
                    },
                    onDragEnd = {
                        if (sheetOffsetY > 150f) finish() else sheetOffsetY = 0f
                    }
                )
            }
        }

        if (mode == MODE_LISTENING) {
            lifecycleScope.launch {
                // Chờ tối đa 5 giây để WebSocket kết nối (singleton đã được khởi tạo từ Application)
                var attempts = 0
                while ((WebSocketManager.wsManager == null || !WebSocketManager.wsManager!!.isConnected) && attempts < 50) {
                    delay(100)
                    attempts++
                }
                if (WebSocketManager.wsManager?.isConnected == true) {
                    startVoiceRecording()
                } else {
                    Toast.makeText(this@OverlayActivity, "Không thể kết nối server", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    // ==================== Recording & Playback ====================
    private fun startVoiceRecording() {
        if (isRecording) return
        val ws = WebSocketManager.wsManager ?: return
        if (!ws.isConnected) {
            Toast.makeText(this, "Chưa kết nối server", Toast.LENGTH_SHORT).show()
            return
        }

        isRecording = true
        isListening = true

        // Gửi lệnh start listen
        val startMsg = JsonObject().apply {
            addProperty("type", "listen")
            addProperty("state", "start")
            addProperty("mode", "auto")
            addProperty("session_id", ws.sessionId ?: "")
        }
        ws.sendText(startMsg.toString())

        audioManager?.startRecordingForServer { opusData ->
            ws.sendAudio(opusData, System.currentTimeMillis() % 0x10000000L)
            lifecycleScope.launch {
                // Cập nhật waveform giả lập (có thể thay bằng dữ liệu thật)
                val fakeAmps = List(30) { (Math.random() * 0.8f + 0.2f).toFloat() }
                waveformAmplitudes = fakeAmps
            }
        }
        setVoiceTimeout()
    }

    private fun stopVoiceRecording() {
        if (!isRecording) return
        isRecording = false
        isListening = false
        audioManager?.stopRecording()
        cancelVoiceTimeout()

        val ws = WebSocketManager.wsManager
        if (ws != null && ws.isConnected && ws.sessionId != null) {
            val stopMsg = JsonObject().apply {
                addProperty("type", "listen")
                addProperty("state", "stop")
                addProperty("session_id", ws.sessionId)
            }
            ws.sendText(stopMsg.toString())
        }
        waveformAmplitudes = List(30) { 0.2f }
    }

    private fun sendTextMessage() {
        val text = inputText.trim()
        if (text.isEmpty()) return
        addUserMessage(text)

        val ws = WebSocketManager.wsManager
        if (ws == null || !ws.isConnected) {
            Toast.makeText(this, "Chưa kết nối server", Toast.LENGTH_SHORT).show()
            return
        }
        val sessionId = ws.sessionId ?: return
        val msg = JsonObject().apply {
            addProperty("type", "listen")
            addProperty("state", "detect")
            addProperty("text", text)
            addProperty("session_id", sessionId)
            addProperty("emotion", "neutral")
        }
        ws.sendText(msg.toString())

        if (mode == MODE_ASSISTANT) finish()
    }

    private fun addUserMessage(text: String) {
        messages = messages + Message(Message.TYPE_USER, text)
    }

    private fun addAssistantMessage(text: String) {
        messages = messages + Message(Message.TYPE_ASSISTANT, text)
        if (mode == MODE_LISTENING && !isExpanded && messages.size > 3) {
            isExpanded = true
            window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    // ==================== Helpers ====================
    private fun fetchBatteryLevel(): Int {
        val batteryIntent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }

    private fun setVoiceTimeout() {
        cancelVoiceTimeout()
        voiceTimeoutRunnable = Runnable {
            if (isRecording) {
                stopVoiceRecording()
                if (mode == MODE_LISTENING) isVoiceMode = false else finish()
            }
        }
        mainHandler.postDelayed(voiceTimeoutRunnable!!, VOICE_TIMEOUT_MS)
    }

    private fun cancelVoiceTimeout() {
        voiceTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        voiceTimeoutRunnable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVoiceRecording()
    }
}

// ==================== Compose UI ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayScreen(
    mode: Int,
    isVoiceMode: Boolean,
    inputText: String,
    messages: List<Message>,
    isExpanded: Boolean,
    sheetOffsetY: Float,
    waveformAmplitudes: List<Float>,
    currentTime: String,
    batteryLevel: Int,
    isListening: Boolean,
    isConnected: Boolean,
    onInputTextChange: (String) -> Unit,
    onSendText: () -> Unit,
    onToggleVoiceMode: () -> Unit,
    onClose: () -> Unit,
    onExpandToggle: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Lớp phủ mờ
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onClose() }
        )

        // Bottom sheet
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, with(density) { sheetOffsetY.toDp().roundToPx() }) }
                .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { _, dragAmount -> onDrag(dragAmount.y) },
                        onDragEnd = { onDragEnd() }
                    )
                },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0F1117).copy(alpha = 0.96f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isExpanded) Modifier.height(screenHeight * 0.85f) else Modifier.wrapContentHeight())
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.3f), CircleShape)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentTime,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.BatteryFull,
                                contentDescription = null,
                                tint = if (batteryLevel > 20) Color(0xFF4ADE80) else Color(0xFFEF4444),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "$batteryLevel%",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color(0xFF00E5FF))
                    }
                }

                // Khu vực sóng âm (chế độ voice)
                AnimatedVisibility(
                    visible = isVoiceMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 20.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp)
                                .padding(horizontal = 20.dp)
                        ) {
                            waveformAmplitudes.forEachIndexed { index, amp ->
                                val height = (amp * 55).dp.coerceIn(8.dp, 65.dp)
                                val colors = if (isListening)
                                    listOf(Color(0xFF00E5FF), Color(0xFFD946EF))
                                else
                                    listOf(Color(0xFF475569), Color(0xFF64748B))
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(height)
                                        .background(
                                            brush = Brush.verticalGradient(colors),
                                            shape = RoundedCornerShape(3.dp)
                                        )
                                )
                                if (index < waveformAmplitudes.size - 1) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                !isConnected -> "🔌 Đang kết nối..."
                                isListening -> "✨ Đang lắng nghe..."
                                else -> "🎤 Nhấn mic để nói"
                            },
                            color = if (isListening) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // Khu vực nhập text
                AnimatedVisibility(
                    visible = !isVoiceMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = onInputTextChange,
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "Nhập tin nhắn...",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF00E5FF),
                                focusedPlaceholderColor = Color.White.copy(alpha = 0.5f)
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = onSendText,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Gửi",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Thanh điều khiển
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleVoiceMode,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            if (isVoiceMode) Icons.Default.Mic else Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                    if (mode == OverlayActivity.MODE_LISTENING) {
                        IconButton(
                            onClick = onExpandToggle,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.05f))
                        ) {
                            Icon(
                                if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = Color(0xFFD946EF),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Khu vực chat (có thể mở rộng)
                AnimatedVisibility(
                    visible = isExpanded && mode == OverlayActivity.MODE_LISTENING,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        reverseLayout = false
                    ) {
                        items(messages) { message ->
                            ChatBubbleQuantum(message)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubbleQuantum(message: Message) {
    val isUser = message.type == Message.TYPE_USER
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 18.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0xFF1D4ED8) else Color(0xFF1E293B)
            ),
            modifier = Modifier
                .widthIn(max = 260.dp)
                .shadow(4.dp, shape = RoundedCornerShape(18.dp))
        ) {
            Text(
                text = message.content,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}