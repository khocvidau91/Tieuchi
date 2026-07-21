package com.xiaozhi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import coil.compose.rememberAsyncImagePainter
import com.xiaozhi.smarthome.HomeAssistantManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// Data class đại diện cho từng hạt tuyết
data class Snowflake(
    var x: Float,
    var y: Float,
    val radius: Float,
    val speed: Float,
    val alpha: Float
)

@OptIn(ExperimentalMaterial3Api::class)
class SettingsActivity : ComponentActivity() {

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            AppState.setProfileAvatarUri(this, it.toString())
            recreate()
        }
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val packageNames = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return packageNames?.contains(context.packageName) == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E5FF),
                    secondary = Color(0xFFD946EF),
                    background = Color.Transparent,
                    surface = Color.Transparent
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    QuantumSettingsBackground()
                    SettingsScreen(onPickImage = { pickImageLauncher.launch("image/*") })
                }
            }
        }
    }

    @Composable
    fun SettingsScreen(onPickImage: () -> Unit) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val systemController = remember { SystemController(context) }

        // State
        var isWakeWordEnabled by remember { mutableStateOf(AppState.isWakeWordEnabled(context)) }
        var isAutoListeningEnabled by remember { mutableStateOf(AppState.isAutoListeningEnabled(context)) }
        var isMcpMusicEnabled by remember { mutableStateOf(AppState.isMcpMusicEnabled(context)) }
        var isMcpVideoEnabled by remember { mutableStateOf(AppState.isMcpVideoEnabled(context)) }
        var isHaEnabled by remember { mutableStateOf(AppState.isHaEnabled(context)) }
        var haUrl by remember { mutableStateOf(AppState.getHaUrl(context) ?: "") }
        var haToken by remember { mutableStateOf(AppState.getHaToken(context) ?: "") }
        var micSensitivity by remember { mutableStateOf(AppState.getMicSensitivity(context)) }
        var showEditProfileDialog by remember { mutableStateOf(false) }
        var showHaDialog by remember { mutableStateOf(false) }
        var showDeviceInfoDialog by remember { mutableStateOf(false) }
        var showAboutDialog by remember { mutableStateOf(false) }
        var showOtaDialog by remember { mutableStateOf(false) }
        var showWssDialog by remember { mutableStateOf(false) }
        var showMusicWsDialog by remember { mutableStateOf(false) }
        var otaTempValue by remember { mutableStateOf(AppState.getOtaUrl(context)) }
        var wssTempValue by remember { mutableStateOf(AppState.getWssUrl(context) ?: "") }
        var musicWsTempValue by remember { mutableStateOf(AppState.getMusicWsUrl(context)) }
        var isAssistantDefault by remember { mutableStateOf(false) }
        var isCheckingAssistant by remember { mutableStateOf(true) }

        // State cho toggle đọc thông báo toàn cục
        var isNotificationReadingEnabled by remember {
            mutableStateOf(AppState.isNotificationReadingEnabled(context))
        }

        LaunchedEffect(Unit) {
            isAssistantDefault = systemController.isDefaultVoiceAssistant()
            isCheckingAssistant = false
        }

        val userName = AppState.getUserName(context) ?: "XiaoZhi AI"
        val userEmail = AppState.getUserEmail(context) ?: ""
        val avatarUri = AppState.getEffectiveAvatarUri(context)

        var currentScreen by remember { mutableStateOf("settings_main") }

        val menuItems = listOf(
            SectionHeader("🔗 KẾT NỐI"),
            MenuItemData(
                id = "ota", title = "OTA Server", subtitle = AppState.getOtaUrl(context),
                icon = Icons.Default.SystemUpdate, onClick = { showOtaDialog = true }
            ),
            MenuItemData(
                id = "wss", title = "WebSocket URL", subtitle = AppState.getWssUrl(context) ?: "Chưa cấu hình",
                icon = Icons.Default.Wifi, onClick = { showWssDialog = true }
            ),
            MenuItemData(
                id = "music_ws", title = "Music Server", subtitle = AppState.getMusicWsUrl(context),
                icon = Icons.Default.MusicNote, onClick = { showMusicWsDialog = true }
            ),
            MenuItemData(
                id = "ha", title = "Home Assistant",
                subtitle = if (isHaEnabled) "Đã bật: ${haUrl.take(30)}" else "Chưa bật",
                icon = Icons.Default.SmartToy,
                trailing = {
                    Switch(
                        checked = isHaEnabled,
                        onCheckedChange = { checked ->
                            isHaEnabled = checked
                            AppState.setHaEnabled(context, checked)
                            if (checked) showHaDialog = true
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0x6600E5FF)
                        )
                    )
                }
            ),
            SectionHeader("🎤 TRỢ LÝ GIỌNG NÓI"),
            MenuItemData(
                id = "wake_word", title = "Wake Word", subtitle = "Kích hoạt bằng 'Ok Lily'",
                icon = Icons.Default.Mic,
                trailing = {
                    Switch(
                        checked = isWakeWordEnabled,
                        onCheckedChange = { enabled ->
                            isWakeWordEnabled = enabled
                            AppState.setWakeWordEnabled(context, enabled)
                            if (enabled) context.startForegroundService(Intent(context, HotwordForegroundService::class.java))
                            else context.stopService(Intent(context, HotwordForegroundService::class.java))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0x6600E5FF)
                        )
                    )
                }
            ),
            MenuItemData(
                id = "auto_listening", title = "Tự động nghe", subtitle = "Tự động kích hoạt mic khi phát hiện khuôn mặt",
                icon = Icons.Default.Hearing,
                trailing = {
                    Switch(
                        checked = isAutoListeningEnabled,
                        onCheckedChange = { enabled ->
                            isAutoListeningEnabled = enabled
                            AppState.setAutoListeningEnabled(context, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0x6600E5FF)
                        )
                    )
                }
            ),
            MenuItemData(
                id = "mic_sensitivity", title = "Độ nhạy mic", subtitle = "${micSensitivity}% | ${(micSensitivity * 2.55).toInt()} QHz",
                icon = Icons.Default.Settings,
                slider = {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val density = LocalDensity.current
                        var trackWidth by remember { mutableStateOf(0) }
                        val fraction = micSensitivity.toFloat() / 100f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .onGloballyPositioned { trackWidth = it.size.width }
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        if (trackWidth > 0) {
                                            val newValue = (offset.x / trackWidth * 100f).coerceIn(0f, 100f)
                                            micSensitivity = newValue.toInt()
                                            AppState.setMicSensitivity(context, micSensitivity)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF))
                            )

                            val thumbOffsetDp = if (trackWidth > 0) {
                                with(density) { (fraction * trackWidth).toDp() } - 10.dp
                            } else {
                                0.dp
                            }

                            Box(
                                modifier = Modifier
                                    .offset(x = thumbOffsetDp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF))
                                    .draggable(
                                        orientation = Orientation.Horizontal,
                                        state = rememberDraggableState { delta ->
                                            if (trackWidth > 0) {
                                                val deltaValue = (delta / trackWidth) * 100f
                                                val computedValue = (micSensitivity + deltaValue).coerceIn(0f, 100f)
                                                micSensitivity = computedValue.toInt()
                                                AppState.setMicSensitivity(context, micSensitivity)
                                            }
                                        }
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0%", color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("50%", color = Color(0xFF00E5FF), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("100%", color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            ),
            MenuItemData(
                id = "default_assistant", title = "Trợ lý mặc định",
                subtitle = if (!isCheckingAssistant) (if (isAssistantDefault) "Đã được đặt" else "Chưa đặt") else "Đang kiểm tra...",
                icon = Icons.Default.Assistant,
                trailing = {
                    Button(
                        onClick = {
                            scope.launch {
                                val success = systemController.setDefaultVoiceAssistant()
                                if (success) { isAssistantDefault = true; Toast.makeText(context, "Đã đặt thành công", Toast.LENGTH_SHORT).show() }
                                else Toast.makeText(context, "Đặt thất bại", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !isCheckingAssistant && !isAssistantDefault,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                    ) { Text("Đặt làm mặc định") }
                }
            ),
            SectionHeader("⚡ MCP (MODEL CONTEXT PROTOCOL)"),
            MenuItemData(
                id = "mcp_music", title = "Điều khiển nhạc", subtitle = "Cho phép AI điều khiển phát nhạc",
                icon = Icons.Default.MusicNote,
                trailing = {
                    Switch(
                        checked = isMcpMusicEnabled,
                        onCheckedChange = { enabled ->
                            isMcpMusicEnabled = enabled
                            AppState.setMcpMusicEnabled(context, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0x6600E5FF)
                        )
                    )
                }
            ),
            MenuItemData(
                id = "mcp_video", title = "Điều khiển video", subtitle = "Cho phép AI điều khiển phát video",
                icon = Icons.Default.Videocam,
                trailing = {
                    Switch(
                        checked = isMcpVideoEnabled,
                        onCheckedChange = { enabled ->
                            isMcpVideoEnabled = enabled
                            AppState.setMcpVideoEnabled(context, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0x6600E5FF)
                        )
                    )
                }
            ),
            SectionHeader("🛠️ HỆ THỐNG"),

            // 🔥 THÊM TOGGLE ĐỌC THÔNG BÁO TOÀN CỤC
            MenuItemData(
                id = "notification_global",
                title = "Đọc thông báo",
                subtitle = if (isNotificationReadingEnabled) "Đang bật" else "Đã tắt",
                icon = Icons.Default.NotificationsActive,
                trailing = {
                    Switch(
                        checked = isNotificationReadingEnabled,
                        onCheckedChange = { enabled ->
                            isNotificationReadingEnabled = enabled
                            AppState.setNotificationReadingEnabled(context, enabled)
                            Toast.makeText(
                                context,
                                if (enabled) "Đã bật đọc thông báo" else "Đã tắt đọc thông báo",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0x6600E5FF)
                        )
                    )
                }
            ),

            MenuItemData(
                id = "notification_apps",
                title = "Ứng dụng đọc thông báo",
                subtitle = "Chọn ứng dụng được phép đọc thông báo",
                icon = Icons.Default.Notifications,
                onClick = { currentScreen = "notification_apps" }
            ),

            MenuItemData(
                id = "shizuku", title = "Shizuku",
                subtitle = when {
                    !ShizukuManager.isInstalled(context) -> "Chưa cài đặt"
                    !ShizukuManager.isReady() -> "Chưa khởi động"
                    else -> "Đang hoạt động"
                },
                icon = Icons.Default.Settings,
                onClick = {
                    when {
                        !ShizukuManager.isInstalled(context) -> ShizukuManager.openShizuku(context)
                        !ShizukuManager.isReady() -> ShizukuManager.openShizuku(context)
                        else -> Toast.makeText(context, "Shizuku đang hoạt động", Toast.LENGTH_SHORT).show()
                    }
                }
            ),

            MenuItemData(
                id = "notification_brain", title = "Trục Thần Kinh Thông Báo",
                subtitle = if (isNotificationListenerEnabled(context)) "ĐANG LIÊN KẾT TOÀN CỤC" else "YÊU CẦU CẤP QUYỀN",
                icon = Icons.Default.NotificationsActive,
                onClick = {
                    if (!isNotificationListenerEnabled(context)) {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        startActivity(intent)
                        Toast.makeText(context, "Hãy kích hoạt quyền cho ứng dụng!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Hệ thống nghe thông báo đang hoạt động tối ưu!", Toast.LENGTH_SHORT).show()
                    }
                }
            ),

            MenuItemData(
                id = "camera_mgmt", title = "Quản lý camera IP", subtitle = "Thêm, sửa, xóa camera RTSP",
                icon = Icons.Default.Camera,
                onClick = { currentScreen = "quantum_camera" }
            ),

            MenuItemData(
                id = "device_info", title = "Thông tin thiết bị", subtitle = "Chi tiết kết nối và định danh",
                icon = Icons.Default.Info, onClick = { showDeviceInfoDialog = true }
            ),

            MenuItemData(
                id = "profile", title = "Hồ sơ", subtitle = userName,
                icon = Icons.Default.Person, onClick = { showEditProfileDialog = true }
            ),

            MenuItemData(
                id = "account", title = if (AppState.isLoggedIn(context)) "Đăng xuất" else "Đăng nhập Google",
                subtitle = AppState.getUserEmail(context) ?: "",
                icon = Icons.Default.Person,
                onClick = {
                    if (AppState.isLoggedIn(context)) {
                        AppState.setLoggedIn(context, false)
                        AppState.setUserName(context, null)
                        AppState.setUserEmail(context, null)
                        AppState.setUserAvatar(context, null)
                        Toast.makeText(context, "Đã đăng xuất", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(context, "Đăng nhập Google", Toast.LENGTH_SHORT).show()
                }
            ),

            MenuItemData(
                id = "about", title = "Giới thiệu", subtitle = "Phiên bản 2.0",
                icon = Icons.Default.Info, onClick = { showAboutDialog = true }
            )
        )

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }
        ) { screen ->
            when (screen) {
                "settings_main" -> {
                    Scaffold(
                        containerColor = Color.Transparent,
                        topBar = {
                            TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent,
                                    titleContentColor = Color.White
                                ),
                                title = {
                                    Text(
                                        "CÀI ĐẶT HỆ THỐNG",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 18.sp,
                                        letterSpacing = 2.sp
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color(0xFF00E5FF)
                                        )
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            item {
                                ProfileCard(
                                    avatarUri = avatarUri,
                                    userName = userName,
                                    userEmail = userEmail,
                                    onPickImage = onPickImage,
                                    onEditProfile = { showEditProfileDialog = true }
                                )
                            }
                            items(menuItems) { item ->
                                when (item) {
                                    is SectionHeader -> SectionHeaderView(title = item.title)
                                    is MenuItemData -> SettingItemQuantum(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        icon = item.icon,
                                        trailing = item.trailing,
                                        slider = item.slider,
                                        onClick = item.onClick
                                    )
                                }
                            }
                        }
                    }
                }
                "quantum_camera" -> {
                    QuantumCameraScreen(onBack = { currentScreen = "settings_main" })
                }
                "notification_apps" -> {
                    NotificationAppListScreen(onBack = { currentScreen = "settings_main" })
                }
            }
        }

        if (showOtaDialog) {
            QuantumEditDialog(title = "OTA Server", currentValue = otaTempValue, onDismiss = { showOtaDialog = false }, onSave = { newValue -> AppState.setOtaUrl(context, newValue); otaTempValue = newValue; showOtaDialog = false; Toast.makeText(context, "Đã cập nhật OTA URL", Toast.LENGTH_SHORT).show() })
        }
        if (showWssDialog) {
            QuantumEditDialog(title = "WebSocket URL", currentValue = wssTempValue, onDismiss = { showWssDialog = false }, onSave = { newValue -> if (newValue.isNotEmpty()) AppState.setWssUrl(context, newValue) else AppState.setWssUrl(context, null); wssTempValue = newValue; showWssDialog = false; Toast.makeText(context, "Đã cập nhật WebSocket URL", Toast.LENGTH_SHORT).show() })
        }
        if (showMusicWsDialog) {
            QuantumEditDialog(title = "Music Server", currentValue = musicWsTempValue, onDismiss = { showMusicWsDialog = false }, onSave = { newValue -> AppState.setMusicWsUrl(context, newValue); musicWsTempValue = newValue; showMusicWsDialog = false; Toast.makeText(context, "Đã cập nhật Music Server URL", Toast.LENGTH_SHORT).show() })
        }
        if (showEditProfileDialog) {
            QuantumEditProfileDialog(initialName = userName, initialEmail = userEmail, onDismiss = { showEditProfileDialog = false }, onSave = { name, email -> AppState.setUserName(context, name); AppState.setUserEmail(context, email); Toast.makeText(context, "Đã lưu", Toast.LENGTH_SHORT).show(); showEditProfileDialog = false })
        }
        if (showHaDialog) {
            QuantumHomeAssistantDialog(haUrl = haUrl, haToken = haToken, onUrlChange = { haUrl = it }, onTokenChange = { haToken = it }, onConnect = { AppState.setHaUrl(context, haUrl); AppState.setHaToken(context, haToken); scope.launch { val success = HomeAssistantManager.getInstance(context).connect(haUrl, haToken); Toast.makeText(context, if (success) "Kết nối thành công" else "Kết nối thất bại", Toast.LENGTH_SHORT).show() }; showHaDialog = false }, onDismiss = { showHaDialog = false })
        }
        if (showDeviceInfoDialog) {
            QuantumDeviceInfoDialog(deviceId = AppState.getDeviceId(context), clientId = AppState.getClientId(context), otaUrl = AppState.getOtaUrl(context), wssUrl = AppState.getWssUrl(context) ?: "Chưa cấu hình", onDismiss = { showDeviceInfoDialog = false })
        }
        if (showAboutDialog) {
            QuantumAboutDialog(version = "2.0", onDismiss = { showAboutDialog = false })
        }
    }

    // ==================== GIAO DIỆN NỀN TUYẾT RƠI MỜ ĐỘNG HOÀN CHỈNH ====================
    @Composable
    fun QuantumSettingsBackground() {
        val snowflakes = remember {
            List(45) {
                Snowflake(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    radius = Random.nextFloat() * 4.0f + 1.5f,
                    speed = Random.nextFloat() * 0.003f + 0.001f,
                    alpha = Random.nextFloat() * 0.4f + 0.15f
                )
            }
        }

        var frameState by remember { mutableStateOf(0L) }

        LaunchedEffect(Unit) {
            while (true) {
                withFrameMillis { time ->
                    frameState = time
                }
                snowflakes.forEach { flake ->
                    flake.y += flake.speed
                    if (flake.y > 1.0f) {
                        flake.y = -0.02f
                        flake.x = Random.nextFloat()
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val codeTrigger = frameState

            drawRect(color = Color(0xFF030712))

            drawCircle(
                color = Color(0xFFD946EF).copy(alpha = 0.04f),
                radius = size.width * 0.5f,
                center = Offset(size.width * 0.8f, size.height * 0.15f)
            )

            snowflakes.forEach { flake ->
                val pxX = flake.x * size.width
                val pxY = flake.y * size.height

                drawCircle(
                    color = Color.White.copy(alpha = flake.alpha),
                    radius = flake.radius,
                    center = Offset(pxX, pxY)
                )
            }
        }
    }

    // ==================== PROFILE CARD ====================
    @Composable
    fun ProfileCard(avatarUri: String?, userName: String, userEmail: String, onPickImage: () -> Unit, onEditProfile: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp).border(
                1.dp,
                Brush.linearGradient(listOf(Color(0xFF00E5FF).copy(alpha = 0.6f), Color(0xFFD946EF).copy(alpha = 0.1f))),
                RoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp, topEnd = 4.dp, bottomStart = 4.dp)
            ),
            shape = RoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp, topEnd = 4.dp, bottomStart = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x1F00E5FF))
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(modifier = Modifier.size(86.dp).border(2.dp, Brush.sweepGradient(listOf(Color(0xFF00E5FF), Color(0xFFD946EF), Color(0xFF00E5FF))), CircleShape).padding(4.dp)) {
                        if (avatarUri != null) Image(painter = rememberAsyncImagePainter(avatarUri), contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        else Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.fillMaxSize(), tint = Color(0xFF94A3B8))
                    }
                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF00E5FF)).clickable { onPickImage() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                    }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(userName, style = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(userEmail, style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF00E5FF).copy(alpha = 0.7f)))
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onEditProfile, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFF00E5FF)), modifier = Modifier.border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), CircleShape)) {
                        Text("CHỈNH SỬA HỒ SƠ", fontSize = 12.sp, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }

    @Composable
    fun SectionHeaderView(title: String) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, color = Color(0xFF00E5FF).copy(alpha = 0.8f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp,
                modifier = Modifier.background(Color(0x1A00E5FF), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }

    @Composable
    fun SettingItemQuantum(
        title: String,
        subtitle: String,
        icon: ImageVector,
        trailing: @Composable (() -> Unit)? = null,
        slider: @Composable (() -> Unit)? = null,
        onClick: (() -> Unit)? = null
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .border(0.5.dp, Color(0xFFFFFFFF).copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF))
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(Brush.radialGradient(listOf(Color(0xFF00E5FF).copy(alpha = 0.15f), Color.Transparent), radius = 30f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFE2E8F0), fontFamily = FontFamily.Monospace))
                        if (subtitle.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)))
                        }
                    }
                    trailing?.invoke()
                    if (onClick != null && trailing == null) Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF00E5FF))
                }
                if (slider != null) { slider(); Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }

    // ==================== CÁC DIALOG ====================
    @Composable
    fun QuantumEditDialog(title: String, currentValue: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
        var textValue by remember { mutableStateOf(currentValue) }
        Dialog(onDismissRequest = onDismiss) {
            Card(shape = RoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp, topEnd = 4.dp, bottomStart = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF060913)), modifier = Modifier.fillMaxWidth(0.95f).border(width = 1.dp, brush = Brush.linearGradient(colors = listOf(Color(0xFF00E5FF), Color(0xFFD946EF).copy(alpha = 0.2f))), shape = RoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp, topEnd = 4.dp, bottomStart = 4.dp))) {
                Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                    Text(text = ">> HIỆU CHỈNH_BIẾN_SỐ: ${title.uppercase()}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedTextField(value = textValue, onValueChange = { textValue = it }, modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color.White.copy(alpha = 0.2f), cursorColor = Color(0xFF00E5FF), focusedLabelColor = Color(0xFF00E5FF)), singleLine = true)
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(text = "HỦY LỆNH", color = Color.White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace) }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(onClick = { onSave(textValue) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)), shape = RoundedCornerShape(4.dp)) { Text(text = "THỰC THI GHI ĐÈ", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
                    }
                }
            }
        }
    }

    @Composable
    fun QuantumEditProfileDialog(initialName: String, initialEmail: String, onDismiss: () -> Unit, onSave: (name: String, email: String) -> Unit) {
        var name by remember { mutableStateOf(initialName) }
        var email by remember { mutableStateOf(initialEmail) }
        Dialog(onDismissRequest = onDismiss) {
            Card(shape = RoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp, topEnd = 4.dp, bottomStart = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF060913)), modifier = Modifier.fillMaxWidth(0.95f).border(width = 1.dp, brush = Brush.linearGradient(colors = listOf(Color(0xFF00E5FF), Color(0xFFD946EF).copy(alpha = 0.2f))), shape = RoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp, topEnd = 4.dp, bottomStart = 4.dp))) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = ">> HIỆU CHỈNH_HỒ_SƠ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tên hiển thị") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E5FF)))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E5FF)))
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), modifier = Modifier.weight(1f)) { Text("HỦY LỆNH") }
                        Button(onClick = { onSave(name, email) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)), modifier = Modifier.weight(1f)) { Text("THỰC THI", color = Color.Black) }
                    }
                }
            }
        }
    }

    @Composable
    fun QuantumHomeAssistantDialog(haUrl: String, haToken: String, onUrlChange: (String) -> Unit, onTokenChange: (String) -> Unit, onConnect: () -> Unit, onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Card(shape = RoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp, topEnd = 4.dp, bottomStart = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF060913)), modifier = Modifier.fillMaxWidth(0.95f).border(width = 1.dp, brush = Brush.linearGradient(colors = listOf(Color(0xFF00E5FF), Color(0xFFD946EF).copy(alpha = 0.2f))), shape = RoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp, topEnd = 4.dp, bottomStart = 4.dp))) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(text = ">> KẾT NỐI_HOME_ASSISTANT", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = haUrl, onValueChange = onUrlChange, label = { Text("URL") }, placeholder = { Text("http://homeassistant.local:8123") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E5FF)), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = haToken, onValueChange = onTokenChange, label = { Text("Long-lived Access Token") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E5FF)), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), modifier = Modifier.weight(1f)) { Text("HỦY LỆNH") }
                        Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)), modifier = Modifier.weight(1f)) { Text("KẾT NỐI", color = Color.Black) }
                    }
                }
            }
        }
    }

    @Composable
    fun QuantumDeviceInfoDialog(deviceId: String, clientId: String, otaUrl: String, wssUrl: String, onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Card(shape = RoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp, topEnd = 4.dp, bottomStart = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF060913)), modifier = Modifier.fillMaxWidth(0.95f).border(width = 1.dp, brush = Brush.linearGradient(colors = listOf(Color(0xFF00E5FF), Color(0xFFD946EF).copy(alpha = 0.2f))), shape = RoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp, topEnd = 4.dp, bottomStart = 4.dp))) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(text = ">> THÔNG_TIN_THIẾT_BỊ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    QuantumDataRow(label = "DEVICE ID NODE", value = deviceId, isPrimary = true)
                    Spacer(modifier = Modifier.height(12.dp))
                    QuantumDataRow(label = "CLIENT IDENTITY", value = clientId)
                    Spacer(modifier = Modifier.height(12.dp))
                    QuantumDataRow(label = "OVER-THE-AIR NETWORK", value = otaUrl)
                    Spacer(modifier = Modifier.height(12.dp))
                    QuantumDataRow(label = "WEBSOCKET STREAM GATE", value = wssUrl)
                    Spacer(modifier = Modifier.height(28.dp))
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)), shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.End)) { Text("ĐÓNG", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
                }
            }
        }
    }

    @Composable
    fun QuantumAboutDialog(version: String, onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Card(shape = RoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp, topEnd = 4.dp, bottomStart = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF060913)), modifier = Modifier.fillMaxWidth(0.95f).border(width = 1.dp, brush = Brush.linearGradient(colors = listOf(Color(0xFF00E5FF), Color(0xFFD946EF).copy(alpha = 0.2f))), shape = RoundedCornerShape(topStart = 24.dp, bottomEnd = 24.dp, topEnd = 4.dp, bottomStart = 4.dp))) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(text = ">> GIỚI_THIỆU", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(text = "XiaoZhi AI", color = Color(0xFF00E5FF), fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Trợ lý thông minh đa năng\nTích hợp điều khiển thiết bị, nhận diện cảm xúc,\nđiều khiển nhạc/video, và nhiều tính năng khác.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontFamily = FontFamily.SansSerif, lineHeight = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    QuantumDataRow(label = "PHIÊN BẢN", value = version, isPrimary = true)
                    Spacer(modifier = Modifier.height(28.dp))
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)), shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.End)) { Text("ĐÓNG", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) }
                }
            }
        }
    }

    @Composable
    fun QuantumDataRow(label: String, value: String, isPrimary: Boolean = false) {
        Row(modifier = Modifier.fillMaxWidth().background(Color(0x05FFFFFF), RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).background(if (isPrimary) Color(0xFFD946EF) else Color(0xFF00E5FF), CircleShape))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(label.uppercase(), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(2.dp))
                Text(value.ifEmpty { "NULL_EMPTY" }, color = if (isPrimary) Color(0xFF00E5FF) else Color(0xFFCBD5E1), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ==================== COMPOSABLE CHO MÀN HÌNH CHỌN ỨNG DỤNG ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationAppListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var appList by remember { mutableStateOf<List<AppItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val whitelist = AppState.getNotificationWhitelist(context)
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = apps.mapNotNull { info ->
                val name = pm.getApplicationLabel(info).toString()
                if (name.isBlank()) return@mapNotNull null
                val pkg = info.packageName
                AppItem(name, pkg, whitelist.contains(pkg))
            }.sortedBy { it.name }
            appList = list
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ứng dụng đọc thông báo", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF00E5FF))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color(0xFF030712)
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(appList) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = app.name,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = app.isEnabled,
                        onCheckedChange = { enabled ->
                            val newList = appList.map {
                                if (it.packageName == app.packageName) it.copy(isEnabled = enabled) else it
                            }
                            appList = newList
                            AppState.toggleNotificationForApp(context, app.packageName, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0x6600E5FF)
                        )
                    )
                }
                Divider(color = Color.White.copy(alpha = 0.1f))
            }
        }
    }
}

data class AppItem(val name: String, val packageName: String, val isEnabled: Boolean)

// ==================== DATA CLASS ====================
sealed class SettingsItem
data class MenuItemData(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val trailing: @Composable (() -> Unit)? = null,
    val slider: @Composable (() -> Unit)? = null,
    val onClick: (() -> Unit)? = null
) : SettingsItem()
data class SectionHeader(val title: String) : SettingsItem()