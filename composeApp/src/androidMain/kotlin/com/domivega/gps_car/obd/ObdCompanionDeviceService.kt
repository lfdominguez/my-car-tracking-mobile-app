package com.domivega.gps_car.obd

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * System callbacks when an associated companion dongle appears/disappears.
 * Wakes idle connect without a 7s polling loop.
 */
@RequiresApi(Build.VERSION_CODES.S)
class ObdCompanionDeviceService : CompanionDeviceService() {

    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(address: String) {
        Log.d(TAG, "onDeviceAppeared(address)=$address")
        ObdBleManager.initialize(applicationContext)
        ObdPresenceController.onDeviceAppeared(address)
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(address: String) {
        Log.d(TAG, "onDeviceDisappeared(address)=$address")
        ObdPresenceController.onDeviceDisappeared(address)
    }

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val mac = associationInfo.deviceMacAddress?.toString()
            Log.d(TAG, "onDeviceAppeared(info)=$mac")
            if (mac != null) {
                ObdBleManager.initialize(applicationContext)
                ObdPresenceController.onDeviceAppeared(mac)
            }
        }
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val mac = associationInfo.deviceMacAddress?.toString()
            Log.d(TAG, "onDeviceDisappeared(info)=$mac")
            if (mac != null) {
                ObdPresenceController.onDeviceDisappeared(mac)
            }
        }
    }

    companion object {
        private const val TAG = "ObdCompanionSvc"
    }
}
