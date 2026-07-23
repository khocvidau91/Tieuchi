package com.xiaozhi.hass.manager

import android.content.Context
import com.xiaozhi.hass.database.AppDatabase
import com.xiaozhi.hass.device.Device
import kotlinx.coroutines.flow.Flow

class DeviceManager(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).deviceDao()

    fun getAllDevices(): Flow<List<Device>> = dao.getAll()

    suspend fun getDevice(id: String): Device? = dao.getById(id)

    suspend fun addDevice(device: Device) = dao.insert(device)

    suspend fun updateDevice(device: Device) = dao.update(device)

    suspend fun deleteDevice(device: Device) = dao.delete(device)

    suspend fun updateOnlineStatus(id: String, online: Boolean) =
        dao.updateOnlineStatus(id, online)
}
