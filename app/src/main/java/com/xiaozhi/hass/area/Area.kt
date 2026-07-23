package com.xiaozhi.hass.area

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "areas")
data class Area(
    @PrimaryKey
    val id: String,
    val name: String,
    val parentId: String? = null // hierarchy
)
