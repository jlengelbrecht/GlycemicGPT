package com.glycemicgpt.wear.presentation

object IoBProjection {

    private const val DEFAULT_DIA_MINUTES = 300 // 5 hours

    fun projectIoB(currentIoB: Float, minutesAhead: Int, diaMinutes: Int = DEFAULT_DIA_MINUTES): Float {
        if (minutesAhead <= 0) return currentIoB
        if (minutesAhead >= diaMinutes) return 0f
        val fractionRemaining = 1f - (minutesAhead.toFloat() / diaMinutes)
        return currentIoB * fractionRemaining
    }
}
