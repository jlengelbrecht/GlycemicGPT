package com.glycemicgpt.mobile.domain.model

import java.time.Instant

enum class BolusType {
    AUTO_CORRECTION,
    CORRECTION,
    MEAL,
    MEAL_WITH_CORRECTION,
    AUTO,
}

data class EnrichedBolusEvent(
    val units: Float,
    val bolusType: BolusType,
    val reason: String,
    val correctionUnits: Float,
    val mealUnits: Float,
    val bgAtEvent: Int?,
    val iobAtEvent: Float?,
    val timestamp: Instant,
)
