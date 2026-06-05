#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

source "$SCRIPT_DIR/env.sh"

which gcc >/dev/null 2>&1 || {
    echo "[build] Installing gcc..."
    sudo apt-get update -qq && sudo apt-get install -y -qq gcc pkg-config libdbus-1-dev
}

REGISTRY_DIR="/config/.cargo/registry/src"
PATCHED_FILES=()

patch_tauri_gradle() {
    echo "[build] Patching Tauri library gradle for Kotlin 2.3 + Java 17..."
    while IFS= read -r f; do
        PATCHED_FILES+=("$f")
    done < <(grep -rl 'jvmTarget = "1.8"\|VERSION_1_8' \
        "$REGISTRY_DIR"/*/tauri-*/mobile/android/build.gradle.kts \
        "$REGISTRY_DIR"/*/tauri-plugin-*/android/build.gradle.kts 2>/dev/null || true)

    node -e "
const fs = require('fs');
const files = process.argv.slice(1);
files.forEach(f => {
  let c = fs.readFileSync(f, 'utf8');
  c = c.replace(/JavaVersion\\.VERSION_1_8/g, 'JavaVersion.VERSION_17');
  c = c.replace(/    kotlinOptions \\{\n\\s*jvmTarget = \"1\\.8\"\n\\s*\\}/g,
    '    kotlin {\n        compilerOptions {\n            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)\n        }\n    }');
  fs.writeFileSync(f, c);
});
" "${PATCHED_FILES[@]}"

    echo "[build] Patched ${#PATCHED_FILES[@]} files."
}

restore_tauri_gradle() {
    if [ ${#PATCHED_FILES[@]} -gt 0 ]; then
        echo "[build] Restoring Tauri library gradle -> original..."
        node -e "
const fs = require('fs');
const files = process.argv.slice(1);
files.forEach(f => {
  let c = fs.readFileSync(f, 'utf8');
  c = c.replace(/    kotlin \\{\n        compilerOptions \\{\n            jvmTarget\\.set\\(org\\.jetbrains\\.kotlin\\.gradle\\.dsl\\.JvmTarget\\.JVM_17\\)\n        \\}\n    \\}/g,
    '    kotlinOptions {\n        jvmTarget = \"1.8\"\n    }');
  c = c.replace(/JavaVersion\\.VERSION_17/g, 'JavaVersion.VERSION_1_8');
  fs.writeFileSync(f, c);
});
" "${PATCHED_FILES[@]}"
        echo "[build] Restored ${#PATCHED_FILES[@]} files."
    fi
}

trap restore_tauri_gradle EXIT

patch_tauri_gradle

cd "$ROOT_DIR"

echo "[build] Building APK (this takes ~3-5 minutes)..."
CARGO_BUILD_JOBS=2 npx tauri android build --apk --target aarch64

APK_PATH="src-tauri/gen/android/app/build/outputs/apk/universal/release/app-universal-release-unsigned.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "[build] ERROR: APK not found at $APK_PATH"
    exit 1
fi

echo "[build] APK built: $(du -h "$APK_PATH" | cut -f1)"

if [[ "$*" == *"--sign"* ]]; then
    VERSION="${VERSION:-dev}"
    OUT_APK="$ROOT_DIR/code-server-app-v${VERSION}.apk"
    echo "[build] Signing APK..."
    $ANDROID_HOME/build-tools/35.0.0/apksigner sign \
        --ks "$ROOT_DIR/release.keystore" \
        --ks-key-alias codeserver \
        --ks-pass pass:codeserver123 \
        --key-pass pass:codeserver123 \
        --out "$OUT_APK" \
        "$APK_PATH"
    echo "[build] Signed: $OUT_APK ($(du -h "$OUT_APK" | cut -f1))"
fi

echo "[build] Done."
