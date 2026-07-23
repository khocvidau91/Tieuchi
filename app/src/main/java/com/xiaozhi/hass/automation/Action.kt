package com.xiaozhi.hass.automation

sealed class Action {
    data class CallService(val domain: String, val service: String, val data: Map<String, Any> = emptyMap()) : Action()
    data class Wait(val milliseconds: Long) : Action()
    data class SceneActivate(val sceneId: String) : Action()
    data class ScriptExecute(val scriptId: String) : Action()
    data class SendNotification(val title: String, val message: String) : Action()
    // thêm các action khác
}
