package com.domivega.gps_car.obd

import android.content.Context
import android.os.Build
import android.util.Log
import com.domivega.gps_car.settings.AppSettings

/**
 * Arms idle connect: [ObdBleManager.startAutoReconnect] every
 * [IdleReconnectPolicy.INTERVAL_MS], plus an alarm so the process can wake
 * after the tracking service has stopped.
 *
 * There is deliberately no Companion Device presence path. Waiting for a system
 * "device appeared" callback used to stop the reconnect loop and leave the adapter
 * idle until the app was opened, and it needs a system association dialog per
 * dongle (API 31+ only). The association request was never wired in, so the
 * presence service could not fire; the dead code, its manifest service and the
 * companion permissions were removed. The minute poll is the one idle mechanism.
 */
object ObdPresenceController {
    private const val TAG = "ObdPresence"

    fun arm(context: Context) {
        val app = context.applicationContext
        ObdBleManager.initialize(app)
        val settings = AppSettings(app)
        val address = settings.bleDeviceAddress.trim()
        val transport = BluetoothTransport.fromName(settings.bluetoothTransport)
        val transportIsBle = transport == BluetoothTransport.Ble
        val hasAddress = address.isNotEmpty()

        Log.i(
            TAG,
            "Idle arm: ${IdleReconnectPolicy.INTERVAL_MS / 1000}s connect poll " +
                "(api=${Build.VERSION.SDK_INT} ble=$transportIsBle addr=$hasAddress)",
        )

        if (hasAddress) {
            ObdBleManager.startAutoReconnect()
            ObdIdleConnectScheduler.schedule(app)
        } else {
            ObdBleManager.stopAutoReconnect()
            ObdIdleConnectScheduler.cancel(app)
        }
    }
}
