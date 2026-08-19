# OBD Device section — Design

Move all dongle and protocol configuration out of Settings into a dedicated drawer screen. Same fields and BLE/OBD behavior; only navigation and grouping change.

## Goal

When the user needs to pair, connect, or set protocol/profile, they open **OBD Device** — not a long Settings page mixed with Backend and Fuel.

## Non-goals

- Changing BLE scan/connect, protocol, profile, DID, or WWH-OBD behavior.
- Moving the dashboard OBD enable switch (it stays on the health card).
- Redesigning Debug Console or About.
- Bottom navigation, nested Settings destinations, or new metrics.
- Secrets or new default URLs/tokens.

## User-facing behavior

### Drawer

Destinations, in order:

1. Dashboard
2. **OBD Device** (new)
3. Settings
4. Debug Console
5. About

Top bar title is `OBD Device` when that screen is open.

### OBD Device screen

Same two cards as today, moved intact:

1. **Adapter** — Bluetooth transport, Classic SPP hint, device label, connection status, OBD protocol, Scan / Connect / Disconnect, BLE device dialog (including empty “Scanning…” and Rescan).
2. **Vehicle** — Vehicle OBD profile, VW odometer DID (VW MQB only), WWH-OBD switch.

Connect remains disabled when no address is selected or OBD is disabled (`state.obdEnabled`), same as today.

### Settings

Keeps three cards only:

1. Backend
2. Fuel
3. Upload fields

Adapter and Vehicle cards are removed from this screen.

### Dashboard

Unchanged. Health card still has Enable/Disable and the existing confirm dialog while tracking.

## Architecture

UI-only Compose extraction.

- New `ObdDeviceScreen` owns Adapter + Vehicle + BLE dialog.
- Shared `SettingsSection` (and `SettingsTextField` if Vehicle still needs it) so both screens keep the same card chrome.
- Tiny `ObdDevicePresentation.deviceLabel(...)` so the “None selected / name (address)” string is not only inside a composable.
- `AppNavigation` routes existing BLE/protocol/profile callbacks to `ObdDeviceScreen` instead of `SettingsScreen`.
- `SettingsScreen` drops unused OBD/BLE parameters once nothing on that screen uses them.
- `SettingsUiState`, `SettingsViewModel`, Android BLE/OBD code stay unchanged.

## Data flow

Unchanged wiring, new destination:

```
SettingsViewModel / BLE callbacks → ObdDeviceScreen
SettingsViewModel (API, fuel, upload flags) → SettingsScreen
Dashboard OBD switch → ObdEnableGate → onObdEnabledChange
```

## Error handling

- BLE empty scan and connection status stay on OBD Device.
- QR error and Test connection stay on Settings Backend.
- No new error types.

## Testing

- Unit tests for `ObdDevicePresentation.deviceLabel` (live label, name+address, address only, name only, none).
- Existing unit tests stay green (`:composeApp:testDebugUnitTest`).
- Compile check after screen/navigation edits.
- No Compose screenshot / UI tests.

## Files (expected)

- `composeApp/src/commonMain/.../ui/screens/ObdDeviceScreen.kt` (new)
- `composeApp/src/commonMain/.../ui/screens/SettingsScreen.kt` (remove Adapter/Vehicle)
- `composeApp/src/commonMain/.../ui/screens/SettingsSection.kt` (extract shared card)
- `composeApp/src/commonMain/.../ui/ObdDevicePresentation.kt` (new)
- `composeApp/src/androidUnitTest/.../ui/ObdDevicePresentationTest.kt` (new)
- `composeApp/src/commonMain/.../ui/navigation/AppNavigation.kt`
