param(
    [string] $Serial,
    [string] $DebugApk = 'app/build/outputs/apk/debug/app-debug.apk',
    [string] $TestApk = 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
    [string] $LogDir = 'deliverables/android/build-logs'
)

$ErrorActionPreference = 'Stop'
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' } else { 'adb.exe' }
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
function Verify([string] $Classes, [string] $Expected, [string] $LogName) {
    & $adb -s $Serial shell am force-stop com.billmivo.app.debug
    $run = & $adb -s $Serial shell am instrument -w -e class $Classes com.billmivo.app.debug.test/androidx.test.runner.AndroidJUnitRunner
    $run | Tee-Object (Join-Path $LogDir $LogName)
    if (($run -join "`n") -notmatch [regex]::Escape($Expected)) { throw "Fire HD 10 test failed: $LogName" }
}
$prefix = 'com.billmivo.app.'
$tests = @('ReminderDeliveryTest','SharingMigrationTest','HouseholdScreenTest','DocumentsReportsTest',
    'PremiumLimitTest','RepositoryTest','DashboardTest')
$classes = ($tests | ForEach-Object { $prefix + $_ }) -join ','
Verify $classes 'OK (7 tests)' 'fire-030.txt'
Verify ($prefix + 'BillJourneyTest') 'OK (1 test)' 'fire-030-journey.txt'
Verify ($prefix + 'AssistantScreenTest') 'OK (1 test)' 'fire-030-assistant.txt'
