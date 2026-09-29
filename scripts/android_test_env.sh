#!/usr/bin/env bash
# Puts the connected emulator into the standard Evolune androidTest environment:
# API 35 image, zh-CN locale, 1080x2400 screen, animations off.
# See docs/evolune/TESTING.md. Used by CI; also usable locally with a running emulator.
set -euo pipefail

LOCALE="${EVOLUNE_TEST_LOCALE:-zh-CN}"
SIZE="${EVOLUNE_TEST_SIZE:-1080x2400}"

adb wait-for-device
adb root >/dev/null 2>&1 || true
adb wait-for-device

adb shell wm size "$SIZE"
adb shell wm density 420
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

# Changing the persisted locale requires a framework restart on emulator images.
adb shell "setprop persist.sys.locale $LOCALE; setprop ctl.restart zygote"
sleep 5
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
# Give SystemUI a moment to settle before instrumentation starts.
sleep 10
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true

echo "locale : $(adb shell getprop persist.sys.locale | tr -d '\r')"
echo "size   : $(adb shell wm size | tr -d '\r')"
echo "sdk    : $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
