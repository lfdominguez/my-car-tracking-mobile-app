package com.domivega.gps_car.data

import android.content.Context
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
                context?.let { UploadPauseStore.clearAndResume(it) }
                ConnectionTestOutcome.Ok(carName = result.carName)
            }
            is ConnectionTestResult.Unreachable -> ConnectionTestOutcome.Unreachable(result.detail)
            is ConnectionTestResult.Unauthorized -> ConnectionTestOutcome.Unauthorized(result.detail)
            is ConnectionTestResult.TokenNotVerified -> ConnectionTestOutcome.TokenNotVerified(result.detail)
            is ConnectionTestResult.Failed -> ConnectionTestOutcome.Failed(result.detail)
        }
    }
}
