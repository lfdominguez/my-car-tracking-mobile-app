package com.domivega.gps_car.data.queue

import java.security.MessageDigest

/**
 * Ties an upload pause to the device token the server refused.
 *
 * The token field saves on every keystroke, so "the token changed" is not a
 * reliable resume signal, and a 401 for the old token can arrive after a new one
 * was saved. Keying the pause to the refused token handles both: the pause holds
 * only while that exact token is configured, and a refusal of a token that is no
 * longer configured is stale and must not pause anything.
 */
object PauseTokenBinding {
    /** SHA-256 hex of [token]; the token itself is never stored twice. */
    fun fingerprint(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** A stored pause applies only while the refused token is still the configured one. */
    fun isActive(storedFingerprint: String?, currentToken: String): Boolean =
        storedFingerprint != null && storedFingerprint == fingerprint(currentToken)

    /** A refusal of a token the user has since replaced says nothing about the new one. */
    fun isStaleRefusal(tokenUsed: String, currentToken: String): Boolean = tokenUsed != currentToken
}
