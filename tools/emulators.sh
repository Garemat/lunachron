#!/usr/bin/env bash
# Launch lunachron_a and lunachron_b emulators for local testing.
# No extra flags — additional GPU/accel/window flags cause segfaults on this machine.

EMULATOR="$HOME/Android/Sdk/emulator/emulator"

echo "Starting lunachron_a (emulator-5554)..."
"$EMULATOR" -avd lunachron_a -port 5554 &

echo "Starting lunachron_b (emulator-5556)..."
"$EMULATOR" -avd lunachron_b -port 5556 &

echo "Waiting for both to boot..."
ADB="$HOME/Android/Sdk/platform-tools/adb"
for serial in emulator-5554 emulator-5556; do
    until [[ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
        sleep 5
    done
    echo "$serial ready"
done
echo "Both emulators up. Run: ./gradlew app:installGithubDebug"
