package com.xiaozhi

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

/**
 * VoiceInteractionService – lắng nghe từ khóa hệ thống (Ok Google) mà không cần icon notification.
 */
class XiaoZhiVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.i("XiaoZhiVoiceService", "VoiceInteractionService ready – no notification icon")
    }
}

/**
 * VoiceInteractionSessionService – tạo session khi trợ lý được kích hoạt.
 */
class XiaoZhiVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Log.d("XiaoZhiSessionService", "onNewSession called")
        return XiaoZhiVoiceInteractionSession(this, args)
    }
}

/**
 * VoiceInteractionSession – xử lý hiển thị overlay khi trợ lý mở.
 */
class XiaoZhiVoiceInteractionSession(
    private val sessionService: XiaoZhiVoiceInteractionSessionService,
    args: Bundle?
) : VoiceInteractionSession(sessionService, android.os.Handler(android.os.Looper.getMainLooper())) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d("XiaoZhiSession", "onShow - opening overlay")
        val intent = Intent(sessionService.applicationContext, OverlayActivity::class.java).apply {
            putExtra(OverlayActivity.EXTRA_MODE, OverlayActivity.MODE_ASSISTANT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        sessionService.applicationContext.startActivity(intent)
        finish()
    }

    override fun onHide() {
        super.onHide()
        Log.d("XiaoZhiSession", "onHide")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("XiaoZhiSession", "onDestroy")
    }
}