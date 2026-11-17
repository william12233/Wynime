#!/usr/bin/env bash
set -euo pipefail

api=$1
package=com.wynime.app.debug2
activity="$package/com.wynime.android.activity.MainActivity"
apk=app/android/build/outputs/apk/default/debug/android-default-x86_64-debug.apk

adb install -r -g "$apk"
trap 'adb shell am force-stop "$package"' EXIT
start=$(adb shell am start -W -n "$activity" | tr -d '\r')
printf '%s\n' "$start"
grep -Fxq 'Status: ok' <<< "$start"

for attempt in {1..15}; do
  activities=$(adb shell dumpsys activity activities)
  if adb shell pidof "$package" > /dev/null && grep -F 'ResumedActivity' <<< "$activities" | grep -Fq "$package/"; then
    adb exec-out screencap -p > "startup-api$api-x86_64.png"
    exit 0
  fi
  sleep 2
done

adb exec-out screencap -p > "startup-api$api-x86_64.png"
echo "Wynime did not reach a resumed activity on API $api." >&2
exit 1
