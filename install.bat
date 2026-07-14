@echo off
rem Build the debug APK and sideload it onto a USB-connected device.
rem Reuses the toolchain set up for this project (JDK 17 + reused Android SDK).
setlocal

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
set "ADB=%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=adb"

cd /d "%~dp0"

echo === Building debug APK ===
call gradlew.bat assembleDebug --console=plain
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

echo.
echo === Installing on device ===
"%ADB%" install -r "app\build\outputs\apk\debug\app-debug.apk"
