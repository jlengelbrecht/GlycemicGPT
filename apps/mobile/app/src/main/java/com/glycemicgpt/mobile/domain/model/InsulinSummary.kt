package com.glycemicgpt.mobile.domain.model

data class InsulinSummary(
    val totalDailyDose: Float,
    val basalUnits: Float,
    val bolusUnits: Float,
    val correctionUnits: Float,
    val basalPercent: Float,
    val bolusPercent: Float,
    val bolusCount: Int,
    val correctionCount: Int,
    val periodDays: Float,
)
