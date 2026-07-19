# Regenerates docs/screenshots/*.png by driving the debug build's screenshot harness.
#
# Each scenario name is passed to the app via `am start ... --es shot <name>` (see
# MainActivity.applyShot); the app drops straight into that state, we screencap it and pull
# the PNG. No manual tapping, no network - leaderboard/definition dialogs use stub data.
#
# Usage:
#   .\update-screenshots.ps1                 # build + install + capture every shot
#   .\update-screenshots.ps1 -Only midgame   # capture one (or a few) by name, skip build
#   .\update-screenshots.ps1 -SkipBuild      # capture all, reuse the installed debug build
[CmdletBinding()]
param(
    [string[]]$Only,        # capture only these scenario names
    [switch]$SkipBuild,     # don't rebuild/reinstall first
    [int]$SettleMs = 1400   # pause after launch before the screencap (dialog/animation settle)
)

# 'Continue' (not 'Stop'): adb writes progress to stderr, which under 'Stop' PowerShell 5.1
# mistakes for a terminating error. Failures are caught via explicit throws / $LASTEXITCODE.
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$pkg  = 'com.hrbons.wordguesser.debug'
$act  = "$pkg/com.hrbons.wordguesser.MainActivity"
$outDir = Join-Path $root 'docs\screenshots'

# --- locate adb from the SDK path in local.properties ---
$sdk = (Get-Content (Join-Path $root 'local.properties') |
        Where-Object { $_ -match '^sdk\.dir=' }) -replace '^sdk\.dir=', ''
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

# name -> output filename (numbering matches the existing docs/screenshots set)
$shots = [ordered]@{
    'empty-board'         = '01-empty-board.png'
    'midgame'             = '02-midgame.png'
    'win'                 = '03-win.png'
    'loss-reveal'         = '04-loss-reveal.png'
    'hard-mode'           = '05-hard-mode.png'
    'strict-reject'       = '06-strict-reject.png'
    'length-4'            = '07-length-4.png'
    'length-8'            = '08-length-8.png'
    'length-8-extrakeys'  = '08b-length-8-extrakeys.png'
    'daily-fresh'         = '09-daily-fresh.png'
    'daily-won-badge'     = '10-daily-won-badge.png'
    'daily-lost-badge'    = '11-daily-lost-badge.png'
    'highcontrast'        = '12-highcontrast.png'
    'timed-bar'           = '13-timed-bar.png'
    'timed-solved'        = '14-timed-solved.png'
    'duel-your-turn'      = '16-duel-your-turn.png'
    'duel-comp-turn'      = '17-duel-comp-turn.png'
    'duel-online-turn'    = '17b-duel-online-turn.png'
    'duel-online-waiting' = '17c-duel-online-waiting.png'
    'modal-newgame'       = '18-modal-newgame.png'
    'modal-language'      = '19-modal-language.png'
    'modal-settings'      = '20-modal-settings.png'
    'modal-stats'         = '21-modal-stats.png'
    'modal-stats-reset'   = '22-modal-stats-reset.png'
    'modal-sources'       = '23-modal-sources.png'
    'modal-definition'    = '24-modal-definition.png'
    'modal-lookup'        = '25-modal-lookup.png'
    'modal-loading'       = '26-modal-loading.png'
    'modal-timeup'        = '27-modal-timeup.png'
    'modal-timed-submit'  = '28-modal-timed-submit.png'
    'modal-timed-board'   = '29-modal-timed-board.png'
    'modal-duel-result'   = '30-modal-duel-result.png'
    'modal-daily-submit'  = '31-modal-daily-submit.png'
    'modal-daily-failed'  = '32-modal-daily-failed.png'
    'modal-daily-played'  = '33-modal-daily-played.png'
    'modal-daily-board'   = '34-modal-daily-board.png'
    'modal-howto'         = '35-modal-howto.png'
}

if (-not $SkipBuild -and -not $Only) {
    Write-Host '== building debug APK ==' -ForegroundColor Cyan
    $env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'
    & (Join-Path $root 'gradlew.bat') :app:assembleDebug -q
    if ($LASTEXITCODE -ne 0) { throw 'gradle build failed' }
    Write-Host '== installing ==' -ForegroundColor Cyan
    & $adb install -r (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk') | Out-Null
}

# keep the screen awake for the whole run
& $adb shell input keyevent KEYCODE_WAKEUP | Out-Null
& $adb shell svc power stayon true | Out-Null

$names = if ($Only) { $Only } else { $shots.Keys }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

foreach ($name in $names) {
    $file = $shots[$name]
    if (-not $file) { Write-Warning "unknown shot '$name' - skipping"; continue }
    & $adb shell am force-stop $pkg 2>$null | Out-Null
    & $adb shell am start -n $act --es shot $name 2>$null | Out-Null
    Start-Sleep -Milliseconds $SettleMs
    & $adb shell screencap -p /sdcard/_shot.png 2>$null | Out-Null
    & $adb pull /sdcard/_shot.png (Join-Path $outDir $file) 2>$null | Out-Null
    Write-Host ("  {0,-22} -> {1}" -f $name, $file) -ForegroundColor Green
}

& $adb shell rm -f /sdcard/_shot.png | Out-Null
& $adb shell svc power stayon false | Out-Null
Write-Host "done -> $outDir" -ForegroundColor Cyan
