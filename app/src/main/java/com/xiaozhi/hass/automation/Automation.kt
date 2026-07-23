package com.xiaozhi.hass.automation

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "automations")
data class Automation(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String? = null,
    val triggersJson: String, // JSON array of Trigger
    val conditionsJson: String, // JSON array of Condition
    val actionsJson: String, // JSON array of Action
    val enabled: Boolean = true,
    val mode: String = "single" // single, restart, queued
) {
    fun getTriggers(): List<Trigger> {
        val type = object : TypeToken<List<Trigger>>() {}.type
        return Gson().fromJson(triggersJson, type)
    }

    fun getConditions(): List<Condition> {
        val type = object : TypeToken<List<Condition>>() {}.type
        return Gson().fromJson(conditionsJson, type)
    }

    fun getActions(): List<Action> {
        val type = object : TypeToken<List<Action>>() {}.type
        return Gson().fromJson(actionsJson, type)
    }

    companion object {
        fun create(
            id: String,
            name: String,
            description: String?,
            triggers: List<Trigger>,
            conditions: List<Condition>,
            actions: List<Action>,
            enabled: Boolean = true,
            mode: String = "single"
        ): Automation {
            val gson = Gson()
            return Automation(
                id = id,
                name = name,
                description = description,
                triggersJson = gson.toJson(triggers),
                conditionsJson = gson.toJson(conditions),
                actionsJson = gson.toJson(actions),
                enabled = enabled,
                mode = mode
            )
        }
    }
}
