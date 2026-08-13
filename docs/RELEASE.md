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
| `.github/workflows/ci.yml` | PRs + pushes to `main` | JDK 21, unit tests, unsigned `assembleRelease` |
| `.github/workflows/release.yml` | Tags `v*` | Re-check tests/build; attach a **CI unsigned** APK to the GitHub Release |

Required check name for branch protection: **`ci`**.

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
