// app/src/main/java/com/xiaozhi/services/FileWatcherService.kt
package com.xiaozhi.services

import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import com.google.gson.JsonObject
import com.xiaozhi.ai.McpNotificationManager
import java.io.File

class FileWatcherService : Service() {
    private lateinit var observer: FileObserver

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val path = Environment.getExternalStorageDirectory().absolutePath + "/Download"
        val downloadDir = File(path).apply { if (!exists()) mkdirs() }

        observer = object : FileObserver(downloadDir, CREATE or MOVED_TO) {
            override fun onEvent(event: Int, relativePath: String?) {
                if (relativePath != null) {
                    val fullPath = "$path/$relativePath"
                    val file = File(fullPath)
                    val mime = guessMimeType(file.name)

                    Log.d("FileWatcher", "📄 New file: $relativePath ($mime)")
                    val params = JsonObject().apply {
                        addProperty("path", fullPath)
                        addProperty("mime", mime)
                        addProperty("size", file.length())
                    }
                    McpNotificationManager.sendNotification("notifications/new_file", params)
                }
            }
        }
        observer.startWatching()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { observer.stopWatching(); super.onDestroy() }

    private fun guessMimeType(name: String) = when {
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".txt", true) -> "text/plain"
        name.endsWith(".mp4", true) -> "video/mp4"
        else -> "application/octet-stream"
    }
}