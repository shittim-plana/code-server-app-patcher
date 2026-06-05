#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEMPLATE_DIR="$SCRIPT_DIR/template"
BUILD_DIR="$SCRIPT_DIR/build"

usage() {
    echo "Usage: $0 --url <URL> [--name <앱이름>] [--id <applicationId>] [--sign]"
    echo ""
    echo "Options:"
    echo "  --url   Code Server URL (필수)"
    echo "  --name  앱 이름 (기본: URL 도메인)"
    echo "  --id    applicationId (기본: 도메인에서 자동 생성)"
    echo "  --sign  빌드 후 서명"
    exit 1
}

URL=""
APP_NAME=""
APP_ID=""
SIGN=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --url) URL="$2"; shift 2 ;;
        --name) APP_NAME="$2"; shift 2 ;;
        --id) APP_ID="$2"; shift 2 ;;
        --sign) SIGN=true; shift ;;
        *) usage ;;
    esac
done

[ -z "$URL" ] && usage

DOMAIN=$(echo "$URL" | sed -E 's|https?://||' | sed 's|/.*||' | sed 's|:.*||')
DOMAIN_SLUG=$(echo "$DOMAIN" | tr '.' '-' | tr -cd 'a-zA-Z0-9-')

[ -z "$APP_NAME" ] && APP_NAME="Code Server ($DOMAIN_SLUG)"
[ -z "$APP_ID" ] && APP_ID="com.codeserver.app.$(echo "$DOMAIN_SLUG" | tr '-' '_' | tr '[:upper:]' '[:lower:]')"

echo "[patch] URL:    $URL"
echo "[patch] Domain: $DOMAIN"
echo "[patch] Name:   $APP_NAME"
echo "[patch] ID:     $APP_ID"
echo ""

rm -rf "$BUILD_DIR"
cp -r "$TEMPLATE_DIR" "$BUILD_DIR"

echo "[patch] applicationId → $APP_ID"
sed -i "s|applicationId = \"com.codeserver.app\"|applicationId = \"$APP_ID\"|" \
    "$BUILD_DIR/app/build.gradle.kts"

echo "[patch] intent-filter host → $DOMAIN"
sed -i "s|android:host=\"257mari.vscode.artblnd.net\"|android:host=\"$DOMAIN\"|" \
    "$BUILD_DIR/app/src/main/AndroidManifest.xml"

echo "[patch] app_name → $APP_NAME"
sed -i "s|<string name=\"app_name\">Code Server</string>|<string name=\"app_name\">$APP_NAME</string>|" \
    "$BUILD_DIR/app/src/main/res/values/strings.xml"

echo "[patch] fileprovider authority → $APP_ID"
sed -i "s|\${applicationId}.fileprovider|$APP_ID.fileprovider|" \
    "$BUILD_DIR/app/src/main/AndroidManifest.xml"

echo ""
echo "[patch] 빌드 중..."
if [ -f "$BUILD_DIR/scripts/env.sh" ]; then
    source "$BUILD_DIR/scripts/env.sh"
elif [ -f "$SCRIPT_DIR/template/scripts/env.sh" ]; then
    source "$SCRIPT_DIR/template/scripts/env.sh"
fi
cd "$BUILD_DIR"
chmod +x gradlew
./gradlew assembleRelease

APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "[patch] ERROR: APK not found"
    exit 1
fi

OUT_NAME="code-server-${DOMAIN_SLUG}.apk"

if [ "$SIGN" = true ] && [ -f "$BUILD_DIR/release.keystore" ]; then
    echo "[patch] 서명 중..."
    $ANDROID_HOME/build-tools/35.0.0/apksigner sign \
        --ks "$BUILD_DIR/release.keystore" \
        --ks-key-alias codeserver \
        --ks-pass pass:codeserver123 \
        --key-pass pass:codeserver123 \
        --out "$SCRIPT_DIR/$OUT_NAME" \
        "$APK_PATH"
    echo "[patch] 완료: $SCRIPT_DIR/$OUT_NAME ($(du -h "$SCRIPT_DIR/$OUT_NAME" | cut -f1))"
else
    cp "$APK_PATH" "$SCRIPT_DIR/$OUT_NAME"
    echo "[patch] 완료 (unsigned): $SCRIPT_DIR/$OUT_NAME ($(du -h "$SCRIPT_DIR/$OUT_NAME" | cut -f1))"
fi
