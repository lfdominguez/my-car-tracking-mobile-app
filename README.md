# GPS Car Tracking

Android (Kotlin Multiplatform / Compose) app that records **GPS tracks** enriched with **live OBD-II metrics** from an ELM327 adapter (BLE GATT or Classic Bluetooth SPP), queues samples offline, and uploads them in batches to your own backend.

```text
Phone GPS (~1 Hz)  ──►  sample + latest OBD snapshot  ──►  local queue  ──►  batch HTTP API
OBD (BLE or SPP)  ──►  pidValues (hot/slow PIDs)  ──────┘
```

### Features

- **Foreground tracking service** — continuous location with notification controls
- **Native OBD (BLE + Classic SPP)** — ELM327 AT init over BLE GATT (default) or Classic RFCOMM; selectable protocol (default ISO 15765-4 CAN 11-bit 500 kbaud); auto-reconnect to last adapter
- **ECU-driven session** — tracking starts/stops with ECU connectivity
- **Prioritized OBD polling** — hot PIDs (RPM, speed, load, MAF, …) every round; slow PIDs every 5th round at max BLE rate
- **Durable send queue** — Room-backed pending samples; ~60s batch flush with retry/backoff
- **Vehicle & fuel settings** — fuel preset (E0/E10/E27/E100/Custom), AFR, density, displacement, VE → estimated L/h from MAF + λ (MAP fallback)
- **In-app OBD debug log** — init steps, BLE lifecycle, errors (Debug tab)
- **Dashboard** — gauges and live metrics

### Requirements

| Item | Notes |
|------|--------|
| Android device | BLE + location; Android 8+ recommended |
| JDK | **21** (see `devenv.nix` / `JAVA_HOME`) |
| Android SDK | Via Android Studio or [devenv](https://devenv.sh) |
| OBD adapter | ELM327-compatible **BLE** (GATT UART) and/or **Classic Bluetooth SPP** (RFCOMM). Dual-mode sticks: pick transport in Settings. |
| Backend | Rust **car-tracking-platform** (or any wire-compatible `/api/track/*` API). Provision via web QR. |

### Quick start

```bash
# Optional: reproducible shell (Nix + devenv)
devenv shell

cp local.properties.example local.properties   # or create manually
# local.properties:
#   sdk.dir=/path/to/Android/Sdk

./gradlew :composeApp:assembleDebug
# APK: composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Install the debug APK, open **Settings**, and configure the backend:

1. **Preferred:** on the web platform, create a car → create a **device** → scan the **QR** (token, track URLs, fuel/engine, optional car name)
2. Or set manually: **API token** (raw device token for `Authorization: Basic <token>`), plus absolute `/api/track/start|stop|sample|samples` URLs
3. Tap **Test connection** — hits public `/health`, then a short start/stop smoke with your device token
4. **Bluetooth transport** — **BLE (GATT)** default, or **Classic Bluetooth (SPP)** for RFCOMM “OBDII” sticks. Classic: pair in system Bluetooth first (PIN often `1234`/`0000`), fully quit Torque/other OBD apps, then Scan.
5. **Adapter** — scan, select, save (auto-reconnect next time)
6. **OBD protocol** — leave default CAN 11/500 unless your car needs another
7. **Vehicle & fuel** — usually filled from QR; adjust if needed

Grant **location (always)** and **Bluetooth** permissions when prompted.

### Rust platform notes

- Start body sends `timestamp_start` as an **ISO-8601 / RFC3339** instant (not a raw millis number).
- If start returns an empty body (current Rust API), the app uses the client epoch-millis string as `tracking_id` so samples still attach.
- Platform QR JSON is camelCase (`apiToken`, `startUrl`, `fuelType`, `carId`, `carName`, …); unknown keys are ignored.

### Architecture (high level)

| Layer | Role |
|-------|------|
| `ForegroundTrackingService` | GPS fixes, build samples, enqueue, session start/stop |
| `ObdBleManager` | ELM session (BLE GATT or Classic SPP transport), PID poll, `pidValues` / `ecuConnected` |
| `SampleQueueRepository` + `SampleQueueUploader` | Persist unsent rows; batch POST |
| `ApiClient` | OkHttp start/stop/sample/samples |
| `AppSettings` | SharedPreferences (URLs, token, BLE, protocol, fuel) |
| Compose UI | Dashboard, Settings, Debug console, About |

Design notes for recent work live under [`docs/plans/`](docs/plans/).

### Backend contract (summary)

- `POST .../start` with `{ "timestamp_start": "<RFC3339>" }` — tracking id from JSON `id` if present, else client millis string  
- `POST .../stop` with `{ "id": "<tracking_id>" }`  
- `POST .../sample` — single sample (legacy)  
- `POST .../samples` — `{ "samples": [ ... ] }` batch (preferred by the app)  
- Auth: `Authorization: Basic <device_token>` (plaintext device token from the web platform)  
- `GET /health` — public probe used by **Test connection**

Metric fields from OBD may be missing; the backend should treat them as optional so GPS-only or partial OBD points are not dropped.

### Configuration & secrets

**Never commit real tokens, server hostnames you want private, or `local.properties`.**

| File / setting | Public default |
|----------------|----------------|
| API token | empty — set in app Settings |
| API URLs | `https://YOUR_SERVER.example/api/track/...` |
| `local.properties` | gitignored (`sdk.dir`) |
| BLE MAC | stored on device only |

If a credential was ever committed historically, **rotate it on the server** before going public and consider rewriting git history (`git filter-repo`) so the old blob is not reachable.

### Development

```bash
./gradlew :composeApp:testDebugUnitTest
./gradlew :composeApp:assembleDebug
```

With devenv:

```bash
devenv shell -- ./gradlew :composeApp:testDebugUnitTest
```

See **[AGENTS.md](AGENTS.md)** for conventions when contributing with AI coding agents.

### Project layout

```text
composeApp/
  src/androidMain/   # Android service, OBD BLE/SPP, Room queue, API
  src/commonMain/    # Compose UI, fuel math, shared models
  src/androidUnitTest/
docs/plans/          # Design & implementation notes
devenv.nix           # Dev shell (JDK 21 + Android SDK)
```

### License

MIT — see [LICENSE](LICENSE).

### Disclaimer

OBD fuel rate is **estimated** unless you extend the stack to prefer ECU PIDs such as `01 5E`. Use at your own risk; do not interact with the phone while driving.
