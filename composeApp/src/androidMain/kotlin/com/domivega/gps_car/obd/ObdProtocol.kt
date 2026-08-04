package com.domivega.gps_car.obd

enum class ObdProtocol(
    val displayName: String,
    val atspCommand: String
) {
    AUTO("Automatic", "ATSP0"),
    SAE_J1850_PWM("SAE J1850 PWM", "ATSP1"),
    SAE_J1850_VPW("SAE J1850 VPW", "ATSP2"),
    ISO_9141_2("ISO 9141-2", "ATSP3"),
    ISO_14230_4_KWP_5BAUD("ISO 14230-4 KWP (5 baud)", "ATSP4"),
    ISO_14230_4_KWP_FAST("ISO 14230-4 KWP (fast)", "ATSP5"),
    ISO_15765_4_CAN_11_500("ISO 15765-4 CAN (11 bit, 500 kbaud)", "ATSP6"),
    ISO_15765_4_CAN_29_500("ISO 15765-4 CAN (29 bit, 500 kbaud)", "ATSP7"),
    ISO_15765_4_CAN_11_250("ISO 15765-4 CAN (11 bit, 250 kbaud)", "ATSP8"),
    ISO_15765_4_CAN_29_250("ISO 15765-4 CAN (29 bit, 250 kbaud)", "ATSP9");

    companion object {
        val DEFAULT = ISO_15765_4_CAN_11_500
    }
}
