package com.domivega.gps_car.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BLE GATT UART transport (Nordic UART or FFE0/FFE1 clones).
 */
class BleElmTransport : ElmTransport {
    companion object {
        private const val TAG = "BleElmTransport"
        private val NORDIC_UART_SERVICE = uuid("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val NORDIC_UART_RX = uuid("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private val NORDIC_UART_TX = uuid("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        private val FFE0_SERVICE = uuid("0000FFE0-0000-1000-8000-00805F9B34FB")
        private val FFE1_CHAR = uuid("0000FFE1-0000-1000-8000-00805F9B34FB")
        private val CCCD = uuid("00002902-0000-1000-8000-00805F9B34FB")
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val GATT_SUCCESS = 0
        private const val WRITE_BUSY_RETRIES = 4
        private const val WRITE_BUSY_RETRY_DELAY_MS = 25L

        private fun uuid(s: String): UUID = UUID.fromString(s)
    }

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var writeChar: BluetoothGattCharacteristic? = null

    @Volatile
    private var notifyChar: BluetoothGattCharacteristic? = null

    private val scanning = AtomicBoolean(false)
    private var connectCompletion: CompletableDeferred<Boolean>? = null
    private var onBytes: ((ByteArray) -> Unit)? = null
    private var onLinkLost: ((String) -> Unit)? = null
    private var scanCallback: ScanCallback? = null
    private var scanAdapter: BluetoothAdapter? = null

    override val isLinked: Boolean
        get() = gatt != null && writeChar != null

    @SuppressLint("MissingPermission")
    override fun startScan(
        adapter: BluetoothAdapter,
        onDevice: (BleDeviceInfo) -> Unit,
        onStatus: (String) -> Unit,
    ) {
        if (!scanning.compareAndSet(false, true)) return
        scanAdapter = adapter
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            scanning.set(false)
            onStatus("BLE scanner unavailable")
            return
        }
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                val address = device.address ?: return
                val name = try {
                    device.name ?: result.scanRecord?.deviceName
                } catch (_: SecurityException) {
                    result.scanRecord?.deviceName
                }
                onDevice(BleDeviceInfo(name = name, address = address))
            }

            override fun onScanFailed(errorCode: Int) {
                scanning.set(false)
                onStatus("Scan failed (code $errorCode)")
                Log.w(TAG, "Scan failed: $errorCode")
            }
        }
        scanCallback = cb
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, cb)
        } catch (t: Throwable) {
            scanning.set(false)
            scanCallback = null
            onStatus("Scan failed: ${t.message}")
            Log.w(TAG, "startScan failed", t)
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopScan(adapter: BluetoothAdapter?) {
        if (!scanning.getAndSet(false)) return
        val cb = scanCallback
        scanCallback = null
        val a = adapter ?: scanAdapter
        scanAdapter = null
        try {
            if (cb != null) {
                a?.bluetoothLeScanner?.stopScan(cb)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "stopScan failed", t)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(
        context: Context,
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
        onBytes: (ByteArray) -> Unit,
        onLinkLost: (String) -> Unit,
    ): Boolean {
        close()
        this.onBytes = onBytes
        this.onLinkLost = onLinkLost
        val completed = CompletableDeferred<Boolean>()
        connectCompletion = completed
        val newGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, gattCallback)
        }
        gatt = newGatt
        val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { completed.await() } == true
        if (!ok) {
            close()
            return false
        }
        return true
    }

    @SuppressLint("MissingPermission")
    override suspend fun write(payload: ByteArray): Boolean {
        val g = gatt ?: return false
        val wc = writeChar ?: return false
        // Android allows only one outstanding GATT operation at a time; writeCharacteristic()
        // returns non-success ("busy") if the previous write's onCharacteristicWrite callback
        // has not landed yet. That is transient (a few ms), not a dead link — retry briefly
        // instead of surfacing it as a fatal write failure that tears down the whole session.
        repeat(WRITE_BUSY_RETRIES) { attempt ->
            if (writeCharacteristic(g, wc, payload)) return true
            if (gatt !== g) return false
            if (attempt < WRITE_BUSY_RETRIES - 1) delay(WRITE_BUSY_RETRY_DELAY_MS)
        }
        return false
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        stopScan(null)
        writeChar = null
        notifyChar = null
        onBytes = null
        val lost = onLinkLost
        onLinkLost = null
        connectCompletion?.complete(false)
        connectCompletion = null
        val g = gatt
        gatt = null
        if (g != null) {
            try {
                g.disconnect()
            } catch (_: Throwable) {
            }
            try {
                g.close()
            } catch (_: Throwable) {
            }
        }
        // Do not invoke lost on intentional close from session disconnect.
        lost?.let { /* intentional */ }
    }

    @Suppress("DEPRECATION")
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "GATT CONNECTED status=$status")
                try {
                    g.discoverServices()
                } catch (t: Throwable) {
                    Log.w(TAG, "discoverServices failed", t)
                    connectCompletion?.complete(false)
                    connectCompletion = null
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "GATT DISCONNECTED status=$status")
                writeChar = null
                notifyChar = null
                val pending = connectCompletion
                if (pending != null && !pending.isCompleted) {
                    pending.complete(false)
                    connectCompletion = null
                } else {
                    val lost = onLinkLost
                    onLinkLost = null
                    lost?.invoke("disconnected")
                }
                if (gatt === g) {
                    try {
                        g.close()
                    } catch (_: Throwable) {
                    }
                    gatt = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                connectCompletion?.complete(false)
                connectCompletion = null
                return
            }
            if (!selectUartCharacteristics(g)) {
                Log.e(TAG, "No UART characteristic found on adapter")
                connectCompletion?.complete(false)
                connectCompletion = null
                return
            }
            enableNotifications(g, notifyChar!!)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = characteristic.value ?: return
            onBytes?.invoke(value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onBytes?.invoke(value)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.uuid != CCCD) return
            val ok = status == BluetoothGatt.GATT_SUCCESS
            if (ok) {
                Log.i(TAG, "Notify enabled (CCCD ok)")
            } else {
                Log.e(TAG, "Notify enable failed (CCCD status=$status)")
            }
            connectCompletion?.complete(ok && writeChar != null)
            connectCompletion = null
        }
    }

    private fun selectUartCharacteristics(g: BluetoothGatt): Boolean {
        // Nordic UART
        g.getService(NORDIC_UART_SERVICE)?.let { svc ->
            val rx = svc.getCharacteristic(NORDIC_UART_RX)
            val tx = svc.getCharacteristic(NORDIC_UART_TX)
            if (rx != null && tx != null) {
                writeChar = rx
                notifyChar = tx
                Log.i(TAG, "Using Nordic UART")
                return true
            }
        }
        // FFE0/FFE1 common clones (often same char for write+notify)
        g.getService(FFE0_SERVICE)?.let { svc ->
            val c = svc.getCharacteristic(FFE1_CHAR)
            if (c != null) {
                writeChar = c
                notifyChar = c
                Log.i(TAG, "Using FFE0/FFE1 UART")
                return true
            }
        }
        // Heuristic: first writable + notifiable char pair
        for (svc in g.services.orEmpty()) {
            val chars = svc.characteristics.orEmpty()
            val writable = chars.firstOrNull {
                val p = it.properties
                (p and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            }
            val notifiable = chars.firstOrNull {
                val p = it.properties
                (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                    (p and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            }
            if (writable != null && notifiable != null) {
                writeChar = writable
                notifyChar = notifiable
                Log.i(TAG, "Using heuristic UART svc=${svc.uuid}")
                return true
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD) ?: run {
            Log.e(TAG, "CCCD missing")
            connectCompletion?.complete(false)
            connectCompletion = null
            return
        }
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val code = g.writeDescriptor(descriptor, value)
            if (code != GATT_SUCCESS) {
                Log.e(TAG, "writeDescriptor failed code=$code")
                connectCompletion?.complete(false)
                connectCompletion = null
            }
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
    ): Boolean {
        val props = characteristic.properties
        val writeType =
            if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 &&
                (props and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0
            ) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val code = g.writeCharacteristic(characteristic, payload, writeType)
            code == GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            g.writeCharacteristic(characteristic)
        }
    }
}
