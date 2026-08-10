package com.domivega.gps_car.obd

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context

/** Low-level ELM byte pipe. Session layer owns AT/PID logic. */
interface ElmTransport {
    val isLinked: Boolean

    fun startScan(
        adapter: BluetoothAdapter,
        onDevice: (BleDeviceInfo) -> Unit,
        onStatus: (String) -> Unit,
    )

    fun stopScan(adapter: BluetoothAdapter?)

    /**
     * Establish link. Invoke [onBytes] for each inbound ASCII chunk.
     * @return true when ready for ELM commands.
     */
    suspend fun connect(
        context: Context,
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
        onBytes: (ByteArray) -> Unit,
        onLinkLost: (String) -> Unit,
    ): Boolean

    suspend fun write(payload: ByteArray): Boolean

    fun close()
}
