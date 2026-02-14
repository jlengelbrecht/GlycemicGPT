package com.glycemicgpt.mobile.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alerts",
    indices = [
        Index(value = ["timestamp_ms"]),
        Index(value = ["acknowledged"]),
        Index(value = ["server_id"], unique = true),
    ],
)
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "server_id") val serverId: String,
    @ColumnInfo(name = "alert_type") val alertType: String,
    val severity: String,
    val message: String,
    @ColumnInfo(name = "current_value") val currentValue: Double,
    @ColumnInfo(name = "predicted_value") val predictedValue: Double? = null,
    @ColumnInfo(name = "iob_value") val iobValue: Double? = null,
    @ColumnInfo(name = "trend_rate") val trendRate: Double? = null,
    @ColumnInfo(name = "patient_name") val patientName: String? = null,
    val acknowledged: Boolean = false,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long,
)
