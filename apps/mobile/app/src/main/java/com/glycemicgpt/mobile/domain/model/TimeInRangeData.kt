package com.glycemicgpt.mobile.domain.model

data class TimeInRangeData(
    val lowPercent: Float,
    val inRangePercent: Float,
    val highPercent: Float,
    val totalReadings: Int,
)
