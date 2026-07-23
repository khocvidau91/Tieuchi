package com.xiaozhi.hass.database.dao

import androidx.room.*
import com.xiaozhi.hass.area.Area
import kotlinx.coroutines.flow.Flow

@Dao
interface AreaDao {
    @Query("SELECT * FROM areas")
    fun getAll(): Flow<List<Area>>

    @Query("SELECT * FROM areas WHERE id = :id")
    suspend fun getById(id: String): Area?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(area: Area)

    @Update
    suspend fun update(area: Area)

    @Delete
    suspend fun delete(area: Area)

    @Query("SELECT * FROM areas WHERE parentId = :parentId")
    fun getChildren(parentId: String): Flow<List<Area>>
}
