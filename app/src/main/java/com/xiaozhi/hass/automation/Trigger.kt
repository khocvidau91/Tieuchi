package com.xiaozhi.hass.automation

sealed class Trigger {
    data class StateChange(val entityId: String, val toState: String) : Trigger()
    data class TimeTrigger(val cron: String) : Trigger()
    data class EventTrigger(val eventType: String, val data: Map<String, Any> = emptyMap()) : Trigger()
    data class MqttMessage(val topic: String, val payload: String) : Trigger()
    // thêm các trigger khác nếu cần
}
