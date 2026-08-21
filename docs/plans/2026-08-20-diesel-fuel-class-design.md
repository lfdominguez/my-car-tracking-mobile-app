# Diesel / Gasoline fuel class — Design

Settings Gasoline/Diesel switch, diesel B7 grade, and load-based diesel L/h. Golf mk4 TDI tank/odometer stay unavailable on Generic OBD.

## Goal

Users pick **Gasoline** or **Diesel**. Gasoline keeps today’s ethanol grades (E0 / E10 / E27 / E100 / Custom). Diesel shows **B7** (AFR 14.5, density 835 g/L) and **Custom**. Estimated fuel rate (`ff125a`) uses gasoline λ-math for gasoline and a PID 04 load-based lean AFR for diesel.

## Non-goals

- VW MQB cluster UDS odometer / fuel level on a Golf mk4 K-line car (CAN cluster hop does not apply).
- Inventing dash km or tank % when SAE PID `A6` / `2F` are unsupported.
- B0 / B20 / B100 grades (v1 is B7 + Custom only).
- Auto-filling engine displacement for ASZ (user still sets 1.9 L).
- Changing GPS, queue, or sample upload field flags.

## User-facing behavior

- Settings **Fuel** section: Gasoline / Diesel control, then a grade dropdown filtered by class.
- Existing installs: class **Gasoline**, grade **E10** (unchanged defaults).
- Switching class applies that class’s default grade (E10 or B7) and writes preset AFR/density.
- Custom keeps typed AFR/density and stays on the current class (diesel Custom still uses diesel math).
- Supporting text: gasoline L/h is MAF × λ; diesel L/h uses MAF and load-based AFR. VE only if MAF missing. Set displacement for the engine (1.9 L for ASZ).

## Architecture

Two persisted fields:

- `fuelClass`: `GASOLINE` | `DIESEL` (default `GASOLINE`)
- `fuelType`: existing grade enum name (`E10`, `B7`, `CUSTOM`, …)

`FuelTypePreset` gains `B7` and a `fuelClass` on each non-custom grade. UI lists `gradesFor(class)`.

`FuelCalcConfig.isDiesel` plus `FuelCalcSensors.calculatedLoadPct` (PID `04`). `ObdBleManager.recomputeFuelRate` passes `settings.fuelClass == DIESEL` and live `04`.

## Diesel L/h

1. ECU PID `5E` still wins when valid.
2. Else air mass unchanged (trusted MAF, else MAP, else peak-air idle bound).
3. Gasoline: unchanged (`λ` in 0.5…1.5 else 1.0, STFT/LTFT).
4. Diesel: ignore gasoline λ and fuel trims. AFR from PID 04:

   - load `null` / invalid → cruise AFR **17.5**
   - load ≥ 25% → **17.5**
   - load 0…25% → linear blend idle **38.0** → **17.5**

   `L/h = air_g/s × 3600 / (AFR × density)`

   Idle-only lean AFR avoids understating highway fuel when PID 04 is modest.

## Golf mk4 TDI (ASZ) — tank and odometer

Friend’s working PIDs: `0c`, `04`, `10`, `ff125a`, `0b`, `05`.

- **MQB fix does not apply.** Cluster UDS on CAN `714`/`77E` is MQB-only. This car is Generic OBD on K-line.
- **Odometer:** SAE `A6` is 2013+; PID `31` is not dash km (already excluded). No odometer without a future mk4 cluster TAP.
- **Fuel tank:** SAE `2F`. Not in the working list; do not poll-invent a level.

## Data flow

```
Settings Gasoline/Diesel + grade
  → AppSettings fuelClass + fuelType + AFR/density
  → ObdBleManager.recomputeFuelRate
  → FuelConsumptionCalculator (diesel AFR from PID 04)
  → pidValues ff125a
```

## Error handling

- Unknown `fuelClass` → Gasoline.
- Diesel with gasoline grade in storage → treat as B7 when class is Diesel (and the reverse as E10) on next class/grade UI bind, without rewriting until the user changes it — or normalize when class is set. Prefer: selecting class always writes default grade (already the switch behavior).
- Invalid load → diesel cruise AFR 17.5.
- Missing MAF/MAP → no `ff125a` (same as today).

## Testing

Unit tests (`composeApp/src/androidUnitTest`):

- `FuelTypePreset`: B7 constants; `gradesFor` gasoline vs diesel
- `FuelConsumptionCalculator`: diesel idle load leaner L/h than 25%+ load; unknown load uses 17.5; gasoline path unchanged; `5E` still wins
- Settings: class switch writes E10/B7 constants; Custom keeps AFR and class
- FakeSettingsRepository / QR parse: new `fuelClass` field

No Compose UI tests. No live car test in CI.

## Files (expected)

- `composeApp/src/commonMain/.../fuel/FuelTypePreset.kt` (+ `FuelClass` or same file)
- `composeApp/src/commonMain/.../fuel/FuelConsumptionCalculator.kt`
- `composeApp/src/androidMain/.../settings/AppSettings.kt`
- `composeApp/src/commonMain/.../data/SettingsRepository.kt`
- `composeApp/src/androidMain/.../data/AndroidSettingsRepository.kt`
- `composeApp/src/commonMain/.../ui/SettingsViewModel.kt` + `SettingsUiState` + `SettingsScreen.kt`
- `composeApp/src/androidMain/.../obd/ObdBleManager.kt` (`recomputeFuelRate`)
- Tests under `androidUnitTest/.../fuel/` and Settings QR fake
- README / Settings copy if it still says ethanol-only
