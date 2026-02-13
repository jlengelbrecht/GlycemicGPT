package com.glycemicgpt.mobile.data.repository

import com.glycemicgpt.mobile.data.local.dao.PumpDao
import javax.inject.Inject
import javax.inject.Singleton

data class PumpContext(
    val contextPrefix: String,
    val snapshot: String,
)

@Singleton
class PumpContextBuilder @Inject constructor(
    private val pumpDao: PumpDao,
) {
    suspend fun buildContext(): PumpContext {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3_600_000

        val parts = mutableListOf<String>()

        // Latest IoB
        val iobReadings = pumpDao.getIoBSince(oneHourAgo)
        iobReadings.firstOrNull()?.let {
            parts.add("IoB %.2fu".format(it.iob))
        }

        // Latest CGM (only if within the last 15 minutes)
        pumpDao.getLatestCgm()?.let { cgm ->
            val ageMs = now - cgm.timestampMs
            if (ageMs <= 900_000) {
                parts.add("BG %d mg/dL %s".format(cgm.glucoseMgDl, cgm.trendArrow))
            }
        }

        // Recent boluses
        val boluses = pumpDao.getBolusesSince(oneHourAgo)
        if (boluses.isNotEmpty()) {
            val total = boluses.sumOf { it.units.toDouble() }
            parts.add("Recent boluses: %.1fu total (%d deliveries)".format(total, boluses.size))
        }

        return if (parts.isNotEmpty()) {
            val contextString = "[Current pump context: ${parts.joinToString("; ")}]"
            PumpContext(
                contextPrefix = "$contextString\n\n",
                snapshot = contextString,
            )
        } else {
            PumpContext(contextPrefix = "", snapshot = "No pump data available")
        }
    }
}
