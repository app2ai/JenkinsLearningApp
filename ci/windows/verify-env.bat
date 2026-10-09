@echo off
rem ===========================================================================
rem verify-env.bat - fail fast with a clear message if the Jenkins node (or a
rem developer machine) is missing Java or the Android SDK.
rem Called by the "Verify environment" stage of the Jenkinsfile. Also runnable
rem locally from the project root:  ci\windows\verify-env.bat
rem Prints paths and versions only. Never prints environment values that could be secrets.
rem ===========================================================================
setlocal EnableExtensions EnableDelayedExpansion
set "FAILED=0"

echo === Java ===
if defined JAVA_HOME (
    echo JAVA_HOME=!JAVA_HOME!
) else (
    echo WARNING: JAVA_HOME is not set. Gradle will use java.exe from PATH.
)
java -version 2>&1
if errorlevel 1 (
    echo ERROR: java was not found. Install JDK 21 or 25 and set JAVA_HOME for the Jenkins node.
    set "FAILED=1"
)

echo.
echo === Android SDK ===
set "SDK=%ANDROID_HOME%"
if not defined SDK set "SDK=%ANDROID_SDK_ROOT%"
if not defined SDK (
    echo ERROR: ANDROID_HOME is not set.
    echo        Jenkins: Manage Jenkins - Nodes - Built-In Node - Configure - Environment variables.
    echo        Add ANDROID_HOME pointing at the SDK folder, e.g. C:\Android\Sdk
    set "FAILED=1"
    goto :summary
)
echo Android SDK: !SDK!
if not exist "!SDK!\" (
    echo ERROR: the folder in ANDROID_HOME does not exist.
    set "FAILED=1"
    goto :summary
)
if exist "!SDK!\platforms\android-37" (
    echo Found platforms\android-37 - matches compileSdk 37.
) else (
    echo WARNING: platforms\android-37 not found. AGP downloads it only if SDK licenses are accepted.
    echo          Install it with: sdkmanager "platforms;android-37"
)
if exist "!SDK!\build-tools" (
    echo Installed build-tools:
    dir /b "!SDK!\build-tools"
) else (
    echo WARNING: no build-tools folder. Install with: sdkmanager "build-tools;36.0.0"
)
if not exist "!SDK!\licenses" (
    echo WARNING: no licenses folder. Run: sdkmanager --licenses   as the Jenkins service user.
)

:summary
echo.
echo === Workspace ===
if exist local.properties (
    echo NOTE: local.properties exists in this workspace. CI normally relies on ANDROID_HOME instead.
) else (
    echo local.properties not present - expected on CI. Gradle uses ANDROID_HOME.
)

if "!FAILED!"=="1" (
    echo.
    echo Environment check FAILED. See docs\06-troubleshooting.md
    exit /b 1
)
echo.
echo Environment check passed.
exit /b 0
