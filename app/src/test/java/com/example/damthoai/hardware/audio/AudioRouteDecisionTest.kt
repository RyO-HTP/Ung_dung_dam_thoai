package com.example.damthoai.hardware.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRouteDecisionTest {

    @Test
    fun decideOnOutputRemoved_userChoseSpeaker_micOpen_doesNotMuteOrForceRelease() {
        val decision = decideOnOutputRemoved(
            userChoseSpeaker = true,
            hadMicOpen = true,
            deviceType = 1
        )

        assertFalse(decision.shouldMuteOutput)
        assertFalse(decision.shouldForceReleaseMic)
        assertEquals(AudioRoute.SPEAKER, decision.targetRoute)
        assertNull(decision.incident)
    }

    @Test
    fun decideOnOutputRemoved_userChoseSpeaker_micClosed_doesNotMuteOrForceRelease() {
        val decision = decideOnOutputRemoved(
            userChoseSpeaker = true,
            hadMicOpen = false,
            deviceType = 1
        )

        assertFalse(decision.shouldMuteOutput)
        assertFalse(decision.shouldForceReleaseMic)
        assertEquals(AudioRoute.SPEAKER, decision.targetRoute)
        assertNull(decision.incident)
    }

    @Test
    fun decideOnOutputRemoved_userDidNotChooseSpeaker_micOpen_mutesOutputAndForceReleasesMic() {
        val decision = decideOnOutputRemoved(
            userChoseSpeaker = false,
            hadMicOpen = true,
            deviceType = 2
        )

        assertTrue(decision.shouldMuteOutput)
        assertTrue(decision.shouldForceReleaseMic)
        assertEquals(AudioRoute.EARPIECE, decision.targetRoute)
        assertTrue(decision.incident is AudioIncident.HeadsetUnplugged)
        assertEquals(2, (decision.incident as AudioIncident.HeadsetUnplugged).deviceType)
    }

    @Test
    fun decideOnOutputRemoved_userDidNotChooseSpeaker_micClosed_mutesOutputWithoutForceReleaseMic() {
        val decision = decideOnOutputRemoved(
            userChoseSpeaker = false,
            hadMicOpen = false,
            deviceType = 3
        )

        assertTrue(decision.shouldMuteOutput)
        assertFalse(decision.shouldForceReleaseMic)
        assertEquals(AudioRoute.EARPIECE, decision.targetRoute)
        assertTrue(decision.incident is AudioIncident.HeadsetUnplugged)
        assertEquals(3, (decision.incident as AudioIncident.HeadsetUnplugged).deviceType)
    }
}
