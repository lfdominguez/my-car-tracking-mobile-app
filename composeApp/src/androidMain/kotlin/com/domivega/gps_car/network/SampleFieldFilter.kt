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
        )
}
