# OBD Device Section Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move Adapter and Vehicle OBD configuration from Settings onto a dedicated OBD Device drawer screen, without changing BLE/OBD behavior.

**Architecture:** UI-only Compose extraction. Unit-tested `ObdDevicePresentation.deviceLabel` owns the device string. Shared `SettingsSection` keeps card chrome. `ObdDeviceScreen` hosts Adapter + Vehicle + BLE dialog. `SettingsScreen` keeps Backend / Fuel / Upload only. `AppNavigation` adds the drawer destination and reroutes existing callbacks.

**Tech Stack:** Kotlin Multiplatform (Android), Jetpack Compose Material 3, JUnit/kotlin.test.

**Design:** `docs/plans/2026-08-19-obd-device-section-design.md`

Work on branch `feature/obd-device-section`.

---

### Task 1: ObdDevicePresentation.deviceLabel (TDD)

**Files:**
- Create: `composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/ui/ObdDevicePresentationTest.kt`
- Create: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/ObdDevicePresentation.kt`

**Step 1: Write the failing test**

```kotlin
package com.domivega.gps_car.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ObdDevicePresentationTest {
    @Test
    fun deviceLabel_prefersLiveLabelThenStoredThenNone() {
        assertEquals(
            "Live ELM (AA:BB)",
            ObdDevicePresentation.deviceLabel(
                liveLabel = "Live ELM (AA:BB)",
                deviceName = "Stored",
                deviceAddress = "11:22",
            ),
        )
        assertEquals(
            "ELM327 (AA:BB:CC:DD:EE:FF)",
            ObdDevicePresentation.deviceLabel(
                liveLabel = "",
                deviceName = "ELM327",
                deviceAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )
        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            ObdDevicePresentation.deviceLabel(
                liveLabel = "   ",
                deviceName = "",
                deviceAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )
        assertEquals(
            "Garage dongle",
            ObdDevicePresentation.deviceLabel(
                liveLabel = "",
                deviceName = "Garage dongle",
                deviceAddress = "",
            ),
        )
        assertEquals(
            "None selected",
            ObdDevicePresentation.deviceLabel(
                liveLabel = "",
                deviceName = "",
                deviceAddress = "",
            ),
        )
    }
}
```

**Step 2: Run test to verify it fails**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest --tests com.domivega.gps_car.ui.ObdDevicePresentationTest`

Expected: FAIL — `ObdDevicePresentation` unresolved.

**Step 3: Write minimal implementation**

```kotlin
package com.domivega.gps_car.ui

object ObdDevicePresentation {
    fun deviceLabel(liveLabel: String, deviceName: String, deviceAddress: String): String {
        val live = liveLabel.trim()
        if (live.isNotEmpty()) return live
        return when {
            deviceName.isNotBlank() && deviceAddress.isNotBlank() ->
                "$deviceName ($deviceAddress)"
            deviceAddress.isNotBlank() -> deviceAddress
            deviceName.isNotBlank() -> deviceName
            else -> "None selected"
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest --tests com.domivega.gps_car.ui.ObdDevicePresentationTest`

Expected: PASS

**Step 5: Commit**

```bash
git add composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/ui/ObdDevicePresentationTest.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/ObdDevicePresentation.kt
git commit -m "feat(ui): add OBD device label helper" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 2: Extract SettingsSection (and SettingsTextField)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/SettingsSection.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/SettingsScreen.kt`

**Step 1: Move shared chrome**

Create `SettingsSection.kt` with the current `SettingsSection` and `SettingsTextField` bodies, same package `com.domivega.gps_car.ui.screens`, **internal** (not private) so both screens can call them.

Remove the private `SettingsSection` and `SettingsTextField` from `SettingsScreen.kt`. Leave `UploadFieldSwitch`, `SettingsDoubleField`, and `formatDoubleForField` in Settings (Fuel/Upload only).

**Step 2: Compile check**

Run: `devenv shell -- ./gradlew :composeApp:compileDebugKotlinAndroid`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/SettingsSection.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/SettingsScreen.kt
git commit -m "refactor(ui): share settings section chrome" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 3: Add ObdDeviceScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/ObdDeviceScreen.kt`

**Step 1: New screen with Adapter + Vehicle + BLE dialog**

Signature (callbacks only what these cards need):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdDeviceScreen(
    state: SettingsUiState,
    bleDeviceLabel: String = "",
    connectionStatus: String = "",
    protocolOptions: List<Pair<String, String>> = emptyList(),
    transportOptions: List<Pair<String, String>> =
        BluetoothTransport.entries.map { it.name to it.displayName },
    scannedDevices: List<Pair<String, String>> = emptyList(),
    onProtocolSelected: (String) -> Unit = {},
    onTransportSelected: (String) -> Unit = {},
    vehicleObdProfileOptions: List<Pair<String, String>> =
        VehicleObdProfile.entries.map { it.name to it.displayName },
    onVehicleObdProfileSelected: (String) -> Unit = {},
    onVwOdometerDidChange: (String) -> Unit = {},
    onWwhObdOnlyChange: (Boolean) -> Unit = {},
    onScanClick: () -> Unit = {},
    onDeviceSelected: (address: String, name: String?) -> Unit = { _, _ -> },
    onConnectClick: () -> Unit = {},
    onDisconnectClick: () -> Unit = {},
)
```

Body: copy Adapter + Vehicle blocks and the BLE `AlertDialog` from `SettingsScreen` **verbatim**, except:

- `deviceLabel` uses `ObdDevicePresentation.deviceLabel(bleDeviceLabel, state.bleDeviceName, state.bleDeviceAddress)`
- Column padding/spacing matches Settings (`16.dp` / `20.dp`)

Keep Connect `enabled = state.bleDeviceAddress.isNotBlank() && state.obdEnabled`.

**Step 2: Compile check**

Run: `devenv shell -- ./gradlew :composeApp:compileDebugKotlinAndroid`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/ObdDeviceScreen.kt
git commit -m "feat(ui): add dedicated OBD Device screen" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 4: Slim SettingsScreen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/SettingsScreen.kt`

**Step 1: Remove Adapter, Vehicle, BLE dialog, and unused params**

Delete:

- Adapter `SettingsSection` and Vehicle `SettingsSection`
- `if (showDeviceDialog) { ... }` dialog
- Locals only used by those cards: `protocolExpanded`, `transportExpanded`, `vehicleProfileExpanded`, `showDeviceDialog`, `selectedProtocolLabel`, `selectedTransportLabel`, `selectedVehicleProfileLabel`, `deviceLabel`
- Parameters: `bleDeviceLabel`, `connectionStatus`, `protocolOptions`, `transportOptions`, `scannedDevices`, `onProtocolSelected`, `onTransportSelected`, `vehicleObdProfileOptions`, `onVehicleObdProfileSelected`, `onVwOdometerDidChange`, `onWwhObdOnlyChange`, `onScanClick`, `onDeviceSelected`, `onConnectClick`, `onDisconnectClick`

Remove unused imports (`BluetoothTransport`, `VehicleObdProfile`, `LazyColumn`, `items`, `heightIn`, `AlertDialog`, `clickable` if unused, `ExperimentalMaterial3Api` / dropdown APIs if unused). Keep Backend / Fuel / Upload unchanged.

`SettingsScreen` will no longer compile from `AppNavigation` until Task 5 — that is expected if you compile the whole app; prefer finishing Task 5 in the same sitting if the compiler fails on the call site.

**Step 2: Commit** (after Task 5 if compile is broken mid-way; otherwise after both)

If committing this file alone would leave `AppNavigation` uncompilable, do **not** commit until Task 5 is done.

---

### Task 5: Wire drawer + callbacks

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/navigation/AppNavigation.kt`

**Step 1: Add destination and route callbacks**

- Import `ObdDeviceScreen`.
- Insert `NavigationDrawerItem` **OBD Device** between Dashboard and Settings. `selected = currentScreen == "OBD Device"`. On click set `currentScreen = "OBD Device"` and close the drawer. Icon: `Icons.Default.Search` (core filled set; scan/pair). Do not reuse Settings/Debug/About icons.
- In `when (currentScreen)`, add `"OBD Device" -> ObdDeviceScreen(...)` with the BLE/protocol/profile callbacks currently passed to `SettingsScreen`.
- Remove those same arguments from the `SettingsScreen(...)` call.

**Step 2: Compile + unit tests**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest`

Expected: BUILD SUCCESSFUL, all tests PASS including `ObdDevicePresentationTest`.

**Step 3: Commit Settings slim + navigation together**

```bash
git add composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/SettingsScreen.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/navigation/AppNavigation.kt
git commit -m "feat(ui): move OBD Device to its own drawer screen" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 6: Verification

**Step 1: Full unit suite**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest`

Expected: BUILD SUCCESSFUL, 0 failed tests.

**Step 2: If Android/resources/navigation compile was touched, also**

Run: `devenv shell -- ./gradlew :composeApp:assembleDebug`

Expected: BUILD SUCCESSFUL.

**Step 3: Manual glance checklist (no automated UI test)**

- Drawer shows OBD Device between Dashboard and Settings.
- OBD Device: Adapter + Vehicle cards; scan dialog still works.
- Settings: only Backend, Fuel, Upload fields.
- Dashboard OBD enable switch and confirm dialog unchanged.

Do not weaken tests. Do not commit secrets.
