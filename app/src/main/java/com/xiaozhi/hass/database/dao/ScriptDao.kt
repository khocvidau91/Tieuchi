package com.xiaozhi.hass.database.dao

import androidx.room.*
import com.xiaozhi.hass.script.Script
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts")
    fun getAll(): Flow<List<Script>>

    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun getById(id: String): Script?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(script: Script)

    @Update
    suspend fun update(script: Script)

    @Delete
    suspend fun delete(script: Script)
}
