package com.domivega.gps_car.obd

enum class BluetoothTransport {
    Ble,
    ClassicSpp;

    val displayName: String
        get() = when (this) {
            Ble -> "BLE (GATT)"
            ClassicSpp -> "Classic Bluetooth (SPP)"
        }

    companion object {
        val DEFAULT = Ble

        fun fromName(name: String): BluetoothTransport {
            val n = name.trim()
            entries.firstOrNull { it.name.equals(n, ignoreCase = true) }?.let { return it }
            val compact = n.replace("_", "")
            return entries.firstOrNull {
                it.name.replace("_", "").equals(compact, ignoreCase = true)
            } ?: DEFAULT
        }
    }
}
