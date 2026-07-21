@file:OptIn(ExperimentalMaterial3Api::class)

package com.xiaozhi

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun QuantumCameraScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraList by remember { mutableStateOf(CameraManager.getCameras(context)) }
    var defaultCameraId by remember { mutableStateOf(CameraManager.getDefaultCameraId(context)) }
    var isRemoteMode by remember { mutableStateOf(false) }
    var activeDialogCamera by remember { mutableStateOf<CameraInfo?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var cameraToWatch by remember { mutableStateOf<CameraInfo?>(null) }
    
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0 to 0) }
    var scannedCameras by remember { mutableStateOf<List<CameraInfo>>(emptyList()) }
    var showScanResultDialog by remember { mutableStateOf(false) }

    val activeDefaultCamera = cameraList.find { it.id == defaultCameraId }

    Box(modifier = Modifier.fillMaxSize()) {
        QuantumCameraBackground()

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
                            "MẠNG LƯỚI CAMERA QUANTUM",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            letterSpacing = 2.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF00E5FF)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            scope.launch {
                                isScanning = true
                                scanProgress = 0 to 254
                                val result = withContext(Dispatchers.IO) {
                                    CameraManager.scanLocalNetwork(context) { current, total ->
                                        scope.launch { scanProgress = current to total }
                                    }
                                }
                                scannedCameras = result
                                isScanning = false
                                if (result.isNotEmpty()) {
                                    showScanResultDialog = true
                                } else {
                                    Toast.makeText(context, "Không tìm thấy camera RTSP nào trong mạng LAN", Toast.LENGTH_LONG).show()
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = "Quét mạng", tint = Color(0xFF00E5FF))
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        activeDialogCamera = null
                        showAddEditDialog = true
                    },
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color.Black,
                    shape = CircleShape
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Thêm camera")
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    QuantumNetworkSelector(
                        isRemoteMode = isRemoteMode,
                        onModeChange = { isRemoteMode = it }
                    )
                }

                item {
                    Text(
                        text = ">> CỔNG HÌNH ẢNH MẶC ĐỊNH",
                        color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    if (activeDefaultCamera != null) {
                        QuantumLiveStreamCard(
                            camera = activeDefaultCamera,
                            isRemoteMode = isRemoteMode
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .background(Color(0x05FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "CHƯA CẤU HÌNH CAMERA CHỦ LỰC",
                                color = Color.White.copy(alpha = 0.4f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = ">> TẤT CẢ CÁC NODE KẾT NỐI (${cameraList.size})",
                        color = Color(0xFFD946EF).copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(cameraList, key = { it.id }) { cam ->
                    QuantumCameraItemRow(
                        camera = cam,
                        isDefault = cam.id == defaultCameraId,
                        isRemoteMode = isRemoteMode,
                        onSelectDefault = {
                            CameraManager.setDefaultCameraId(context, cam.id)
                            defaultCameraId = cam.id
                        },
                        onPlay = { cameraToWatch = cam },
                        onEdit = {
                            activeDialogCamera = cam
                            showAddEditDialog = true
                        },
                        onDelete = {
                            CameraManager.removeCamera(context, cam.id)
                            cameraList = CameraManager.getCameras(context)
                            if (defaultCameraId == cam.id) defaultCameraId = null
                        }
                    )
                }
            }
        }
    }

    if (isScanning) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Đang quét mạng LAN...") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { scanProgress.first.toFloat() / scanProgress.second },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Đã quét: ${scanProgress.first}/${scanProgress.second}")
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    if (showScanResultDialog) {
        AlertDialog(
            onDismissRequest = { showScanResultDialog = false },
            title = { Text("📡 Camera tìm thấy (${scannedCameras.size})") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scannedCameras) { cam ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    CameraManager.addCamera(context, cam)
                                    cameraList = CameraManager.getCameras(context)
                                    showScanResultDialog = false
                                    Toast.makeText(context, "Đã thêm ${cam.name}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color(0xFF00E5FF))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(cam.name, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(cam.ip ?: "", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScanResultDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    if (showAddEditDialog) {
        QuantumCameraEditDialog(
            camera = activeDialogCamera,
            onDismiss = { showAddEditDialog = false },
            onSave = {
                cameraList = CameraManager.getCameras(context)
                showAddEditDialog = false
            }
        )
    }

    if (cameraToWatch != null) {
        QuantumMonitorDialog(
            camera = cameraToWatch!!,
            isRemoteMode = isRemoteMode,
            onDismiss = { cameraToWatch = null }
        )
    }
}

@Composable
fun QuantumNetworkSelector(isRemoteMode: Boolean, onModeChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF))
    ) {
        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onModeChange(false) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isRemoteMode) Color(0xFF00E5FF) else Color.Transparent,
                    contentColor = if (!isRemoteMode) Color.Black else Color.White
                )
            ) {
                Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("CHUNG WIFI (LAN)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { onModeChange(true) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRemoteMode) Color(0xFFD946EF) else Color.Transparent,
                    contentColor = if (isRemoteMode) Color.Black else Color.White
                )
            ) {
                Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("KHÁC MẠNG (WAN)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun QuantumLiveStreamCard(camera: CameraInfo, isRemoteMode: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val streamUrl = camera.getQuantumUrl(isRemoteMode)
            key(streamUrl) {
                QuantumCoreStreamer(rtspUrl = streamUrl)
            }
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                Text(
                    text = "LIVE MẶC ĐỊNH: ${camera.name.uppercase()}",
                    color = Color(0xFF00E5FF),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun QuantumCameraItemRow(
    camera: CameraInfo,
    isDefault: Boolean,
    isRemoteMode: Boolean,
    onSelectDefault: () -> Unit,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0x08FFFFFF))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp, 36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                if (isDefault) Color(0xFFD946EF) else Color(0xFF00E5FF),
                                Color.Transparent
                            )
                        )
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    camera.name,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    camera.getQuantumUrl(isRemoteMode),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayCircle, contentDescription = "Stream", tint = Color(0xFF00E5FF))
            }
            IconButton(onClick = onSelectDefault) {
                Icon(
                    imageVector = if (isDefault) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Đặt làm mặc định",
                    tint = if (isDefault) Color(0xFFD946EF) else Color.White.copy(alpha = 0.3f)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Sửa", tint = Color.White.copy(alpha = 0.6f))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Xóa", tint = Color.Red.copy(alpha = 0.6f))
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun QuantumCoreStreamer(rtspUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(Uri.parse(rtspUrl)))
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
fun QuantumCameraEditDialog(
    camera: CameraInfo?,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(camera?.name ?: "") }
    var ip by remember { mutableStateOf(camera?.ip ?: "") }
    var port by remember { mutableStateOf(camera?.port?.toString() ?: "554") }
    var user by remember { mutableStateOf(camera?.username ?: "") }
    var pass by remember { mutableStateOf(camera?.password ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(
                topStart = 24.dp,
                bottomEnd = 24.dp,
                topEnd = 4.dp,
                bottomStart = 4.dp
            ),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF060913)),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF00E5FF),
                            Color(0xFFD946EF).copy(alpha = 0.2f)
                        )
                    ),
                    shape = RoundedCornerShape(
                        topStart = 24.dp,
                        bottomEnd = 24.dp,
                        topEnd = 4.dp,
                        bottomStart = 4.dp
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (camera == null) ">> THIẾT LẬP_NODE_CAMERA_MỚI" else ">> CẬP NHẬT_NODE_CAMERA",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên Camera") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E5FF)),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Địa chỉ IP / Tên miền") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E5FF)),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Cổng (Port)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E5FF)),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Tài khoản đăng nhập") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E5FF)),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Mật mã (Password)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF00E5FF)),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            "HỦY LỆNH",
                            color = Color.White.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(4.dp),
                        onClick = {
                            if (name.trim().isEmpty() || ip.trim().isEmpty()) {
                                Toast.makeText(context, "Thiếu định danh cấu trúc!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val newCam = CameraInfo(
                                id = camera?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                ip = ip.trim(),
                                port = port.toIntOrNull() ?: 554,
                                username = user.ifEmpty { null },
                                password = pass.ifEmpty { null }
                            )
                            if (camera == null) {
                                CameraManager.addCamera(context, newCam)
                            } else {
                                CameraManager.updateCamera(context, newCam)
                            }
                            onSave()
                        }
                    ) {
                        Text(
                            "THỰC THI GHI ĐÈ",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuantumMonitorDialog(
    camera: CameraInfo,
    isRemoteMode: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                QuantumCoreStreamer(rtspUrl = camera.getQuantumUrl(isRemoteMode))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Đóng",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun QuantumCameraBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "quantum_camera_bg")
    val scanlineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanline_node"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = Color(0xFF030712))
        val animatedY = size.height * scanlineY
        drawLine(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFFD946EF).copy(alpha = 0.08f),
                    Color.Transparent
                )
            ),
            start = Offset(0f, animatedY - 60f),
            end = Offset(size.width, animatedY + 60f),
            strokeWidth = 30f
        )
    }
}