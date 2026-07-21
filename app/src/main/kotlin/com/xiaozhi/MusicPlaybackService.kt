package com.xiaozhi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class MusicPlaybackService : Service() {

    companion object {
        private const val TAG = "MusicPlaybackService"
        const val ACTION_PLAY = "com.xiaozhi.action.PLAY"
        const val ACTION_STOP = "com.xiaozhi.action.STOP"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_THUMBNAIL = "thumbnail"
        const val NOTIFICATION_CHANNEL_ID = "music_playback"
        const val NOTIFICATION_ID = 1001

        const val ACTION_WAVEFORM = "com.xiaozhi.WAVEFORM"
        const val EXTRA_WAVEFORM = "waveform"
    }

    private var exoPlayer: ExoPlayer? = null
    private var currentTitle: String? = null
    private var visualizer: Visualizer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val title = intent.getStringExtra(EXTRA_TITLE)
                if (!url.isNullOrEmpty()) {
                    currentTitle = title ?: "Đang phát nhạc"
                    Log.d(TAG, "Start playback: $title - $url")
                    startPlayback(url)
                } else {
                    Log.e(TAG, "URL is null or empty")
                }
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stop playback")
                stopPlayback()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "Task removed, releasing player")
        stopPlayback()
        stopSelf()
    }

    override fun onDestroy() {
        stopVisualizer()
        stopPlayback()
        super.onDestroy()
    }

    private fun startPlayback(url: String) {
        Log.d(TAG, "startPlayback: $url")
        exoPlayer?.release()
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36")
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(mapOf(
                    "Accept" to "audio/webm,audio/ogg,audio/mp4,audio/*;q=0.9,*/*;q=0.8",
                    "Accept-Language" to "en-US,en;q=0.9",
                    "Referer" to "https://www.youtube.com/",
                    "Origin" to "https://www.youtube.com",
                    "Range" to "bytes=0-"
                ))

            val source = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            setMediaSource(source)
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    Log.d(TAG, "Playback state changed: $state")
                    when (state) {
                        Player.STATE_READY -> {
                            Log.d(TAG, "Player ready, audio session ID: $audioSessionId")
                            startVisualizer(audioSessionId)
                        }
                        Player.STATE_ENDED -> {
                            Log.d(TAG, "Playback ended")
                            stopSelf()
                        }
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "ExoPlayer error: ${error.message}", error)
                    if (error.cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        val httpError = error.cause as androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                        Log.e(TAG, "HTTP Error: ${httpError.responseCode}")
                        if (httpError.responseCode == 403) {
                            Log.e(TAG, "403 Forbidden - Thử lại với cookie hoặc URL khác")
                        }
                    }
                    stopSelf()
                }
            })
            prepare()
            play()
            Log.d(TAG, "Player started")
        }

        startForeground(NOTIFICATION_ID, buildNotification(currentTitle ?: "Nhạc"))
    }

    private fun startVisualizer(audioSessionId: Int) {
        stopVisualizer()
        if (audioSessionId == 0) {
            Log.e(TAG, "Invalid audio session id")
            return
        }
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer,
                        waveform: ByteArray,
                        samplingRate: Int
                    ) {
                        val amplitudes = FloatArray(waveform.size) { i ->
                            abs(waveform[i].toFloat()) / 128f
                        }
                        val intent = Intent(ACTION_WAVEFORM).apply {
                            putExtra(EXTRA_WAVEFORM, amplitudes)
                        }
                        sendBroadcast(intent)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer,
                        fft: ByteArray,
                        samplingRate: Int
                    ) {}
                }, Visualizer.getMaxCaptureRate(), true, false)
                enabled = true
                Log.d(TAG, "Visualizer started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start visualizer", e)
        }
    }

    private fun stopVisualizer() {
        visualizer?.let {
            it.enabled = false
            it.release()
        }
        visualizer = null
    }

    private fun stopPlayback() {
        stopVisualizer()
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(title: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Đang phát nhạc")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Phát nhạc",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kênh thông báo khi phát nhạc"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}