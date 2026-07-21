package com.xiaozhi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class HotwordForegroundService : Service() {

    companion object {
        private const val TAG = "HotwordService"
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "hotword_channel"
    }

    private var porcupineManager: PorcupineManager? = null
    private var audioManager: AudioManager? = null
    private var screenOffReceiver: BroadcastReceiver? = null

    private val wakeWordCallback = PorcupineManagerCallback { keywordIndex ->
        if (keywordIndex == 0) {
            Log.i(TAG, "Hotword detected, opening assistant")
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                Intent("com.xiaozhi.WAKE_WORD_TRIGGERED")
            )
            val intent = Intent(this, OverlayActivity::class.java).apply {
                putExtra(OverlayActivity.EXTRA_MODE, OverlayActivity.MODE_LISTENING)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {}
                Intent.ACTION_SCREEN_ON -> {}
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        initializePorcupine()
        registerScreenOffReceiver()
        Log.i(TAG, "Hotword service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initializePorcupine() {
        try {
            val builder = PorcupineManager.Builder()
                .setAccessKey(BuildConfig.PICOVOICE_ACCESS_KEY)
                .setSensitivity(0.5f)
                .setKeywordPath("ok-lily.ppn")
                .setModelPath("porcupine_params.pv")
            porcupineManager = builder.build(applicationContext, wakeWordCallback)
            porcupineManager?.start()
            Log.i(TAG, "Porcupine started, listening for 'Ok Lily'")
        } catch (e: PorcupineException) {
            Log.e(TAG, "Failed to init Porcupine", e)
            stopSelf()
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("XiaoZhi AI")
            .setContentText("Đang lắng nghe 'Ok Lily'")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setSilent(true)
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hotword Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun registerScreenOffReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenStateReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        porcupineManager?.stop()
        porcupineManager?.delete()
        unregisterReceiver(screenStateReceiver)
        Log.i(TAG, "Hotword service stopped")
    }
}