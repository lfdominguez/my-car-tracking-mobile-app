package com.domivega.gps_car.data.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseTokenBindingTest {
    @Test
    fun pauseHoldsOnlyForTheRefusedToken() {
        val refused = PauseTokenBinding.fingerprint("old-token")
        assertTrue(PauseTokenBinding.isActive(refused, "old-token"))
        // Typing a replacement (even a partial one) lifts it; typing the refused one back restores it.
        assertFalse(PauseTokenBinding.isActive(refused, "new-tok"))
        assertFalse(PauseTokenBinding.isActive(refused, "new-token"))
        assertTrue(PauseTokenBinding.isActive(refused, "old-token"))
    }

    @Test
    fun noStoredFingerprintMeansNotPaused() {
        assertFalse(PauseTokenBinding.isActive(null, "any"))
    }

    @Test
    fun refusalOfAReplacedTokenIsStale() {
        assertTrue(PauseTokenBinding.isStaleRefusal(tokenUsed = "old", currentToken = "new"))
        assertFalse(PauseTokenBinding.isStaleRefusal(tokenUsed = "same", currentToken = "same"))
    }

    @Test
    fun fingerprintIsStableAndDoesNotContainTheToken() {
        val fp = PauseTokenBinding.fingerprint("secret-token")
        assertEquals(fp, PauseTokenBinding.fingerprint("secret-token"))
        assertEquals(64, fp.length)
        assertFalse(fp.contains("secret"))
        assertNotEquals(fp, PauseTokenBinding.fingerprint("secret-token2"))
    }
}
