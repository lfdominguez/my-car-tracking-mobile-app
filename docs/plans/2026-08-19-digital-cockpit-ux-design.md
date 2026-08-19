# Digital cockpit UX — Design

Replace the analog dashboard cluster and long Settings form with a glanceable digital cockpit: system health first, then digital metric tiles, plus grouped Settings cards. This is a momentary information surface, not an always-on HUD.

## Goal

When the user opens the app for a few seconds they should immediately see whether tracking, ECU, GPS, and upload are healthy, then read speed / RPM / fuel / load as large numbers. Settings should feel like the same product (sectioned cards, same fields).

## Non-goals

- Always-on HUD / mount mode, keep-screen-on, or landscape instrument cluster.
- New metrics, maps, or live charts.
- Bottom navigation or nested Settings destinations.
- Redesigning Debug Console or About.
- Changing OBD, GPS, queue, fuel, or upload behavior.
- Secrets or new default URLs/tokens.

## User-facing behavior

### Dashboard

1. **Health card (first glance)**
   - Large trip state: `ACTIVE TRACKING` or `IDLE` (from existing `isTracking`).
   - Chips: ECU connected / not connected, GPS locked / not locked.
   - Upload warning + Retry live on this card when `uploadWarning` is non-null (same debounce as today).
   - OBD Enable/Disable switch on the card. Same `ObdEnableGate` confirm dialog while tracking.
2. **Digital tiles (2×2)**
   - Speed (`km/h`), RPM (`rpm`), Fuel (`%`), Engine load (`%`).
   - Large number + unit + label. No analog dials or needles.
   - Speed / RPM / fuel / load keep today’s numeric mapping from `DashboardState` (including `0.0` when the backend state is zero).
3. **Secondary row**
   - Odometer always (`— km` when unknown; same rounding as today’s banner).
   - Oil temp and doors only when cluster values are present.

### Settings

Same fields, callbacks, and validation. Visual grouping only, in this order:

1. **Backend** — QR scan, QR error, provisioned car, token, four URLs, Test connection + result.
2. **Adapter** — transport, device, status, protocol, scan / connect / disconnect, Classic SPP hint.
3. **Vehicle** — OBD profile, VW odometer DID, WWH-OBD switch (moved out of the current mixed OBD / fuel block).
4. **Fuel** — type, AFR, density, displacement, VE, tank capacity + existing helper copy.
5. **Upload fields** — existing optional metric toggles; lat/lon/velocity/RPM remain always-on in logic.

No search, no nested routes, no collapse-by-default.

### Chrome

- Keep the hamburger drawer destinations: Dashboard, Settings, Debug, About.
- Restyle drawer + top bar to match the new surfaces.
- Stay dark-first. Soften neon: cooler surfaces, one cyan accent, no magenta theater.

## Architecture

UI-only Compose rewrite. `DashboardState`, `SettingsUiState`, ViewModels, and Android OBD/GPS/queue code stay unchanged.

Extract tiny presentation helpers (unit-tested) so odometer / tracking labels do not live only inside composables:

- `DashboardPresentation.trackingLabel(isTracking)`
- `DashboardPresentation.odometerLabel(odometerKm)`
- `DashboardPresentation.clusterExtras(oilTempC, doorsSummary)`

New/rewritten composables:

- `HealthStatusCard`
- `MetricTile`
- `SettingsSection` (card wrapper)
- Theme tokens in `AutomotiveTheme`

Analog `VWDial`, `FuelTankGauge`, and `EngineLoadGauge` are unused after the rewrite and should be deleted.

## Data flow

Unchanged:

```
ViewModels / service → DashboardState / SettingsUiState → screens
Dashboard OBD switch → ObdEnableGate → onObdEnabledChange
Upload Retry → onRetryUpload (1.5s lock)
Settings fields → existing SettingsViewModel setters
```

## Error handling

- Upload warning stays on the health card with Retry.
- Settings QR error and Test connection result stay in the Backend card.
- OBD disable cancel remains a no-op.
- No new error types.

## Testing

- Unit tests for `DashboardPresentation` (tracking label, odometer rounding, extras join / empty).
- Existing unit tests must stay green (`:composeApp:testDebugUnitTest`).
- No Compose screenshot / UI tests.

## Files (expected)

- `composeApp/src/commonMain/.../ui/screens/DashboardScreen.kt`
- `composeApp/src/commonMain/.../ui/screens/SettingsScreen.kt`
- `composeApp/src/commonMain/.../ui/navigation/AppNavigation.kt`
- `composeApp/src/commonMain/.../ui/theme/AutomotiveTheme.kt`
- `composeApp/src/commonMain/.../ui/DashboardPresentation.kt` (new)
- `composeApp/src/androidUnitTest/.../ui/DashboardPresentationTest.kt` (new)
- Delete analog gauges under `composeApp/src/commonMain/.../components/` once unused
