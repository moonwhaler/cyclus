#!/usr/bin/env bash
# Build, install and launch the Cyclus debug app.
#
#   tools/deploy.sh            -> local emulator (AVD "cyclus-pixel9pro"), started if not running   [default]
#   tools/deploy.sh --phone    -> physical phone over wireless adb
#   ADB_TARGET=<serial> tools/deploy.sh   -> exactly that device
#
# Phone selection: $ADB_TARGET, then .deploy.local (one line, gitignored), then the single connected
# physical device, then mDNS discovery (_adb-tls-connect) + adb connect.
# One-time phone pairing (Developer options > Wireless debugging > Pair device with pairing code):
#   adb pair <ip>:<pair-port> <code>
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="com.nordinary.cyclus.debug"
ACTIVITY="com.nordinary.cyclus.MainActivity"
VARIANT_TASK=":androidApp:installDebug"

die() { echo "deploy: $*" >&2; exit 1; }
command -v adb >/dev/null || die "adb not found on PATH"

connected() { adb devices | awk 'NR>1 && $2=="device" {print $1}'; }

AVD="${CYCLUS_AVD:-cyclus-pixel9pro}"
mode="emulator"
[[ "${1:-}" == "--phone" ]] && mode="phone"

emulators() { connected | grep '^emulator-' || true; }
physical() { connected | grep -v '^emulator-' || true; }

start_emulator() {
  local emu="${ANDROID_HOME:-$HOME/Android/Sdk}/emulator/emulator"
  [[ -x "$emu" ]] || die "emulator binary not found at $emu"
  echo "deploy: starting emulator $AVD..."
  # The emulator needs the desktop session; fall back to KDE/XWayland defaults when run from a plain shell.
  local xauth; xauth="$(ls /run/user/$(id -u)/xauth_* 2>/dev/null | head -n1 || true)"
  DISPLAY="${DISPLAY:-:0}" XAUTHORITY="${XAUTHORITY:-$xauth}" \
    nohup "$emu" -avd "$AVD" -no-snapshot-save -no-boot-anim -gpu host >/tmp/cyclus-emulator.log 2>&1 &
  adb wait-for-device
  local tries=0
  until [[ "$(adb -s "$(emulators | head -n1)" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    (( tries++ < 120 )) || die "emulator did not boot (see /tmp/cyclus-emulator.log)"
    sleep 2
  done
}

target="${ADB_TARGET:-}"
if [[ -z "$target" && "$mode" == "emulator" ]]; then
  [[ -n "$(emulators)" ]] || start_emulator
  target="$(emulators | head -n1)"
elif [[ -z "$target" ]]; then
  [[ -f "$ROOT/.deploy.local" ]] && target="$(head -n1 "$ROOT/.deploy.local" | tr -d '[:space:]')"
  if [[ -z "$target" ]]; then
    mapfile -t devs < <(physical)
    if (( ${#devs[@]} == 0 )); then
      echo "deploy: no phone connected, trying mDNS discovery..."
      while read -r addr; do
        [[ -n "$addr" ]] && adb connect "$addr" >/dev/null 2>&1 || true
      done < <(adb mdns services | awk '/_adb-tls-connect/ {print $3}')
      sleep 1
      mapfile -t devs < <(physical)
    fi
    (( ${#devs[@]} > 0 )) || die "no phone found. Pair once: adb pair <ip>:<pair-port> <code>"
    (( ${#devs[@]} == 1 )) || die "several phones connected (${devs[*]}); set ADB_TARGET"
    target="${devs[0]}"
  fi
fi
if ! connected | grep -qx "$target"; then
  [[ "$target" == *:* ]] && adb connect "$target" >/dev/null 2>&1 || true
fi
connected | grep -qx "$target" || die "target $target is not connected"

export ANDROID_SERIAL="$target"
model="$(adb shell getprop ro.product.model | tr -d '\r')"
echo "deploy: target $target ($model)"

"$ROOT/gradlew" -p "$ROOT" "$VARIANT_TASK" --console=plain -q
adb shell am start -n "$APP_ID/$ACTIVITY" >/dev/null
echo "deploy: launched $APP_ID on $model"
