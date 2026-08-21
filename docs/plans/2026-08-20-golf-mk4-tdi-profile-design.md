# VW Golf mk4 TDI (ASZ) vehicle profile — Design

Third Vehicle OBD profile for a 2003 Golf IV 1.9 TDI PD 130 (ASZ). Isolated from Generic OBD and VW MQB. Applies K-line + diesel defaults; skips CAN functional header. Does not invent dash km or tank %.

## Goal

Users pick **VW Golf mk4 TDI (ASZ)** in the same Vehicle OBD profile dropdown as Generic and VW MQB (Nivus). Selecting it writes this car’s known settings and, at ELM init, skips CAN-only `ATSH7DF`. Other cars stay on Generic/MQB with today’s behavior.

## Non-goals

- MQB cluster UDS hop (`714`/`77E`) on this K-line car.
- Inventing SAE odometer (`A6`) or tank PID (`2F`).
- mk4 instrument-cluster TAP / VAG KWP extras.
- Changing BLE vs Classic SPP, VE, Performance mode, or API URLs.
- Reverting settings when leaving the profile (no snapshot).
- Shrinking the shared hot/slow PID lists.
- Auto protocol fallback if ISO 9141-2 fails (user can switch to KWP 5 baud / Automatic).

## User-facing behavior

- OBD Device **Vehicle OBD profile**: Generic OBD | VW MQB (Nivus) | **VW Golf mk4 TDI (ASZ)**.
- Default remains **Generic**. Existing installs unchanged.
- Selecting Golf mk4 writes:
  - Protocol `ISO_9141_2`
  - Fuel class `DIESEL`, grade `B7` (AFR 14.5, density 835)
  - Displacement **1.9 L**
  - Tank capacity **55 L** (session-start field only)
  - WWH-OBD **off**
- MQB odometer DID field stays visible only for VW MQB.
- Copy: K-line ISO 9141; diesel B7 and 1.9 L applied; no dash odometer or tank % on this ECU.

## Architecture

New enum value `VehicleObdProfile.VwGolfMk4Tdi`.

`fromName` unknown → Generic (unchanged). MQB gates stay `profile == VwMqb`.

`GolfMk4TdiDefaults` (commonMain) holds protocol name, displacement, tank, and apply-on-select logic used by `SettingsViewModel.updateVehicleObdProfile`.

`ElmInitPolicy.sendCanFunctionalHeader(profile)` is false only for `VwGolfMk4Tdi`. `ObdBleManager.runInitSequence` skips `ATAR` + `ATSH7DF` when false. Generic and MQB still send them.

## Data flow

```
OBD Device profile dropdown
  → updateVehicleObdProfile(VwGolfMk4Tdi)
  → persist profile + ISO 9141-2 + Diesel B7 + 1.9 L + 55 L + WWH off
  → next ELM init: ATSP3, skip ATSH7DF
  → Mode 01 0100 / poll as Generic (no cluster hop)
```

## Error handling

- Unknown profile string → Generic; no ASZ writes.
- Selecting Generic or MQB does not rewrite Golf fuel/protocol/tank.
- QR that sets `vehicleObdProfile` only stores the enum (same as today); the settings pack applies when the user selects the profile in UI (`updateVehicleObdProfile`), not as a side effect of QR field-by-field apply.
- Init still fails closed if `0100`/`010C` never answers (wrong stick, ignition, or protocol). User can try KWP 5 baud without leaving the profile.
- Invalid load / missing MAF: existing diesel L/h rules.

## Golf mk4 TDI — tank and odometer

Friend’s working PIDs: `0c`, `04`, `10`, `ff125a`, `0b`, `05`.

- No SAE `A6` / `2F`.
- No MQB cluster hop.
- Tank 55 L is capacity for `/track/start` only.

## Testing

Unit tests (`composeApp/src/androidUnitTest`):

- `VehicleObdProfile`: display name; `fromName` parses `VwGolfMk4Tdi`; unknown → Generic; default Generic.
- `SettingsViewModel`: selecting Golf writes protocol/fuel/displacement/tank/WWH; selecting Generic afterward does not revert those fields; selecting MQB does not write ASZ pack.
- `ElmInitPolicy`: skip CAN header only for Golf.
- `VwOdoFirstGate`: false for Golf and Generic; true only for MQB.

No Compose UI tests. No live car test in CI.

## Files (expected)

- `composeApp/src/commonMain/.../obd/VehicleObdProfile.kt`
- `composeApp/src/commonMain/.../obd/GolfMk4TdiDefaults.kt` (new)
- `composeApp/src/commonMain/.../obd/ElmInitPolicy.kt` (new)
- `composeApp/src/commonMain/.../ui/SettingsViewModel.kt`
- `composeApp/src/androidMain/.../obd/ObdBleManager.kt`
- `composeApp/src/commonMain/.../ui/screens/ObdDeviceScreen.kt` (copy; DID still MQB-only)
- Tests under `androidUnitTest/.../obd/` and Settings VM tests
- README if it still says only two profiles
