package com.glycemicgpt.wear.watchface

import org.junit.Assert.assertEquals
import org.junit.Test

class GlycemicRendererTest {

    // bgColor tests

    @Test
    fun `bgColor returns green for in-range value`() {
        val color = GlycemicRenderer.bgColor(mgDl = 120, low = 70, high = 180, urgentLow = 55, urgentHigh = 250)
        assertEquals(0xFF22C55E.toInt(), color)
    }

    @Test
    fun `bgColor returns yellow for high value`() {
        val color = GlycemicRenderer.bgColor(mgDl = 200, low = 70, high = 180, urgentLow = 55, urgentHigh = 250)
        assertEquals(0xFFEAB308.toInt(), color)
    }

    @Test
    fun `bgColor returns yellow for low value`() {
        val color = GlycemicRenderer.bgColor(mgDl = 65, low = 70, high = 180, urgentLow = 55, urgentHigh = 250)
        assertEquals(0xFFEAB308.toInt(), color)
    }

    @Test
    fun `bgColor returns red for urgent high value`() {
        val color = GlycemicRenderer.bgColor(mgDl = 260, low = 70, high = 180, urgentLow = 55, urgentHigh = 250)
        assertEquals(0xFFEF4444.toInt(), color)
    }

    @Test
    fun `bgColor returns red for urgent low value`() {
        val color = GlycemicRenderer.bgColor(mgDl = 50, low = 70, high = 180, urgentLow = 55, urgentHigh = 250)
        assertEquals(0xFFEF4444.toInt(), color)
    }

    @Test
    fun `bgColor at exact low boundary returns yellow`() {
        val color = GlycemicRenderer.bgColor(mgDl = 70, low = 70, high = 180, urgentLow = 55, urgentHigh = 250)
        assertEquals(0xFFEAB308.toInt(), color)
    }

    @Test
    fun `bgColor at exact high boundary returns yellow`() {
        val color = GlycemicRenderer.bgColor(mgDl = 180, low = 70, high = 180, urgentLow = 55, urgentHigh = 250)
        assertEquals(0xFFEAB308.toInt(), color)
    }

    @Test
    fun `bgColor just inside range returns green`() {
        val color = GlycemicRenderer.bgColor(mgDl = 71, low = 70, high = 180, urgentLow = 55, urgentHigh = 250)
        assertEquals(0xFF22C55E.toInt(), color)
    }

    // trendSymbol tests

    @Test
    fun `trendSymbol maps DOUBLE_UP`() {
        assertEquals("\u21C8", GlycemicRenderer.trendSymbol("DOUBLE_UP"))
    }

    @Test
    fun `trendSymbol maps SINGLE_UP`() {
        assertEquals("\u2191", GlycemicRenderer.trendSymbol("SINGLE_UP"))
    }

    @Test
    fun `trendSymbol maps FORTY_FIVE_UP`() {
        assertEquals("\u2197", GlycemicRenderer.trendSymbol("FORTY_FIVE_UP"))
    }

    @Test
    fun `trendSymbol maps FLAT`() {
        assertEquals("\u2192", GlycemicRenderer.trendSymbol("FLAT"))
    }

    @Test
    fun `trendSymbol maps FORTY_FIVE_DOWN`() {
        assertEquals("\u2198", GlycemicRenderer.trendSymbol("FORTY_FIVE_DOWN"))
    }

    @Test
    fun `trendSymbol maps SINGLE_DOWN`() {
        assertEquals("\u2193", GlycemicRenderer.trendSymbol("SINGLE_DOWN"))
    }

    @Test
    fun `trendSymbol maps DOUBLE_DOWN`() {
        assertEquals("\u21CA", GlycemicRenderer.trendSymbol("DOUBLE_DOWN"))
    }

    @Test
    fun `trendSymbol maps UNKNOWN`() {
        assertEquals("?", GlycemicRenderer.trendSymbol("UNKNOWN"))
    }

    @Test
    fun `trendSymbol returns question mark for unrecognized value`() {
        assertEquals("?", GlycemicRenderer.trendSymbol("BOGUS"))
    }

    // formatAge tests

    @Test
    fun `formatAge returns just now for less than 1 minute`() {
        assertEquals("just now", GlycemicRenderer.formatAge(30_000))
    }

    @Test
    fun `formatAge returns minutes for less than 1 hour`() {
        assertEquals("5m ago", GlycemicRenderer.formatAge(5 * 60_000))
    }

    @Test
    fun `formatAge returns hours and minutes for over 1 hour`() {
        assertEquals("1h 30m ago", GlycemicRenderer.formatAge(90 * 60_000))
    }

    // freshnessColor tests

    @Test
    fun `freshnessColor returns green for fresh data`() {
        assertEquals(0xFF22C55E.toInt(), GlycemicRenderer.freshnessColor(60_000))
    }

    @Test
    fun `freshnessColor returns orange for slightly stale data`() {
        assertEquals(0xFFF97316.toInt(), GlycemicRenderer.freshnessColor(5 * 60_000))
    }

    @Test
    fun `freshnessColor returns red for very stale data`() {
        assertEquals(0xFFEF4444.toInt(), GlycemicRenderer.freshnessColor(15 * 60_000))
    }

    // alertColor tests

    @Test
    fun `alertColor returns red for urgent_low`() {
        assertEquals(0xFFEF4444.toInt(), GlycemicRenderer.alertColor("urgent_low"))
    }

    @Test
    fun `alertColor returns red for urgent_high`() {
        assertEquals(0xFFEF4444.toInt(), GlycemicRenderer.alertColor("urgent_high"))
    }

    @Test
    fun `alertColor returns yellow for low`() {
        assertEquals(0xFFEAB308.toInt(), GlycemicRenderer.alertColor("low"))
    }

    @Test
    fun `alertColor returns yellow for high`() {
        assertEquals(0xFFEAB308.toInt(), GlycemicRenderer.alertColor("high"))
    }

    @Test
    fun `alertColor returns white for unknown type`() {
        assertEquals(android.graphics.Color.WHITE, GlycemicRenderer.alertColor("unknown"))
    }
}
