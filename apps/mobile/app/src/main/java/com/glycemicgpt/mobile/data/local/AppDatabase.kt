package com.glycemicgpt.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.glycemicgpt.mobile.data.local.dao.PumpDao
import com.glycemicgpt.mobile.data.local.entity.BasalReadingEntity
import com.glycemicgpt.mobile.data.local.entity.BatteryReadingEntity
import com.glycemicgpt.mobile.data.local.entity.BolusEventEntity
import com.glycemicgpt.mobile.data.local.entity.IoBReadingEntity
import com.glycemicgpt.mobile.data.local.entity.ReservoirReadingEntity

@Database(
    entities = [
        IoBReadingEntity::class,
        BasalReadingEntity::class,
        BolusEventEntity::class,
        BatteryReadingEntity::class,
        ReservoirReadingEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pumpDao(): PumpDao
}
