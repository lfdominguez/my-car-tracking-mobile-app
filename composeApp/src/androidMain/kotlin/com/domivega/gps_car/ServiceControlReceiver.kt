package com.domivega.gps_car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.domivega.gps_car.obd.ObdBleManager
import com.domivega.gps_car.obd.ObdIdleConnectScheduler
import com.domivega.gps_car.obd.ObdPresenceController
import com.domivega.gps_car.settings.AppSettings

/**
 * Receives broadcasts from notification action and system boot to start/stop the tracking service.
 */
class ServiceControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ForegroundTrackingService.ACTION_START -> context.startForegroundServiceCompat(ForegroundTrackingService.ACTION_START)
            ForegroundTrackingService.ACTION_STOP -> context.startForegroundServiceCompat(ForegroundTrackingService.ACTION_STOP)
            ForegroundTrackingService.ACTION_SHUTDOWN -> context.startForegroundServiceCompat(ForegroundTrackingService.ACTION_SHUTDOWN)
            ObdIdleConnectScheduler.ACTION -> {
                ObdBleManager.initialize(context.applicationContext)
                ObdPresenceController.arm(context.applicationContext)
            }

            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.MY_PACKAGE_REPLACED" -> {
                // Re-arm idle connect poll. Keep permanent WAITING when a dongle is configured
                // so OBD can reconnect without the user opening the app.
                val app = context.applicationContext
                ObdBleManager.initialize(app)
                ObdPresenceController.arm(app)
                val address = AppSettings(app).bleDeviceAddress.trim()
                if (address.isNotEmpty()) {
                    context.startForegroundServiceCompat(ForegroundTrackingService.ACTION_START_WAITING)
                }
            }
            else -> {}
        }
    }
}

internal fun Context.startForegroundServiceCompat(action: String) {
    val svc = Intent(this, ForegroundTrackingService::class.java).apply { this.action = action }
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For background-started actions that don't immediately go foreground,
            // just use startService to avoid race conditions and exceptions.
            val useForeground = action == ForegroundTrackingService.ACTION_START || action == ForegroundTrackingService.ACTION_START_WAITING
            if (useForeground) {
                startForegroundService(svc)
            } else {
                startService(svc)
            }
        } else {
            startService(svc)
        }
    } catch (e: Throwable) {
        // Ignore exceptions like IllegalStateException if app is in a state where it can't start services.
    }
}
