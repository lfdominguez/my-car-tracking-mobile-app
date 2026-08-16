# Obtainium

[Obtainium](https://obtainium.imranr.dev/) polls GitHub Releases and installs APKs. This repo does not embed Obtainium — it only publishes configs so users pick the **signed** asset, not the CI unsigned APK or the wrong channel.

Package id: `com.domivega.gps_car`  
Repo: `https://github.com/lfdominguez/my-car-tracking-mobile-app`

## Channels

| Channel | Config | What it tracks | Use when |
|---------|--------|----------------|----------|
| **Stable** | [`stable.json`](stable.json) | Version tags `v*` · signed `com.domivega.gps_car_<versionCode>.apk` | Same line as F-Droid / `create-release.sh` |
| **Continuous** | [`continuous.json`](continuous.json) | Prerelease tag `latest` · `com.domivega.gps_car-latest.apk` | Sideload every green `main` |

Both configs share the same package id. Install **one** channel on a device. Switching later may require uninstall if `versionCode` went backwards (continuous uses a huge CI `versionCode`; see [`docs/RELEASE.md`](../RELEASE.md)).

F-Droid builds use a **different signer**. Do not mix an F-Droid install with these GitHub configs.

## One-tap add (phone with Obtainium)

Stable (recommended):

[Add GPS Car Tracking (stable)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.domivega.gps_car%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Flfdominguez%2Fmy-car-tracking-mobile-app%22%2C%22author%22%3A%22lfdominguez%22%2C%22name%22%3A%22GPS%20Car%20Tracking%22%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22com%5C%5C%5C%5C.domivega%5C%5C%5C%5C.gps_car_%5C%5C%5C%5Cd%2B%5C%5C%5C%5C.apk%5C%22%2C%5C%22includePrereleases%5C%22%3Afalse%7D%22%7D)

Continuous (`latest` only — not the store line):

[Add GPS Car Tracking (continuous)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.domivega.gps_car%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Flfdominguez%2Fmy-car-tracking-mobile-app%22%2C%22author%22%3A%22lfdominguez%22%2C%22name%22%3A%22GPS%20Car%20Tracking%20%28latest%29%22%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22com%5C%5C%5C%5C.domivega%5C%5C%5C%5C.gps_car-latest%5C%5C%5C%5C.apk%5C%22%2C%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22latest%5C%22%7D%22%7D)

Or open the matching `obtainium://app/…` deep link from the JSON below (share / QR).

## Settings that matter

**Stable**

- `includePrereleases`: **false** — ignores tag `latest`
- `apkFilterRegEx`: `com\.domivega\.gps_car_\d+\.apk` — signed store APK only (not `*-ci-unsigned.apk`)

**Continuous**

- `includePrereleases`: **true**
- `filterReleaseTitlesByRegEx`: `latest` — matches release title `Continuous (latest)` (or tag `latest` if the title is empty)
- `apkFilterRegEx`: `com\.domivega\.gps_car-latest\.apk`

## Manual add

1. Install [Obtainium](https://github.com/ImranR98/Obtainium/releases).
2. **Add App** → URL `https://github.com/lfdominguez/my-car-tracking-mobile-app`.
3. Apply the filters from the table above (or import the JSON via Obtainium export/import).

## Not in this repo

Listing on [apps.obtainium.imranr.dev](https://apps.obtainium.imranr.dev/) is a separate PR against [ImranR98/apps.obtainium.imranr.dev](https://github.com/ImranR98/apps.obtainium.imranr.dev) (`complex` entry — this app needs `additionalSettings`). The JSON here is the source of truth for that later PR.

## Related

- Releases / `latest` `versionCode`: [`docs/RELEASE.md`](../RELEASE.md)
- F-Droid: [`docs/fdroid/`](../fdroid/)
