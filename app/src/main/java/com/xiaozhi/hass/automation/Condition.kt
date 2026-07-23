package com.xiaozhi.hass.automation

sealed class Condition {
    data class StateCondition(val entityId: String, val state: String) : Condition()
    data class NumericCondition(val entityId: String, val operator: String, val value: Double) : Condition()
    data class AndCondition(val conditions: List<Condition>) : Condition()
    data class OrCondition(val conditions: List<Condition>) : Condition()
    data class TimeRange(val start: String, val end: String) : Condition() // HH:mm
    // thêm các condition khác
}
