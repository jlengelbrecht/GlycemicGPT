package com.glycemicgpt.mobile.domain.pump

/**
 * User-configured safety limits for data validation.
 *
 * These values come from the user's backend settings (glucose range,
 * insulin configuration) and are used by parsers to reject implausible
 * readings. The defaults match common clinical ranges but should always
 * be overridden with the user's actual configured values.
 */
data class SafetyLimits(
    /** Minimum valid CGM glucose reading in mg/dL. */
    val minGlucoseMgDl: Int = DEFAULT_MIN_GLUCOSE,
    /** Maximum valid CGM glucose reading in mg/dL. */
    val maxGlucoseMgDl: Int = DEFAULT_MAX_GLUCOSE,
    /** Maximum valid basal rate in milliunits/hr. */
    val maxBasalRateMilliunits: Int = DEFAULT_MAX_BASAL_MILLIUNITS,
) {
    companion object {
        const val DEFAULT_MIN_GLUCOSE = 20
        const val DEFAULT_MAX_GLUCOSE = 500
        const val DEFAULT_MAX_BASAL_MILLIUNITS = 25_000 // 25 u/hr
    }
}
