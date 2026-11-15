param(
    [string] $Serial,
    [string] $Output = (Join-Path $PSScriptRoot '../deliverables/store-assets/screenshots/en-GB/dashboard.png'),
    [switch] $Seed
)
$ErrorActionPreference = 'Stop'
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' } else { 'adb.exe' }
$deviceArgs = if ($Serial) { @('-s', $Serial) } else { @() }
$package = 'com.billmivo.app.debug'
if ($Seed) {
    $result = & $adb @deviceArgs shell am instrument -w -e class com.billmivo.app.StorePreviewFixture -e demo true "$package.test/androidx.test.runner.AndroidJUnitRunner"
    $result | Write-Output
    if (($result -join "`n") -notmatch 'OK \(1 test\)') { throw 'Demo setup did not complete.' }
}
& $adb @deviceArgs shell am start -W -n "$package/com.billmivo.app.MainActivity"
if ($LASTEXITCODE -ne 0) { throw 'Could not start Billmivo.' }
$deviceView = '/data/local/tmp/billmivo-dashboard-view.xml'
try {
    $visible = $false
    for ($attempt = 0; $attempt -lt 4; $attempt++) {
        & $adb @deviceArgs shell uiautomator dump $deviceView | Out-Null
        $hierarchy = (& $adb @deviceArgs shell cat $deviceView 2>$null) -join ''
        if ($hierarchy -match 'text="Billmivo"' -and $hierarchy -match 'package="com.billmivo.app.debug"') {
            $visible = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (!$visible) { throw 'Billmivo did not finish rendering the dashboard.' }
} finally {
    & $adb @deviceArgs shell rm -f $deviceView
}
$target = [IO.Path]::GetFullPath($Output)
New-Item -ItemType Directory -Force ([IO.Path]::GetDirectoryName($target)) | Out-Null
$deviceFile = '/data/local/tmp/billmivo-dashboard.png'
& $adb @deviceArgs shell screencap -p $deviceFile
if ($LASTEXITCODE -ne 0) { throw 'Could not capture device screen.' }
& $adb @deviceArgs pull $deviceFile $target
if ($LASTEXITCODE -ne 0) { throw 'Could not retrieve screenshot.' }
& $adb @deviceArgs shell rm $deviceFile
Get-Item -LiteralPath $target
