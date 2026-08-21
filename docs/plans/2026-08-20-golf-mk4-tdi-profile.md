# VW Golf mk4 TDI (ASZ) Profile Implementation Plan

**Goal:** Add Vehicle OBD profile `VwGolfMk4Tdi` that applies ISO 9141-2 + Diesel B7 + 1.9 L + 55 L tank and skips CAN `ATSH7DF`, without changing Generic or MQB.

**Architecture:** New enum value. `GolfMk4TdiDefaults` applied only from `SettingsViewModel.updateVehicleObdProfile`. `ElmInitPolicy.sendCanFunctionalHeader` false only for Golf. `VwOdoFirstGate` remains MQB-only.

**Tech Stack:** Kotlin Multiplatform (Android), Jetpack Compose, JUnit.

**Design:** `docs/plans/2026-08-20-golf-mk4-tdi-profile-design.md`

Work on branch `feature/golf-mk4-tdi-profile`.

---

### Task 1: Enum + parse (TDD)

**Files:**
- Modify: `composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/obd/VehicleObdProfileTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/obd/VehicleObdProfile.kt`
- Modify: `composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/obd/VwOdoFirstGateTest.kt`

**Step 1: Failing tests**

In `VehicleObdProfileTest`:

```kotlin
@Test
fun `display names match product labels`() {
    assertEquals("Generic OBD", VehicleObdProfile.Generic.displayName)
    assertEquals("VW MQB (Nivus)", VehicleObdProfile.VwMqb.displayName)
    assertEquals("VW Golf mk4 TDI (ASZ)", VehicleObdProfile.VwGolfMk4Tdi.displayName)
}

@Test
fun `fromName parses known and defaults unknown to Generic`() {
    assertEquals(VehicleObdProfile.Generic, VehicleObdProfile.fromName("Generic"))
    assertEquals(VehicleObdProfile.VwMqb, VehicleObdProfile.fromName("VwMqb"))
    assertEquals(VehicleObdProfile.VwMqb, VehicleObdProfile.fromName("vwmqb"))
    assertEquals(VehicleObdProfile.VwGolfMk4Tdi, VehicleObdProfile.fromName("VwGolfMk4Tdi"))
    assertEquals(VehicleObdProfile.VwGolfMk4Tdi, VehicleObdProfile.fromName("vwgolfmk4tdi"))
    assertEquals(VehicleObdProfile.Generic, VehicleObdProfile.fromName("NOPE"))
    assertEquals(VehicleObdProfile.Generic, VehicleObdProfile.fromName(""))
}
```

In `VwOdoFirstGateTest`:

```kotlin
assertFalse(VwOdoFirstGate.requiresOdometerBeforeMode01(VehicleObdProfile.VwGolfMk4Tdi))
```

**Step 2:** `devenv shell -- ./gradlew :composeApp:testDebugUnitTest --tests com.domivega.gps_car.obd.VehicleObdProfileTest`

Expected: FAIL — `VwGolfMk4Tdi` unresolved.

**Step 3:** Add enum entry:

```kotlin
VwGolfMk4Tdi -> "VW Golf mk4 TDI (ASZ)"
```

**Step 4:** Same tests + `VwOdoFirstGateTest` PASS.

**Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/domivega/gps_car/obd/VehicleObdProfile.kt \
  composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/obd/VehicleObdProfileTest.kt \
  composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/obd/VwOdoFirstGateTest.kt
git commit -m "feat(obd): add VW Golf mk4 TDI (ASZ) vehicle profile" --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 2: ElmInitPolicy (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/obd/ElmInitPolicy.kt`
- Create: `composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/obd/ElmInitPolicyTest.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/domivega/gps_car/obd/ObdBleManager.kt`

**Step 1: Failing tests**

```kotlin
@Test
fun `CAN functional header skipped only for Golf mk4 TDI`() {
    assertFalse(ElmInitPolicy.sendCanFunctionalHeader(VehicleObdProfile.VwGolfMk4Tdi))
    assertTrue(ElmInitPolicy.sendCanFunctionalHeader(VehicleObdProfile.Generic))
    assertTrue(ElmInitPolicy.sendCanFunctionalHeader(VehicleObdProfile.VwMqb))
}
```

**Step 2:** FAIL until type exists.

**Step 3:**

```kotlin
object ElmInitPolicy {
    fun sendCanFunctionalHeader(profile: VehicleObdProfile): Boolean =
        profile != VehicleObdProfile.VwGolfMk4Tdi
}
```

In `runInitSequence`, wrap existing ATAR/ATSH7DF:

```kotlin
val profile = VehicleObdProfile.fromName(settings.vehicleObdProfile)
if (ElmInitPolicy.sendCanFunctionalHeader(profile)) {
    sendCommandLogged("ATAR", COMMAND_TIMEOUT_MS, isInit = true)
    sendCommandLogged("ATSH7DF", COMMAND_TIMEOUT_MS, isInit = true)
    sessionEngineHeader = "7DF"
}
```

Keep `sessionEngineHeader = "7DF"` earlier in init as today; Golf simply does not send those AT commands.

**Step 4:** Tests PASS. `compileDebugKotlinAndroid` SUCCESS.

**Step 5: Commit** `feat(obd): skip CAN ATSH7DF on Golf mk4 TDI profile`

---

### Task 3: Apply ASZ defaults on profile select (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/obd/GolfMk4TdiDefaults.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/SettingsViewModel.kt`
- Modify: `composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/ui/SettingsQrParseTest.kt`

**Step 1: Failing tests** in `SettingsQrParseTest`:

```kotlin
@Test
fun updateVehicleObdProfile_golfMk4WritesAszPack() {
    val repo = FakeSettingsRepository()
    val vm = SettingsViewModel(repo)
    vm.updateVehicleObdProfile("VwGolfMk4Tdi")
    assertEquals("VwGolfMk4Tdi", repo.vehicleObdProfile)
    assertEquals("ISO_9141_2", repo.obdProtocol)
    assertEquals("DIESEL", repo.fuelClass)
    assertEquals("B7", repo.fuelType)
    assertEquals(14.5, repo.fuelStoichAfr, 0.0001)
    assertEquals(835.0, repo.fuelDensityGl, 0.0001)
    assertEquals(1.9, repo.engineDisplacementL, 0.0001)
    assertEquals(55.0, repo.tankCapacityL, 0.0001)
    assertFalse(repo.wwhObdOnly)
}

@Test
fun updateVehicleObdProfile_leavingGolfDoesNotRevertAszPack() {
    val repo = FakeSettingsRepository()
    val vm = SettingsViewModel(repo)
    vm.updateVehicleObdProfile("VwGolfMk4Tdi")
    vm.updateVehicleObdProfile("Generic")
    assertEquals("Generic", repo.vehicleObdProfile)
    assertEquals("ISO_9141_2", repo.obdProtocol)
    assertEquals("DIESEL", repo.fuelClass)
    assertEquals(1.9, repo.engineDisplacementL, 0.0001)
    assertEquals(55.0, repo.tankCapacityL, 0.0001)
}

@Test
fun updateVehicleObdProfile_mqbDoesNotWriteAszPack() {
    val repo = FakeSettingsRepository()
    val vm = SettingsViewModel(repo)
    vm.updateVehicleObdProfile("VwMqb")
    assertEquals("VwMqb", repo.vehicleObdProfile)
    assertEquals("ISO_15765_4_CAN_11_500", repo.obdProtocol)
    assertEquals("GASOLINE", repo.fuelClass)
    assertEquals(1.0, repo.engineDisplacementL, 0.0001)
    assertEquals(0.0, repo.tankCapacityL, 0.0001)
}
```

**Step 2:** FAIL — still only writes profile name.

**Step 3:** `GolfMk4TdiDefaults`:

```kotlin
object GolfMk4TdiDefaults {
    const val PROTOCOL = "ISO_9141_2"
    const val DISPLACEMENT_L = 1.9
    const val TANK_CAPACITY_L = 55.0
}
```

In `updateVehicleObdProfile`, after writing profile name: if `VwGolfMk4Tdi`, set protocol, `updateFuelClass`-equivalent Diesel/B7, displacement, tank, `wwhObdOnly = false`, and copy those into `uiState`. Do not change VE, transport, performance, API.

Unknown names still normalize to Generic with **no** pack.

**Step 4:** Tests PASS.

**Step 5: Commit** `feat(settings): Golf mk4 TDI profile applies K-line diesel defaults`

---

### Task 4: OBD Device copy + README

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/ObdDeviceScreen.kt`
- Modify: `README.md`

Vehicle section supporting text: mention Golf mk4 applies ISO 9141-2 + Diesel B7 + 1.9 L + 55 L tank; no dash odo/tank %; MQB DID still MQB-only.

README vehicle/fuel bullet: third profile name.

`compileDebugKotlinAndroid` SUCCESS.

Commit `docs: Golf mk4 TDI (ASZ) vehicle profile`

---

### Task 5: Full unit tests

```bash
devenv shell -- ./gradlew :composeApp:testDebugUnitTest
```

Expected: PASS.

---

### Done when

- Dropdown lists three profiles; default Generic.
- Selecting Golf writes the ASZ pack; Generic/MQB select does not write it.
- Golf init skips `ATSH7DF`; MQB hop never runs for Golf.
- No odometer/tank PID invention.
