package com.xiaozhi.hass.scene

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xiaozhi.hass.entity.EntityState

@Entity(tableName = "scenes")
data class Scene(
    @PrimaryKey
    val id: String,
    val name: String,
    val entitiesJson: String // JSON array of EntityState
) {
    fun getEntityStates(): List<EntityState> {
        val type = object : TypeToken<List<EntityState>>() {}.type
        return Gson().fromJson(entitiesJson, type)
    }

    companion object {
        fun create(id: String, name: String, entities: List<EntityState>): Scene {
            return Scene(id, name, Gson().toJson(entities))
        }
    }
}
