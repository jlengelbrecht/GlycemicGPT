package com.glycemicgpt.mobile.di

import com.glycemicgpt.mobile.ble.connection.TandemBleDriver
import com.glycemicgpt.mobile.domain.pump.PumpDriver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PumpModule {

    @Binds
    @Singleton
    abstract fun bindPumpDriver(impl: TandemBleDriver): PumpDriver
}
