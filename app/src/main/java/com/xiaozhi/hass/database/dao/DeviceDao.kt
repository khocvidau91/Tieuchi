package com.xiaozhi.hass.database.dao

import androidx.room.*
import com.xiaozhi.hass.device.Device
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices")
    fun getAll(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun getById(id: String): Device?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: Device)

    @Update
    suspend fun update(device: Device)

    @Delete
    suspend fun delete(device: Device)

    @Query("UPDATE devices SET isOnline = :online, lastSeen = :time WHERE id = :id")
    suspend fun updateOnlineStatus(id: String, online: Boolean, time: Long = System.currentTimeMillis())
}
