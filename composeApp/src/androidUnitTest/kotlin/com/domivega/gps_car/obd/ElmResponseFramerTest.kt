package com.domivega.gps_car.obd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ElmResponseFramerTest {
    @Test
    fun append_until_prompt_returns_complete() {
        val f = ElmResponseFramer()
        assertNull(f.append("41 0C 1A F8\r"))
        val done = f.append(">")
        assertEquals("41 0C 1A F8\r>", done)
        assertTrue(f.snapshot().isEmpty())
    }

    @Test
    fun multiple_chunks_and_drain() {
        val f = ElmResponseFramer()
        f.append("OK\r")
        assertNull(f.append("\n"))
        assertEquals("OK\r\n", f.drainPartial())
        assertTrue(f.snapshot().isEmpty())
    }

    @Test
    fun clear_discards_buffer() {
        val f = ElmResponseFramer()
        f.append("partial")
        f.clear()
        assertTrue(f.snapshot().isEmpty())
    }
}
