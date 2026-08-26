package com.domivega.gps_car.obd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmFrameCorrelationTest {

    @Test
    fun matchingFrameIsAccepted() {
        assertTrue(ElmFrameCorrelation.isForRequestedPid("410C1AF8", "0c"))
        assertTrue(ElmFrameCorrelation.isForRequestedPid("41 0C 1A F8", "0C"))
    }

    @Test
    fun anotherPidsFrameIsRejected() {
        // The classic one-command-off cascade: 010D's reply lands on 010C.
        assertFalse(ElmFrameCorrelation.isForRequestedPid("410D2A", "0c"))
    }

    @Test
    fun multiEcuReplyIsAcceptedWhenOneFrameMatches() {
        assertTrue(ElmFrameCorrelation.isForRequestedPid("410C1AF8410C1AF9", "0c"))
    }

    @Test
    fun noDataIsAPlainMissNotADesync() {
        assertTrue(ElmFrameCorrelation.isForRequestedPid("NO DATA", "0c"))
        assertTrue(ElmFrameCorrelation.isForRequestedPid("?", "0c"))
        assertTrue(ElmFrameCorrelation.isForRequestedPid("", "0c"))
        assertTrue(ElmFrameCorrelation.isForRequestedPid(null, "0c"))
    }

    @Test
    fun negativeResponseIsAPlainMiss() {
        assertTrue(ElmFrameCorrelation.isForRequestedPid("7F0112", "0c"))
    }

    @Test
    fun oddOffsetSpoofDoesNotCountAsAForeignFrame() {
        // 41 appearing inside a payload at an odd offset is not a frame start.
        assertTrue(ElmFrameCorrelation.isForRequestedPid("410CF410", "0c"))
    }
}
