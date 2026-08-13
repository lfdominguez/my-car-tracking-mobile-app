# Submitting GPS Car Tracking to F-Droid

This repo is prepared for inclusion in the main [F-Droid](https://f-droid.org) repository. Official listing still requires a merge request against [fdroiddata](https://gitlab.com/fdroid/fdroiddata).

**First inclusion MR:** https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45659  
**First release tag:** `v1.0` (`versionName` 1.0 / `versionCode` 1)

## Prerequisites (already done in-app)

| Check | Status |
|-------|--------|
| Free software license (MIT) | `LICENSE` |
| No Play Services / Firebase / proprietary Maps | Platform `GpsLocator` only |
| No secrets required at build time | Token/URLs set in-app; `local.properties` gitignored |
| applicationId | `com.domivega.gps_car` |
| Upstream store text | `fastlane/metadata/android/en-US/` |
| Draft recipe | `docs/fdroid/com.domivega.gps_car.yml` |

## 1. Cut a release tag

Version fields live in `composeApp/build.gradle.kts`:

- `versionName` (e.g. `1.0`)
- `versionCode` (integer, monotonic)

```bash
# After the tree you want on F-Droid is on main and green:
git tag -a v1.0 -m "Release 1.0"
git push origin v1.0
```

Ensure the tag’s `versionCode` / `versionName` match the draft YAML (`CurrentVersion` / `CurrentVersionCode` and the `Builds` entry).

## 2. Confirm a clean release APK locally

```bash
# JDK 21 recommended (see devenv.nix)
./gradlew :composeApp:assembleRelease :composeApp:testDebugUnitTest
ls composeApp/build/outputs/apk/release/
```

- **F-Droid CI** builds an **unsigned** APK (`composeApp-release-unsigned.apk`) when no keystore props are present.
- **Reproducible builds** need a **developer-signed** APK on GitHub Releases matching that build. Optional local signing reads (never commit these):
  - `~/.config/gps-car-tracking/keystore.properties` + `release.jks`, or
  - `keystore.properties` at the repo root (gitignored)
- Publish: `com.domivega.gps_car_<versionCode>.apk` on tag `v<versionName>` (see recipe `Binaries` / `AllowedAPKSigningKeys`).
- `full_description.txt` must be **HTML** (not Markdown) for F-Droid.

## 3. Fork fdroiddata and add metadata

1. Fork https://gitlab.com/fdroid/fdroiddata  
2. Copy this repo’s draft:

   ```bash
   cp docs/fdroid/com.domivega.gps_car.yml \
     /path/to/fdroiddata/metadata/com.domivega.gps_car.yml
   ```

3. Edit the copy:
   - Set `Builds[0].commit` to the **full 40-char commit hash** of the release (not the `v1.0` tag name)
   - Align `versionName` / `versionCode` / `CurrentVersion*` with that tag
   - Keep `Binaries` + `AllowedAPKSigningKeys` for reproducible builds
   - Drop or adjust `sudo` / `rm` if reviewers prefer leaner recipes
4. Optional local checks (if you install [fdroidserver](https://gitlab.com/fdroid/fdroidserver)):

   ```bash
   fdroid readmeta
   fdroid lint com.domivega.gps_car
   fdroid build -v -l com.domivega.gps_car
   ```

5. Open a merge request on GitLab. Mention:
   - MIT license  
   - No GMS  
   - Self-hosted FLOSS backend: https://github.com/lfdominguez/my-car-tracking-platform  
   - Optional hosted instance: https://mycar.domivega.com (user-configured only)

## 4. Fastlane metadata & graphics

F-Droid picks up upstream Fastlane when present:

```text
fastlane/metadata/android/en-US/
  title.txt
  short_description.txt   # keep ≤ 80 characters
  full_description.txt
  changelogs/<versionCode>.txt
  images/icon.png         # optional 512×512
  images/phoneScreenshots/  # optional
```

Add a new `changelogs/<versionCode>.txt` for every bump. Screenshots help review but are not mandatory for a first MR.

## 5. After inclusion (later releases)

For each new version **after** the app is listed, you normally do **not** open a new fdroiddata MR:

1. Bump `versionCode` + `versionName` in `composeApp/build.gradle.kts` (monotonic `versionCode`).
2. Add Fastlane notes: `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Merge to `main`, then tag and push:

   ```bash
   git tag -a v1.1 -m "Release 1.1"
   git push origin v1.1
   ```

4. Rely on the recipe’s `UpdateCheckMode: Tags` and `UpdateCheckData` (parses Gradle versions). F-Droid’s updater should propose/build the new tag.

Open a **metadata follow-up MR** only if the build recipe breaks, for example:

- Gradle module rename or release APK `output` path change
- JDK / AGP requirements that need different `sudo` or build flags
- New non-free deps or scan issues needing `scanignore` / recipe changes

Keep `docs/fdroid/com.domivega.gps_car.yml` in sync when you change the live recipe so the next edit starts from a known-good file.

## Related links

- App source: https://github.com/lfdominguez/my-car-tracking-mobile-app  
- Backend: https://github.com/lfdominguez/my-car-tracking-platform  
- Inclusion policy: https://f-droid.org/docs/Inclusion_Policy/  
- Metadata reference: https://f-droid.org/docs/Build_Metadata_Reference/  
