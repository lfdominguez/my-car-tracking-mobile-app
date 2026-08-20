# OBD Performance Mode — Design

Optional ELM327 “Performance” switch so users can try faster polling without replacing Normal mode.

## Goal

When **Performance** is on, Mode 01 PID queries skip the ELM response-timeout wait (expected-line suffix) and init uses aggressive adaptive timing (`ATAT2`). When off, behavior stays today’s Normal path (`ATAT1`, no line suffix). Default is **off**.

## Non-goals

- Manual `ATST` timeout override.
- Shrinking the hot/slow PID lists.
- Changing always-on payload trimming (`ATE0` / `ATS0` / `ATH0` / `ATL0` / `ATAL`).
- Applying a line suffix to WWH-OBD (`22F4xx`), VW cluster UDS (`22xxxx`), SAE `A6` multi-frame odometer, or Mode 01 support bitmaps (`0100` / `0120` / …).

## User-facing behavior

- New switch on the **OBD Device** screen (same section as WWH-OBD only): **Performance mode**.
- Default **off** for existing installs.
- Persisted in `app_settings`; survives process death.
- Copy should say: line suffix applies on the next Mode 01 PID query; `ATAT2` applies on the next ELM init / reconnect. Some clones may drop PIDs — turn the switch off to restore Normal.
- Toggling does not force a reconnect.

## Architecture

Persisted flag `obdPerformanceMode` (default `false`) in `AppSettings`, through `SettingsRepository` / `SettingsViewModel` / `SettingsUiState`.

Pure helper `ElmPerformanceMode` (commonMain, unit-tested):

- `adaptiveTimingCommand(performance)` → `ATAT2` or `ATAT1`
- `mode01PollCommand(pidHex, performance)` → `010C` vs `010C1`
- Single-line rule: Mode 01 PIDs get suffix `1` only when Performance is on **and** the PID is not a support bitmap (`00`/`20`/`40`/…) and not `A6`

`ObdBleManager`:

- Init `baseSteps` uses `ElmPerformanceMode.adaptiveTimingCommand(settings.obdPerformanceMode)` instead of a hardcoded `ATAT1`
- Poll loop Mode 01 commands (and Mode 01 health `010C`) go through `mode01PollCommand`; WWH and UDS stay unsuffixed

## Data flow

```
OBD Device switch
  → SettingsRepository.obdPerformanceMode
  → next Mode 01 poll uses 01xx1 when on
  → next runInitSequence sends ATAT2 when on
```

## Error handling

- Miss / `NO DATA` / timeout keep existing `PidPollPolicy` (drop RPM/speed immediately; other PIDs last-good 10s).
- If a clone ignores or mishandles the suffix, the user turns Performance off; no automatic fallback in v1.
- Invalid PID hex → no suffix (safe).

## Testing

Unit tests in `composeApp/src/androidUnitTest`:

- `ElmPerformanceMode`: off vs on for RPM/speed; no suffix for `a6` and `00`; `ATAT1` vs `ATAT2`
- `SettingsViewModel.updateObdPerformanceMode` persists like WWH-OBD

No Compose UI tests.

## Files (expected)

- `composeApp/src/commonMain/.../obd/ElmPerformanceMode.kt` (new)
- `composeApp/src/androidUnitTest/.../obd/ElmPerformanceModeTest.kt` (new)
- `composeApp/src/androidMain/.../settings/AppSettings.kt`
- `composeApp/src/androidMain/.../data/AndroidSettingsRepository.kt`
- `composeApp/src/commonMain/.../data/SettingsRepository.kt`
- `composeApp/src/commonMain/.../ui/SettingsViewModel.kt` + `SettingsUiState`
- `composeApp/src/commonMain/.../ui/screens/ObdDeviceScreen.kt`
- `composeApp/src/commonMain/.../ui/navigation/AppNavigation.kt`
- `composeApp/src/androidMain/.../obd/ObdBleManager.kt`
- `composeApp/src/androidUnitTest/.../ui/SettingsQrParseTest.kt` (`FakeSettingsRepository`)
