# Diesel / Gasoline Fuel Class Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a Settings Gasoline/Diesel class switch (gasoline ethanol grades unchanged; diesel B7 + Custom) and load-based diesel L/h from PID 04.

**Architecture:** Persist `fuelClass` plus existing `fuelType` grade. `FuelTypePreset` gains B7 and `gradesFor`. `FuelConsumptionCalculator` uses diesel AFR (idle blend below 25% load, else 17.5). `ObdBleManager.recomputeFuelRate` passes class and PID 04. Do not reuse MQB cluster odometer/tank on Generic OBD.

**Tech Stack:** Kotlin Multiplatform (Android), Jetpack Compose, JUnit.

**Design:** `docs/plans/2026-08-20-diesel-fuel-class-design.md`

Work in: `.worktrees/diesel-fuel-type` on `feature/diesel-fuel-type`.

---

### Task 1: FuelClass + B7 preset (TDD)

**Files:**
- Modify: `composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/fuel/FuelTypePresetTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/fuel/FuelTypePreset.kt`

**Step 1: Write the failing tests** (append to `FuelTypePresetTest`)

```kotlin
@Test
fun `B7 preset has diesel stoich and density`() {
    val p = FuelTypePreset.B7
    assertEquals(14.5, p.stoichAfr!!, 0.0001)
    assertEquals(835.0, p.densityGl!!, 0.0001)
    assertEquals(FuelClass.DIESEL, p.fuelClass)
}

@Test
fun `gradesFor gasoline includes ethanol and custom not B7`() {
    val names = FuelTypePreset.gradesFor(FuelClass.GASOLINE).map { it.name }
    assertEquals(listOf("E0", "E10", "E27", "E100", "CUSTOM"), names)
}

@Test
fun `gradesFor diesel is B7 and custom`() {
    val names = FuelTypePreset.gradesFor(FuelClass.DIESEL).map { it.name }
    assertEquals(listOf("B7", "CUSTOM"), names)
}

@Test
fun `fromName parses B7 and FuelClass defaults unknown to GASOLINE`() {
    assertEquals(FuelTypePreset.B7, FuelTypePreset.fromName("B7"))
    assertEquals(FuelClass.GASOLINE, FuelClass.fromName("NOPE"))
    assertEquals(FuelClass.DIESEL, FuelClass.fromName("diesel"))
}

@Test
fun `defaultGrade is E10 gasoline and B7 diesel`() {
    assertEquals(FuelTypePreset.E10, FuelTypePreset.defaultGrade(FuelClass.GASOLINE))
    assertEquals(FuelTypePreset.B7, FuelTypePreset.defaultGrade(FuelClass.DIESEL))
}
```

**Step 2: Run test to verify it fails**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest --tests com.domivega.gps_car.fuel.FuelTypePresetTest`

Expected: FAIL — `B7` / `FuelClass` unresolved.

**Step 3: Write minimal implementation**

Replace `FuelTypePreset.kt` with:

```kotlin
package com.domivega.gps_car.fuel

enum class FuelClass(
    val displayName: String,
) {
    GASOLINE("Gasoline"),
    DIESEL("Diesel");

    companion object {
        fun fromName(name: String): FuelClass =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: GASOLINE
    }
}

enum class FuelTypePreset(
    val displayName: String,
    val stoichAfr: Double?,
    val densityGl: Double?,
    val fuelClass: FuelClass?,
) {
    E0("E0 (gasoline)", 14.7, 745.0, FuelClass.GASOLINE),
    E10("E10", 14.08, 745.0, FuelClass.GASOLINE),
    E27("E27 (BR gasohol)", 13.2, 755.0, FuelClass.GASOLINE),
    E100("E100 (ethanol)", 9.0, 789.0, FuelClass.GASOLINE),
    B7("B7 (diesel)", 14.5, 835.0, FuelClass.DIESEL),
    CUSTOM("Custom", null, null, null);

    companion object {
        fun fromName(name: String): FuelTypePreset =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CUSTOM

        fun gradesFor(fuelClass: FuelClass): List<FuelTypePreset> =
            entries.filter { it == CUSTOM || it.fuelClass == fuelClass }

        fun defaultGrade(fuelClass: FuelClass): FuelTypePreset =
            if (fuelClass == FuelClass.DIESEL) B7 else E10
    }
}
```

**Step 4: Run tests**

Same command. Expected: PASS.

**Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/domivega/gps_car/fuel/FuelTypePreset.kt \
  composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/fuel/FuelTypePresetTest.kt
git commit -m "feat(fuel): add FuelClass and B7 diesel preset" --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 2: Diesel load-based AFR (TDD)

**Files:**
- Modify: `composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/fuel/FuelConsumptionCalculatorTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/fuel/FuelConsumptionCalculator.kt`

**Step 1: Write the failing tests** (append)

```kotlin
private val dieselConfig = FuelCalcConfig(
    stoichAfr = 14.5,
    densityGl = 835.0,
    displacementL = 1.9,
    ve = 0.85,
    isDiesel = true,
)

@Test
fun `dieselAfr idle load blends toward 38`() {
    assertEquals(38.0, FuelConsumptionCalculator.dieselAfr(0.0), 0.001)
    assertEquals(27.75, FuelConsumptionCalculator.dieselAfr(12.5), 0.001)
    assertEquals(17.5, FuelConsumptionCalculator.dieselAfr(25.0), 0.001)
}

@Test
fun `dieselAfr high or unknown load is cruise 17_5`() {
    assertEquals(17.5, FuelConsumptionCalculator.dieselAfr(80.0), 0.001)
    assertEquals(17.5, FuelConsumptionCalculator.dieselAfr(null), 0.001)
    assertEquals(17.5, FuelConsumptionCalculator.dieselAfr(Double.NaN), 0.001)
}

@Test
fun `diesel idle load uses leaner AFR than cruise load`() {
    val idle = FuelConsumptionCalculator.litersPerHour(
        dieselConfig,
        FuelCalcSensors(mafGs = 6.0, calculatedLoadPct = 10.0),
    )!!
    val cruise = FuelConsumptionCalculator.litersPerHour(
        dieselConfig,
        FuelCalcSensors(mafGs = 6.0, calculatedLoadPct = 40.0),
    )!!
    assertTrue(idle < cruise)
    // cruise: 6*3600/(17.5*835) ≈ 1.480
    assertEquals(1.480, cruise, 0.01)
}

@Test
fun `diesel ignores gasoline lambda and fuel trims`() {
    val base = FuelConsumptionCalculator.litersPerHour(
        dieselConfig,
        FuelCalcSensors(mafGs = 10.0, calculatedLoadPct = 50.0),
    )!!
    val withLambda = FuelConsumptionCalculator.litersPerHour(
        dieselConfig,
        FuelCalcSensors(mafGs = 10.0, calculatedLoadPct = 50.0, lambda = 0.8, stftPct = 10.0),
    )!!
    assertEquals(base, withLambda, 1e-9)
}

@Test
fun `diesel still prefers ECU PID 5E`() {
    val result = FuelConsumptionCalculator.litersPerHour(
        dieselConfig,
        FuelCalcSensors(mafGs = 10.0, calculatedLoadPct = 50.0, ecuFuelRateLh = 8.0),
    )
    assertEquals(8.0, result!!, 1e-9)
}

@Test
fun `gasoline path unchanged when isDiesel false`() {
    val result = FuelConsumptionCalculator.litersPerHour(
        e10Config,
        FuelCalcSensors(mafGs = 10.0, lambda = 1.0, calculatedLoadPct = 10.0),
    )
    assertEquals(3.433, result!!, 0.01)
}
```

**Step 2: Run test to verify it fails**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest --tests com.domivega.gps_car.fuel.FuelConsumptionCalculatorTest`

Expected: FAIL — `isDiesel` / `dieselAfr` / `calculatedLoadPct` unresolved.

**Step 3: Write minimal implementation**

Add to `FuelCalcConfig`: `val isDiesel: Boolean = false`

Add to `FuelCalcSensors`: `val calculatedLoadPct: Double? = null`

Add constants and functions on `FuelConsumptionCalculator`:

```kotlin
const val DIESEL_AFR_CRUISE = 17.5
const val DIESEL_AFR_IDLE = 38.0
const val DIESEL_IDLE_LOAD_MAX_PCT = 25.0

fun dieselAfr(loadPct: Double?): Double {
    if (loadPct == null || !loadPct.isFinite()) return DIESEL_AFR_CRUISE
    val load = loadPct.coerceIn(0.0, 100.0)
    if (load >= DIESEL_IDLE_LOAD_MAX_PCT) return DIESEL_AFR_CRUISE
    val t = load / DIESEL_IDLE_LOAD_MAX_PCT
    return DIESEL_AFR_IDLE + (DIESEL_AFR_CRUISE - DIESEL_AFR_IDLE) * t
}
```

In `litersPerHour`, after air mass is known:

```kotlin
if (config.isDiesel) {
    val afr = dieselAfr(sensors.calculatedLoadPct)
    val lh = airGs * 3600.0 / (afr * config.densityGl)
    return lh.takeIf { it.isFinite() && it > 0.0 && it <= 200.0 }
}
val lambda = sensors.lambda?.takeIf { it in 0.5..1.5 } ?: 1.0
val trim = trimFactor(sensors.stftPct, sensors.ltftPct)
val lh = airGs * 3600.0 / (config.stoichAfr * lambda * config.densityGl) * trim
return lh.takeIf { it.isFinite() && it > 0.0 && it <= 200.0 }
```

Keep the existing `stoichAfr <= 0 || densityGl <= 0` guard before air mass. Diesel still needs `densityGl > 0` (stoich unused on diesel path).

**Step 4: Run tests** — same command. Expected: PASS (including existing gasoline tests).

**Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/domivega/gps_car/fuel/FuelConsumptionCalculator.kt \
  composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/fuel/FuelConsumptionCalculatorTest.kt
git commit -m "feat(fuel): load-based diesel AFR for estimated L/h" --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 3: Persist fuelClass

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/domivega/gps_car/settings/AppSettings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/data/SettingsRepository.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/domivega/gps_car/data/AndroidSettingsRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/state/SettingsUiState.kt`
- Modify: `composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/ui/SettingsQrParseTest.kt` (`FakeSettingsRepository`)

**Step 1:** Add `KEY_FUEL_CLASS = "fuel_class"`, `DEFAULT_FUEL_CLASS = "GASOLINE"`, and `var fuelClass: String` next to `fuelType` in `AppSettings`. Thread through repository + `SettingsUiState.fuelClass` (default `"GASOLINE"`). Add `override var fuelClass: String = "GASOLINE"` on `FakeSettingsRepository`.

**Step 2:** Compile: `devenv shell -- ./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileDebugUnitTestKotlinAndroid`

Expected: SUCCESS.

**Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/domivega/gps_car/settings/AppSettings.kt \
  composeApp/src/commonMain/kotlin/com/domivega/gps_car/data/SettingsRepository.kt \
  composeApp/src/androidMain/kotlin/com/domivega/gps_car/data/AndroidSettingsRepository.kt \
  composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/state/SettingsUiState.kt \
  composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/ui/SettingsQrParseTest.kt
git commit -m "feat(settings): persist fuel class gasoline/diesel" --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 4: SettingsViewModel class + grade (TDD)

**Files:**
- Modify: `composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/ui/SettingsQrParseTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/SettingsViewModel.kt`

**Step 1: Failing tests**

```kotlin
@Test
fun updateFuelClass_dieselWritesB7Constants() {
    val repo = FakeSettingsRepository()
    val vm = SettingsViewModel(repo)
    vm.updateFuelClass("DIESEL")
    assertEquals("DIESEL", repo.fuelClass)
    assertEquals("B7", repo.fuelType)
    assertEquals(14.5, repo.fuelStoichAfr, 0.0001)
    assertEquals(835.0, repo.fuelDensityGl, 0.0001)
    assertEquals("DIESEL", vm.uiState.value.fuelClass)
    assertEquals("B7", vm.uiState.value.fuelType)
}

@Test
fun updateFuelClass_gasolineWritesE10Constants() {
    val repo = FakeSettingsRepository()
    val vm = SettingsViewModel(repo)
    vm.updateFuelClass("DIESEL")
    vm.updateFuelClass("GASOLINE")
    assertEquals("GASOLINE", repo.fuelClass)
    assertEquals("E10", repo.fuelType)
    assertEquals(14.08, repo.fuelStoichAfr, 0.0001)
    assertEquals(745.0, repo.fuelDensityGl, 0.0001)
}

@Test
fun updateFuelStoichAfr_keepsDieselClassWhenCustom() {
    val repo = FakeSettingsRepository()
    val vm = SettingsViewModel(repo)
    vm.updateFuelClass("DIESEL")
    vm.updateFuelStoichAfr(14.2)
    assertEquals("CUSTOM", repo.fuelType)
    assertEquals("DIESEL", repo.fuelClass)
    assertEquals(14.2, repo.fuelStoichAfr, 0.0001)
}

@Test
fun qrWithFuelClass_appliesDiesel() {
    val repo = FakeSettingsRepository()
    val vm = SettingsViewModel(repo)
    val qr = """
        {
          "apiToken": "tok",
          "startUrl": "https://track.example.com/api/track/start",
          "stopUrl": "https://track.example.com/api/track/stop",
          "sampleUrl": "https://track.example.com/api/track/sample",
          "samplesUrl": "https://track.example.com/api/track/samples",
          "fuelClass": "DIESEL",
          "fuelType": "B7",
          "fuelStoichAfr": 14.5,
          "fuelDensityGl": 835.0
        }
    """.trimIndent()
    vm.updateSettingsFromQr(qr)
    assertEquals("", vm.uiState.value.qrError)
    assertEquals("DIESEL", repo.fuelClass)
    assertEquals("B7", repo.fuelType)
}
```

**Step 2:** Run `--tests com.domivega.gps_car.ui.SettingsQrParseTest` — FAIL until `updateFuelClass` exists.

**Step 3:** In `SettingsViewModel`:
- Load `fuelClass = repository.fuelClass` in initial state.
- `updateFuelClass(name: String)`: `FuelClass.fromName`, write class, `defaultGrade`, AFR/density like `updateFuelType`.
- `updateFuelType` unchanged except it must not change `fuelClass`.
- `updateFuelStoichAfr` / `updateFuelDensityGl`: still set grade CUSTOM, do **not** change `fuelClass`.
- QR apply: if `newState.fuelClass.isNotEmpty()` write `FuelClass.fromName(newState.fuelClass).name`.

**Step 4:** Tests PASS.

**Step 5: Commit** `feat(settings): switch fuel class writes E10 or B7`

---

### Task 5: Settings screen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/navigation/AppNavigation.kt`

**Step 1:** Fuel section:
- Supporting: `Gasoline uses MAF × λ. Diesel uses MAF and load-based AFR (PID 04). Defaults: E10 or B7. Set displacement for the engine (1.9 L for a 1.9 TDI). VE only if MAF is unavailable.`
- Dropdown **Fuel class** (Gasoline / Diesel) → `onFuelClassSelected`.
- Grade dropdown options: `FuelTypePreset.gradesFor(FuelClass.fromName(state.fuelClass)).map { it.name to it.displayName }` (caller can pass `fuelTypeOptions` already filtered, or filter inside the screen). Prefer filtering in the screen from `state.fuelClass` so the parent can pass all grades or none.
- Keep AFR / density / displacement / VE / tank fields.

Wire `onFuelClassSelected = settingsViewModel::updateFuelClass` in `AppNavigation`.

**Step 2:** `compileDebugKotlinAndroid` SUCCESS.

**Step 3: Commit** `feat(settings): gasoline/diesel class control in Fuel section`

---

### Task 6: Wire ObdBleManager

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/domivega/gps_car/obd/ObdBleManager.kt`

In `recomputeFuelRate`:

```kotlin
val config = FuelCalcConfig(
    stoichAfr = settings.fuelStoichAfr,
    densityGl = settings.fuelDensityGl,
    displacementL = settings.engineDisplacementL,
    ve = settings.engineVe,
    isDiesel = FuelClass.fromName(settings.fuelClass) == FuelClass.DIESEL,
)
val sensors = FuelCalcSensors(
    mafGs = into["10"],
    lambda = into["44"],
    mapKpa = into["0b"],
    rpm = into["0c"],
    iatC = into["0f"],
    stftPct = into["06"],
    ltftPct = into["07"],
    ecuFuelRateLh = into["5e"],
    speedKph = into["0d"],
    calculatedLoadPct = into["04"],
)
```

**Commit:** `feat(obd): pass diesel class and PID 04 into fuel L/h`

---

### Task 7: README

**Files:**
- Modify: `README.md`

Update the vehicle & fuel bullet to mention Gasoline/Diesel, B7, and load-based diesel AFR. Note Golf mk4 TDI: Generic OBD, no MQB odometer/tank.

**Commit:** `docs: gasoline/diesel fuel class in README`

---

### Task 8: Full unit tests

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest`

Expected: all PASS.

If Android resources were not touched, skip `assembleDebug`.
