package com.domivega.gps_car.data.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadFailureKindTest {

    @Test
    fun `http 5xx is transient`() {
        assertEquals(UploadFailureKind.Transient, UploadFailureClassifier.classify("HTTP 500: boom"))
        assertEquals(UploadFailureKind.Transient, UploadFailureClassifier.classify("HTTP 503: unavailable"))
    }

    @Test
    fun `http 429 and 408 are transient`() {
        assertEquals(UploadFailureKind.Transient, UploadFailureClassifier.classify("HTTP 429: slow down"))
        assertEquals(UploadFailureKind.Transient, UploadFailureClassifier.classify("HTTP 408: timeout"))
    }

    @Test
    fun `network-ish messages are transient`() {
        assertEquals(
            UploadFailureKind.Transient,
            UploadFailureClassifier.classify("Unable to resolve host api.example"),
        )
        assertEquals(
            UploadFailureKind.Transient,
            UploadFailureClassifier.classify("Connection reset"),
        )
        assertEquals(
            UploadFailureKind.Transient,
            UploadFailureClassifier.classify("timeout waiting for response"),
        )
    }

    @Test
    fun `http 4xx client errors are permanent`() {
        assertEquals(UploadFailureKind.Permanent, UploadFailureClassifier.classify("HTTP 401: unauthorized"))
        assertEquals(UploadFailureKind.Permanent, UploadFailureClassifier.classify("HTTP 400: bad request"))
    }

    @Test
    fun `decode errors are permanent`() {
        assertEquals(UploadFailureKind.Permanent, UploadFailureClassifier.classify("decode_error"))
        assertEquals(UploadFailureKind.Permanent, UploadFailureClassifier.classify("Failed to decode sample"))
    }
}
