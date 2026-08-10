package com.domivega.gps_car.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Classic Bluetooth RFCOMM SPP transport for ELM327-style adapters.
 */
class SppElmTransport : ElmTransport {
    companion object {
        private const val TAG = "SppElmTransport"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @Volatile
    private var socket: BluetoothSocket? = null

    @Volatile
    private var input: InputStream? = null

    @Volatile
    private var output: OutputStream? = null

    private val scanning = AtomicBoolean(false)
    private val reading = AtomicBoolean(false)
    private var appContext: Context? = null
    private var discoveryReceiver: BroadcastReceiver? = null
    private var onLinkLost: ((String) -> Unit)? = null
    private var onBytes: ((ByteArray) -> Unit)? = null

    override val isLinked: Boolean
        get() = socket?.isConnected == true && output != null

    @SuppressLint("MissingPermission")
    override fun startScan(
        adapter: BluetoothAdapter,
        onDevice: (BleDeviceInfo) -> Unit,
        onStatus: (String) -> Unit,
    ) {
        if (!scanning.compareAndSet(false, true)) return
        try {
            // Bonded first so paired OBD sticks appear immediately.
            for (d in adapter.bondedDevices.orEmpty()) {
                val address = d.address ?: continue
                val name = try {
                    d.name
                } catch (_: SecurityException) {
                    null
                }
                onDevice(BleDeviceInfo(name = name, address = address))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "bondedDevices failed", t)
        }

        val ctx = appContext
        if (ctx == null) {
            // Context set on connect; for scan-only path caller should have set via prepare.
            // Fall through to discovery if we can cancel without receiver.
        }

        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
        } catch (_: Throwable) {
        }

        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_FOUND) return
                val device: BluetoothDevice? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                val d = device ?: return
                val address = d.address ?: return
                val name = try {
                    d.name
                } catch (_: SecurityException) {
                    null
                }
                onDevice(BleDeviceInfo(name = name, address = address))
            }
        }
        discoveryReceiver = receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        try {
            if (ctx != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    ctx.registerReceiver(receiver, filter)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "registerReceiver failed", t)
        }

        try {
            val started = adapter.startDiscovery()
            if (!started) {
                onStatus("Classic discovery did not start (bonded list still shown)")
            }
        } catch (t: Throwable) {
            scanning.set(false)
            onStatus("Classic scan failed: ${t.message}")
            Log.w(TAG, "startDiscovery failed", t)
        }
    }

    /**
     * Bind application context used for discovery BroadcastReceiver registration.
     * Call from session [initialize] / before [startScan].
     */
    fun attachContext(context: Context) {
        appContext = context.applicationContext
    }

    @SuppressLint("MissingPermission")
    override fun stopScan(adapter: BluetoothAdapter?) {
        if (!scanning.getAndSet(false) && discoveryReceiver == null) {
            try {
                adapter?.cancelDiscovery()
            } catch (_: Throwable) {
            }
            return
        }
        scanning.set(false)
        try {
            adapter?.cancelDiscovery()
        } catch (_: Throwable) {
        }
        val receiver = discoveryReceiver
        discoveryReceiver = null
        val ctx = appContext
        if (receiver != null && ctx != null) {
            try {
                ctx.unregisterReceiver(receiver)
            } catch (_: Throwable) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(
        context: Context,
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
        onBytes: (ByteArray) -> Unit,
        onLinkLost: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        attachContext(context)
        closeSocketOnly()
        this@SppElmTransport.onBytes = onBytes
        this@SppElmTransport.onLinkLost = onLinkLost
        try {
            try {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
            } catch (_: Throwable) {
            }

            val sock = openSocket(device)
            sock.connect()
            socket = sock
            input = sock.inputStream
            output = sock.outputStream
            startReadLoop()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "SPP connect failed", t)
            closeSocketOnly()
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun openSocket(device: BluetoothDevice): BluetoothSocket {
        return try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (t: Throwable) {
            Log.w(TAG, "createRfcommSocketToServiceRecord failed, trying insecure", t)
            try {
                device.javaClass
                    .getMethod("createInsecureRfcommSocketToServiceRecord", UUID::class.java)
                    .invoke(device, SPP_UUID) as BluetoothSocket
            } catch (t2: Throwable) {
                Log.w(TAG, "insecure SPP failed, channel 1", t2)
                @Suppress("DEPRECATION")
                device.javaClass
                    .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
            }
        }
    }

    private fun startReadLoop() {
        if (!reading.compareAndSet(false, true)) return
        val stream = input ?: run {
            reading.set(false)
            return
        }
        Thread({
            val buf = ByteArray(1024)
            try {
                while (reading.get()) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    if (n == 0) continue
                    val chunk = buf.copyOf(n)
                    onBytes?.invoke(chunk)
                }
            } catch (e: IOException) {
                if (reading.get()) {
                    Log.w(TAG, "SPP read ended", e)
                    val lost = onLinkLost
                    onLinkLost = null
                    lost?.invoke(e.message ?: "SPP link lost")
                }
            } finally {
                reading.set(false)
            }
        }, "spp-elm-read").apply {
            isDaemon = true
            start()
        }
    }

    override suspend fun write(payload: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val out = output ?: return@withContext false
        return@withContext try {
            out.write(payload)
            out.flush()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "SPP write failed", t)
            false
        }
    }

    override fun close() {
        stopScan(null)
        onLinkLost = null
        onBytes = null
        closeSocketOnly()
    }

    private fun closeSocketOnly() {
        reading.set(false)
        try {
            input?.close()
        } catch (_: Throwable) {
        }
        try {
            output?.close()
        } catch (_: Throwable) {
        }
        try {
            socket?.close()
        } catch (_: Throwable) {
        }
        input = null
        output = null
        socket = null
    }
}
