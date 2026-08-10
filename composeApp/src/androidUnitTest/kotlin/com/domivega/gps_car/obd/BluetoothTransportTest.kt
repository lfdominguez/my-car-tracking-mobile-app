package com.domivega.gps_car.obd

import kotlin.test.Test
import kotlin.test.assertEquals

class BluetoothTransportTest {
    @Test
    fun default_is_ble() {
        assertEquals(BluetoothTransport.Ble, BluetoothTransport.DEFAULT)
    }

    @Test
    fun fromName_parses_case_insensitive() {
        assertEquals(BluetoothTransport.Ble, BluetoothTransport.fromName("ble"))
        assertEquals(BluetoothTransport.ClassicSpp, BluetoothTransport.fromName("CLASSIC_SPP"))
        assertEquals(BluetoothTransport.ClassicSpp, BluetoothTransport.fromName("ClassicSpp"))
    }

    @Test
    fun fromName_unknown_falls_back_to_default() {
        assertEquals(BluetoothTransport.DEFAULT, BluetoothTransport.fromName(""))
        assertEquals(BluetoothTransport.DEFAULT, BluetoothTransport.fromName("wifi"))
    }

    @Test
    fun display_names_are_user_facing() {
        assertEquals("BLE (GATT)", BluetoothTransport.Ble.displayName)
        assertEquals("Classic Bluetooth (SPP)", BluetoothTransport.ClassicSpp.displayName)
    }
}
