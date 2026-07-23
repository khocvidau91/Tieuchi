package com.xiaozhi.hass.entity

data class EntityState(
    val entityId: String,
    val state: String,
    val attributes: Map<String, Any> = emptyMap(),
    val lastUpdated: Long = System.currentTimeMillis()
)
