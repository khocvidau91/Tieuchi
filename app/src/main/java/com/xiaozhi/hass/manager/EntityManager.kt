package com.xiaozhi.hass.manager

import android.content.Context
import com.xiaozhi.hass.database.AppDatabase
import com.xiaozhi.hass.entity.Entity
import com.xiaozhi.hass.entity.EntityType
import kotlinx.coroutines.flow.Flow

class EntityManager(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).entityDao()

    fun getAllEntities(): Flow<List<Entity>> = dao.getAll()

    suspend fun getEntity(id: String): Entity? = dao.getById(id)

    suspend fun addEntity(entity: Entity) = dao.insert(entity)

    suspend fun updateEntity(entity: Entity) = dao.update(entity)

    suspend fun deleteEntity(entity: Entity) = dao.delete(entity)

    suspend fun updateState(id: String, state: String) =
        dao.updateState(id, state)

    fun getByArea(areaId: String): Flow<List<Entity>> = dao.getByAreaId(areaId)

    // Helper methods
    suspend fun createLight(id: String, name: String, areaId: String? = null, deviceId: String? = null): Entity {
        val entity = Entity(
            id = id,
            name = name,
            type = EntityType.LIGHT,
            areaId = areaId,
            deviceId = deviceId,
            state = "off"
        )
        addEntity(entity)
        return entity
    }

    suspend fun createSwitch(id: String, name: String, areaId: String? = null, deviceId: String? = null): Entity {
        val entity = Entity(
            id = id,
            name = name,
            type = EntityType.SWITCH,
            areaId = areaId,
            deviceId = deviceId,
            state = "off"
        )
        addEntity(entity)
        return entity
    }

    suspend fun createSensor(id: String, name: String, areaId: String? = null, deviceId: String? = null): Entity {
        val entity = Entity(
            id = id,
            name = name,
            type = EntityType.SENSOR,
            areaId = areaId,
            deviceId = deviceId,
            state = "unknown"
        )
        addEntity(entity)
        return entity
    }

    // Tương tự cho các loại entity khác
}
