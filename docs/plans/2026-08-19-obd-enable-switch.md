# OBD Enable/Disable Switch Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the dashboard Start/Stop FAB with a persisted Enable/Disable switch that hard-gates every OBD connect attempt and confirms before ending an active trip.

**Architecture:** `ObdEnableGate` is the unit-tested policy. `AppSettings.obdEnabled` (default true) is the persisted flag. `ObdBleManager.connect()` returns immediately when the gate is closed. The dashboard switch writes the flag, optionally confirms, then `ACTION_STOP` + `disconnect()` on Disable.

**Tech Stack:** Kotlin Multiplatform (Android), Jetpack Compose, JUnit/kotlin.test, Room/FGS unchanged.

**Design:** `docs/plans/2026-08-19-obd-enable-switch-design.md`

Work in: `/home/luis/Work/Personal/Kotlin/GPSCarTracking/.worktrees/obd-enable-switch` on `feature/obd-enable-switch`.

---

### Task 1: ObdEnableGate policy (TDD)

**Files:**
- Create: `composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/ObdEnableGateTest.kt`
- Create: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ObdEnableGate.kt`

**Step 1: Write the failing test**

```kotlin
package com.domivega.gps_car

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObdEnableGateTest {
    @Test
    fun mayConnect_whenEnabled() {
        assertTrue(ObdEnableGate.mayConnect(obdEnabled = true))
    }

    @Test
    fun mayConnect_falseWhenDisabled() {
        assertFalse(ObdEnableGate.mayConnect(obdEnabled = false))
    }

    @Test
    fun disableRequiresConfirmation_onlyWhileTracking() {
        assertTrue(ObdEnableGate.disableRequiresConfirmation(isTracking = true))
        assertFalse(ObdEnableGate.disableRequiresConfirmation(isTracking = false))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest --tests com.domivega.gps_car.ObdEnableGateTest`

Expected: FAIL — `ObdEnableGate` unresolved.

**Step 3: Write minimal implementation**

```kotlin
package com.domivega.gps_car

/**
 * Policy for the persisted OBD Enable/Disable switch.
 * When disabled, no connect path may touch the adapter.
 */
object ObdEnableGate {
    fun mayConnect(obdEnabled: Boolean): Boolean = obdEnabled

    fun disableRequiresConfirmation(isTracking: Boolean): Boolean = isTracking
}
```

**Step 4: Run test to verify it passes**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest --tests com.domivega.gps_car.ObdEnableGateTest`

Expected: PASS

**Step 5: Commit**

```bash
git add composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/ObdEnableGateTest.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/ObdEnableGate.kt
git commit -m "feat: add ObdEnableGate connect and confirm policy" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 2: Persist `obdEnabled` (default true)

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/domivega/gps_car/settings/AppSettings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/data/SettingsRepository.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/domivega/gps_car/data/AndroidSettingsRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/state/SettingsUiState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/SettingsViewModel.kt`
- Modify: `composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/ui/SettingsQrParseTest.kt` (`FakeSettingsRepository` must compile)

**Step 1: Extend FakeSettingsRepository first so existing tests stay green after the interface change**

In `FakeSettingsRepository` add:

```kotlin
override var obdEnabled: Boolean = true
```

**Step 2: Add the setting end-to-end**

`AppSettings`:

```kotlin
private const val KEY_OBD_ENABLED = "obd_enabled"
const val DEFAULT_OBD_ENABLED = true

var obdEnabled: Boolean
    get() = prefs.getBoolean(KEY_OBD_ENABLED, DEFAULT_OBD_ENABLED)
    set(value) = prefs.edit().putBoolean(KEY_OBD_ENABLED, value).apply()
```

`SettingsRepository` + `AndroidSettingsRepository`: `var obdEnabled: Boolean`

`SettingsUiState`: `val obdEnabled: Boolean = true`

`SettingsViewModel.loadSettings()` copy `obdEnabled = repository.obdEnabled`

```kotlin
fun updateObdEnabled(enabled: Boolean) {
    repository.obdEnabled = enabled
    _uiState.update { it.copy(obdEnabled = enabled) }
}
```

**Step 3: Run existing settings tests**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest --tests com.domivega.gps_car.ui.SettingsQrParseTest`

Expected: PASS

**Step 4: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/domivega/gps_car/settings/AppSettings.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/data/SettingsRepository.kt \
        composeApp/src/androidMain/kotlin/com/domivega/gps_car/data/AndroidSettingsRepository.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/state/SettingsUiState.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/SettingsViewModel.kt \
        composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/ui/SettingsQrParseTest.kt
git commit -m "feat: persist obdEnabled setting (default on)" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 3: Hard-gate `ObdBleManager.connect()`

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/domivega/gps_car/obd/ObdBleManager.kt` (`fun connect()`, ~line 437)

**Step 1: No new Android-instrumented test** — policy is already tested. This step wires the gate.

**Step 2: At the top of `connect()`, after `ensureInit()`:**

```kotlin
if (!ObdEnableGate.mayConnect(settings.obdEnabled)) {
    logI("connect skipped: OBD disabled")
    setStatus("OBD disabled")
    return
}
```

Do not change `disconnect()`. Presence poll, idle reconnect, and Settings Connect already call `connect()`.

**Step 3: Compile**

Run: `devenv shell -- ./gradlew :composeApp:compileDebugKotlinAndroid`

Expected: SUCCESS

**Step 4: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/domivega/gps_car/obd/ObdBleManager.kt
git commit -m "fix(obd): never connect while OBD is disabled" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 4: Dashboard switch + confirmation

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/state/DashboardState.kt` — add `val obdEnabled: Boolean = true`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/DashboardScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/navigation/AppNavigation.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/domivega/gps_car/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/SettingsScreen.kt` — disable Connect when `!state.obdEnabled`
- Delete unused: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/components/TrackingControlButtons.kt`

**Step 1: Replace FAB with switch + dialog**

`DashboardScreen` signature:

```kotlin
fun DashboardScreen(
    state: DashboardState,
    onObdEnabledChange: (Boolean) -> Unit,
    onRetryUpload: () -> Unit = {},
)
```

Replace `LargeFloatingActionButton` with a bottom `Row` + `Switch` labeled **Enable** / **Disable** (`checked = state.obdEnabled`).

Local `var confirmDisable by remember { mutableStateOf(false) }`.

On switch request `enabled`:
- if `!enabled && ObdEnableGate.disableRequiresConfirmation(state.isTracking)` → `confirmDisable = true` (do not call callback yet)
- else `onObdEnabledChange(enabled)`

`AlertDialog` when `confirmDisable`:
- title/text: “Stop tracking and release the OBD adapter?”
- Confirm → `confirmDisable = false`; `onObdEnabledChange(false)`
- Dismiss/Cancel → `confirmDisable = false` only

**Step 2: Wire App.kt**

Replace `onToggleTracking` with:

```kotlin
onObdEnabledChange = { enabled ->
    settingsViewModel.updateObdEnabled(enabled)
    if (!enabled) {
        if (isTracking) {
            context.startForegroundServiceCompat(ForegroundTrackingService.ACTION_STOP)
        }
        ObdBleManager.disconnect()
    } else {
        connectWithPermissions()
    }
}
```

Pass `obdEnabled` into dashboard state (copy on collect, or add a `LaunchedEffect` / combine in the composable):

```kotlin
val dashboardState = viewModel.uiState.collectAsState().value.copy(
    obdEnabled = settingsState.obdEnabled,
)
```

(`settingsState` is already collected.)

`AppNavigation`: replace `onToggleTracking` with `onObdEnabledChange`.

Settings Connect button:

```kotlin
enabled = state.bleDeviceAddress.isNotBlank() && state.obdEnabled
```

**Step 3: Compile**

Run: `devenv shell -- ./gradlew :composeApp:compileDebugKotlinAndroid`

Expected: SUCCESS

**Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/state/DashboardState.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/DashboardScreen.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/navigation/AppNavigation.kt \
        composeApp/src/androidMain/kotlin/com/domivega/gps_car/App.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/SettingsScreen.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/components/TrackingControlButtons.kt
git commit -m "feat: replace Start/Stop with OBD Enable/Disable switch" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 5: Full unit-test verification

**Step 1: Run all unit tests**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest`

Expected: all existing tests PASS, including `ObdEnableGateTest`, `WaitingFgsGateTest` (WAITING still only cares about dongle address).

**Step 2: Commit only if anything was fixed; otherwise done.**

---

### Manual check (not automated)

- Enable + dongle configured: app may connect; vehicle-on still auto-starts a trip.
- Disable while idle: disconnects, no dialog, reboot does not reconnect.
- Disable while tracking: dialog; cancel keeps trip + switch on; confirm ends trip, disconnects, WAITING notification remains.
- Settings Connect is disabled while OBD is off.
