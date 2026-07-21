package com.xiaozhi.ai

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class QuantumNotificationReceiver : NotificationListenerService() {

    private val receiverScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val TAG = "🧠 LõiThôngBáoToànCục"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "⚡ Đã kết nối vào Trục thần kinh thông báo Android thành công!")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return

        val packageName = sbn.packageName
        val title = extras.getCharSequence("android.title")?.toString() ?: "ẨN_DANH"
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (text.isEmpty()) return

        Log.d(TAG, "📥 Thu nhận xung từ [$packageName] -> $title: $text")

        receiverScope.launch {
            EventBus.emit(
                AIEvent.GlobalNotificationIntercepted(
                    appPackage = packageName,
                    title = title,
                    message = text
                )
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}