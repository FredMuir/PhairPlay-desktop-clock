#!/usr/bin/env bash
# PhairPlay local release script
#
# Builds signed release APKs using the workspace-wide Android signing identity,
# then publishes a GitHub Release with the universal APK attached.
#
# Usage:
#   ./scripts/release.sh v1.2.0
#
# Check that the existing shared keystore is present (no credentials are saved):
#   ./scripts/release.sh --setup
#
# Signing credentials are requested for each release and kept only in this process.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
KEYSTORE_FILE="${ANDROID_SHARED_KEYSTORE:-${REPO_ROOT}/../.codex-android-signing/android-unified.keystore}"

# ── helpers ─────────────────────────────────────────────────────────────────

red()   { printf '\033[0;31m%s\033[0m\n' "$*"; }
green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
bold()  { printf '\033[1m%s\033[0m\n' "$*"; }
die()   { red "ERROR: $*"; exit 1; }

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "'$1' not found. Install it and try again."
}

# ── first-time keystore setup ────────────────────────────────────────────────

cmd_setup() {
    bold "=== Shared Android signing identity ==="
    [[ -f "$KEYSTORE_FILE" ]] || die "Shared keystore not found at $KEYSTORE_FILE. Do not create a replacement key."
    echo "Using the existing workspace-wide keystore at $KEYSTORE_FILE"
    echo "No key will be generated or overwritten."
    echo ""

    green "Shared keystore is present. No key or password was created or saved."
    echo "Run ./scripts/release.sh vX.Y.Z; it will request the credentials for that release."
}

# ── main release flow ─────────────────────────────────────────────────────────

cmd_release() {
    local VERSION="$1"

    # Validate tag format
    [[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9] ]] \
        || die "Version must start with 'v' and follow semver, e.g. v1.2.0 (got: $VERSION)"

    require_cmd gh
    require_cmd keytool
    require_cmd git

    [[ -f "$KEYSTORE_FILE" ]] || die "Keystore not found at $KEYSTORE_FILE"
    read -rp "Key alias [androiddebugkey]: " KEY_ALIAS
    KEY_ALIAS="${KEY_ALIAS:-androiddebugkey}"
    read -rsp "Keystore password: " KEYSTORE_PASSWORD; echo
    [[ -n "$KEYSTORE_PASSWORD" ]] || die "Keystore password is required."
    read -rsp "Key password [same as keystore]: " KEY_PASSWORD; echo
    KEY_PASSWORD="${KEY_PASSWORD:-$KEYSTORE_PASSWORD}"
    export ANDROID_SHARED_KEYSTORE="$KEYSTORE_FILE"
    export ANDROID_SHARED_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD"
    export ANDROID_SHARED_KEY_ALIAS="$KEY_ALIAS"
    export ANDROID_SHARED_KEY_PASSWORD="$KEY_PASSWORD"

    # Auto-detect Android SDK if ANDROID_HOME is not set
    if [[ -z "${ANDROID_HOME:-}" ]]; then
        if [[ -d "$HOME/Android/Sdk" ]]; then
            export ANDROID_HOME="$HOME/Android/Sdk"
        elif [[ -d "$HOME/Library/Android/sdk" ]]; then
            export ANDROID_HOME="$HOME/Library/Android/sdk"
        else
            die "ANDROID_HOME not set and no SDK found. Install Android Studio or set ANDROID_HOME."
        fi
    fi

    # Auto-detect Homebrew JDK if JAVA_HOME is not set
    if [[ -z "${JAVA_HOME:-}" ]]; then
        for jdk in /opt/homebrew/opt/openjdk@17 /opt/homebrew/opt/openjdk /usr/libexec/java_home; do
            if [[ -d "$jdk" ]]; then export JAVA_HOME="$jdk"; break; fi
        done
    fi
    [[ -n "${JAVA_HOME:-}" ]] && export PATH="$JAVA_HOME/bin:$PATH"

    cd "$REPO_ROOT"

    # Write local.properties so Gradle finds the Android SDK
    echo "sdk.dir=$ANDROID_HOME" > local.properties

    # Ensure working tree is clean (local.properties is gitignored)
    if ! git diff --quiet -- ':!local.properties' || ! git diff --cached --quiet; then
        die "Working tree has uncommitted changes. Commit or stash them first."
    fi

    # Check gh auth
    gh auth status >/dev/null 2>&1 || die "Not logged in to GitHub. Run: gh auth login"

    bold "=== Building PhairPlay $VERSION ==="
    echo ""

    # Build the one universal Android package with the shared signing identity.
    echo "▸ Building universal release APK..."
    ANDROID_SHARED_KEYSTORE="$KEYSTORE_FILE" \
    ANDROID_SHARED_KEYSTORE_PASSWORD="$KEYSTORE_PASSWORD" \
    ANDROID_SHARED_KEY_ALIAS="$KEY_ALIAS" \
    ANDROID_SHARED_KEY_PASSWORD="$KEY_PASSWORD" \
    ./gradlew :app:assembleRelease --quiet --stacktrace

    APK_SRC="app/build/outputs/apk/release/app-release.apk"
    RELEASE_APK="PhairPlay-${VERSION}.apk"
    [[ -f "$APK_SRC" ]] || die "Universal APK not found at $APK_SRC"
    cp "$APK_SRC" "$RELEASE_APK"

    green "▸ Universal APK built:"
    echo "    $RELEASE_APK ($(du -sh "$RELEASE_APK" | cut -f1))"
    echo ""

    # Create and push git tag
    if git tag -l "$VERSION" | grep -q "$VERSION"; then
        echo "Tag $VERSION already exists locally — skipping tag creation."
    else
        echo "▸ Creating git tag $VERSION..."
        git tag -a "$VERSION" -m "Release $VERSION"
        git push origin "$VERSION"
    fi

    # Generate changelog from git log since last tag
    PREV_TAG=$(git tag --sort=-version:refname | grep -v "^${VERSION}$" | head -1)
    if [[ -n "$PREV_TAG" ]]; then
        COMMITS=$(git log --pretty=format:"- %s" "${PREV_TAG}..${VERSION}" 2>/dev/null | head -30)
        SINCE_TEXT="since $PREV_TAG"
    else
        COMMITS=$(git log --pretty=format:"- %s" "$VERSION" 2>/dev/null | head -30)
        SINCE_TEXT="initial release"
    fi

    RELEASE_NOTES="## What's changed (${SINCE_TEXT})

${COMMITS}

---
### Installation
Side-load the universal APK on a compatible Android phone, tablet, Google TV, or Fire TV:
- **${RELEASE_APK}** — one package for all supported Android devices

Use [adb](https://developer.android.com/tools/adb) or a sideloading app like *Downloader* to install."

    # Determine if pre-release (contains a dash, e.g. v1.0.0-beta.1)
    PRERELEASE_FLAG=""
    [[ "$VERSION" == *-* ]] && PRERELEASE_FLAG="--prerelease"

    echo "▸ Publishing GitHub Release $VERSION..."
    gh release create "$VERSION" \
        --title "PhairPlay $VERSION" \
        --notes "$RELEASE_NOTES" \
        $PRERELEASE_FLAG \
        "$RELEASE_APK"

    # Clean up local APK copies
    rm -f "$RELEASE_APK"

    echo ""
    green "=== Release $VERSION published! ==="
    gh release view "$VERSION" --web 2>/dev/null || true
}

# ── entry point ───────────────────────────────────────────────────────────────

case "${1:-}" in
    --setup|-s)
        cmd_setup
        ;;
    v*)
        cmd_release "$1"
        ;;
    *)
        echo "Usage:"
        echo "  ./scripts/release.sh --setup       # verify the shared signing key is present"
        echo "  ./scripts/release.sh v1.2.0        # build APKs and publish GitHub Release"
        exit 1
        ;;
esac
