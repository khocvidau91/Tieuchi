package com.xiaozhi.hass.database.dao

import androidx.room.*
import com.xiaozhi.hass.automation.Automation
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations")
    fun getAll(): Flow<List<Automation>>

    @Query("SELECT * FROM automations WHERE enabled = 1")
    fun getEnabled(): Flow<List<Automation>>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getById(id: String): Automation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(automation: Automation)

    @Update
    suspend fun update(automation: Automation)

    @Delete
    suspend fun delete(automation: Automation)

    @Query("UPDATE automations SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}
