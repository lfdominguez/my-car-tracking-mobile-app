package com.domivega.gps_car.data.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(UploadFailureKind.Permanent, UploadFailureClassifier.classify("HTTP 400: bad request"))
        assertEquals(UploadFailureKind.Permanent, UploadFailureClassifier.classify("HTTP 422: invalid"))
    }

    @Test
    fun `http 401 and 403 mean the device is unauthorized, not a bad payload`() {
        assertEquals(
            UploadFailureKind.DeviceUnauthorized,
            UploadFailureClassifier.classify("HTTP 401: unauthorized"),
        )
        assertEquals(
            UploadFailureKind.DeviceUnauthorized,
            UploadFailureClassifier.classify("HTTP 403: device token revoked"),
        )
        // Even when the body happens to mention decoding.
        assertEquals(
            UploadFailureKind.DeviceUnauthorized,
            UploadFailureClassifier.classify("HTTP 401: could not decode authorization header"),
        )
    }

    @Test
    fun `http 409 vault rejection pauses, other 409 stays permanent`() {
        assertEquals(
            UploadFailureKind.VaultRequired,
            UploadFailureClassifier.classify("HTTP 409: vault car requires encrypted chunk upload"),
        )
        assertEquals(
            UploadFailureKind.Permanent,
            UploadFailureClassifier.classify("HTTP 409: track already finished"),
        )
    }

    @Test
    fun `status code classification`() {
        assertEquals(UploadFailureKind.DeviceUnauthorized, UploadFailureClassifier.classifyHttp(401))
        assertEquals(UploadFailureKind.DeviceUnauthorized, UploadFailureClassifier.classifyHttp(403))
        assertEquals(
            UploadFailureKind.VaultRequired,
            UploadFailureClassifier.classifyHttp(409, """{"error":"vault car requires encrypted chunk upload"}"""),
        )
        assertEquals(UploadFailureKind.Permanent, UploadFailureClassifier.classifyHttp(409, "conflict"))
        assertEquals(UploadFailureKind.Permanent, UploadFailureClassifier.classifyHttp(400))
        assertEquals(UploadFailureKind.Permanent, UploadFailureClassifier.classifyHttp(404))
        assertEquals(UploadFailureKind.Transient, UploadFailureClassifier.classifyHttp(408))
        assertEquals(UploadFailureKind.Transient, UploadFailureClassifier.classifyHttp(429))
        assertEquals(UploadFailureKind.Transient, UploadFailureClassifier.classifyHttp(500))
        assertEquals(UploadFailureKind.Transient, UploadFailureClassifier.classifyHttp(503))
    }

    @Test
    fun `only unauthorized and vault kinds pause uploading`() {
        assertEquals(UploadPauseReason.DeviceUnauthorized, UploadFailureKind.DeviceUnauthorized.pauseReason())
        assertEquals(UploadPauseReason.VaultRequired, UploadFailureKind.VaultRequired.pauseReason())
        assertNull(UploadFailureKind.Transient.pauseReason())
        assertNull(UploadFailureKind.Permanent.pauseReason())
    }

    @Test
    fun `pause reason round trips through its persisted name`() {
        UploadPauseReason.entries.forEach {
            assertEquals(it, UploadPauseReason.fromName(it.name))
        }
        assertNull(UploadPauseReason.fromName(null))
        assertNull(UploadPauseReason.fromName("garbage"))
    }

    @Test
    fun `decode errors are permanent`() {
        assertEquals(UploadFailureKind.Permanent, UploadFailureClassifier.classify("decode_error"))
        assertEquals(UploadFailureKind.Permanent, UploadFailureClassifier.classify("Failed to decode sample"))
    }
}
