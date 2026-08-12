package com.domivega.gps_car.data.queue

/**
 * Rewrites the `tracking_id` field inside a queued sample JSON payload.
 */
object SamplePayloadRewrite {
    private val trackingIdField = Regex(""""tracking_id"\s*:\s*"[^"]*"""")

    fun replaceTrackingId(payloadJson: String, newTrackingId: String): String {
        val escaped = newTrackingId
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val replacement = """"tracking_id":"$escaped""""
        return if (trackingIdField.containsMatchIn(payloadJson)) {
            trackingIdField.replace(payloadJson, replacement)
        } else {
            payloadJson
        }
    }
}
