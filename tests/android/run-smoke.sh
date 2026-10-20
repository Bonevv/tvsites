#!/usr/bin/env bash
# Dedicated Android emulator only: installs debug APKs and seeds disposable fixture settings.
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${ANDROID_HOME:?Set ANDROID_HOME to the SDK}"
serial=${ANDROID_SERIAL:-emulator-5554}
[[ "$serial" == emulator-* ]] || { echo 'Use a disposable emulator, not a personal device.' >&2; exit 1; }
adb_target=("$ANDROID_HOME/platform-tools/adb" -s "$serial")
[[ "$(${adb_target[@]} shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || exit 1
work=$(mktemp -d)
artifacts=${TVSITES_TEST_ARTIFACTS:-/tmp/tvsites-smoke-artifacts}
mkdir -p "$work/classes" "$work/dex" "$work/media" "$artifacts"
server_pid=
cleanup() {
  if [ -n "$server_pid" ]; then kill "$server_pid" 2>/dev/null || true; fi
  "${adb_target[@]}" reverse --remove tcp:8765 >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT
curl -fLsS https://iptv-epg.org/files/epg-bg.xml.gz -o "$work/epg.xml.gz"
ffmpeg -v error -f lavfi -i testsrc=size=320x180:rate=15 -f lavfi -i sine=frequency=440:sample_rate=44100 -t 30 -c:v libx264 -threads 1 -preset ultrafast -pix_fmt yuv420p -g 30 -c:a aac -b:a 64k -f hls -hls_time 2 -hls_list_size 0 -hls_flags omit_endlist "$work/media/live.m3u8"
printf '<!doctype html><title>Local test</title><h1>TV Sites browser test</h1>' > "$work/media/site.html"
python3 -m http.server 8765 --bind 127.0.0.1 --directory "$work/media" > "$artifacts/http.log" 2>&1 &
server_pid=$!
javac -source 8 -target 8 -cp "$ANDROID_HOME/platforms/android-34/android.jar" -d "$work/classes" tests/android/Smoke.java
"$ANDROID_HOME/build-tools/34.0.0/d8" --min-api 21 --lib "$ANDROID_HOME/platforms/android-34/android.jar" --output "$work/dex" "$work/classes/com/example/tvsites/smoke/Smoke.class"
"$ANDROID_HOME/build-tools/34.0.0/aapt2" link -I "$ANDROID_HOME/platforms/android-34/android.jar" --manifest tests/android/AndroidManifest.xml -o "$work/smoke.apk"
python3 - "$work" <<'PY'
import pathlib, sys, zipfile
p=pathlib.Path(sys.argv[1])
with zipfile.ZipFile(p/'smoke.apk','a') as z: z.write(p/'dex/classes.dex','classes.dex')
PY
"$ANDROID_HOME/build-tools/34.0.0/apksigner" sign --ks "${ANDROID_USER_HOME:-$HOME/.android}/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --v4-signing-enabled false "$work/smoke.apk"
"${adb_target[@]}" install --no-incremental -r app/build/outputs/apk/debug/app-debug.apk
"${adb_target[@]}" install --no-incremental -r "$work/smoke.apk"
"${adb_target[@]}" push "$work/epg.xml.gz" /data/local/tmp/tvsites-epg.xml.gz
"${adb_target[@]}" reverse tcp:8765 tcp:8765
"${adb_target[@]}" shell am instrument -w com.example.tvsites.smoke/com.example.tvsites.smoke.Smoke > "$artifacts/result.txt"
cat "$artifacts/result.txt"
rg -q 'Passed [0-9]+ Android workflow checks; HLS playback passed' "$artifacts/result.txt"
"${adb_target[@]}" exec-out run-as com.example.tvsites cat cache/tv-overview.png > "$artifacts/tv-overview.png"
