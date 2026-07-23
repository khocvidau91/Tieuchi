package com.xiaozhi.hass.database.dao

import androidx.room.*
import com.xiaozhi.hass.scene.Scene
import kotlinx.coroutines.flow.Flow

@Dao
interface SceneDao {
    @Query("SELECT * FROM scenes")
    fun getAll(): Flow<List<Scene>>

    @Query("SELECT * FROM scenes WHERE id = :id")
    suspend fun getById(id: String): Scene?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scene: Scene)

    @Update
    suspend fun update(scene: Scene)

    @Delete
    suspend fun delete(scene: Scene)
}
