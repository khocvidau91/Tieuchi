package com.xiaozhi.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityActionService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                "com.xiaozhi.ACCESSIBILITY_ACTION" -> {
                    val action = it.getIntExtra("action", -1)
                    if (action != -1) performGlobalAction(action)
                }
                "com.xiaozhi.ACCESSIBILITY_SCROLL" -> {
                    // Có thể implement scroll gesture nếu cần (tạm bỏ qua)
                }
                "com.xiaozhi.CLICK_TEXT" -> {
                    val text = it.getStringExtra("text") ?: return START_NOT_STICKY
                    clickOnText(text)
                }
                "com.xiaozhi.CLICK_AT" -> {
                    val x = it.getFloatExtra("x", 0f)
                    val y = it.getFloatExtra("y", 0f)
                    clickAt(x, y)
                }
                "com.xiaozhi.INPUT_TEXT" -> {
                    val text = it.getStringExtra("text") ?: return START_NOT_STICKY
                    typeText(text)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun clickOnText(text: String) {
        val root = rootInActiveWindow ?: return
        val node = root.findAccessibilityNodeInfosByText(text)?.firstOrNull()
        node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun clickAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun typeText(text: String) {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }
}