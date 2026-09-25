package com.domivega.gps_car.obd

/**
 * Fault codes read once at trip start. Each list is null when that read failed or
 * timed out ("not read"), empty when the car answered with no codes.
 */
data class TripStartFaultCodes(
    val stored: List<String>?,
    val pending: List<String>?,
)

/**
 * Parses ELM327 answers to OBD Mode 03 (stored DTCs, positive reply `43`) and
 * Mode 07 (pending DTCs, positive reply `47`) into codes like `P0133`.
 *
 * Handles, with or without spaces (`ATS0`):
 * - CAN (ISO 15765) single frames, which carry a DTC count byte:
 *   `43 02 01 33 01 34` -> P0133, P0134
 * - legacy non-CAN (J1850 / ISO 9141 / KWP) 7-byte frames with no count byte,
 *   padded with `00 00`: `43 01 33 00 00 00 00` -> P0133; one line per 3 codes
 * - CAN multi-frame answers as ELM prints them with headers off (a byte-count
 *   line, then `0:` / `1:` / … frame lines), including `ATL0` glued lines
 * - CAN answers with headers on (`7E8 06 43 …`, 29-bit `18 DA F1 10 …`), where the
 *   PCI byte is shown and each ECU is reassembled by its header
 * - several ECUs answering one request (one message per line / per header)
 *
 * A CAN message is `SID count (A B)*` — always an even byte count — while a
 * legacy frame is `SID (A B)*3` — always odd — so a headerless line's parity
 * says which layout it is.
 *
 * Returns the deduplicated codes in answer order, `emptyList()` for `NO DATA` or
 * an ECU reporting zero codes ("read, no codes"), and `null` when the read failed
 * (`?`, `ERROR`, `UNABLE TO CONNECT`, a negative response, or garbage).
 */
object DtcParser {
    /** Positive response SID to Mode 03 (stored / confirmed DTCs). */
    const val STORED_RESPONSE_SID: Int = 0x43

    /** Positive response SID to Mode 07 (pending DTCs). */
    const val PENDING_RESPONSE_SID: Int = 0x47

    private const val NEGATIVE_RESPONSE_SID: Int = 0x7F

    private val FAILURE_MARKERS = listOf(
        "?",
        "ERROR", // BUS ERROR, CAN ERROR, DATA ERROR, <RX ERROR, FB ERROR, BUS INIT: ...ERROR
        "UNABLE TO CONNECT",
        "BUS BUSY",
        "STOPPED",
        "BUFFER FULL",
        "LV RESET",
        "ACT ALERT",
    )

    private val NOISE = listOf("SEARCHING...", "SEARCHING", "BUS INIT: ...OK", "BUS INIT: OK", "BUS INIT:", "...OK")

    private val FRAME_MARKER = Regex("""([0-9A-F]):""")

    fun parseStored(raw: String?): List<String>? = parse(raw, STORED_RESPONSE_SID)

    fun parsePending(raw: String?): List<String>? = parse(raw, PENDING_RESPONSE_SID)

    /** Mode 01 PID 01 byte A: check-engine lamp (bit 7) and confirmed DTC count (bits 0–6). */
    data class MonitorStatus(val milOn: Boolean, val confirmedCount: Int)

    private val MONITOR_STATUS = Regex("""4101([0-9A-F]{2})[0-9A-F]{6}""")

    /**
     * Parses a `0101` answer, OR-ing the lamp and summing counts when several ECUs
     * reply. Null when nothing decodes: every OBD-II ECU supports PID 01, so here
     * `NO DATA` is a lost reply, not "no codes".
     */
    fun parseMonitorStatus(raw: String?): MonitorStatus? {
        if (raw.isNullOrBlank()) return null
        val compact = raw.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        val bytes = MONITOR_STATUS.findAll(compact).map { it.groupValues[1].toInt(16) }.toList()
        if (bytes.isEmpty()) return null
        return MonitorStatus(
            milOn = bytes.any { it and 0x80 != 0 },
            confirmedCount = bytes.sumOf { it and 0x7F },
        )
    }

    /**
     * Cross-checks a Mode 03 read against PID 01's confirmed-code count. Mode 03
     * treats `NO DATA` as "no codes", which a lost reply also looks like; an empty
     * list while the ECU counts stored codes is a failed read, so it becomes null.
     */
    fun reconcileStored(stored: List<String>?, status: MonitorStatus?): List<String>? {
        if (stored == null) return null
        if (stored.isEmpty() && status != null && status.confirmedCount > 0) return null
        return stored
    }

    fun parse(raw: String?, responseSid: Int): List<String>? {
        if (raw.isNullOrBlank()) return null
        var text = raw.uppercase().replace(">", "\n")
        if (FAILURE_MARKERS.any { text.contains(it) }) return null
        for (noise in NOISE) text = text.replace(noise, "\n")
        val noData = text.contains("NO DATA")
        text = text.replace("NO DATA", "\n")

        val messages = collectMessages(text)
        val sid = responseSid and 0xFF
        val positive = messages.filter { it.bytes.isNotEmpty() && it.bytes[0] == sid }
        if (positive.isEmpty()) {
            // Nothing positive: NO DATA means "read, no codes"; a negative
            // response (7F) or unparseable text is a failed read.
            val negative = messages.any { it.bytes.isNotEmpty() && it.bytes[0] == NEGATIVE_RESPONSE_SID }
            return if (noData && !negative) emptyList() else null
        }

        val codes = LinkedHashSet<String>()
        for (message in positive) {
            val pairsStart: Int
            var pairLimit = Int.MAX_VALUE
            if (!message.canFramed && message.bytes.size % 2 == 1) {
                // Legacy frame: SID then code pairs, no count byte.
                pairsStart = 1
            } else {
                // CAN: SID, count, code pairs.
                if (message.bytes.size < 2) continue
                pairsStart = 2
                pairLimit = message.bytes[1]
            }
            var i = pairsStart
            var taken = 0
            while (i + 1 < message.bytes.size && taken < pairLimit) {
                decodeDtc(message.bytes[i], message.bytes[i + 1])?.let { codes.add(it) }
                i += 2
                taken += 1
            }
        }
        return codes.toList()
    }

    /**
     * Two DTC bytes to the SAE form: the top two bits of [a] pick the system
     * (P/C/B/U), the next two the first digit, then three hex digits.
     * `00 00` is padding, not a code, and yields null.
     */
    fun decodeDtc(a: Int, b: Int): String? {
        val hi = a and 0xFF
        val lo = b and 0xFF
        if (hi == 0 && lo == 0) return null
        val system = "PCBU"[hi shr 6]
        val firstDigit = (hi shr 4) and 0x03
        val secondDigit = (hi and 0x0F).toString(16).uppercase()
        val rest = lo.toString(16).uppercase().padStart(2, '0')
        return "$system$firstDigit$secondDigit$rest"
    }

    /** One reassembled answer. [canFramed] = came through ISO-TP framing (count byte present). */
    private class Message(val bytes: List<Int>, val canFramed: Boolean)

    private class IsoTpBuffer(val expectedLength: Int?) {
        val bytes = ArrayList<Int>()

        fun isComplete(): Boolean {
            val len = expectedLength ?: return false
            return bytes.size >= len
        }

        fun toMessage(): Message {
            val len = expectedLength
            val trimmed = if (len != null && len in 0..bytes.size) bytes.subList(0, len) else bytes
            return Message(ArrayList(trimmed), canFramed = true)
        }
    }

    private fun collectMessages(text: String): List<Message> {
        val out = ArrayList<Message>()
        // Headers-on CAN: per-ECU reassembly keyed by header.
        val byHeader = LinkedHashMap<String, IsoTpBuffer>()
        // Headers-off CAN multi-frame (length line + `N:` frames).
        var pendingLength: Int? = null
        var current: IsoTpBuffer? = null

        fun flushCurrent() {
            current?.let { out.add(it.toMessage()) }
            current = null
        }

        for (rawLine in text.split('\r', '\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.contains(':')) {
                // Headers-off ISO-TP frames; ATL0 may glue several onto one line.
                val compact = line.filterNot { it.isWhitespace() }
                val markers = FRAME_MARKER.findAll(compact).toList()
                if (markers.isEmpty()) continue
                val prefix = compact.substring(0, markers.first().range.first)
                if (prefix.isNotEmpty() && isHex(prefix)) {
                    pendingLength = prefix.toIntOrNull(16)
                }
                markers.forEachIndexed { index, marker ->
                    val frameIndex = marker.groupValues[1].toInt(16)
                    val dataEnd = if (index + 1 < markers.size) markers[index + 1].range.first else compact.length
                    val data = hexBytes(compact.substring(marker.range.last + 1, dataEnd))
                    val active = current
                    // Frame indices wrap after F, so a `0:` only opens a new message
                    // after a length line or once the open one is complete.
                    val startsNew = active == null ||
                        (frameIndex == 0 && (pendingLength != null || active.isComplete() || active.expectedLength == null))
                    if (startsNew) {
                        flushCurrent()
                        current = IsoTpBuffer(pendingLength)
                        pendingLength = null
                    }
                    current?.bytes?.addAll(data)
                }
                continue
            }

            val tokens = line.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val compact = tokens.joinToString("")
            if (!isHex(compact)) continue

            // A bare 3-hex-digit line is the byte count ELM prints before `0:`.
            if (compact.length == 3 && tokens.size == 1) {
                flushCurrent()
                pendingLength = compact.toInt(16)
                continue
            }

            val headerLength = headerLength(tokens, compact)
            if (headerLength == 0) {
                // Headers off: the line is one whole message.
                flushCurrent()
                out.add(Message(hexBytes(compact), canFramed = false))
                continue
            }

            // Headers on (CAN): header, PCI byte, data.
            val header = compact.substring(0, headerLength)
            val frame = hexBytes(compact.substring(headerLength))
            if (frame.isEmpty()) continue
            val pci = frame[0]
            when (pci shr 4) {
                0x0 -> {
                    val len = pci and 0x0F
                    val data = frame.drop(1)
                    out.add(Message(data.take(minOf(len, data.size)), canFramed = true))
                }
                0x1 -> {
                    if (frame.size < 2) continue
                    val len = ((pci and 0x0F) shl 8) or frame[1]
                    byHeader[header]?.let { out.add(it.toMessage()) }
                    val buffer = IsoTpBuffer(len)
                    buffer.bytes.addAll(frame.drop(2))
                    byHeader[header] = buffer
                }
                0x2 -> {
                    byHeader[header]?.bytes?.addAll(frame.drop(1))
                }
                else -> Unit // flow control or unknown: not data
            }
        }
        flushCurrent()
        byHeader.values.forEach { out.add(it.toMessage()) }
        return out
    }

    /**
     * Hex chars of CAN header at the start of a line, or 0 when there is none.
     * 11-bit ids print as three hex digits (`7E8`), 29-bit as four bytes (`18 DA F1 10`).
     */
    private fun headerLength(tokens: List<String>, compact: String): Int {
        if (tokens.size > 1) {
            if (tokens[0].length == 3) return 3
            if (tokens.size >= 5 && tokens[0] == "18" && (tokens[1] == "DA" || tokens[1] == "DB")) return 8
            return 0
        }
        // No spaces (ATS0): an 11-bit header makes the digit count odd.
        if (compact.length % 2 == 1 && compact.length >= 5 && compact[0] == '7') return 3
        if (compact.length >= 12 && (compact.startsWith("18DA") || compact.startsWith("18DB"))) return 8
        return 0
    }

    private fun isHex(s: String): Boolean =
        s.isNotEmpty() && s.all { it in '0'..'9' || it in 'A'..'F' }

    private fun hexBytes(hex: String): List<Int> {
        val clean = hex.filter { it in '0'..'9' || it in 'A'..'F' }
        val out = ArrayList<Int>(clean.length / 2)
        var i = 0
        while (i + 1 < clean.length) {
            out.add(clean.substring(i, i + 2).toInt(16))
            i += 2
        }
        return out
    }
}
