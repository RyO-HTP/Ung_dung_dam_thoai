package com.example.damthoai.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionStateResolverTest {

    @Test
    fun resolvePermissionState_allGranted_returnsGranted() {
        val state1 = PermissionManager.resolvePermissionState(
            allGranted = true,
            wasRequested = false,
            showRationale = false
        )
        val state2 = PermissionManager.resolvePermissionState(
            allGranted = true,
            wasRequested = true,
            showRationale = false
        )
        val state3 = PermissionManager.resolvePermissionState(
            allGranted = true,
            wasRequested = true,
            showRationale = true
        )

        assertEquals(PermissionState.GRANTED, state1)
        assertEquals(PermissionState.GRANTED, state2)
        assertEquals(PermissionState.GRANTED, state3)
    }

    @Test
    fun resolvePermissionState_notGranted_neverRequested_returnsDenied() {
        val stateWithoutRationale = PermissionManager.resolvePermissionState(
            allGranted = false,
            wasRequested = false,
            showRationale = false
        )
        val stateWithRationale = PermissionManager.resolvePermissionState(
            allGranted = false,
            wasRequested = false,
            showRationale = true
        )

        assertEquals(PermissionState.DENIED, stateWithoutRationale)
        assertEquals(PermissionState.DENIED, stateWithRationale)
    }

    @Test
    fun resolvePermissionState_notGranted_wasRequested_showRationaleTrue_returnsDenied() {
        val state = PermissionManager.resolvePermissionState(
            allGranted = false,
            wasRequested = true,
            showRationale = true
        )

        assertEquals(PermissionState.DENIED, state)
    }

    @Test
    fun resolvePermissionState_notGranted_wasRequested_showRationaleFalse_returnsPermanentlyDenied() {
        val state = PermissionManager.resolvePermissionState(
            allGranted = false,
            wasRequested = true,
            showRationale = false
        )

        assertEquals(PermissionState.PERMANENTLY_DENIED, state)
    }
}
