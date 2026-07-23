package com.xiaozhi.hass.database.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xiaozhi.hass.entity.EntityType

class Converters {
    @TypeConverter
    fun fromStringMap(value: String): Map<String, Any> {
        val type = object : TypeToken<Map<String, Any>>() {}.type
        return Gson().fromJson(value, type) ?: emptyMap()
    }

    @TypeConverter
    fun toStringMap(map: Map<String, Any>): String {
        return Gson().toJson(map)
    }

    @TypeConverter
    fun fromEntityType(value: EntityType): String {
        return value.name
    }

    @TypeConverter
    fun toEntityType(value: String): EntityType {
        return EntityType.valueOf(value)
    }
}
