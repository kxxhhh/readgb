#!/bin/bash
set -e

PACKAGE_NAME="com.dutongjian.app"
OUTPUT_DIR="build/ui_checks"

export ANDROID_HOME=${ANDROID_HOME:-/home/codespace/android-sdk}
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH

echo "[1/6] 检查无头模拟器状态..."
if ! adb devices | grep -q "emulator-"; then
    echo "正在后台启动无头模拟器..."
    emulator -avd test_emulator -no-window -no-audio -no-boot-anim -gpu off -memory 3072 &
    adb wait-for-device
    while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
        echo "等待模拟器 Boot 完成..."
        sleep 3
    done
    echo "⚡ 模拟器已准备就绪！"
else
    echo "⚡ 检测到模拟器已在后台运行！"
fi

echo "[2/6] 执行 Gradle 编译 Inspection/Debug 变体..."
BUILD_VARIANT="inspection"
if [ -f "./gradlew" ]; then
    if ! ./gradlew assembleInspection; then
        BUILD_VARIANT="debug"
        ./gradlew assembleDebug
    fi
elif [ -f "android/gradlew" ]; then
    if ! (cd android && ./gradlew assembleInspection); then
        BUILD_VARIANT="debug"
        (cd android && ./gradlew assembleDebug)
    fi
else
    if ! gradle assembleInspection; then
        BUILD_VARIANT="debug"
        gradle assembleDebug
    fi
fi

# Prefer the variant compiled by this script; do not accidentally install a stale Debug APK.
if [ "$BUILD_VARIANT" = "inspection" ] && [ -f "./android/app/build/outputs/apk/inspection/app-inspection.apk" ]; then
    APK_PATH="./android/app/build/outputs/apk/inspection/app-inspection.apk"
elif [ "$BUILD_VARIANT" = "debug" ] && [ -f "./android/app/build/outputs/apk/debug/app-debug.apk" ]; then
    APK_PATH="./android/app/build/outputs/apk/debug/app-debug.apk"
else
    APK_PATH=$(find . -name "*.apk" | grep -E "inspection|debug" | head -n 1)
fi
echo "📦 匹配到的 APK 文件: $APK_PATH"

if [ -z "$APK_PATH" ]; then
    echo "❌ 错误：未找到构建成功的 APK 文件！"
    exit 1
fi

echo "[3/6] 安装 APK 到模拟器..."
adb uninstall "$PACKAGE_NAME" || true
adb install -r "$APK_PATH"

echo "[4/6] 唤醒 App 并进行 UI 采样截图..."
mkdir -p "$OUTPUT_DIR"
adb shell am force-stop "$PACKAGE_NAME"
adb shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1
# Allow the offline asset importer to finish before sampling the home screen.
for attempt in $(seq 1 45); do
    adb shell uiautomator dump /sdcard/autodev_ready.xml >/dev/null 2>&1 || true
    if adb shell cat /sdcard/autodev_ready.xml 2>/dev/null | grep -q "精选条目"; then
        break
    fi
    sleep 1
done

# 1. 首页截图
adb shell screencap -p /sdcard/01_home.png
adb pull /sdcard/01_home.png "$OUTPUT_DIR/01_home.png"

# 2. 点击首页首张正文卡片，进入详情页
adb shell input tap 160 440
sleep 2
adb shell uiautomator dump /sdcard/autodev_detail.xml >/dev/null 2>&1 || true
if ! adb shell cat /sdcard/autodev_detail.xml 2>/dev/null | grep -q "原文"; then
    adb shell input tap 160 440
    sleep 2
fi
adb shell screencap -p /sdcard/02_detail.png
adb pull /sdcard/02_detail.png "$OUTPUT_DIR/02_detail.png"

# 3. 滑动阅读，滚动到字号控制和正文段落
adb shell input swipe 160 580 160 180 400
sleep 2
adb shell screencap -p /sdcard/03_scroll.png
adb pull /sdcard/03_scroll.png "$OUTPUT_DIR/03_scroll.png"

# 4. Dump 界面 Node Tree
adb shell uiautomator dump /sdcard/window_dump.xml
adb pull /sdcard/window_dump.xml "$OUTPUT_DIR/window_dump.xml"

echo "[5/6] 检查 Runtime 崩溃日志..."
CRASH_LOG=$(adb logcat -d *:E | grep -iE "Fatal|AndroidRuntime|NullPointer" || true)
if [ -n "$CRASH_LOG" ]; then
    echo "⚠️ 运行时崩溃/异常日志:"
    echo "$CRASH_LOG" > "$OUTPUT_DIR/crash.log"
    echo "$CRASH_LOG"
else
    printf 'No Fatal, AndroidRuntime, or NullPointer errors detected.\n' > "$OUTPUT_DIR/crash.log"
    echo "✅ 运行过程未发现 Crash 报错。"
fi

echo "[6/6] 导出最终构建产物..."
mkdir -p build/outputs/
cp "$APK_PATH" build/outputs/app-autodev.apk
echo "================================================="
echo "🎉 运行与截图已存至: $OUTPUT_DIR"
echo "📦 可安装的 APK 产物已导出至: build/outputs/app-autodev.apk"
echo "================================================="
