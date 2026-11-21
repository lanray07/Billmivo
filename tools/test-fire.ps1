param(
    [string] $Serial,
    [string] $DebugApk = 'app/build/outputs/apk/debug/app-debug.apk',
    [string] $TestApk = 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
    [string] $LogDir = 'deliverables/android/build-logs'
)

$ErrorActionPreference = 'Stop'
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
$adb = if ($sdk) { Join-Path $sdk 'platform-tools/adb.exe' } else { 'adb.exe' }
if (!$Serial) {
    $hardware = @(& $adb devices -l | Where-Object { $_ -match '\sdevice\s.*\bmodel:KFTRWI\b' })
    if ($hardware.Count -ne 1) { throw 'Connect one KFTRWI Fire HD 10 or supply its ADB serial.' }
    $Serial = ($hardware[0] -split '\s+')[0]
}
New-Item -ItemType Directory -Force $LogDir | Out-Null
foreach ($apk in @($DebugApk, $TestApk)) {
    $result = & $adb -s $Serial install -r $apk
    $result | Write-Output
    if (($result -join ' ') -notmatch 'Success') { throw "Installation failed: $apk" }
}
& $adb -s $Serial shell am force-stop com.billmivo.app.debug
$log = Join-Path $LogDir 'fire-instrumentation.txt'
# Store previews replace demo records and run separately from the regression suite.
$run = & $adb -s $Serial shell am instrument -w -e notClass 'com.billmivo.app.StorePreviewFixture,com.billmivo.app.StoreScreenshotCapture' com.billmivo.app.debug.test/androidx.test.runner.AndroidJUnitRunner
$instrumentExit = $LASTEXITCODE
$run | Tee-Object $log
$passed = $run -match '^OK \(([1-9][0-9]*) tests?\)\s*$'
if ($instrumentExit -ne 0 -or !$passed) { throw "Fire HD 10 test failed. See $log" }
