package com.xiaozhi

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// ==================== Data classes ====================
data class AppVideoInfo(
    val id: String,
    val title: String,
    val uploader: String,
    val duration: Int,
    val durationStr: String,
    val webpageUrl: String,
    val thumbnail: String
)

data class StreamInfo(
    val success: Boolean,
    val url: String?,
    val title: String?,
    val thumbnail: String?,
    val error: String? = null
)

data class DownloadResult(
    val success: Boolean,
    val fileUri: Uri? = null,
    val fileName: String? = null,
    val error: String? = null
)

// ==================== YtDlpClient ====================
object YtDlpClient {
    private const val TAG = "YtDlpClient"
    private var binaryPath: String? = null
    private var isInitialized = false

    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return@withContext true
        }
        try {
            val destFile = File(context.filesDir, "yt-dlp")
            context.assets.open("yt-dlp").use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.setExecutable(true)
            // Đảm bảo quyền 755
            Runtime.getRuntime().exec(arrayOf("chmod", "755", destFile.absolutePath)).waitFor()

            // Copy libz.so.1
            val libZFile = File(context.filesDir, "libz.so.1")
            if (!libZFile.exists()) {
                try {
                    context.assets.open("libz.so.1").use { input ->
                        FileOutputStream(libZFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "libz.so.1 not found in assets")
                }
            }

            binaryPath = destFile.absolutePath
            isInitialized = true

            Log.d(TAG, "Binary: $binaryPath, canExecute: ${destFile.canExecute()}")
            Log.d(TAG, "LD_LIBRARY_PATH: ${context.filesDir.absolutePath}:/system/lib64:/system/lib")

            // Test version
            testBinary(context)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Initialize failed", e)
            false
        }
    }

    private suspend fun testBinary(context: Context) {
        try {
            val result = executeYtDlp(context, "--version")
            Log.d(TAG, "yt-dlp version test: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Version test failed", e)
        }
    }

    private suspend fun executeYtDlp(context: Context, vararg args: String): String? =
        withContext(Dispatchers.IO) {
            if (!isInitialized) {
                Log.e(TAG, "YtDlpClient not initialized")
                return@withContext null
            }
            try {
                // Chạy trực tiếp, không qua shell
                val command = arrayOf(binaryPath!!, *args)
                Log.d(TAG, "Executing: ${command.joinToString(" ")}")
                val processBuilder = ProcessBuilder(*command)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["LD_LIBRARY_PATH"] = "${context.filesDir.absolutePath}:/system/lib64:/system/lib"
                    }
                val process = processBuilder.start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    output
                } else {
                    Log.e(TAG, "Exit code $exitCode, output: $output")
                    null
                }
            } catch (e: IOException) {
                Log.e(TAG, "Execution error: ${e.message}", e)
                // In thêm cause để biết nguyên nhân
                if (e.cause != null) {
                    Log.e(TAG, "Cause: ${e.cause?.message}")
                }
                null
            }
        }

    suspend fun searchFirst(context: Context, query: String): AppVideoInfo? {
        val output = executeYtDlp(
            context,
            "ytsearch1:$query",
            "--dump-json",
            "--no-warnings",
            "--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        ) ?: return null

        return try {
            val json = if (output.trim().startsWith("{")) {
                JSONObject(output)
            } else {
                val firstLine = output.lines().firstOrNull { it.trim().startsWith("{") }
                JSONObject(firstLine ?: return null)
            }
            val entry = if (json.has("entries")) {
                json.getJSONArray("entries").getJSONObject(0)
            } else json
            AppVideoInfo(
                id = entry.getString("id"),
                title = entry.getString("title"),
                uploader = entry.optString("uploader", "Unknown"),
                duration = entry.optInt("duration", 0),
                durationStr = formatDuration(entry.optInt("duration", 0)),
                webpageUrl = "https://youtube.com/watch?v=${entry.getString("id")}",
                thumbnail = entry.optString("thumbnail", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
            null
        }
    }

    suspend fun getAudioStream(context: Context, videoUrl: String): StreamInfo {
        val output = executeYtDlp(
            context,
            videoUrl,
            "-f", "bestaudio",
            "--get-url",
            "--no-warnings"
        ) ?: return StreamInfo(false, null, null, null, "No output")

        val url = output.lines().firstOrNull { it.isNotBlank() }
        return if (url != null) {
            val meta = executeYtDlp(context, videoUrl, "--dump-json", "--no-warnings")
            var title: String? = null
            var thumbnail: String? = null
            if (meta != null) {
                try {
                    val json = JSONObject(meta)
                    title = json.optString("title", null)
                    thumbnail = json.optString("thumbnail", null)
                } catch (_: Exception) {}
            }
            StreamInfo(true, url, title, thumbnail)
        } else {
            StreamInfo(false, null, null, null, "No URL")
        }
    }

    suspend fun getVideoStream(context: Context, videoUrl: String): StreamInfo {
        val output = executeYtDlp(
            context,
            videoUrl,
            "-f", "best",
            "--get-url",
            "--no-warnings"
        ) ?: return StreamInfo(false, null, null, null, "No output")

        val url = output.lines().firstOrNull { it.isNotBlank() }
        return if (url != null) {
            val meta = executeYtDlp(context, videoUrl, "--dump-json", "--no-warnings")
            var title: String? = null
            var thumbnail: String? = null
            if (meta != null) {
                try {
                    val json = JSONObject(meta)
                    title = json.optString("title", null)
                    thumbnail = json.optString("thumbnail", null)
                } catch (_: Exception) {}
            }
            StreamInfo(true, url, title, thumbnail)
        } else {
            StreamInfo(false, null, null, null, "No URL")
        }
    }

    suspend fun downloadMedia(
        context: Context,
        videoUrl: String,
        format: String = "best",
        outputFileName: String? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.tmp")
        val args = mutableListOf(
            videoUrl,
            "-f", format,
            "-o", tempFile.absolutePath,
            "--no-warnings"
        )
        try {
            val result = executeYtDlp(context, *args.toTypedArray())
            if (result == null) return@withContext DownloadResult(false, error = "yt-dlp failed")
            val meta = executeYtDlp(context, videoUrl, "--dump-json", "--no-warnings")
            var finalName = outputFileName
            if (finalName == null && meta != null) {
                try {
                    val json = JSONObject(meta)
                    val title = json.optString("title", "video")
                    val ext = when (format) {
                        "bestaudio" -> "m4a"
                        "bestvideo" -> "mp4"
                        else -> "mp4"
                    }
                    finalName = "$title.$ext".replace(Regex("[^a-zA-Z0-9._-]"), "_")
                } catch (_: Exception) {}
            }
            if (finalName == null) {
                finalName = "video_${System.currentTimeMillis()}.${if (format == "bestaudio") "m4a" else "mp4"}"
            }
            val uri = saveToMediaStore(context, tempFile, finalName)
            tempFile.delete()
            if (uri != null) {
                DownloadResult(true, uri, finalName)
            } else {
                DownloadResult(false, error = "Failed to save to MediaStore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            DownloadResult(false, error = e.message)
        }
    }

    private fun saveToMediaStore(context: Context, sourceFile: File, fileName: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, guessMimeType(fileName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            return uri
        }
        return null
    }

    private fun guessMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".mp4") -> "video/mp4"
            fileName.endsWith(".m4a") -> "audio/mp4"
            fileName.endsWith(".webm") -> "video/webm"
            fileName.endsWith(".mp3") -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }

    private fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", mins, secs)
    }
}