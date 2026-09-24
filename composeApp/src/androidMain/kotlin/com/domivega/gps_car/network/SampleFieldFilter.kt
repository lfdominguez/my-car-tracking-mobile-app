package com.domivega.gps_car.network

import com.domivega.gps_car.settings.SampleUploadFieldFlags

object SampleFieldFilter {
    fun apply(sample: Sample, flags: SampleUploadFieldFlags): Sample =
        sample.copy(
            fuelConsumptionRate = sample.fuelConsumptionRate.takeIf { flags.fuelConsumptionRate },
            engineLoadPct = sample.engineLoadPct.takeIf { flags.engineLoadPct },
            absoluteEngineLoadPct = sample.absoluteEngineLoadPct.takeIf { flags.absoluteEngineLoadPct },
            shortTermFuelTrimPct = sample.shortTermFuelTrimPct.takeIf { flags.shortTermFuelTrimPct },
            longTermFuelTrimPct = sample.longTermFuelTrimPct.takeIf { flags.longTermFuelTrimPct },
            fuelLevelPct = sample.fuelLevelPct.takeIf { flags.fuelLevelPct },
            acceleratorPedalPct = sample.acceleratorPedalPct.takeIf { flags.acceleratorPedalPct },
            ambientAirTempC = sample.ambientAirTempC.takeIf { flags.ambientAirTempC },
            odometerValueKm = sample.odometerValueKm.takeIf { flags.odometerValueKm },
            engineCoolantTempC = sample.engineCoolantTempC.takeIf { flags.engineCoolantTempC },
            manifoldAbsolutePressureKpa =
                sample.manifoldAbsolutePressureKpa.takeIf { flags.manifoldAbsolutePressureKpa },
            controlModuleVoltage = sample.controlModuleVoltage.takeIf { flags.controlModuleVoltage },
            engineOnTime = sample.engineOnTime.takeIf { flags.engineOnTime },
            massAirFlow = sample.massAirFlow.takeIf { flags.massAirFlow },
            lambdaCmd = sample.lambdaCmd.takeIf { flags.lambdaCmd },
            atmosphericPressure = sample.atmosphericPressure.takeIf { flags.atmosphericPressure },
            intakeAirTemperature = sample.intakeAirTemperature.takeIf { flags.intakeAirTemperature },
            // HV battery fields are always kept (no toggle), like SoC and power.
            batterySocPct = sample.batterySocPct,
            batteryPowerKw = sample.batteryPowerKw,
            hvBatteryVoltageV = sample.hvBatteryVoltageV,
            hvBatteryCurrentA = sample.hvBatteryCurrentA,
            distanceSinceDtcClearKm =
                sample.distanceSinceDtcClearKm.takeIf { flags.distanceSinceDtcClearKm },
            accelPeakMps2 = sample.accelPeakMps2.takeIf { flags.motion },
            accelRmsMps2 = sample.accelRmsMps2.takeIf { flags.motion },
            deviceTiltDeltaDeg = sample.deviceTiltDeltaDeg.takeIf { flags.motion },
            // Opt-in via its own setting (default off), so never filtered here.
            dtcCodes = sample.dtcCodes,
            pendingDtcCodes = sample.pendingDtcCodes,
        )
}
