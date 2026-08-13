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
- **ECU auto tracking**: connect/responding → start; drop → stop (unless product decision changes).
- **Fuel L/h** (`ff125a`): `FuelConsumptionCalculator` + settings (not a fixed ×0.339 gasoline hack). Rejects peak-air idle MAF (`peak×VE×0.14` bound; MAP preferred when present).
- **OBD debug log**: errors + init/lifecycle; not full successful PID TX/RX spam.

## Settings surface

Configurable in-app (and via `AppSettings` / `SettingsRepository`):

- API URLs + token  
- BLE device + OBD protocol  
- Fuel type preset / AFR / density / displacement / VE  

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

## Security checklist (before public push)

- [ ] No `DEFAULT_API_TOKEN` or real Basic blobs in tree  
- [ ] No private production hostnames as defaults  
- [ ] No personal names/emails/phones in UI or docs unless intentional  
- [ ] `git log -S'password-or-token-fragment'` clean **or** history rewritten + server credential rotated  
- [ ] `.gitignore` still covers `local.properties`, `.idea`, `build/`, `.worktrees/`  

## Branching & releases

- **`main` is production** — protected; land work via `feature/*` branches and PRs.
- CI: `.github/workflows/ci.yml` (PR/`main`) and `release.yml` (tags `v*`).
- Ship a store/F-Droid version with `scripts/create-release.sh` (bumps `versionCode` / `versionName` in `composeApp/build.gradle.kts`, Fastlane changelog, tag, signed GitHub Release APK).
- Details: `docs/RELEASE.md`.

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
