package com.xiaozhi.hass.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.xiaozhi.hass.automation.Automation
import com.xiaozhi.hass.database.converters.Converters
import com.xiaozhi.hass.database.dao.*
import com.xiaozhi.hass.device.Device
import com.xiaozhi.hass.entity.Entity
import com.xiaozhi.hass.area.Area
import com.xiaozhi.hass.scene.Scene
import com.xiaozhi.hass.script.Script

@Database(
    entities = [Entity::class, Device::class, Area::class, Automation::class, Scene::class, Script::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entityDao(): EntityDao
    abstract fun deviceDao(): DeviceDao
    abstract fun areaDao(): AreaDao
    abstract fun automationDao(): AutomationDao
    abstract fun sceneDao(): SceneDao
    abstract fun scriptDao(): ScriptDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hass_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
