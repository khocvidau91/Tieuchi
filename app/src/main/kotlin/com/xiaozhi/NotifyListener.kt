package com.xiaozhi

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class NotifyListener : NotificationListenerService() {

    companion object {
        var lastPackage: String = ""
        var lastTitle: String = ""
        var lastText: String = ""
        var lastTime: Long = 0L
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            val extras = it.notification.extras
            lastPackage = it.packageName
            lastTitle = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
            lastText = extras.getString(android.app.Notification.EXTRA_TEXT) ?: ""
            lastTime = it.postTime

            val intent = Intent("com.xiaozhi.NEW_NOTIFICATION")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }

    override fun onListenerConnected() {}
    override fun onListenerDisconnected() {}
}