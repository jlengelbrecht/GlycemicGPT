package com.glycemicgpt.mobile.domain.model

import java.time.Instant

/**
 * A fingerstick blood glucose meter reading.
 */
data class BgmReading(
    val glucoseMgDl: Int,
    val timestamp: Instant,
    val meterName: String? = null,
)
