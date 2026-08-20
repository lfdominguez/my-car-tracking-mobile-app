# OBD Performance Mode Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a default-off Performance switch that enables ELM expected-line suffixes for single-line Mode 01 PIDs and `ATAT2` on init, without changing Normal mode.

**Architecture:** `ElmPerformanceMode` is the unit-tested helper. `AppSettings.obdPerformanceMode` is the persisted flag. `ObdBleManager` reads the flag at init (timing) and on each Mode 01 poll (command suffix). WWH/UDS/A6/support bitmaps never get a suffix.

**Tech Stack:** Kotlin Multiplatform (Android), Jetpack Compose, JUnit.

**Design:** `docs/plans/2026-08-20-obd-performance-mode-design.md`

Work in: `/home/luis/Work/Personal/Kotlin/GPSCarTracking/.worktrees/obd-performance-mode` on `feature/obd-performance-mode`.

---

### Task 1: ElmPerformanceMode helper (TDD)

**Files:**
- Create: `composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/obd/ElmPerformanceModeTest.kt`
- Create: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/obd/ElmPerformanceMode.kt`

**Step 1: Write the failing test**

```kotlin
package com.domivega.gps_car.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmPerformanceModeTest {

    @Test
    fun adaptiveTiming_normalIsAtat1() {
        assertEquals("ATAT1", ElmPerformanceMode.adaptiveTimingCommand(performance = false))
    }

    @Test
    fun adaptiveTiming_performanceIsAtat2() {
        assertEquals("ATAT2", ElmPerformanceMode.adaptiveTimingCommand(performance = true))
    }

    @Test
    fun mode01Poll_normalHasNoLineSuffix() {
        assertEquals("010C", ElmPerformanceMode.mode01PollCommand("0c", performance = false))
        assertEquals("010D", ElmPerformanceMode.mode01PollCommand("0d", performance = false))
    }

    @Test
    fun mode01Poll_performanceAppendsOneLineForSingleFramePids() {
        assertEquals("010C1", ElmPerformanceMode.mode01PollCommand("0c", performance = true))
        assertEquals("010D1", ElmPerformanceMode.mode01PollCommand("0d", performance = true))
        assertEquals("01421", ElmPerformanceMode.mode01PollCommand("42", performance = true))
        assertEquals("01101", ElmPerformanceMode.mode01PollCommand("10", performance = true))
    }

    @Test
    fun mode01Poll_neverSuffixesOdometerA6OrSupportBitmaps() {
        assertEquals("01A6", ElmPerformanceMode.mode01PollCommand("a6", performance = true))
        assertEquals("0100", ElmPerformanceMode.mode01PollCommand("00", performance = true))
        assertEquals("0120", ElmPerformanceMode.mode01PollCommand("20", performance = true))
        assertEquals("0140", ElmPerformanceMode.mode01PollCommand("40", performance = true))
    }

    @Test
    fun expectsSingleResponseLine_falseForA6AndSupport() {
        assertTrue(ElmPerformanceMode.expectsSingleResponseLine("0c"))
        assertFalse(ElmPerformanceMode.expectsSingleResponseLine("a6"))
        assertFalse(ElmPerformanceMode.expectsSingleResponseLine("00"))
        assertFalse(ElmPerformanceMode.expectsSingleResponseLine("not-hex"))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest --tests com.domivega.gps_car.obd.ElmPerformanceModeTest`

Expected: FAIL — `ElmPerformanceMode` unresolved.

**Step 3: Write minimal implementation**

```kotlin
package com.domivega.gps_car.obd

/**
 * ELM327 Performance-mode command helpers.
 * Normal mode keeps ATAT1 and unsuffixed Mode 01 polls (adapter waits for timeout).
 * Performance uses ATAT2 and a trailing expected-line count for single-frame Mode 01 PIDs.
 */
object ElmPerformanceMode {
    fun adaptiveTimingCommand(performance: Boolean): String =
        if (performance) "ATAT2" else "ATAT1"

    fun mode01PollCommand(pidHex: String, performance: Boolean): String {
        val pid = pidHex.trim().lowercase()
        val base = "01" + pid.uppercase()
        if (!performance || !expectsSingleResponseLine(pid)) return base
        return base + "1"
    }

    fun expectsSingleResponseLine(pidHex: String): Boolean {
        val pid = pidHex.trim().lowercase()
        if (pid == "a6") return false
        val n = pid.toIntOrNull(16) ?: return false
        // SAE J1979 support bitmaps: 00, 20, 40, 60, 80, A0, C0
        if (n % 0x20 == 0) return false
        return true
    }
}
```

**Step 4: Run test to verify it passes**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest --tests com.domivega.gps_car.obd.ElmPerformanceModeTest`

Expected: PASS

**Step 5: Commit**

```bash
git add composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/obd/ElmPerformanceModeTest.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/obd/ElmPerformanceMode.kt
git commit -m "feat(obd): add ElmPerformanceMode ATAT2 and line-suffix helpers" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 2: Persist `obdPerformanceMode` (default false)

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/domivega/gps_car/settings/AppSettings.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/data/SettingsRepository.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/domivega/gps_car/data/AndroidSettingsRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/state/SettingsUiState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/SettingsViewModel.kt`
- Modify: `composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/ui/SettingsQrParseTest.kt`

**Step 1: Extend FakeSettingsRepository so the interface change compiles**

In `FakeSettingsRepository` add:

```kotlin
override var obdPerformanceMode: Boolean = false
```

**Step 2: Write the failing persist test** (same file)

```kotlin
@Test
fun updateObdPerformanceMode_persistsFlag() {
    val repo = FakeSettingsRepository()
    val vm = SettingsViewModel(repo)
    assertEquals(false, vm.uiState.value.obdPerformanceMode)
    vm.updateObdPerformanceMode(true)
    assertEquals(true, repo.obdPerformanceMode)
    assertEquals(true, vm.uiState.value.obdPerformanceMode)
}
```

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest --tests com.domivega.gps_car.ui.SettingsQrParseTest.updateObdPerformanceMode_persistsFlag`

Expected: FAIL — property / method missing.

**Step 3: Wire the setting**

`AppSettings` companion:

```kotlin
private const val KEY_OBD_PERFORMANCE_MODE = "obd_performance_mode"
const val DEFAULT_OBD_PERFORMANCE_MODE = false
```

Property (next to `wwhObdOnly`):

```kotlin
/** Faster ELM polling: ATAT2 + Mode 01 expected-line suffix. Default off (Normal). */
var obdPerformanceMode: Boolean
    get() = prefs.getBoolean(KEY_OBD_PERFORMANCE_MODE, DEFAULT_OBD_PERFORMANCE_MODE)
    set(value) = prefs.edit().putBoolean(KEY_OBD_PERFORMANCE_MODE, value).apply()
```

`SettingsRepository`:

```kotlin
var obdPerformanceMode: Boolean
```

`AndroidSettingsRepository`:

```kotlin
override var obdPerformanceMode: Boolean
    get() = appSettings.obdPerformanceMode
    set(value) { appSettings.obdPerformanceMode = value }
```

`SettingsUiState` (next to `wwhObdOnly`):

```kotlin
/** Faster ELM polling (ATAT2 + Mode 01 line suffix). Default off. */
val obdPerformanceMode: Boolean = false,
```

`SettingsViewModel.loadSettings`: copy `obdPerformanceMode = repository.obdPerformanceMode`.

```kotlin
fun updateObdPerformanceMode(enabled: Boolean) {
    repository.obdPerformanceMode = enabled
    _uiState.update { it.copy(obdPerformanceMode = enabled) }
}
```

**Step 4: Run persist test**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest --tests com.domivega.gps_car.ui.SettingsQrParseTest`

Expected: PASS

**Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/domivega/gps_car/settings/AppSettings.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/data/SettingsRepository.kt \
        composeApp/src/androidMain/kotlin/com/domivega/gps_car/data/AndroidSettingsRepository.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/state/SettingsUiState.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/SettingsViewModel.kt \
        composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/ui/SettingsQrParseTest.kt
git commit -m "feat(settings): persist OBD Performance mode flag (default off)" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 3: OBD Device switch

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/ObdDeviceScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/navigation/AppNavigation.kt`

**Step 1: Add callback + switch next to WWH-OBD**

`ObdDeviceScreen` parameter (after `onWwhObdOnlyChange`):

```kotlin
onObdPerformanceModeChange: (Boolean) -> Unit = {},
```

UI row after the WWH-OBD switch:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Column(Modifier.weight(1f).padding(end = 12.dp)) {
        Text("Performance mode")
        Text(
            text = "Faster ELM polling (expected response lines + aggressive timing). " +
                "Line suffix applies on the next PID query; ATAT2 on next OBD reconnect. " +
                "Turn off if PIDs start dropping.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Switch(
        checked = state.obdPerformanceMode,
        onCheckedChange = onObdPerformanceModeChange,
    )
}
```

`AppNavigation` ObdDeviceScreen call:

```kotlin
onWwhObdOnlyChange = settingsViewModel::updateWwhObdOnly,
onObdPerformanceModeChange = settingsViewModel::updateObdPerformanceMode,
```

**Step 2: Compile**

Run: `devenv shell -- ./gradlew :composeApp:compileDebugKotlinAndroid`

Expected: SUCCESS

**Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/ObdDeviceScreen.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/navigation/AppNavigation.kt
git commit -m "feat(ui): add OBD Performance mode switch" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 4: Use helper in ObdBleManager

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/domivega/gps_car/obd/ObdBleManager.kt`

**Step 1: Init timing**

In `runInitSequence` `baseSteps`, replace `"ATAT1"` with:

```kotlin
ElmPerformanceMode.adaptiveTimingCommand(settings.obdPerformanceMode)
```

Keep the timeout `COMMAND_TIMEOUT_MS`. Optionally log `performance=${settings.obdPerformanceMode}` in the existing “ELM init OK” lines.

**Step 2: Poll Mode 01 commands**

In `startPollLoop`, replace `"01${pid.uppercase()}"` with:

```kotlin
ElmPerformanceMode.mode01PollCommand(pid, settings.obdPerformanceMode)
```

WWH branch stays `WwhObd.commandForPidHex(pid)`.

**Step 3: Health probe**

In `applyEngineHealthProbe` classic branch, replace hardcoded `"010C"` with:

```kotlin
ElmPerformanceMode.mode01PollCommand("0c", settings.obdPerformanceMode)
```

Do **not** suffix `0100` probes, UDS `22…`, or WWH `22F4xx`.

**Step 4: Compile + unit tests**

Run:

```bash
devenv shell -- ./gradlew :composeApp:testDebugUnitTest
devenv shell -- ./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: all unit tests PASS; compile SUCCESS.

**Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/domivega/gps_car/obd/ObdBleManager.kt
git commit -m "feat(obd): apply Performance mode ATAT2 and Mode 01 line suffix" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 5: Verify complete

**Step 1:** `devenv shell -- ./gradlew :composeApp:testDebugUnitTest`

Expected: PASS (including `ElmPerformanceModeTest` and `updateObdPerformanceMode_persistsFlag`).

**Step 2:** Confirm Normal defaults: `DEFAULT_OBD_PERFORMANCE_MODE = false`, no `ATST`, HOT/SLOW lists unchanged, `ATE0`/`ATS0`/`ATH0` still always sent.

Do not push unless asked. Open a PR into `main` only after the user chooses merge/PR.
