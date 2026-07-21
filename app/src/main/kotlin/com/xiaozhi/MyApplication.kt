package com.xiaozhi

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.xiaozhi.smarthome.HomeAssistantManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MyApplication : Application() {
    lateinit var audioManager: XiaoZhiAudioManager
        private set

    override fun onCreate() {
        super.onCreate()
        
        ShizukuManager.init(this)
        
        audioManager = XiaoZhiAudioManager(this)
        audioManager.start()

        // ========== KHÔNG CÓ KHỞI TẠO YT-DLP ==========
        // (Binary sẽ được copy từ assets khi cần qua YtDlpClient.initialize())

        // ========== WebSocket & HomeAssistant ==========
        if (AppState.isActivated(this)) {
            val deviceId = AppState.getDeviceId(this)
            val clientId = AppState.getClientId(this)
            val wsUrl = AppState.getWssUrl(this)
            val wsToken = AppState.getWsToken(this)
            if (!wsUrl.isNullOrEmpty() && !wsToken.isNullOrEmpty()) {
                val wsManager = WebSocketManager(wsUrl, wsToken, deviceId, clientId)
                WebSocketManager.wsManager = wsManager
                wsManager.connect()
                Log.i("MyApp", "WebSocket initialized from Application")
            }

            val url = AppState.getHaUrl(this)
            val token = AppState.getHaToken(this)
            if (!url.isNullOrEmpty() && !token.isNullOrEmpty()) {
                ProcessLifecycleOwner.get().lifecycleScope.launch {
                    HomeAssistantManager.getInstance(this@MyApplication).connect(url, token)
                }
            }
        }

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(thread, throwable)
        }
    }

    private fun handleCrash(thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()
        val fullLog = "Thread: ${thread.name}\n${stackTrace}"

        try {
            val crashDir = File(getExternalFilesDir(null), "crash_logs")
            crashDir.mkdirs()
            val crashFile = File(crashDir, "crash_${System.currentTimeMillis()}.txt")
            crashFile.writeText(fullLog)
        } catch (e: Exception) { }

        if (thread == Looper.getMainLooper().thread) {
            val intent = Intent(this, CrashReportActivity::class.java).apply {
                putExtra(CrashReportActivity.EXTRA_STACK_TRACE, fullLog)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
        } else {
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        }
    }
}