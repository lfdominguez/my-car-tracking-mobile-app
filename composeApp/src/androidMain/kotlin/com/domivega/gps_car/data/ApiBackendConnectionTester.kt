package com.domivega.gps_car.data

import com.domivega.gps_car.network.ApiClient
import com.domivega.gps_car.network.ConnectionTestResult

class ApiBackendConnectionTester(
    private val api: ApiClient,
) : BackendConnectionTester {
    override suspend fun test(): ConnectionTestOutcome {
        return when (val result = api.testConnection()) {
            ConnectionTestResult.Ok -> ConnectionTestOutcome.Ok
            is ConnectionTestResult.Unreachable -> ConnectionTestOutcome.Unreachable(result.detail)
            is ConnectionTestResult.Unauthorized -> ConnectionTestOutcome.Unauthorized(result.detail)
            is ConnectionTestResult.Failed -> ConnectionTestOutcome.Failed(result.detail)
        }
    }
}
