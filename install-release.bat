@echo off
rem Build the SIGNED release APK (R8 + shrink) and sideload it onto a USB-connected device.
rem Requires the release keystore configured in local.properties (RELEASE_STORE_FILE etc.).
setlocal

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
set "ADB=%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=adb"

cd /d "%~dp0"

echo === Building signed release APK ===
call gradlew.bat assembleRelease --console=plain
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

echo.
echo === Installing on device ===
rem A debug-signed build already on the device has a different signature, so remove it first.
"%ADB%" uninstall com.hrbons.wordguesser
"%ADB%" install "app\build\outputs\apk\release\app-release.apk"
