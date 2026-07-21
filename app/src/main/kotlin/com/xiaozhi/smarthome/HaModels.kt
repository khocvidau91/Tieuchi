package com.xiaozhi.smarthome

data class HaEntity(
    val entityId: String,
    val name: String,
    val state: String,
    val domain: String,
    val attributes: Map<String, Any> = emptyMap()
)

data class HaServiceCall(
    val domain: String,
    val service: String,
    val target: Map<String, String>? = null,
    val data: Map<String, Any>? = null
)

data class DeviceCapability(
    val entityId: String,
    val domain: String,
    val actions: List<String>
)

data class Room(
    val name: String,
    val entities: List<String>
)