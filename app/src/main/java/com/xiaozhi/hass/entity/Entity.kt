package com.xiaozhi.hass.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entities")
data class Entity(
    @PrimaryKey
    val id: String, // e.g., light.living_room
    val name: String,
    val type: EntityType,
    val deviceId: String? = null,
    val areaId: String? = null,
    val attributes: String = "{}", // JSON string
    var state: String = "unknown",
    var lastUpdated: Long = System.currentTimeMillis()
)
