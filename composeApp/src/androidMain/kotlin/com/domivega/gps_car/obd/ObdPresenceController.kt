package com.domivega.gps_car.obd

import android.app.Activity
import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.domivega.gps_car.settings.AppSettings
import java.util.concurrent.Executor
import java.util.regex.Pattern

/**
 * Arms idle “wait for car” mode: Companion Device presence when possible,
 * otherwise [ObdBleManager.startAutoReconnect] with [IdleReconnectPolicy] delays.
 */
object ObdPresenceController {
    private const val TAG = "ObdPresence"
    const val REQUEST_CODE_ASSOCIATE = 0x0BD1

    @Volatile
    private var observingAddress: String? = null

    /**
     * One-time system association for [address] so presence observation can run.
     * No-op if already associated, not BLE, or API &lt; 31.
     */
    fun requestAssociationIfNeeded(activity: Activity, address: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (address.isBlank()) return
        val settings = AppSettings(activity)
        if (BluetoothTransport.fromName(settings.bluetoothTransport) != BluetoothTransport.Ble) return
        val cdm = companionManagerOrNull(activity) ?: return
        if (hasAssociatedDevice(cdm, address)) {
            arm(activity)
            return
        }
        try {
            val leFilter = BluetoothLeDeviceFilter.Builder()
                .setNamePattern(Pattern.compile(".*"))
                .setScanFilter(
                    ScanFilter.Builder()
                        .setDeviceAddress(address)
                        .build(),
                )
                .build()
            val classicFilter = BluetoothDeviceFilter.Builder()
                .setAddress(address)
                .build()
            val request = AssociationRequest.Builder()
                .addDeviceFilter(leFilter)
                .addDeviceFilter(classicFilter)
                .setSingleDevice(true)
                .build()
            val callback = object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    try {
                        activity.startIntentSenderForResult(
                            intentSender,
                            REQUEST_CODE_ASSOCIATE,
                            null,
                            0,
                            0,
                            0,
                        )
                    } catch (t: Throwable) {
                        Log.w(TAG, "startIntentSenderForResult failed", t)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onDeviceFound(intentSender: IntentSender) {
                    onAssociationPending(intentSender)
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    Log.i(TAG, "Companion association created")
                    arm(activity)
                }

                override fun onFailure(error: CharSequence?) {
                    Log.w(TAG, "Companion association failed: $error")
                    arm(activity) // fallback reconnect
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val executor = Executor { command -> Handler(Looper.getMainLooper()).post(command) }
                cdm.associate(request, executor, callback)
            } else {
                @Suppress("DEPRECATION")
                cdm.associate(request, callback, null)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "requestAssociationIfNeeded failed", t)
            arm(activity)
        }
    }

    fun onAssociationActivityResult(context: Context, resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            Log.i(TAG, "Association activity RESULT_OK — re-arming idle")
        } else {
            Log.w(TAG, "Association activity result=$resultCode — fallback idle")
        }
        arm(context)
    }

    fun arm(context: Context) {
        val app = context.applicationContext
        ObdBleManager.initialize(app)
        val settings = AppSettings(app)
        val address = settings.bleDeviceAddress.trim()
        val transport = BluetoothTransport.fromName(settings.bluetoothTransport)
        val transportIsBle = transport == BluetoothTransport.Ble
        val hasAddress = address.isNotEmpty()

        val cdm = companionManagerOrNull(app)
        val presenceAvailable = cdm != null &&
            hasAssociatedDevice(cdm, address)

        val usePresence = IdleReconnectPolicy.shouldUsePresenceObservation(
            apiLevel = Build.VERSION.SDK_INT,
            transportIsBle = transportIsBle,
            hasDeviceAddress = hasAddress,
            presenceAvailable = presenceAvailable,
        )

        if (usePresence) {
            ObdBleManager.stopAutoReconnect()
            val started = startObserving(app, cdm!!, address)
            if (started) {
                Log.i(TAG, "Idle arm: CDM presence for $address")
                return
            }
            Log.w(TAG, "Presence observe failed — falling back to reconnect loop")
        } else {
            stopObserving(app)
            Log.i(
                TAG,
                "Idle arm: fallback reconnect " +
                    "(api=${Build.VERSION.SDK_INT} ble=$transportIsBle addr=$hasAddress " +
                    "assoc=$presenceAvailable)",
            )
        }

        if (hasAddress) {
            ObdBleManager.startAutoReconnect()
        } else {
            ObdBleManager.stopAutoReconnect()
        }
    }

    fun onDeviceAppeared(address: String) {
        Log.i(TAG, "Companion device appeared: $address")
        val selected = ObdBleManager.selectedDeviceAddress()
        if (!selected.isNullOrBlank() &&
            !selected.equals(address, ignoreCase = true)
        ) {
            Log.d(TAG, "Ignoring appeared $address (selected=$selected)")
            return
        }
        if (ObdBleManager.isSessionReady() ||
            ObdBleManager.isTransportLinked() ||
            ObdBleManager.isConnecting()
        ) {
            return
        }
        ObdBleManager.connect()
    }

    fun onDeviceDisappeared(address: String) {
        Log.i(TAG, "Companion device disappeared: $address")
        // Link-loss handlers on the transport clear the session; trip stop is EcuConnectionController.
    }

    private fun companionManagerOrNull(context: Context): CompanionDeviceManager? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return context.getSystemService(CompanionDeviceManager::class.java)
    }

    private fun hasAssociatedDevice(cdm: CompanionDeviceManager, address: String): Boolean {
        if (address.isBlank()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // API 31–32: myAssociations is List<String> addresses on older; AssociationInfo on 33+.
            @Suppress("DEPRECATION")
            val associations = cdm.associations
            return associations.any { it.equals(address, ignoreCase = true) }
        }
        val infos: List<AssociationInfo> = cdm.myAssociations
        return infos.any { info ->
            val mac = info.deviceMacAddress?.toString()
            mac != null && mac.equals(address, ignoreCase = true)
        }
    }

    private fun startObserving(
        context: Context,
        cdm: CompanionDeviceManager,
        address: String,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            val prev = observingAddress
            if (prev != null && !prev.equals(address, ignoreCase = true)) {
                runCatching { cdm.stopObservingDevicePresence(prev) }
            }
            cdm.startObservingDevicePresence(address)
            observingAddress = address
            true
        } catch (t: Throwable) {
            Log.w(TAG, "startObservingDevicePresence failed for $address", t)
            observingAddress = null
            false
        }
    }

    private fun stopObserving(context: Context) {
        val addr = observingAddress ?: return
        observingAddress = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val cdm = companionManagerOrNull(context) ?: return
        runCatching { cdm.stopObservingDevicePresence(addr) }
            .onFailure { Log.w(TAG, "stopObservingDevicePresence failed", it) }
    }
}
