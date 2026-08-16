# AGENTS.md

Guidance for AI coding agents and humans working in this repository.

## Project

- **Name:** GPS Car Tracking  
- **Stack:** Kotlin Multiplatform (Android app target), Jetpack Compose, Coroutines/Flow, Room, OkHttp, Android BLE  
- **Purpose:** Collect GPS + OBD-II metrics, queue locally, batch-upload to a user-owned telemetry backend  

## Hard rules

1. **No secrets in git** — API tokens, Basic auth strings, private hostnames, BLE addresses, or real credentials must not be committed. Defaults stay empty or `YOUR_SERVER.example`.
2. **Do not commit `local.properties`** — SDK paths stay local (already gitignored).
3. **Do not weaken tests** to make a change pass; fix the code or extend tests properly.
4. **Prefer minimal diffs** — match existing style; avoid drive-by refactors unrelated to the task.
5. **Junie commits** — when the user asks to commit, append  
   `--trailer "Co-authored-by: Junie <junie@jetbrains.com>"`.
6. **Feature branches, not direct `main`** — all work goes on `feature/<short-name>` (or `fix/…`), then PR → CI green → merge to protected `main`. Version tags only via `scripts/create-release.sh` on clean `main` (see **Branching & releases**).

## Layout map

| Path | Own |
|------|-----|
| `composeApp/src/androidMain/.../obd/` | BLE ELM327, parser, debug log, poll schedule usage |
| `composeApp/src/androidMain/.../data/queue/` | Room pending samples + uploader |
| `composeApp/src/androidMain/.../ForegroundTrackingService.kt` | GPS + sample enqueue + session |
| `composeApp/src/androidMain/.../network/ApiClient.kt` | HTTP client |
| `composeApp/src/androidMain/.../settings/AppSettings.kt` | Persisted settings defaults |
| `composeApp/src/commonMain/.../fuel/` | Fuel rate calculator + presets (unit-tested) |
| `composeApp/src/commonMain/.../ui/` | Compose screens / ViewModels |
| `composeApp/src/androidUnitTest/` | JUnit tests |
| `docs/plans/` | Approved designs & implementation plans |
| `docs/obtainium/` | Obtainium stable + continuous GitHub configs |

## Dev environment

- Prefer **JDK 21**. `devenv.nix` pins `pkgs.jdk21` and sets `JAVA_HOME` / Gradle JDK opts.
- Useful commands:

```bash
devenv shell -- ./gradlew :composeApp:testDebugUnitTest
devenv shell -- ./gradlew :composeApp:assembleDebug
devenv shell -- ./gradlew :composeApp:compileDebugKotlinAndroid
```

- Without devenv: ensure `JAVA_HOME` points at JDK 21 and Android SDK is installed.

## Product behavior to preserve

- **OBD loop** is independent of GPS: continuous hot/slow PID rounds (`ObdPollSchedule`), publish `pidValues` per PID; no 1 s full-cycle throttle.
- **GPS** (~1 Hz) only **snapshots** latest `pidValues`; never block samples on BLE.
- **Queue**: enqueue on accept; flusher batch-posts `/samples`; mark sent only after success.
- **ECU auto tracking**: start only after a vehicle-on proof — RPM>0, speed>0, or (before this session has seen RPM>0) module voltage `42`. After RPM>0, voltage / RPM=0 do not keep the trip. Sustained lack of proof → offline → 90s grace → end trip but stay in WAITING FGS until user Shutdown. Hung adapter ages out via the 5s reconcile. WAITING FGS when a dongle address is configured — on boot/process start **and** when the user first selects a dongle (not only after Play).
- **Sample upload fields**: Settings toggles optional metrics; always send lat/lon, velocity, RPM (plus tracking id / accuracy). Filter at enqueue via `SampleFieldFilter`.
- **Fuel L/h** (`ff125a`): `FuelConsumptionCalculator` + settings (not a fixed ×0.339 gasoline hack). Rejects peak-air idle MAF (`peak×VE×0.14` bound; MAP preferred when present).
- **OBD debug log**: errors + init/lifecycle; not full successful PID TX/RX spam.

## Settings surface

Configurable in-app (and via `AppSettings` / `SettingsRepository`):

- API URLs + token  
- BLE device + OBD protocol  
- Fuel type preset / AFR / density / displacement / VE  
- Optional sample fields to upload (lat/lon, velocity, RPM always on)  

Changing defaults in code: keep them **non-secret** and documented in README.

## Testing expectations

| Change type | Expectation |
|-------------|-------------|
| Pure logic (fuel, schedule, parser) | Unit tests in `androidUnitTest` |
| Queue / networking | Tests where practical; don’t hit real servers |
| UI-only | Manual or existing patterns; no secret data in screenshots/fixtures |
| Before “done” | `:composeApp:testDebugUnitTest` (and `assembleDebug` if Android/resources touched) |

## Docs / plans

- Multi-step features: design + plan under `docs/plans/YYYY-MM-DD-topic-*.md` when the user uses the planning workflow.
- Prefer **repo-relative** paths in docs (`composeApp/...`, `../car_telemetry_backend`), never `/home/<user>/...`.
- **F-Droid:** Fastlane text under `fastlane/metadata/android/`; recipe draft + submission notes under `docs/fdroid/`. Keep the tree free of GMS/Firebase and build-time secrets.
- **Obtainium:** Ready-made GitHub configs under `docs/obtainium/` (stable = versioned signed APK; continuous = `latest` only). No app-code change.

## Security checklist (before public push)

- [ ] No `DEFAULT_API_TOKEN` or real Basic blobs in tree  
- [ ] No private production hostnames as defaults  
- [ ] No personal names/emails/phones in UI or docs unless intentional  
- [ ] `git log -S'password-or-token-fragment'` clean **or** history rewritten + server credential rotated  
- [ ] `.gitignore` still covers `local.properties`, `.idea`, `build/`, `.worktrees/`  

## Branching & releases

**Every agent must follow this path.** Do not commit feature work directly on `main`.

```text
feature/<short-name>  →  PR (+ CI green)  →  main  →  (optional) scripts/create-release.sh → tag vX.Y
```

### Feature work (required)

1. Start from up-to-date `main`:
   ```bash
   git checkout main
   git pull origin main
   git checkout -b feature/<short-name>
   ```
2. Implement on that branch only. Keep diffs focused on the task.
3. Run tests before offering the branch as done:
   ```bash
   devenv shell -- ./gradlew :composeApp:testDebugUnitTest
   ```
4. Push the branch and open a **PR into `main`** (do not push feature commits straight to `main`).
5. Wait for CI (`.github/workflows/ci.yml`, required check name **`ci`**) to pass, then merge (prefer linear history / squash).
6. After merge, delete the local feature branch if finished; pull `main` before the next task.

**Exceptions (only when the user explicitly asks):**

- Docs-only or agent-guidance tweaks the user wants committed immediately — still prefer a `feature/*` PR unless they say otherwise.
- **Release bumps** via `scripts/create-release.sh` run on clean `main` (maintainer path below).

### Naming

| Branch | Use |
|--------|-----|
| `main` | Production only; protected |
| `feature/<short-name>` | All product/code/docs changes |
| `fix/...` | Optional alias for bugfixes (same PR flow) |

Examples: `feature/obd-idle-reconnect`, `feature/fdroid-docs`, `fix/queue-backoff`.

### CI

| Workflow | Trigger | Role |
|----------|---------|------|
| `.github/workflows/ci.yml` | PRs + pushes to `main` | Unit tests; one unsigned `assembleRelease` + artifact (main stamps rolling versions) |
| `.github/workflows/latest.yml` | After CI success on `main` | Sign CI APK only (zipalign + apksigner) → GitHub Release `latest` |
| `.github/workflows/release.yml` | Tags `v*` | Re-check build; attach CI unsigned APK to the GitHub Release |

### Version release (F-Droid / GitHub)

Only when shipping a new store/F-Droid version — **not** for every feature merge:

1. Ensure the work is **merged to `main`**, tree clean, `main == origin/main`.
2. Run (from repo root, on `main`):
   ```bash
   scripts/create-release.sh                 # patch bump, versionCode +1
   scripts/create-release.sh 1.3 --notes "…"
   scripts/create-release.sh 1.3 --dry-run
   ```
3. The script bumps `versionName` / `versionCode` in `composeApp/build.gradle.kts` (what F-Droid `UpdateCheckData` reads), writes Fastlane `changelogs/<versionCode>.txt`, commits, tags `vX.Y`, builds a **signed** release APK (local keystore), pushes `main` + tag, and uploads `com.domivega.gps_car_<versionCode>.apk` to the GitHub Release.

Do **not** hand-edit version fields or move tags unless fixing a broken release with explicit user approval.

### Agent do / don’t

| Do | Don’t |
|----|--------|
| Branch off latest `main` as `feature/…` | Commit features on `main` |
| Open a PR and rely on CI | Bypass branch protection without asking |
| Merge only after tests/CI are green | Weaken tests to go green |
| Use `create-release.sh` for version tags | Invent ad-hoc version commits on a feature branch |
| Ask before force-push / history rewrite | Force-push `main` or rewrite shared history |

Full detail: `docs/RELEASE.md`. F-Droid notes: `docs/fdroid/SUBMISSION.md`. Obtainium: `docs/obtainium/`.

## Out of scope unless asked

- Renaming applicationId / package (`com.domivega.gps_car`)  
- iOS target  
- Publishing to Play Store  
- Implementing backend changes (separate repo)  

## Communication

- Match the user’s language for replies.
- Prefer short status notes naming real components (`ObdBleManager`, queue uploader, Settings).
- Ask before destructive git history rewrites or force-pushes.

### License

MIT License — see `LICENSE` in the repository root.
