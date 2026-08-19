# OBD Enable/Disable Switch — Design

Replace the dashboard Start/Stop control with a persisted Enable/Disable switch that can fully release the OBD adapter (so another app can use it) without tearing down the WAITING foreground service.

## Goal

When OBD is **disabled**, the app must never attempt to connect to the adapter — not on boot, not from idle reconnect, not from Settings Connect — until the user enables it again. If a trip is running, Disable requires confirmation; confirm ends the trip, disconnects, and stays in WAITING.

## Non-goals

- Removing ECU auto-start of trips while OBD is enabled (vehicle-on proof still starts tracking).
- Stopping the WAITING notification when a dongle is still configured.
- Full Shutdown of the foreground service from this switch.
- A second Enable/Disable control in Settings (dashboard switch is the surface).

## User-facing behavior

- Dashboard Start/Stop FAB is removed.
- A single switch replaces it: **on = Enable (OBD allowed)**, **off = Disable**.
- Default is **enabled** (current behavior for existing installs).
- The choice is persisted in `app_settings` and survives process death and reboot.
- Disable while **idle**: no dialog; persist off; disconnect immediately if linked.
- Disable while **tracking**: `AlertDialog` (“Stop tracking and release the OBD adapter?”). Cancel leaves tracking running and the switch on. Confirm persists off, sends `ACTION_STOP` (end trip, stay WAITING), then disconnects.
- Enable: persist on. Existing presence / WAITING / idle-reconnect loop may connect again. There is no manual Start button; trips still start only on vehicle-on proof.
- Settings Connect is disabled or no-ops while off (same connect gate). Scan and device selection still work; selecting a device does not connect until Enable.

## Architecture

Persisted flag `obdEnabled` (default `true`) in `AppSettings`, exposed through `SettingsRepository` / `SettingsViewModel` so the dashboard can observe it.

Hard gate at `ObdBleManager.connect()`: if `obdEnabled` is false, log and return without touching BLE/SPP. All callers inherit the gate:

- `ObdPresenceController` idle poll
- idle reconnect after parked sleep
- Settings Connect
- any future connect path

`WaitingFgsGate` stays “dongle address configured” only. Disabled + configured dongle still ensures WAITING FGS and the paused notification, but connect never starts.

Pure policy helpers (unit-tested, no Android):

- `ObdEnableGate.mayConnect(obdEnabled)` — false means connect must no-op
- `ObdEnableGate.disableRequiresConfirmation(isTracking)` — true only while a trip is active

## Data flow

```
Dashboard switch
  → if turning off && tracking → confirm
  → SettingsRepository.obdEnabled = value
  → if off: ACTION_STOP if tracking, then ObdBleManager.disconnect()
  → if on: allow next connect() from presence/WAITING

ObdBleManager.connect()
  → if !obdEnabled → status/log “OBD disabled”, return
  → else existing connect/init/poll
```

## Error handling

- Confirmation cancel is a no-op (no persist, no stop, no disconnect).
- Disconnect after Disable is best-effort; the connect gate is what guarantees the adapter stays free even if disconnect races.
- Enable does not force an immediate connect from the UI; the existing presence loop is enough.

## Testing

Unit tests in `composeApp/src/androidUnitTest`:

- `ObdEnableGate`: connect allowed vs blocked; confirmation required only when tracking
- `WaitingFgsGate` still true with a dongle address even when OBD is disabled (gate does not take `obdEnabled`)

UI remains visual; no Compose UI tests required.

## Files (expected)

- `composeApp/src/androidMain/.../settings/AppSettings.kt`
- `composeApp/src/androidMain/.../data/AndroidSettingsRepository.kt`
- `composeApp/src/commonMain/.../data/SettingsRepository.kt`
- `composeApp/src/commonMain/.../ui/SettingsViewModel.kt` + `SettingsUiState`
- `composeApp/src/commonMain/.../ui/screens/DashboardScreen.kt`
- `composeApp/src/commonMain/.../ui/state/DashboardState.kt`
- `composeApp/src/commonMain/.../components/TrackingControlButtons.kt` (replace or remove)
- `composeApp/src/androidMain/.../obd/ObdBleManager.kt`
- `composeApp/src/androidMain/.../App.kt`
- `composeApp/src/commonMain/.../ObdEnableGate.kt` (new)
