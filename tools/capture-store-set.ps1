param(
    [Parameter(Mandatory)]
    [ValidateSet('en-GB','fr-FR','es-ES','de-DE')]
    [string] $Locale,
    [ValidateSet('en','fr','es','de')]
    [string] $CurrentLanguage = 'en',
    [string] $Serial,
    [switch] $ChangesOnly
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' } else { 'adb.exe' }
$device = if ($Serial) { @('-s', $Serial) } else { @() }
$package = 'com.billmivo.app.debug'
$activity = "$package/com.billmivo.app.MainActivity"
$view = '/data/local/tmp/billmivo-store-view.xml'
$remotePng = '/data/local/tmp/billmivo-store-capture.png'
$output = Join-Path $root "deliverables/store-assets/amazon-raw/$Locale"
$language = $Locale.Split('-')[0]

function Strings([string] $Lang) {
    $folder = if ($Lang -eq 'en') { 'values' } else { "values-$Lang" }
    [xml] $xml = Get-Content -LiteralPath (Join-Path $root "app/src/main/res/$folder/strings.xml") -Raw -Encoding UTF8
    $map = @{}
    foreach ($node in $xml.resources.string) { $map[$node.name] = $node.InnerText }
    $map
}

function Find-AppLabel([string] $Label, [switch] $Prefix, [int] $Attempts = 4) {
    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        $dump = (& $adb @device shell uiautomator dump $view 2>&1) -join ' '
        if ($dump -notmatch 'dumped to') { continue }
        $raw = (& $adb @device shell cat $view 2>$null) -join ''
        if (!$raw) { continue }
        [xml] $layout = $raw
        $result = $layout.SelectNodes("//node[@package='$package']") | Where-Object {
            $text = $_.GetAttribute('text')
            if ($Prefix) { $text.StartsWith($Label) }
            else { $text -eq $Label -or $_.GetAttribute('content-desc') -eq $Label }
        } | Select-Object -First 1
        if ($result) { return $result }
    }
}

function Tap-Label([string] $Label, [switch] $Prefix, [int[]] $Fallback) {
    $node = Find-AppLabel $Label -Prefix:$Prefix
    if (!$node) {
        if ($Fallback.Count -eq 2) { & $adb @device shell input tap $Fallback[0] $Fallback[1]; return }
        throw "App control missing: $Label"
    }
    $rect = @([regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int] $_.Value })
    & $adb @device shell input tap ([int](($rect[0] + $rect[2]) / 2)) ([int](($rect[1] + $rect[3]) / 2))
}

function Save-Scene([string] $File, [string] $Label) {
    if (!(Find-AppLabel $Label)) { throw "Scene $File did not show $Label" }
    & $adb @device shell screencap -p $remotePng
    if ($LASTEXITCODE -ne 0) { throw "Capture failed: $File" }
    & $adb @device pull $remotePng (Join-Path $output "$File.png")
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $File" }
}

function Scroll-ToLabel([string] $Label, [switch] $Prefix) {
    for ($attempt = 0; $attempt -lt 5; $attempt++) {
        if (Find-AppLabel $Label -Prefix:$Prefix -Attempts 1) { return }
        & $adb @device shell input swipe 1000 1020 1000 420 350
    }
    throw "Could not scroll to $Label"
}

New-Item -ItemType Directory -Force $output | Out-Null
try {
    $result = & $adb @device shell am instrument -w -e class com.billmivo.app.StorePreviewFixture -e demo true -e previewLanguage $language "$package.test/androidx.test.runner.AndroidJUnitRunner"
    $result | Write-Output
    if (($result -join "`n") -notmatch 'OK \(1 test\)') { throw 'Preview fixture failed.' }
    & $adb @device shell am force-stop $package
    & $adb @device shell am start -W -n $activity
    $strings = Strings $language
    $from = Strings $CurrentLanguage
    Tap-Label $from.more
    Scroll-ToLabel "$($from.language) · " -Prefix
    Tap-Label "$($from.language) · " -Prefix
    Tap-Label $from["language_$language"]
    Tap-Label $strings.dashboard

    if ($ChangesOnly) {
        Tap-Label $strings.insights
        if (!(Find-AppLabel $strings.spending)) { throw 'Spending chart did not load.' }
        & $adb @device shell input swipe 1000 925 1000 700 670
        Scroll-ToLabel $strings.bill_changes
        Save-Scene 'changes' $strings.bill_changes
        python (Join-Path $PSScriptRoot 'store-art.py') --raw $output --locale $Locale
        if ($LASTEXITCODE -ne 0) { throw 'Artwork composition failed.' }
        return
    }

    Save-Scene 'forecast' $strings.forecast
    Tap-Label $strings.days_7
    Save-Scene 'week' $strings.forecast
    Tap-Label $strings.more
    Tap-Label $strings.reminders -Fallback @(506, 282)
    Save-Scene 'reminders' $strings.reminders
    Tap-Label $strings.settings
    Tap-Label $strings.household -Fallback @(514, 369)
    Scroll-ToLabel $strings.shares_30
    Save-Scene 'sharing' $strings.shares_30
    Tap-Label $strings.insights
    Save-Scene 'spending' $strings.spending
    & $adb @device shell input swipe 1000 925 1000 700 670
    Scroll-ToLabel $strings.bill_changes
    Save-Scene 'changes' $strings.bill_changes
    Tap-Label $strings.voice
    Save-Scene 'voice' $strings.voice_command_title
    & $adb @device shell input keyevent KEYCODE_BACK
    Tap-Label $strings.calendar
    Save-Scene 'calendar' $strings.calendar
    python (Join-Path $PSScriptRoot 'store-art.py') --raw $output --locale $Locale
    if ($LASTEXITCODE -ne 0) { throw 'Artwork composition failed.' }
} finally {
    & $adb @device shell rm -f $view $remotePng
}
