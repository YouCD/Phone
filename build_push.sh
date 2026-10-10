#!/bin/bash
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_DIR="$PROJECT_DIR/app/build/outputs/apk/core/release"
SIGN_KEY="$HOME/.android/security/platform.pk8"
SIGN_CERT="$HOME/.android/security/platform.x509.pem"

VERSION=$(grep VERSION_CODE "$PROJECT_DIR/gradle.properties" | cut -d= -f2)
UNSIGNED_APK="$APK_DIR/phone-${VERSION}-core-release-unsigned.apk"
SIGNED_APK="/tmp/phone-signed.apk"
DEVICE_PATH="/system/app/org.fossify.phone/org.fossify.phone.apk"

cd "$PROJECT_DIR"
echo ":: 编译 core 版本..."
./gradlew assembleCoreRelease

echo ":: 签名..."
cp "$UNSIGNED_APK" /tmp/phone-unsigned.apk
/home/ycd/Android/Sdk/build-tools/34.0.0/apksigner sign \
    --key "$SIGN_KEY" --cert "$SIGN_CERT" --out "$SIGNED_APK" /tmp/phone-unsigned.apk

echo ":: 推送..."
adb wait-for-device
adb root 2>/dev/null
adb remount 2>/dev/null
adb push "$SIGNED_APK" "$DEVICE_PATH"

echo ":: 重新启动应用..."
adb shell "am force-stop org.fossify.phone; am start -n org.fossify.phone/.activities.MainActivity"

echo ":: 完成"
