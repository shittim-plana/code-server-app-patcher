#!/usr/bin/env bash
# Layream Android build environment — source this file before building.
# All aarch64 native, fully persistent under /config/android/.

ANDROID_BASE="/config/android"

export JAVA_HOME="$ANDROID_BASE/jdk17"
export ANDROID_HOME="$ANDROID_BASE/sdk"
export NDK_HOME="$ANDROID_BASE/ndk/r29"

export PATH="$JAVA_HOME/bin:$NDK_HOME/toolchains/llvm/prebuilt/linux-arm64/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$ANDROID_HOME/cmdline-tools/latest/bin:${CARGO_HOME:-/config/.cargo}/bin:$PATH"

_ok=true
for _t in clang java aapt2; do
    command -v "$_t" >/dev/null 2>&1 || { echo "WARNING: $_t not found"; _ok=false; }
done

if [ "$_ok" = true ]; then
    echo "Android build environment loaded."
    echo "  JAVA_HOME=$JAVA_HOME"
    echo "  NDK_HOME=$NDK_HOME"
    echo "  clang: $(clang --version 2>&1 | head -1)"
else
    echo "Run: bash /config/workspace/layream/scripts/setup-android-env.sh"
fi
unset _ok _t ANDROID_BASE
