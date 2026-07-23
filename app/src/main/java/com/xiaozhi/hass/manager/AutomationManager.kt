package com.xiaozhi.hass.manager

import android.content.Context
import com.xiaozhi.hass.automation.Automation
import com.xiaozhi.hass.database.AppDatabase
import kotlinx.coroutines.flow.Flow

class AutomationManager(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).automationDao()

    fun getAllAutomations(): Flow<List<Automation>> = dao.getAll()

    fun getEnabledAutomations(): Flow<List<Automation>> = dao.getEnabled()

    suspend fun getAutomation(id: String): Automation? = dao.getById(id)

    suspend fun addAutomation(automation: Automation) = dao.insert(automation)

    suspend fun updateAutomation(automation: Automation) = dao.update(automation)

    suspend fun deleteAutomation(automation: Automation) = dao.delete(automation)

    suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled)
}
