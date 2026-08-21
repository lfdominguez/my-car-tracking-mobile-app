package com.domivega.gps_car.obd

/**
 * ELM327 init extras that depend on vehicle profile.
 * Golf mk4 TDI is K-line: do not send CAN functional address ATSH7DF.
 */
object ElmInitPolicy {
    fun sendCanFunctionalHeader(profile: VehicleObdProfile): Boolean =
        profile != VehicleObdProfile.VwGolfMk4Tdi
}
