# Branching & release workflow

Production branch is **`main`**. It is protected: changes land via pull requests; CI must pass.

## Branch model

```text
feature/<short-name>  →  PR  →  main  →  (optional) tag vX.Y release
```

1. Start from up-to-date `main`:
   ```bash
   git checkout main
   git pull origin main
   git checkout -b feature/my-change
   ```
2. Implement and test locally:
   ```bash
   devenv shell -- ./gradlew :composeApp:testDebugUnitTest
   ```
3. Push the branch and open a PR into `main`.
4. After CI is green, merge the PR (squash or merge commit — prefer linear history).
5. When the merged work should ship to users / F-Droid, cut a **version release** (below).

Do **not** commit directly to `main` for features. Version bumps from `scripts/create-release.sh` are the intentional exception (maintainer, from a clean `main`).

## CI

| Workflow | Trigger | What it does |
|----------|---------|----------------|
| `.github/workflows/ci.yml` | PRs + pushes to `main` | JDK 21 + unit tests + **one** unsigned `assembleRelease` + artifact (main stamps rolling `-latest.<run>` versions) |
| `.github/workflows/latest.yml` | After **CI succeeds** on `main` (`workflow_run`) | Download CI APK → **zipalign + apksigner only** → GitHub Release tag `latest` (no Gradle recompile) |
| `.github/workflows/release.yml` | Tags `v*` | Re-check tests/build; attach a **CI unsigned** APK to the GitHub Release |

Required check name for branch protection: **`ci`**.

On `main`, compile once and sign separately:

1. **CI** — unit tests + one unsigned release assemble (required check + artifact).
2. **Latest** — download that artifact and sign only (same commit’s APK; no second compile).

PRs still run tests + one unsigned release assemble (base versions; no signing secrets on forks/PRs).

## Continuous signed APK (`latest`)

After **CI** succeeds on `main`, workflow **Latest signed APK** (`.github/workflows/latest.yml`)
**signs** CI’s unsigned APK (no second `assembleRelease`) and uploads it to the GitHub Release tag `latest`:

`https://github.com/lfdominguez/my-car-tracking-mobile-app/releases/download/latest/com.domivega.gps_car-latest.apk`

This is a **sideload / continuous** channel, not F-Droid. Same signing cert as `scripts/create-release.sh`.

### One-time: Actions secrets

Repo → **Settings → Secrets and variables → Actions**:

| Secret | Value |
|--------|--------|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 /path/to/release.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias (e.g. `gps-car-release`) |
| `RELEASE_KEY_PASSWORD` | key password |

Do **not** commit the keystore or passwords. Encode locally:

```bash
base64 -w0 ~/.config/gps-car-tracking/release.jks
# paste into RELEASE_KEYSTORE_BASE64 only — never commit the output
```

### versionCode on `latest`

**CI on `main`** stamps the unsigned APK (not committed) before upload:

- `versionCode = baseVersionCode * 100000 + github.run_number`
- `versionName = "<base>-latest.<run>"`

via `-PciVersionCode` / `-PciVersionName` (see `baseVersionCode` / `baseVersionName` in `composeApp/build.gradle.kts`).
Latest does not re-run Gradle; it only signs that APK.

- Rolling installs upgrade over previous `latest`.
- Official F-Droid/store builds keep small monotonic `versionCode`s.
- A device left on `latest` may **not** accept a store APK with a lower `versionCode` until uninstall (or a store code higher than the CI value, which is unlikely). Continuous ≠ store channel.

### What stays unsigned

- PR / `ci` workflow artifacts remain unsigned (no signing secrets on PRs).
- Tag workflow may still attach a CI-unsigned APK; signed **versioned** assets come from `create-release.sh`.

## Cut a release (F-Droid-facing)

F-Droid reads **`versionName` / `versionCode`** from `composeApp/build.gradle.kts` and builds from git tags. Reproducible builds also expect a developer-signed APK on GitHub Releases:

`https://github.com/lfdominguez/my-car-tracking-mobile-app/releases/download/v%v/com.domivega.gps_car_%c.apk`

### Prerequisites

- Clean `main`, aligned with `origin/main`
- Local release keystore (never commit):
  - `~/.config/gps-car-tracking/keystore.properties` + `release.jks`, or
  - `./keystore.properties` (gitignored)
- `GH_TOKEN` / `gh auth` with `repo` scope to push and upload assets

### Command

```bash
# From repo root, on main:
scripts/create-release.sh                 # 1.0 → 1.1, versionCode +1
scripts/create-release.sh 1.2 --notes $'• Fix idle MAF\n• …'
scripts/create-release.sh 1.2 --dry-run   # show next versions only
```

The script:

1. Bumps `versionCode` and `versionName` in `composeApp/build.gradle.kts`
2. Writes `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
3. Runs unit tests (unless `--skip-tests`)
4. Commits `release: vX.Y (versionCode N)` and annotated tag `vX.Y`
5. Builds release APK (signed if keystore props resolve)
6. Pushes `main` + tag
7. Creates/updates the GitHub Release and uploads `com.domivega.gps_car_<versionCode>.apk`

### After the tag

- F-Droid auto-update (`UpdateCheckMode: Tags`) should propose the new build once the app is listed; open an fdroiddata MR only if the recipe breaks.
- Keep `docs/fdroid/com.domivega.gps_car.yml` in mind when build paths or signing keys change.
- Back up the release keystore; losing it breaks matching `AllowedAPKSigningKeys`.

## Version rules

- `versionCode` — integer, **monotonic +1** every store/F-Droid release
- `versionName` — user-facing (`1.0`, `1.1`, …); tag is `v` + versionName
- Every release needs Fastlane `changelogs/<versionCode>.txt`

## Related

- F-Droid notes: `docs/fdroid/SUBMISSION.md`
- Agent/security rules: `AGENTS.md`
