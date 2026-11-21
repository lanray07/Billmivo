param(
    [Parameter(Mandatory)]
    [ValidateSet('en-GB','fr-FR','es-ES','de-DE','it-IT','pt-BR','nl-NL','sv-SE','pl-PL','tr-TR')]
    [string] $Locale,
    # Retained for callers of the earlier UI Automator capture command.
    [string] $CurrentLanguage = 'en',
    [string] $Serial,
    [switch] $ChangesOnly
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' } else { 'adb.exe' }
if (!$Serial) {
    $hardware = @(& $adb devices -l | Where-Object { $_ -match '\sdevice\s.*\bmodel:KFTRWI\b' })
    if ($hardware.Count -ne 1) { throw 'Connect one Fire HD 10 or supply its ADB serial.' }
    $Serial = ($hardware[0] -split '\s+')[0]
}
$output = Join-Path $root "deliverables/store-assets/amazon-raw/$Locale"
New-Item -ItemType Directory -Force $output | Out-Null
$package = 'com.billmivo.app.debug'
& $adb -s $Serial shell input keyevent KEYCODE_WAKEUP
& $adb -s $Serial shell wm dismiss-keyguard
& $adb -s $Serial shell am force-stop $package
$changes = $ChangesOnly.IsPresent.ToString().ToLowerInvariant()
try {
    $result = & $adb -s $Serial shell am instrument -w -e class com.billmivo.app.StoreScreenshotCapture -e demo true -e previewLanguage $Locale -e changesOnly $changes "$package.test/androidx.test.runner.AndroidJUnitRunner"
    $instrumentExit = $LASTEXITCODE
    $result | Write-Output
    if ($instrumentExit -ne 0 -or ($result -join "`n") -notmatch 'OK \(1 test\)') {
        throw "Instrumented capture failed for $Locale."
    }
    $scenes = if ($ChangesOnly) { @('changes') } else {
        @('forecast','week','reminders','spending','changes','voice','sharing','calendar')
    }
    foreach ($scene in $scenes) {
        & $adb -s $Serial pull "/sdcard/Android/data/$package/files/store-captures/$Locale/$scene.png" (Join-Path $output "$scene.png")
        if ($LASTEXITCODE -ne 0) { throw "Could not retrieve $Locale/$scene.png." }
    }
    if (!$ChangesOnly) {
        $hashes = @($scenes | ForEach-Object {
            (Get-FileHash (Join-Path $output "${_}.png") -Algorithm SHA256).Hash
        } | Sort-Object -Unique)
        if ($hashes.Count -ne $scenes.Count) { throw "Duplicate raw screenshot scenes for $Locale." }
    }
    python (Join-Path $PSScriptRoot 'store-art.py') --raw $output --locale $Locale
    if ($LASTEXITCODE -ne 0) { throw 'Artwork composition failed.' }
} finally {
    & $adb -s $Serial shell am force-stop "$package.test"
}
