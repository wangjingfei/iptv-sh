#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
android_root="$repo_root/android-tv"
apk=${HOME_TV_APK:-$android_root/app/build/outputs/apk/debug/app-debug.apk}
package_name=${HOME_TV_PACKAGE:-com.wangjingfei.hometv.debug}
activity_name=${HOME_TV_ACTIVITY:-com.wangjingfei.hometv.MainActivity}
serial=${ANDROID_TV_SERIAL:-}
run_tests=0

case ${1:-} in
    "") ;;
    --with-tests) run_tests=1 ;;
    *)
        echo "用法：$0 [--with-tests]" >&2
        exit 2
        ;;
esac

adb_bin=${ADB:-}
if [ -z "$adb_bin" ]; then
    adb_bin=$(command -v adb || true)
fi
if [ -z "$adb_bin" ] && [ -f "$android_root/local.properties" ]; then
    sdk_root=$(sed -n 's/^sdk\.dir=//p' "$android_root/local.properties" | head -n 1 | sed 's/\\:/:/g; s/\\\\/\\/g')
    if [ -x "$sdk_root/platform-tools/adb" ]; then
        adb_bin="$sdk_root/platform-tools/adb"
    fi
fi
if [ -z "$adb_bin" ] || [ ! -x "$adb_bin" ]; then
    echo "错误：未找到 adb，请安装 Android Platform Tools 或设置 ADB" >&2
    exit 1
fi

if [ -z "$serial" ]; then
    serials=$($adb_bin devices | awk 'NR > 1 && $2 == "device" { print $1 }')
    device_count=$(printf '%s\n' "$serials" | awk 'NF { count += 1 } END { print count + 0 }')
    if [ "$device_count" -eq 0 ]; then
        echo "错误：没有已授权的 Android TV 设备。先执行 adb connect <电视IP>:5555，并在电视上确认授权。" >&2
        $adb_bin devices -l >&2
        exit 1
    fi
    if [ "$device_count" -ne 1 ]; then
        echo "错误：发现多个设备，请设置 ANDROID_TV_SERIAL 指定目标。" >&2
        $adb_bin devices -l >&2
        exit 1
    fi
    serial=$serials
fi

if [ "$($adb_bin -s "$serial" get-state 2>/dev/null || true)" != "device" ]; then
    echo "错误：设备 $serial 未连接或未授权。" >&2
    $adb_bin devices -l >&2
    exit 1
fi

if [ ! -f "$apk" ]; then
    echo "未找到 Debug APK，正在构建。"
    "$android_root/gradlew" --no-daemon --no-parallel -p "$android_root" assembleDebug
fi

model=$($adb_bin -s "$serial" shell getprop ro.product.model | tr -d '\r')
android_version=$($adb_bin -s "$serial" shell getprop ro.build.version.release | tr -d '\r')
sdk_version=$($adb_bin -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')
echo "目标设备：${serial}（${model:-未知型号}，Android ${android_version:-未知} / API ${sdk_version:-未知}）"

if [ "$run_tests" -eq 1 ]; then
    echo "运行设备测试……"
    ANDROID_SERIAL="$serial" "$android_root/gradlew" --no-daemon --no-parallel -p "$android_root" connectedDebugAndroidTest
fi

echo "安装 $apk"
$adb_bin -s "$serial" install -r -t "$apk"
$adb_bin -s "$serial" shell am force-stop "$package_name"
$adb_bin -s "$serial" shell monkey -p "$package_name" -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null
sleep 2

foreground=$($adb_bin -s "$serial" shell dumpsys activity activities | tr -d '\r' | awk '/(topResumedActivity|mResumedActivity|ResumedActivity)/ { print; exit }')
case "$foreground" in
    *"$package_name/$activity_name"*) ;;
    *)
        echo "错误：应用未进入前台。当前前台 Activity：${foreground:-未知}" >&2
        exit 1
        ;;
esac

echo "通过：APK 已安装，并从 TV 启动器进入 $activity_name"
echo "下一步：用实体遥控器验证首次设置、首页、播放浮层、4K 频道、连续换台和 1 小时播放。"
