package com.glycemicgpt.mobile.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.glycemicgpt.mobile.data.local.AppDatabase
import com.glycemicgpt.mobile.data.local.dao.AlertDao
import com.glycemicgpt.mobile.data.local.dao.PumpDao
import com.glycemicgpt.mobile.data.local.dao.RawHistoryLogDao
import com.glycemicgpt.mobile.data.local.dao.SyncDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Migration 6->7: make cgm_readings.timestampMs index unique for dedup. */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Drop the old non-unique index and create a unique one.
            // Remove duplicate timestamps first (keep the row with the highest id).
            db.execSQL(
                """DELETE FROM cgm_readings WHERE id NOT IN (
                    SELECT MAX(id) FROM cgm_readings GROUP BY timestampMs
                )""",
            )
            db.execSQL("DROP INDEX IF EXISTS index_cgm_readings_timestampMs")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_cgm_readings_timestampMs ON cgm_readings(timestampMs)",
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "glycemicgpt.db")
            .addMigrations(MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePumpDao(db: AppDatabase): PumpDao = db.pumpDao()

    @Provides
    fun provideSyncDao(db: AppDatabase): SyncDao = db.syncDao()

    @Provides
    fun provideRawHistoryLogDao(db: AppDatabase): RawHistoryLogDao = db.rawHistoryLogDao()

    @Provides
    fun provideAlertDao(db: AppDatabase): AlertDao = db.alertDao()
}
