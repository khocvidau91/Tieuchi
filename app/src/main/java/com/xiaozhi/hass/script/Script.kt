package com.xiaozhi.hass.script

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xiaozhi.hass.automation.Action

@Entity(tableName = "scripts")
data class Script(
    @PrimaryKey
    val id: String,
    val name: String,
    val actionsJson: String // JSON array of Action
) {
    fun getActions(): List<Action> {
        val type = object : TypeToken<List<Action>>() {}.type
        return Gson().fromJson(actionsJson, type)
    }

    companion object {
        fun create(id: String, name: String, actions: List<Action>): Script {
            return Script(id, name, Gson().toJson(actions))
        }
    }
}
