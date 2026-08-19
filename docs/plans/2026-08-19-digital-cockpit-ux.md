# Digital Cockpit UX Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the analog dashboard cluster and long Settings form with a health-first digital cockpit and grouped Settings cards, without changing OBD/GPS/queue behavior.

**Architecture:** UI-only Compose rewrite. Unit-tested `DashboardPresentation` owns tracking/odometer/extras labels. `AutomotiveTheme` softens neon. Dashboard becomes a health card + 2×2 digital tiles. Settings wrap existing fields in `SettingsSection` cards. Analog gauges are deleted once unused.

**Tech Stack:** Kotlin Multiplatform (Android), Jetpack Compose Material 3, JUnit/kotlin.test.

**Design:** `docs/plans/2026-08-19-digital-cockpit-ux-design.md`

Work on branch `feature/digital-cockpit-ux`.

---

### Task 1: DashboardPresentation helpers (TDD)

**Files:**
- Create: `composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/ui/DashboardPresentationTest.kt`
- Create: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/DashboardPresentation.kt`

**Step 1: Write the failing test**

```kotlin
package com.domivega.gps_car.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashboardPresentationTest {
    @Test
    fun trackingLabel_activeAndIdle() {
        assertEquals("ACTIVE TRACKING", DashboardPresentation.trackingLabel(isTracking = true))
        assertEquals("IDLE", DashboardPresentation.trackingLabel(isTracking = false))
    }

    @Test
    fun odometerLabel_unknownAndRounding() {
        assertEquals("— km", DashboardPresentation.odometerLabel(null))
        assertEquals("— km", DashboardPresentation.odometerLabel(Double.NaN))
        assertEquals("12.3 km", DashboardPresentation.odometerLabel(12.34))
        assertEquals("100 km", DashboardPresentation.odometerLabel(100.4))
    }

    @Test
    fun clusterExtras_joinWhenPresent() {
        assertNull(DashboardPresentation.clusterExtras(null, null))
        assertEquals("Oil 92°C", DashboardPresentation.clusterExtras(92.4, null))
        assertEquals("Doors closed", DashboardPresentation.clusterExtras(null, "Doors closed"))
        assertEquals("Oil 90°C · Doors closed", DashboardPresentation.clusterExtras(90.0, "Doors closed"))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest --tests com.domivega.gps_car.ui.DashboardPresentationTest`

Expected: FAIL — `DashboardPresentation` unresolved.

**Step 3: Write minimal implementation**

```kotlin
package com.domivega.gps_car.ui

object DashboardPresentation {
    fun trackingLabel(isTracking: Boolean): String =
        if (isTracking) "ACTIVE TRACKING" else "IDLE"

    fun odometerLabel(odometerKm: Double?): String {
        if (odometerKm == null || !odometerKm.isFinite()) return "— km"
        return if (odometerKm >= 100.0) {
            "${odometerKm.toLong()} km"
        } else {
            val tenths = ((odometerKm * 10.0) + 0.5).toInt()
            "${tenths / 10}.${tenths % 10} km"
        }
    }

    fun clusterExtras(oilTempC: Double?, doorsSummary: String?): String? {
        val parts = buildList {
            if (oilTempC != null && oilTempC.isFinite()) add("Oil ${oilTempC.toInt()}°C")
            if (doorsSummary != null) add(doorsSummary)
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
```

**Step 4: Run test to verify it passes**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest --tests com.domivega.gps_car.ui.DashboardPresentationTest`

Expected: PASS

**Step 5: Commit**

```bash
git add composeApp/src/androidUnitTest/kotlin/com/domivega/gps_car/ui/DashboardPresentationTest.kt \
        composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/DashboardPresentation.kt
git commit -m "feat(ui): add dashboard presentation labels" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 2: Soften AutomotiveTheme

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/theme/AutomotiveTheme.kt`

**Step 1: Update tokens (no unit test — visual only)**

Keep dark-first. Cooler surfaces, one cyan accent, drop unused neon amber as a loud secondary if it fights the tiles. Suggested scheme:

```kotlin
val AccentCyan = Color(0xFF5EE0F0)
val DarkBackground = Color(0xFF0B0F12)
val SurfaceDark = Color(0xFF151A1E)
val SurfaceContainer = Color(0xFF1C2328)
val OnSurfaceLight = Color(0xFFE6EEF2)
val ErrorRed = Color(0xFFFF8A80)

private val AutomotiveDarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF0E3A42),
    onPrimaryContainer = AccentCyan,
    secondary = Color(0xFF9BB0B8),
    onSecondary = Color(0xFF101417),
    secondaryContainer = Color(0xFF243036),
    onSecondaryContainer = Color(0xFFD5E3E8),
    background = DarkBackground,
    onBackground = OnSurfaceLight,
    surface = SurfaceDark,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainer,
    onSurfaceVariant = Color(0xFFB5C4CA),
    error = ErrorRed,
    errorContainer = Color(0xFF5C1F1F),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF3A474D),
)
```

Keep existing large typography and rounded shapes. `assembleDebug` is not required until screens compile against the new tokens.

**Step 2: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/theme/AutomotiveTheme.kt
git commit -m "feat(ui): soften dark cockpit theme tokens" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 3: Rewrite DashboardScreen

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/DashboardScreen.kt`
- Delete after unused: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/components/VWDial.kt`
- Delete after unused: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/components/FuelTankGauge.kt`
- Delete after unused: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/components/EngineLoadGauge.kt`

**Step 1: Replace analog cluster with health card + tiles**

Keep the same `DashboardScreen` signature and OBD confirm dialog. Structure:

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    HealthStatusCard(
        state = state,
        onObdEnabledChange = onObdEnabledChange,
        onRetryUpload = onRetryUpload,
        onRequestDisableConfirm = { confirmDisable = true },
    )
    MetricGrid(state)
    SecondaryMetricsRow(state)
}
```

`HealthStatusCard` (same file or `ui/components`):
- Title: `DashboardPresentation.trackingLabel(state.isTracking)` — `headlineMedium`, primary when tracking, muted when idle.
- Row of `AssistChip` / small `Surface` chips: `ECU` and `GPS` with connected/locked coloring.
- If `state.uploadWarning != null`, error-container row + Retry (reuse existing 1.5s debounce).
- Bottom row: `Enabled` / `Disabled` + `Switch` using `ObdEnableGate.disableRequiresConfirmation`.

`MetricGrid`: two `Row`s of two `MetricTile`s — Speed, RPM, Fuel, Load. `MetricTile` is a `Card` (`surfaceVariant`) with label, large int value (`toInt()` of the existing doubles), unit.

`SecondaryMetricsRow`: odometer label from `DashboardPresentation.odometerLabel`; extras from `clusterExtras` as supporting text.

Remove `VWDial`, `FuelTankGauge`, `EngineLoadGauge`, unused `MetricCard`, old `StatusHeader` / `OdometerBanner` / `ClusterExtrasBanner` if inlined.

**Step 2: Delete analog gauge files only after no remaining references**

Search: `VWDial|FuelTankGauge|EngineLoadGauge`

**Step 3: Compile check**

Run: `devenv shell -- ./gradlew :composeApp:compileDebugKotlinAndroid`

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/DashboardScreen.kt
git add -u composeApp/src/commonMain/kotlin/com/domivega/gps_car/components/
git commit -m "feat(ui): replace analog dashboard with digital cockpit" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 4: Group Settings into cards

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/SettingsScreen.kt`

**Step 1: Add SettingsSection wrapper**

```kotlin
@Composable
private fun SettingsSection(
    title: String,
    supporting: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}
```

**Step 2: Wrap existing fields; split Vehicle vs Fuel**

Keep every callback and field. Replace headline + `HorizontalDivider` blocks with:

1. `SettingsSection("Backend", "QR provisioning, token, and ingest URLs")` — QR, car, token, URLs, test.
2. `SettingsSection("Adapter", connectionStatus)` — transport, device, protocol, scan/connect. **Move** vehicle profile / VW DID / WWH-OBD out of this card.
3. `SettingsSection("Vehicle", "Profile and experimental protocol")` — profile, DID, WWH-OBD.
4. `SettingsSection("Fuel", existing MAF helper copy)` — fuel fields only.
5. `SettingsSection("Upload fields", "Always sent: GPS lat/lon, velocity, RPM…")` — existing switches.

Do not change `SettingsTextField`, BLE dialog, or ViewModel wiring.

**Step 3: Compile check**

Run: `devenv shell -- ./gradlew :composeApp:compileDebugKotlinAndroid`

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/screens/SettingsScreen.kt
git commit -m "feat(ui): group settings into cockpit cards" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 5: Restyle drawer chrome

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/navigation/AppNavigation.kt`

**Step 1: Match drawer + top bar to new surfaces**

- `ModalDrawerSheet` container = `surface`.
- Header: “GPS Car Tracker” + short subtitle “Digital cockpit”.
- Keep destinations and `onOpenSettings()` behavior.
- Top bar: `surface` / no elevation theater; title can stay `currentScreen`.
- Do not redesign Debug or About screens.

**Step 2: Compile + unit tests**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest`

Expected: BUILD SUCCESSFUL, all tests PASS including `DashboardPresentationTest`.

**Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/domivega/gps_car/ui/navigation/AppNavigation.kt
git commit -m "feat(ui): restyle navigation drawer to match cockpit" \
  --trailer "Co-authored-by: Junie <junie@jetbrains.com>"
```

---

### Task 6: Verification

**Step 1: Full unit suite**

Run: `devenv shell -- ./gradlew :composeApp:testDebugUnitTest`

Expected: BUILD SUCCESSFUL, 0 failed tests.

**Step 2: If Android/resources/theme compile was touched, also**

Run: `devenv shell -- ./gradlew :composeApp:assembleDebug`

Expected: BUILD SUCCESSFUL.

**Step 3: Manual glance checklist (no automated UI test)**

- Open Dashboard: health card first, then 2×2 tiles, odometer row.
- Toggle OBD while idle: no dialog. While tracking: confirm dialog unchanged.
- Upload warning Retry still works.
- Settings: five cards, all previous fields present, QR/test/BLE dialog still work.

Do not weaken tests. Do not commit secrets.

---
