package com.glycemicgpt.mobile.di

import android.content.Context
import androidx.room.Room
import com.glycemicgpt.mobile.data.local.AppDatabase
import com.glycemicgpt.mobile.data.local.dao.PumpDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "glycemicgpt.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePumpDao(db: AppDatabase): PumpDao = db.pumpDao()
}
