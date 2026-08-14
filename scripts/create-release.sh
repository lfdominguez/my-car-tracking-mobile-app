#!/usr/bin/env bash
# Create a versioned Android release for GPS Car Tracking.
#
# Bumps versionName / versionCode in composeApp/build.gradle.kts (F-Droid
# UpdateCheckData), writes Fastlane changelog, commits on main, tags, pushes,
# builds a developer-signed APK (local keystore), and publishes a GitHub Release
# asset named com.domivega.gps_car_<versionCode>.apk for reproducible builds.
#
# Usage:
#   scripts/create-release.sh                 # bump patch: 1.0 -> 1.1, code +1
#   scripts/create-release.sh 1.2             # set versionName, code +1
#   scripts/create-release.sh 1.2 --notes "…"
#   scripts/create-release.sh 1.2 --dry-run
#   scripts/create-release.sh 1.2 --skip-tests
#   scripts/create-release.sh 1.2 --no-push   # commit+tag+build locally only
#
# Signing (never commit these):
#   ~/.config/gps-car-tracking/keystore.properties + release.jks
#   or repo-root keystore.properties (gitignored)
#
# Auth for push / release upload:
#   gh auth, or GITHUB_TOKEN / GH_TOKEN with repo scope
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

GRADLE_FILE="composeApp/build.gradle.kts"
CHANGELOG_DIR="fastlane/metadata/android/en-US/changelogs"
DEFAULT_KEYSTORE_PROPS="${HOME}/.config/gps-car-tracking/keystore.properties"
REPO_SLUG_DEFAULT="lfdominguez/my-car-tracking-mobile-app"

VERSION_NAME_ARG=""
NOTES=""
DRY_RUN=0
SKIP_TESTS=0
NO_PUSH=0

die() { echo "error: $*" >&2; exit 1; }
info() { echo "→ $*"; }

usage() {
  sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage 0 ;;
    --dry-run) DRY_RUN=1; shift ;;
    --skip-tests) SKIP_TESTS=1; shift ;;
    --no-push) NO_PUSH=1; shift ;;
    --notes)
      [[ $# -ge 2 ]] || die "--notes requires a value"
      NOTES="$2"
      shift 2
      ;;
    --notes=*)
      NOTES="${1#--notes=}"
      shift
      ;;
    -*)
      die "unknown option: $1"
      ;;
    *)
      [[ -z "$VERSION_NAME_ARG" ]] || die "unexpected extra argument: $1"
      VERSION_NAME_ARG="$1"
      shift
      ;;
  esac
done

command -v git >/dev/null || die "git is required"
command -v python3 >/dev/null || die "python3 is required"

[[ -f "$GRADLE_FILE" ]] || die "missing $GRADLE_FILE"

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
[[ "$BRANCH" == "main" ]] || die "must run on main (currently on $BRANCH)"

if [[ -n "$(git status --porcelain)" ]]; then
  die "working tree is not clean; commit or stash first"
fi

if [[ "$NO_PUSH" -eq 0 && "$DRY_RUN" -eq 0 ]]; then
  info "Fetching origin"
  git fetch origin main --tags
  LOCAL="$(git rev-parse HEAD)"
  REMOTE="$(git rev-parse origin/main)"
  [[ "$LOCAL" == "$REMOTE" ]] || die "main is not aligned with origin/main (pull/push first)"
fi

mapfile -t CUR < <(python3 - <<'PY'
from pathlib import Path
import re
text = Path("composeApp/build.gradle.kts").read_text()
# Prefer baseVersion* (CI override hooks); fall back to plain versionCode/versionName.
code = re.search(r"baseVersionCode\s*=\s*(\d+)", text) or re.search(r"versionCode\s*=\s*(\d+)", text)
name = re.search(r'baseVersionName\s*=\s*"([^"]+)"', text) or re.search(r'versionName\s*=\s*"([^"]+)"', text)
if not code or not name:
    raise SystemExit("could not parse baseVersionCode/baseVersionName (or versionCode/versionName)")
print(code.group(1))
print(name.group(1))
PY
)

CUR_CODE="${CUR[0]}"
CUR_NAME="${CUR[1]}"
NEXT_CODE=$((CUR_CODE + 1))

if [[ -n "$VERSION_NAME_ARG" ]]; then
  NEXT_NAME="$VERSION_NAME_ARG"
else
  NEXT_NAME="$(python3 - <<PY
major, minor, *rest = "${CUR_NAME}".split(".")
minor_i = int(minor) if minor.isdigit() else 0
print(f"{major}.{minor_i + 1}" + (("." + ".".join(rest)) if rest else ""))
PY
)"
fi

[[ "$NEXT_NAME" =~ ^[0-9]+(\.[0-9]+){1,3}([.-][A-Za-z0-9]+)?$ ]] || \
  die "invalid versionName: $NEXT_NAME"

TAG="v${NEXT_NAME}"
if git rev-parse "$TAG" >/dev/null 2>&1; then
  die "tag $TAG already exists"
fi

if [[ -z "$NOTES" ]]; then
  NOTES="Release ${NEXT_NAME} (versionCode ${NEXT_CODE})."
fi

info "Current: versionName=${CUR_NAME} versionCode=${CUR_CODE}"
info "Next:    versionName=${NEXT_NAME} versionCode=${NEXT_CODE} tag=${TAG}"

if [[ "$DRY_RUN" -eq 1 ]]; then
  info "dry-run: no files changed"
  exit 0
fi

python3 - <<PY
from pathlib import Path
import re
path = Path("$GRADLE_FILE")
text = path.read_text()
# Bump store/F-Droid bases; CI latest uses -PciVersion* overrides separately.
if re.search(r"baseVersionCode\s*=\s*\d+", text):
    text2, n1 = re.subn(r"(baseVersionCode\s*=\s*)\d+", r"\g<1>${NEXT_CODE}", text, count=1)
    text3, n2 = re.subn(r'(baseVersionName\s*=\s*)"[^"]+"', r'\g<1>"${NEXT_NAME}"', text2, count=1)
    label = "baseVersionCode/baseVersionName"
else:
    text2, n1 = re.subn(r"(versionCode\s*=\s*)\d+", r"\g<1>${NEXT_CODE}", text, count=1)
    text3, n2 = re.subn(r'(versionName\s*=\s*)"[^"]+"', r'\g<1>"${NEXT_NAME}"', text2, count=1)
    label = "versionCode/versionName"
if n1 != 1 or n2 != 1:
    raise SystemExit(f"expected one {label} replace, got {n1}, {n2}")
path.write_text(text3)
print(f"updated {path}")
PY

mkdir -p "$CHANGELOG_DIR"
CHANGELOG_FILE="${CHANGELOG_DIR}/${NEXT_CODE}.txt"
if [[ -f "$CHANGELOG_FILE" ]]; then
  die "changelog already exists: $CHANGELOG_FILE"
fi
printf '%s\n' "$NOTES" > "$CHANGELOG_FILE"
info "Wrote $CHANGELOG_FILE"

if [[ "$SKIP_TESTS" -eq 0 ]]; then
  info "Running unit tests"
  if command -v devenv >/dev/null 2>&1 && [[ -f devenv.nix ]]; then
    devenv shell -- ./gradlew :composeApp:testDebugUnitTest --no-daemon
  else
    ./gradlew :composeApp:testDebugUnitTest --no-daemon
  fi
fi

git add "$GRADLE_FILE" "$CHANGELOG_FILE"
git commit --trailer "Co-authored-by: Junie <junie@jetbrains.com>" \
  -m "release: v${NEXT_NAME} (versionCode ${NEXT_CODE})"

git tag -a "$TAG" -m "Release ${NEXT_NAME}"

info "Building signed release APK"
if command -v devenv >/dev/null 2>&1 && [[ -f devenv.nix ]]; then
  devenv shell -- ./gradlew :composeApp:assembleRelease --no-daemon
else
  ./gradlew :composeApp:assembleRelease --no-daemon
fi

APK_SRC=""
for cand in \
  "composeApp/build/outputs/apk/release/composeApp-release.apk" \
  "composeApp/build/outputs/apk/release/composeApp-release-unsigned.apk"
do
  if [[ -f "$cand" ]]; then
    APK_SRC="$cand"
    break
  fi
done
[[ -n "$APK_SRC" ]] || die "release APK not found under composeApp/build/outputs/apk/release/"

if [[ "$APK_SRC" == *unsigned* ]]; then
  echo "warning: built unsigned APK (no keystore props). F-Droid reproducible builds need a signed asset." >&2
  echo "warning: expected signing via $DEFAULT_KEYSTORE_PROPS or ./keystore.properties" >&2
fi

DIST_DIR="$(mktemp -d)"
ASSET_NAME="com.domivega.gps_car_${NEXT_CODE}.apk"
ASSET_PATH="${DIST_DIR}/${ASSET_NAME}"
cp "$APK_SRC" "$ASSET_PATH"
info "Asset: $ASSET_PATH ($(wc -c <"$ASSET_PATH") bytes)"

# Optional cert print
if command -v devenv >/dev/null 2>&1; then
  devenv shell -- bash -lc '
    set -e
    APK="'"$ASSET_PATH"'"
    if [[ -z "${ANDROID_HOME:-}" ]]; then exit 0; fi
    BT=$(ls -d "$ANDROID_HOME/build-tools"/* 2>/dev/null | sort -V | tail -1 || true)
    if [[ -n "$BT" && -x "$BT/apksigner" ]]; then
      echo "APK signer cert:"
      "$BT/apksigner" verify --print-certs "$APK" 2>/dev/null | grep -E "SHA-256|Verified" || true
    fi
  ' || true
fi

if [[ "$NO_PUSH" -eq 1 ]]; then
  info "--no-push: left commit+tag local. Asset at $ASSET_PATH"
  info "Push later: git push origin main && git push origin $TAG"
  exit 0
fi

# Push helpers
push_with_auth() {
  local token="${GH_TOKEN:-${GITHUB_TOKEN:-}}"
  if [[ -n "$token" ]]; then
    git push "https://x-access-token:${token}@github.com/${REPO_SLUG_DEFAULT}.git" main
    git push "https://x-access-token:${token}@github.com/${REPO_SLUG_DEFAULT}.git" "refs/tags/${TAG}"
  else
    git push origin main
    git push origin "refs/tags/${TAG}"
  fi
}

info "Pushing main and $TAG"
push_with_auth

create_or_update_release() {
  local token="${GH_TOKEN:-${GITHUB_TOKEN:-}}"
  if command -v gh >/dev/null 2>&1; then
    if [[ -n "$token" ]]; then
      export GH_TOKEN="$token"
    fi
    if gh release view "$TAG" >/dev/null 2>&1; then
      gh release upload "$TAG" "$ASSET_PATH" --clobber
    else
      gh release create "$TAG" "$ASSET_PATH" \
        --title "$TAG" \
        --notes "$NOTES"
    fi
    return 0
  fi

  [[ -n "$token" ]] || die "need gh CLI or GH_TOKEN to publish GitHub Release"

  python3 - <<PY
import json, os, urllib.request, urllib.error
from pathlib import Path

token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
repo = "${REPO_SLUG_DEFAULT}"
tag = "${TAG}"
notes = """${NOTES}"""
asset = Path("${ASSET_PATH}")
name = asset.name

def api(method, url, data=None, content_type="application/json"):
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    body = None
    if data is not None:
        if content_type == "application/json":
            body = json.dumps(data).encode()
            headers["Content-Type"] = content_type
        else:
            body = data
            headers["Content-Type"] = content_type
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        err = e.read().decode()
        raise SystemExit(f"GitHub API {method} {url} failed: {e.code} {err}")

rel = None
try:
    rel = api("GET", f"https://api.github.com/repos/{repo}/releases/tags/{tag}")
except SystemExit as e:
    if "404" not in str(e):
        raise
    rel = api("POST", f"https://api.github.com/repos/{repo}/releases", {
        "tag_name": tag,
        "name": tag,
        "body": notes,
        "draft": False,
        "prerelease": False,
    })

for a in rel.get("assets") or []:
    if a.get("name") == name:
        api("DELETE", f"https://api.github.com/repos/{repo}/releases/assets/{a['id']}")

upload_url = rel["upload_url"].split("{", 1)[0] + f"?name={name}"
data = asset.read_bytes()
headers_ok = api(
    "POST",
    upload_url,
    data=data,
    content_type="application/vnd.android.package-archive",
)
print("uploaded", headers_ok.get("browser_download_url") or headers_ok.get("name"))
PY
}

info "Publishing GitHub Release $TAG with $ASSET_NAME"
create_or_update_release

info "Done."
info "  versionName=$NEXT_NAME versionCode=$NEXT_CODE"
info "  tag=$TAG commit=$(git rev-parse HEAD)"
info "  asset=https://github.com/${REPO_SLUG_DEFAULT}/releases/download/${TAG}/${ASSET_NAME}"
info "F-Droid picks tags via UpdateCheckMode when the recipe stays valid."
