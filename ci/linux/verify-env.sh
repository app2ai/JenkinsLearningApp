#!/usr/bin/env bash
# verify-env.sh - Linux/macOS equivalent of ci/windows/verify-env.bat.
# Used by ci/linux/Jenkinsfile. Prints paths and versions only, never secrets.
set -u
failed=0

echo "=== Java ==="
if [ -n "${JAVA_HOME:-}" ]; then echo "JAVA_HOME=$JAVA_HOME"; else echo "WARNING: JAVA_HOME is not set; using java from PATH."; fi
if ! java -version 2>&1; then
  echo "ERROR: java not found. Install JDK 21 or 25 and set JAVA_HOME for the Jenkins agent."
  failed=1
fi

echo
echo "=== Android SDK ==="
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ]; then
  echo "ERROR: ANDROID_HOME is not set (configure it on the Jenkins node/agent)."
  failed=1
elif [ ! -d "$sdk" ]; then
  echo "ERROR: ANDROID_HOME points to a missing folder: $sdk"
  failed=1
else
  echo "Android SDK: $sdk"
  if [ -d "$sdk/platforms/android-37" ]; then echo "Found platforms/android-37 (compileSdk 37)."
  else echo "WARNING: platforms/android-37 missing. Install: sdkmanager \"platforms;android-37\""; fi
  if [ -d "$sdk/build-tools" ]; then echo "Installed build-tools:"; ls -1 "$sdk/build-tools"
  else echo "WARNING: no build-tools installed."; fi
  [ -d "$sdk/licenses" ] || echo "WARNING: no licenses folder. Run: yes | sdkmanager --licenses"
fi

echo
echo "=== Workspace ==="
if [ -f local.properties ]; then echo "NOTE: local.properties exists here; CI normally relies on ANDROID_HOME."
else echo "local.properties not present (expected on CI)."; fi

if [ "$failed" -ne 0 ]; then echo; echo "Environment check FAILED. See docs/06-troubleshooting.md"; exit 1; fi
echo; echo "Environment check passed."
