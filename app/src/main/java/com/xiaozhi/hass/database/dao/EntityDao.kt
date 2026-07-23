package com.xiaozhi.hass.database.dao

import androidx.room.*
import com.xiaozhi.hass.entity.Entity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntityDao {
    @Query("SELECT * FROM entities")
    fun getAll(): Flow<List<Entity>>

    @Query("SELECT * FROM entities WHERE id = :id")
    suspend fun getById(id: String): Entity?

    @Query("SELECT * FROM entities WHERE areaId = :areaId")
    fun getByAreaId(areaId: String): Flow<List<Entity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: Entity)

    @Update
    suspend fun update(entity: Entity)

    @Delete
    suspend fun delete(entity: Entity)

    @Query("UPDATE entities SET state = :state, lastUpdated = :time WHERE id = :id")
    suspend fun updateState(id: String, state: String, time: Long = System.currentTimeMillis())
}
