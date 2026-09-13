# WLO dev shortcuts. `just` = build, `just drop` = dogfood APK via AirDrop,
# `just run` = boot emulator + install + launch.

sdk := `echo "${ANDROID_HOME:-$HOME/Library/Android/sdk}"`
adb := sdk + "/platform-tools/adb"
emulator := sdk + "/emulator/emulator"

avd := "wlo-api29"
pkg := "app.wlo"

apk_debug := "app/build/outputs/apk/debug/app-debug.apk"
apk_release := "app/build/outputs/apk/release/app-release.apk"

default: build

# Build the debug APK
build:
    ./gradlew :app:assembleDebug

# Build the dogfood (release) APK — signed with ~/.android/wlo-release.keystore
build-release:
    ./gradlew :app:assembleRelease

# Build the dogfood APK and AirDrop it to the phone
drop: build-release
    airdrop {{absolute_path(apk_release)}}

# Build, boot the wlo-api29 emulator (headed), install and launch the app
run: build
    #!/usr/bin/env bash
    set -euo pipefail
    adb="{{adb}}"
    emulator="{{emulator}}"
    avd="{{avd}}"
    sdk="{{sdk}}"

    # First use: create the AVD (arm64 image on Apple Silicon)
    if ! "$emulator" -list-avds | grep -qx "$avd"; then
        case "$(uname -m)" in
            arm64) image="system-images;android-29;google_apis;arm64-v8a" ;;
            *)     image="system-images;android-29;google_apis;x86_64" ;;
        esac
        echo "AVD '$avd' not found — installing $image and creating it…"
        sdkmanager="$sdk/cmdline-tools/latest/bin/sdkmanager"
        [ -x "$sdkmanager" ] || sdkmanager="$sdk/tools/bin/sdkmanager"
        yes | "$sdkmanager" "$image" >/dev/null
        avdmanager="$sdk/cmdline-tools/latest/bin/avdmanager"
        [ -x "$avdmanager" ] || avdmanager="$sdk/tools/bin/avdmanager"
        echo no | "$avdmanager" create avd -n "$avd" -k "$image" -d pixel_4
    fi

    # A headless instance can't show a window — stop it, restart headed below
    headless="$(ps -Ao pid=,command= | awk -v avd="$avd" \
        '$0 ~ "qemu-system" && $0 ~ ("-avd " avd) && (/-no-window/ || /-headless/) {print $1}')"
    if [ -n "$headless" ]; then
        echo "Stopping headless emulator…"
        kill $headless 2>/dev/null || true
        sleep 2
        kill -9 $headless 2>/dev/null || true
    fi

    # Boot headed unless an instance is already up
    if ! pgrep -f "qemu-system.*-avd $avd" >/dev/null; then
        echo "Booting emulator '$avd'…"
        nohup "$emulator" -avd "$avd" >/tmp/wlo-emulator.log 2>&1 &
    fi

    echo "Waiting for boot…"
    "$adb" -e wait-for-device
    for i in $(seq 1 180); do
        [ "$("$adb" -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
        [ "$i" = 180 ] && { echo "Emulator boot timed out (log: /tmp/wlo-emulator.log)"; exit 1; }
        sleep 1
    done

    "$adb" -e install -r {{apk_debug}}
    # Resolve the launcher activity at runtime (the activity lives under the
    # app.wlo.app namespace, and `monkey` is broken on the api29 image).
    launcher="$("$adb" -e shell cmd package resolve-activity --brief \
        -c android.intent.category.LAUNCHER {{pkg}} | tail -1 | tr -d '\r')"
    "$adb" -e shell am start -n "$launcher"

# Clear app data on the emulator (resets the seeded demo data) and relaunch
fresh:
    {{adb}} -e shell pm clear {{pkg}}
    launcher="$({{adb}} -e shell cmd package resolve-activity --brief \
        -c android.intent.category.LAUNCHER {{pkg}} | tail -1 | tr -d '\r')"
    {{adb}} -e shell am start -n "$launcher"
