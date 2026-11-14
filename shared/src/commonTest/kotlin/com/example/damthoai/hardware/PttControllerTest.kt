package com.example.damthoai.hardware

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PttControllerTest {

    @BeforeTest
    fun setUp() {
        PttController.resetForTest()
    }

    @Test
    fun pressAndRelease_normalFlow() = runTest {
        val events = mutableListOf<PttEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            PttController.events.collect { events.add(it) }
        }

        PttController.onKeyPressed(PttSource.VOLUME_DOWN)

        assertTrue(PttController.isPttActive.value)
        assertEquals(PttSource.VOLUME_DOWN, PttController.activeSource.value)
        assertEquals(1, events.size)
        assertEquals(PttEvent.PttPressed(PttSource.VOLUME_DOWN), events[0])

        PttController.onKeyReleased(PttSource.VOLUME_DOWN)

        assertFalse(PttController.isPttActive.value)
        assertNull(PttController.activeSource.value)
        assertEquals(2, events.size)
        assertEquals(PttEvent.PttReleased(PttSource.VOLUME_DOWN, forced = false), events[1])

        job.cancel()
    }

    @Test
    fun releaseWhenIdle_doesNotEmitEvent() = runTest {
        val events = mutableListOf<PttEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            PttController.events.collect { events.add(it) }
        }

        PttController.onKeyReleased(PttSource.VOLUME_DOWN)

        assertFalse(PttController.isPttActive.value)
        assertTrue(events.isEmpty())

        job.cancel()
    }

    @Test
    fun consecutivePressSameSource_emitsOnlyOnePressedEvent() = runTest {
        val events = mutableListOf<PttEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            PttController.events.collect { events.add(it) }
        }

        PttController.onKeyPressed(PttSource.VOLUME_UP)
        PttController.onKeyPressed(PttSource.VOLUME_UP)

        assertTrue(PttController.isPttActive.value)
        assertEquals(1, events.size)
        assertEquals(PttEvent.PttPressed(PttSource.VOLUME_UP), events[0])

        job.cancel()
    }

    @Test
    fun sourceBIgnoredWhenSourceAHolding() = runTest {
        val events = mutableListOf<PttEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            PttController.events.collect { events.add(it) }
        }

        PttController.onKeyPressed(PttSource.VOLUME_UP)
        PttController.onKeyPressed(PttSource.VOLUME_DOWN)

        assertEquals(PttSource.VOLUME_UP, PttController.activeSource.value)
        assertEquals(1, events.size)

        PttController.onKeyReleased(PttSource.VOLUME_DOWN)

        assertTrue(PttController.isPttActive.value)
        assertEquals(PttSource.VOLUME_UP, PttController.activeSource.value)
        assertEquals(1, events.size)

        job.cancel()
    }

    @Test
    fun lastCaptureOwnerRelease_forceReleasesPttWithForcedTrue() = runTest {
        val events = mutableListOf<PttEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            PttController.events.collect { events.add(it) }
        }

        PttController.acquireCapture("owner1")
        assertTrue(PttController.isCaptureActive.value)

        PttController.onKeyPressed(PttSource.VOLUME_DOWN)
        assertTrue(PttController.isPttActive.value)

        PttController.releaseCapture("owner1")

        assertFalse(PttController.isCaptureActive.value)
        assertFalse(PttController.isPttActive.value)
        assertEquals(2, events.size)
        assertEquals(PttEvent.PttReleased(PttSource.VOLUME_DOWN, forced = true), events[1])

        job.cancel()
    }

    @Test
    fun disablePttMode_whileActive_forceReleasesPttWithForcedTrue() = runTest {
        val events = mutableListOf<PttEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            PttController.events.collect { events.add(it) }
        }

        PttController.onKeyPressed(PttSource.VOLUME_DOWN)
        assertTrue(PttController.isPttActive.value)

        PttController.setPttModeEnabled(false)

        assertFalse(PttController.isPttModeEnabled.value)
        assertFalse(PttController.isPttActive.value)
        assertEquals(2, events.size)
        assertEquals(PttEvent.PttReleased(PttSource.VOLUME_DOWN, forced = true), events[1])

        job.cancel()
    }

    @Test
    fun multipleCaptureOwners_isCaptureActiveRemainsTrueUntilAllReleased() = runTest {
        val events = mutableListOf<PttEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            PttController.events.collect { events.add(it) }
        }

        PttController.acquireCapture("ownerA")
        PttController.acquireCapture("ownerB")
        assertTrue(PttController.isCaptureActive.value)

        PttController.onKeyPressed(PttSource.HEADSET)

        PttController.releaseCapture("ownerA")
        assertTrue(PttController.isCaptureActive.value)
        assertTrue(PttController.isPttActive.value)

        PttController.releaseCapture("ownerB")
        assertFalse(PttController.isCaptureActive.value)
        assertFalse(PttController.isPttActive.value)
        assertEquals(2, events.size)
        assertEquals(PttEvent.PttReleased(PttSource.HEADSET, forced = true), events[1])

        job.cancel()
    }
}
