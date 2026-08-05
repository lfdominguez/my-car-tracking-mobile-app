package com.domivega.gps_car.obd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmHeaderRestoreTest {

    @Test
    fun `ATSH must succeed for restore to count as OK`() {
        assertFalse(
            ElmHeaderRestore.commandsSucceeded(
                atarRaw = "OK",
                atshRaw = null,
                receiveFilterWasSet = false,
            ),
        )
        assertFalse(
            ElmHeaderRestore.commandsSucceeded(
                atarRaw = "OK",
                atshRaw = "?",
                receiveFilterWasSet = false,
            ),
        )
    }

    @Test
    fun `without receive filter ATSH OK is enough even if ATAR is weak`() {
        assertTrue(
            ElmHeaderRestore.commandsSucceeded(
                atarRaw = "?",
                atshRaw = "OK",
                receiveFilterWasSet = false,
            ),
        )
        assertTrue(
            ElmHeaderRestore.commandsSucceeded(
                atarRaw = null,
                atshRaw = "OK\r\n>",
                receiveFilterWasSet = false,
            ),
        )
    }

    @Test
    fun `with receive filter both ATAR and ATSH must succeed`() {
        assertFalse(
            ElmHeaderRestore.commandsSucceeded(
                atarRaw = "?",
                atshRaw = "OK",
                receiveFilterWasSet = true,
            ),
        )
        assertFalse(
            ElmHeaderRestore.commandsSucceeded(
                atarRaw = null,
                atshRaw = "OK",
                receiveFilterWasSet = true,
            ),
        )
        assertTrue(
            ElmHeaderRestore.commandsSucceeded(
                atarRaw = "OK",
                atshRaw = "OK",
                receiveFilterWasSet = true,
            ),
        )
    }

    @Test
    fun `Mode 01 live requires 41-pid marker and rejects NO DATA`() {
        assertTrue(ElmHeaderRestore.isMode01Live("410C1AF8", expectPid = 0x0C))
        assertTrue(ElmHeaderRestore.isMode01Live("41 0C 1A F8\r>", expectPid = 0x0C))
        assertFalse(ElmHeaderRestore.isMode01Live("NO DATA", expectPid = 0x0C))
        assertFalse(ElmHeaderRestore.isMode01Live(null, expectPid = 0x0C))
        assertFalse(ElmHeaderRestore.isMode01Live("4100BE3EA813", expectPid = 0x0C))
    }
}
