package com.glycemicgpt.wear.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatMessageParserTest {

    @Test
    fun `parse valid JSON returns response and disclaimer`() {
        val json = """{"response":"Your levels look stable.","disclaimer":"Not medical advice."}"""
        val result = ChatMessageParser.parse(json.toByteArray(Charsets.UTF_8))

        assertEquals("Your levels look stable.", result!!.response)
        assertEquals("Not medical advice.", result.disclaimer)
    }

    @Test
    fun `parse with missing disclaimer returns empty string`() {
        val json = """{"response":"Hello"}"""
        val result = ChatMessageParser.parse(json.toByteArray(Charsets.UTF_8))

        assertEquals("Hello", result!!.response)
        assertEquals("", result.disclaimer)
    }

    @Test
    fun `parse with escaped characters`() {
        val json = """{"response":"He said \"hello\"","disclaimer":"Use with care."}"""
        val result = ChatMessageParser.parse(json.toByteArray(Charsets.UTF_8))

        assertEquals("He said \"hello\"", result!!.response)
    }

    @Test
    fun `parse malformed JSON returns null`() {
        val result = ChatMessageParser.parse("not json".toByteArray(Charsets.UTF_8))
        assertNull(result)
    }

    @Test
    fun `parse empty bytes returns null`() {
        val result = ChatMessageParser.parse(ByteArray(0))
        assertNull(result)
    }

    @Test
    fun `parse JSON missing response field returns null`() {
        val json = """{"disclaimer":"No response field"}"""
        val result = ChatMessageParser.parse(json.toByteArray(Charsets.UTF_8))
        assertNull(result)
    }

    @Test
    fun `parse with unicode content`() {
        val json = """{"response":"BG: 120 mg/dL \u2192 stable","disclaimer":""}"""
        val result = ChatMessageParser.parse(json.toByteArray(Charsets.UTF_8))

        assertEquals("BG: 120 mg/dL \u2192 stable", result!!.response)
    }
}
