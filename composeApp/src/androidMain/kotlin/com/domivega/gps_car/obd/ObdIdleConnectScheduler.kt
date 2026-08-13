package com.domivega.gps_car.obd

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.domivega.gps_car.ServiceControlReceiver

/**
 * Wakes the process every [IdleReconnectPolicy.INTERVAL_MS] so idle connect
 * still runs after the tracking foreground service has stopped.
 */
object ObdIdleConnectScheduler {
    const val ACTION = "com.domivega.gps_car.action.IDLE_CONNECT"

    private const val TAG = "ObdIdleSched"
    private const val REQUEST_CODE = 0x0BD2

    fun schedule(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = SystemClock.elapsedRealtime() + IdleReconnectPolicy.INTERVAL_MS
        try {
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent(app),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "schedule failed", t)
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            am.cancel(pendingIntent(app))
        } catch (t: Throwable) {
            Log.w(TAG, "cancel failed", t)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ServiceControlReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
