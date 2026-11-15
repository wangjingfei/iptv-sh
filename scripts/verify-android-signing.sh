#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
android_root="$repo_root/android-tv"
sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}

if [ -z "$sdk_root" ] && [ -f "$android_root/local.properties" ]; then
    sdk_root=$(sed -n 's/^sdk\.dir=//p' "$android_root/local.properties" | head -n 1 | sed 's/\\:/:/g; s/\\\\/\\/g')
fi

if [ -z "$sdk_root" ]; then
    echo "错误：未找到 Android SDK，请设置 ANDROID_HOME 或 ANDROID_SDK_ROOT" >&2
    exit 1
fi

apksigner="$sdk_root/build-tools/36.0.0/apksigner"
if [ ! -x "$apksigner" ]; then
    echo "错误：未找到 $apksigner" >&2
    exit 1
fi

signing_tmp=$(mktemp -d "${TMPDIR:-/tmp}/home-tv-signing.XXXXXX")
signed_apk="$android_root/app/build/outputs/apk/release/app-release.apk"
previous_signed_apk="$signing_tmp/app-release.previous.apk"
had_previous_signed_apk=0
may_remove_signed_apk=0
cleanup() {
    cleanup_status=0
    if [ "$may_remove_signed_apk" -eq 1 ]; then
        rm -f "$signed_apk" || cleanup_status=$?
    fi
    if [ "$had_previous_signed_apk" -eq 1 ]; then
        if mv "$previous_signed_apk" "$signed_apk"; then
            had_previous_signed_apk=0
        else
            echo "错误：无法恢复原有 Release APK；备份保留在 $previous_signed_apk" >&2
            cleanup_status=1
        fi
    fi
    if [ "$had_previous_signed_apk" -eq 0 ]; then
        rm -rf "$signing_tmp" || cleanup_status=$?
    fi
    return "$cleanup_status"
}
trap cleanup EXIT HUP INT TERM

if [ -e "$signed_apk" ]; then
    mv "$signed_apk" "$previous_signed_apk"
    had_previous_signed_apk=1
fi
may_remove_signed_apk=1

store_password=$(openssl rand -hex 16)
key_password=$(openssl rand -hex 16)
keystore="$signing_tmp/release-smoke.jks"

keytool -genkeypair -noprompt \
    -keystore "$keystore" \
    -storetype JKS \
    -storepass "$store_password" \
    -keypass "$key_password" \
    -alias home-tv-ci \
    -dname "CN=Home TV Signing Smoke Test,O=Local Build,C=CN" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 1 >/dev/null 2>&1

HOME_TV_KEYSTORE="$keystore" \
HOME_TV_STORE_PASSWORD="$store_password" \
HOME_TV_KEY_ALIAS=home-tv-ci \
HOME_TV_KEY_PASSWORD="$key_password" \
    "$android_root/gradlew" --no-daemon --no-parallel -p "$android_root" assembleRelease >/dev/null

if [ ! -f "$signed_apk" ]; then
    echo "错误：Gradle 未生成签名 Release APK" >&2
    exit 1
fi

verify_log="$signing_tmp/apksigner.log"
if ! "$apksigner" verify --verbose --print-certs "$signed_apk" >"$verify_log" 2>&1; then
    cat "$verify_log" >&2
    exit 1
fi
grep -E '^(Verifies|Verified using v[12] scheme|Number of signers|Signer #1 certificate DN|Signer #1 certificate SHA-256 digest)' "$verify_log"
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$signed_apk"
else
    shasum -a 256 "$signed_apk"
fi

echo "通过：临时 release keystore 构建和 APK 签名校验成功；测试 APK 已安排删除"
