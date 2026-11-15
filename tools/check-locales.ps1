$ErrorActionPreference = 'Stop'
$resources = Join-Path $PSScriptRoot '../app/src/main/res'
function Get-FormatArguments([string] $value) {
    ([regex]::Matches($value, '%(?:[0-9]+\$)?[ds]') | ForEach-Object Value | Sort-Object) -join ','
}
function Read-Strings([string] $file) {
    [xml] $xml = Get-Content -LiteralPath $file -Raw -Encoding UTF8
    $strings = @{}
    foreach ($node in $xml.resources.string) {
        $key = [string] $node.name
        if ($strings.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($node.InnerText)) {
            throw "Duplicate or empty string ${key} in $file"
        }
        $strings[$key] = Get-FormatArguments $node.InnerText
    }
    [pscustomobject] @{ Strings = $strings; Plurals = $xml.resources.plurals }
}

$base = Read-Strings (Join-Path $resources 'values/strings.xml')
[xml] $localeConfig = Get-Content -LiteralPath (Join-Path $resources 'xml/locales.xml') -Raw -Encoding UTF8
$localeConfig.'locale-config'.locale | ForEach-Object {
    $language = $_.GetAttribute('name','http://schemas.android.com/apk/res/android')
    $directory = if ($language -eq 'en') { 'values' } else { "values-$language" }
    $locale = Read-Strings (Join-Path $resources "$directory/strings.xml")
    $missing = @($base.Strings.Keys | Where-Object { !$locale.Strings.ContainsKey($_) })
    $extra = @($locale.Strings.Keys | Where-Object { !$base.Strings.ContainsKey($_) })
    $invalid = @($base.Strings.Keys | Where-Object { $base.Strings[$_] -ne $locale.Strings[$_] })
    if ($missing.Count -or $extra.Count -or $invalid.Count) {
        throw "${language}: missing [$missing], extra [$extra], invalid placeholders [$invalid]"
    }
    foreach ($group in $base.Plurals) {
        $translated = @($locale.Plurals | Where-Object name -eq $group.name)
        $expected = Get-FormatArguments ($group.item | Where-Object quantity -eq 'other').InnerText
        $items = if ($translated.Count -eq 1) { $translated[0].SelectNodes('item') }
        $quantities = @($items | ForEach-Object quantity)
        $required = if ($language -eq 'ar') { 'zero','one','two','few','many','other' } else { 'one','other' }
        if ($translated.Count -ne 1 -or @($required | Where-Object { $_ -notin $quantities }).Count -or
            @($items | Where-Object { (Get-FormatArguments $_.InnerText) -ne $expected }).Count) {
            throw "${language}: invalid plural $($group.name)"
        }
    }
    Write-Output "$language $($locale.Strings.Count)/$($base.Strings.Count) strings, placeholders and plurals matched"
}
