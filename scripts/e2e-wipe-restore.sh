#!/usr/bin/env bash
#
# WLO Milestone 6 (WLO-0028 PART A) — the wipe-restore E2E, the F13 §1
# acceptance: "a fresh install restores 100% of data from the backup".
#
#   1. Seed + backup:      M6SeederBackupTest seeds M2–M5-shaped data through
#                          the REAL repositories and runs a REAL encrypted
#                          backup into the emulator's Documents folder via the
#                          real SAF picker; the bytes are mirrored to
#                          files/e2e/ and pulled to the HOST.
#   2. THE WIPE:           `adb uninstall app.wlo` — the device forgets
#                          everything (app data, SAF grants, keystore-wrapped
#                          auto-backup key, the mirror). Only the HOST copy of
#                          the backup file survives — a lost phone in miniature.
#   3. Restore + assert:   M6RestoreAssertTest restores from the surviving
#                          backup and asserts logical state EQUALITY against
#                          the seeded spec (counts, spot values, consent
#                          ledger chain, recomputed projections).
#
# Plus the M6 hostile/opacity/window tests. The script exits NON-ZERO on any
# step failure.
#
# Usage: scripts/e2e-wipe-restore.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Locate adb (sdk path lives in local.properties; ADB env var wins).
ADB="${ADB:-adb}"
if ! command -v "$ADB" >/dev/null 2>&1; then
  SDK="$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties" | tr -d '\r')"
  [ -n "$SDK" ] && ADB="$SDK/platform-tools/adb"
fi
command -v "$ADB" >/dev/null 2>&1 || { echo "adb not found" >&2; exit 1; }
PKG="app.wlo"
OUT="$ROOT/build/e2e"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_PKG="$PKG.test"
BACKUP_NAME="wlo_backup_e2e.wlo"

cd "$ROOT"
mkdir -p "$OUT"

say() { printf '\n=== %s\n' "$1"; }
fail() { printf '\n!!! FAILED: %s\n' "$1" >&2; exit 1; }

run_instrumented() {
  local label="$1"; shift
  say "$label"
  # `am instrument -w` prints "OK (N tests)" on success, "FAILURES!!!" otherwise.
  # clearPackageData mirrors the gradle-connected default: every test starts
  # from a cold install (the onboarding/money-test acceptance semantics).
  local output
  if ! output="$("$ADB" shell am instrument -w -e clearPackageData true "$@" "$TEST_PKG/$RUNNER" 2>&1)"; then
    printf '%s\n' "$output" | tail -40
    fail "$label (instrumentation exit)"
  fi
  printf '%s\n' "$output" | tail -5
  if grep -q "FAILURES" <<<"$output" || ! grep -q "OK (" <<<"$output"; then
    printf '%s\n' "$output" | tail -40
    fail "$label"
  fi
}

# ---------------------------------------------------------------- leg 0: build
say "building + installing (debug + androidTest)"
./gradlew --console=plain -q :app:installDebug :app:installDebugAndroidTest \
  || fail "gradle install"

# ------------------------------------------------------- leg 1: seed + backup
# A fresh slate for the seed (a previous failed run may have left rows).
"$ADB" shell pm clear "$PKG" >/dev/null || true
# MediaProvider drops an EMPTY /sdcard/Documents on clears — the SAF tree the
# seeder picks must exist or the backup write fails.
"$ADB" shell mkdir -p /sdcard/Documents >/dev/null || true
run_instrumented "leg 1: seed M2–M5 data + encrypted backup into SAF Documents" \
  -e class app.wlo.app.M6SeederBackupTest

say "pulling the surviving backup to the host"
mkdir -p "$OUT"
"$ADB" shell "run-as $PKG sh -c 'ls files/e2e/'" | grep -q backup-export.wlo \
  || fail "seeder mirror missing (run-as pull)"
"$ADB" shell "run-as $PKG cat files/e2e/backup-export.wlo" > "$OUT/$BACKUP_NAME" \
  || fail "run-as pull"
[ -s "$OUT/$BACKUP_NAME" ] || fail "pulled backup is empty"
echo "host copy: $OUT/$BACKUP_NAME ($(wc -c < "$OUT/$BACKUP_NAME" | tr -d ' ') bytes)"

# ------------------------------------------------------------- leg 2: THE WIPE
say "leg 2: adb uninstall $PKG (the device forgets everything)"
"$ADB" uninstall "$PKG" || fail "adb uninstall"

say "reinstalling a factory-fresh app"
./gradlew --console=plain -q :app:installDebug :app:installDebugAndroidTest \
  || fail "gradle reinstall"

# ------------------------------------------------- leg 3: restore + assertions
# The surviving bytes go back in as an instrumentation arg (base64 survives
# the orchestrator's clearPackageData, unlike anything pushed into app data).
say "leg 3: restore from the surviving backup + assert logical equality"
B64="$(base64 < "$OUT/$BACKUP_NAME" | tr -d '\n')"
[ -n "$B64" ] || fail "base64 of the backup"
run_instrumented "leg 3" \
  -e class app.wlo.app.M6RestoreAssertTest -e backupBase64 "$B64"

# ------------------------------------------- leg 4: hostiles, vault, FLAG_SECURE
# Fresh slate again (leg 3 restored data; the hostile test seeds its own).
"$ADB" shell pm clear "$PKG" >/dev/null || true
run_instrumented "leg 4: hostile backups rejected, vault blobs opaque, FLAG_SECURE route flips" \
  -e class app.wlo.app.M6HostileVaultTest,app.wlo.app.M6FlagSecureWindowTest

say "E2E PASS: wipe → restore → logical state equals the seeded spec"
