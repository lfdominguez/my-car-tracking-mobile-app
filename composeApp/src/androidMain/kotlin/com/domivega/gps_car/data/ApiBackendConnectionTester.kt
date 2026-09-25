package com.domivega.gps_car.data

import android.content.Context
import com.domivega.gps_car.data.queue.UploadPauseReason
import com.domivega.gps_car.data.queue.UploadPauseStore
import com.domivega.gps_car.network.ApiClient
import com.domivega.gps_car.network.ConnectionTestResult

class ApiBackendConnectionTester(
    private val api: ApiClient,
    /** When set, a verified token lifts an upload pause and resumes the drain. */
    private val context: Context? = null,
) : BackendConnectionTester {
    override suspend fun test(): ConnectionTestOutcome {
        return when (val result = api.testConnection()) {
            is ConnectionTestResult.Ok -> {
                onTokenVerified(result.vaultRequired)
                ConnectionTestOutcome.Ok(
                    carName = result.carName,
                    vaultRequired = result.vaultRequired,
                )
            }
            is ConnectionTestResult.Unreachable -> ConnectionTestOutcome.Unreachable(result.detail)
            is ConnectionTestResult.Unauthorized -> ConnectionTestOutcome.Unauthorized(result.detail)
            is ConnectionTestResult.TokenNotVerified -> ConnectionTestOutcome.TokenNotVerified(result.detail)
            is ConnectionTestResult.Failed -> ConnectionTestOutcome.Failed(result.detail)
        }
    }

    private fun onTokenVerified(vaultRequired: Boolean) {
        val ctx = context ?: return
        if (!vaultRequired) {
            UploadPauseStore.clearAndResume(ctx)
        } else if (UploadPauseStore.get(ctx) == UploadPauseReason.DeviceUnauthorized) {
            // The token is fine again; what still blocks uploads is the car's vault.
            UploadPauseStore.pause(
                ctx,
                UploadPauseReason.VaultRequired,
                com.domivega.gps_car.settings.AppSettings(ctx).apiToken,
            )
        }
    }
}
