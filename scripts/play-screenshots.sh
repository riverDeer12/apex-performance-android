#!/usr/bin/env bash
# Takes the Google Play phone screenshots on a running emulator (adb):
# installs the debug app and its tests, runs PlayStoreScreenshots with the
# demo data and pulls the PNGs to ./play-screenshots/<language>/NN_name.png.
# Used by .github/workflows/play-screenshots.yml; the APKs are built first:
#   ./gradlew assembleDebug assembleDebugAndroidTest
# LANGUAGES (e.g. "en" or "hr,en") takes only some of the languages.
set -uo pipefail

package=software.rdd.apexperformance
device_dir=/sdcard/Android/data/$package/files/play-screenshots

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
adb shell wm size
adb shell wm density

# Gesture navigation, like current phones.
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural || true

# Clean status bar: 9:41, full battery and signal, no notification icons.
adb shell settings put global sysui_demo_allowed 1
echo "Demo mode allowed: $(adb shell settings get global sysui_demo_allowed)"
# SystemUI starts showing demo mode when this is on (what "enter" sets).
adb shell settings put global sysui_tuner_demo_on 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@"; }
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false
demo network -e wifi hide
demo network -e mobile show -e datatype none -e level 4 -e fully true
demo notifications -e visible false

adb install -r -g app/build/outputs/apk/debug/app-debug.apk || exit 1
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || exit 1
adb shell rm -rf "$device_dir"
adb logcat -c

arguments=(-e class "$package.PlayStoreScreenshots")
if [ -n "${LANGUAGES:-}" ]; then arguments+=(-e languages "$LANGUAGES"); fi
adb shell am instrument -w -r "${arguments[@]}" "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee instrument.log

demo exit

rm -rf play-screenshots
adb pull "$device_dir" play-screenshots || true
adb logcat -d > logcat.txt || true

grep "Demo mode" logcat.txt | head -12 || true

# am instrument exits with 0 even when the test fails.
if ! grep -q "^OK (" instrument.log; then
  echo "::error::Screenshot test failed, see the instrumentation output above."
  # The test's own log, with the screen's semantics tree when a wait timed out.
  grep -E "PlayStoreScreenshots|DemoAPI|AndroidRuntime|ApiClient" logcat.txt | tail -400 || true
  exit 1
fi
