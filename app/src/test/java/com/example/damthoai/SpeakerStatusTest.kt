package com.example.damthoai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpeakerStatusTest {

    private fun getSpeakerStatusLabel(speakerInput: String?): String {
        val raw = speakerInput?.trim() ?: "Không ai đang nói"
        if (raw.isBlank() || raw.equals("yên lặng", ignoreCase = true)) {
            return "Yên lặng"
        }
        if (raw.contains("đang nói", ignoreCase = true)) {
            val baseName = raw.substringBefore(" đang nói").trim()
            return if (baseName.isNotBlank() && !baseName.equals("không ai", ignoreCase = true)) {
                "$baseName đang nói"
            } else {
                "Không ai đang nói"
            }
        }
        if (raw.equals("không có tín hiệu", ignoreCase = true)) {
            return "Không có tín hiệu"
        }
        return raw
    }

    @Test
    fun testSpeakerStatusCases() {
        // Case 1: Active speaker
        val status1 = getSpeakerStatusLabel("Lan Phương đang nói")
        assertEquals("Lan Phương đang nói", status1)
        assertFalse(status1.contains("đang nói đang nói"))

        // Case 2: Local speaker
        val status2 = getSpeakerStatusLabel("Bạn đang nói")
        assertEquals("Bạn đang nói", status2)
        assertFalse(status2.contains("đang nói đang nói"))

        // Case 3: No speaker
        val status3 = getSpeakerStatusLabel("Không ai đang nói")
        assertEquals("Không ai đang nói", status3)
        assertFalse(status3.contains("đang nói đang nói"))

        // Case 4: Quiet / ducked
        val status4 = getSpeakerStatusLabel("Yên lặng")
        assertEquals("Yên lặng", status4)
        assertFalse(status4.contains("đang nói đang nói"))
    }
}
