#requires -Version 5.1
<#
Read-only inventory of apps installed or updated on a given day on Pixel 9.
No APK pull/install/uninstall, package mutation, or app-data access.
Only exact Pixel 9 (tokay) is selected. ADB serials are never printed.
An absent update owner is NOT by itself a reason to uninstall an app.
#>
[CmdletBinding()]
param(
    [datetime]$Date = (Get-Date).Date,
    [string]$AdbPath = "",
    [switch]$SelfTest
)

$ErrorActionPreference = "Stop"
$day = $Date.ToString("yyyy-MM-dd")

function Get-PackageField {
    param([string]$Block, [string]$Field)
    $match = [regex]::Match($Block, '(?m)\b' + [regex]::Escape($Field) + '=([^\r\n]+)')
    if ($match.Success) { return $match.Groups[1].Value.Trim() }
    return $null
}

function Read-PackageInfo {
    param([string]$Dump, [string]$PackageName)
    $header = [regex]::Match(
        $Dump,
        '(?m)^[ \t]*Package \[' + [regex]::Escape($PackageName) + '\] \([^\r\n]*\):'
    )
    if (-not $header.Success) { throw "Package block missing for $PackageName" }
    $block = $Dump.Substring($header.Index + $header.Length)
    $next = [regex]::Match($block, '(?m)^[ \t]*Package \[[^\]]+\] \([^\r\n]*\):')
    if ($next.Success) { $block = $block.Substring(0, $next.Index) }

    $version = Get-PackageField -Block $block -Field "versionName"
    $first = Get-PackageField -Block $block -Field "firstInstallTime"
    $last = Get-PackageField -Block $block -Field "lastUpdateTime"
    $owner = Get-PackageField -Block $block -Field "updateOwnerPackageName"
    $installer = Get-PackageField -Block $block -Field "installerPackageName"

    if ($null -eq $owner -or $owner -eq "null") { $owner = "NICHT_ANGEZEIGT" }
    if ($null -eq $installer -or $installer -eq "null") { $installer = "NICHT_ANGEZEIGT" }
    if ($null -eq $version) { $version = "UNBEKANNT" }

    return [pscustomobject]@{
        Package = $PackageName
        Version = $version
        FirstInstall = if ($first) { $first } else { "NICHT_ANGEZEIGT" }
        LastUpdate = if ($last) { $last } else { "NICHT_ANGEZEIGT" }
        UpdateOwner = $owner
        Installer = $installer
    }
}

if ($SelfTest) {
    $fixture = @'
Packages:
  Package [com.example.first] (abc):
    versionName=1.2.3
    firstInstallTime=2026-10-23 13:14:15
    lastUpdateTime=2026-10-23 13:14:15
    installerPackageName=com.android.shell
    updateOwnerPackageName=com.android.shell
  Package [com.example.second] (def):
    versionName=4.5.6
    firstInstallTime=2026-10-21 09:00:00
    lastUpdateTime=2026-10-23 12:00:00
    installerPackageName=com.android.vending
'@
    $one = Read-PackageInfo -Dump $fixture -PackageName "com.example.first"
    $two = Read-PackageInfo -Dump $fixture -PackageName "com.example.second"
    if ($one.UpdateOwner -ne "com.android.shell" -or
        $one.FirstInstall -notmatch '^2026-10-23' -or
        $one.Package -ne "com.example.first" -or
        $two.UpdateOwner -ne "NICHT_ANGEZEIGT" -or
        $two.Installer -ne "com.android.vending" -or
        $two.LastUpdate -notmatch '^2026-10-23') {
        throw "Read-only inventory parser self-test failed."
    }
    Write-Output "MORPHE_PIXEL9_APP_INVENTORY_SELF_TEST=PASS"
    exit 0
}

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $previousPreference = $ErrorActionPreference
    try {
        # PowerShell 5.1 sometimes converts harmless ADB startup stderr to errors.
        $ErrorActionPreference = "Continue"
        $lines = @(& $AdbPath @Arguments 2>&1 | ForEach-Object { [string]$_ })
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($code -ne 0) { throw ("ADB failed (exit $code): " + ($lines -join " ")) }
    return $lines
}

if ([string]::IsNullOrWhiteSpace($AdbPath)) {
    $command = Get-Command adb.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -ne $command) {
        $AdbPath = $command.Source
    } else {
        $localData = [Environment]::GetFolderPath("LocalApplicationData")
        $candidates = @(
            (Join-Path $localData "Android\Sdk\platform-tools\adb.exe"),
            (Join-Path $localData "platform-tools\adb.exe"),
            "C:\Android\sdk\platform-tools\adb.exe",
            "C:\Program Files\Android\platform-tools\adb.exe"
        )
        $AdbPath = $candidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
    }
}
if ([string]::IsNullOrWhiteSpace($AdbPath) -or -not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
    throw "ADB not found. Pass -AdbPath with the path used by Morphe Desktop."
}

$serials = @()
foreach ($line in @(Invoke-Adb -Arguments @("devices"))) {
    if ($line -match '^\s*(\S+)\s+device(?:\s|$)') { $serials += $Matches[1] }
}
$targets = @()
foreach ($serial in $serials) {
    $model = ((Invoke-Adb -Arguments @("-s", $serial, "shell", "getprop", "ro.product.model")) -join "").Trim()
    $device = ((Invoke-Adb -Arguments @("-s", $serial, "shell", "getprop", "ro.product.device")) -join "").Trim()
    if ($model -eq "Pixel 9" -and $device -eq "tokay") { $targets += $serial }
}
if ($targets.Count -ne 1) { throw "Expected exactly one authorized Pixel 9 (tokay), found $($targets.Count)." }
$selectedSerial = $targets[0]

$packageNames = @()
foreach ($line in @(Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "pm", "list", "packages", "-3"))) {
    if ($line -match '^package:([A-Za-z0-9_.]+)$') { $packageNames += $Matches[1] }
}
$packageNames = @($packageNames | Sort-Object -Unique)
if ($packageNames.Count -eq 0) { throw "No third-party packages returned; report is incomplete." }

Write-Output "MORPHE_PIXEL9_APP_INVENTORY=1"
Write-Output "TARGET=Pixel 9 (tokay)"
Write-Output "ADB_TRANSPORT=WIRELESS_OR_EXISTING_AUTHORIZED"
Write-Output "ADB_SERIAL=NICHT_AUSGEGEBEN"
Write-Output "MODE=READ_ONLY"
Write-Output "DATE=$day"
Write-Output "THIRD_PARTY_PACKAGES=$($packageNames.Count)"

$matches = @()
$unreadable = @()
$dateFieldCount = 0
foreach ($packageName in $packageNames) {
    try {
        $dump = (Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "dumpsys", "package", $packageName)) -join [Environment]::NewLine
        $info = Read-PackageInfo -Dump $dump -PackageName $packageName
        if ($info.FirstInstall -ne "NICHT_ANGEZEIGT" -or $info.LastUpdate -ne "NICHT_ANGEZEIGT") { $dateFieldCount++ }
        if ($info.FirstInstall.StartsWith($day) -or $info.LastUpdate.StartsWith($day)) {
            $matches += $info
        }
    } catch {
        # Continue safely but disclose any incomplete inventory.
        $unreadable += $packageName
    }
}

Write-Output "DATE_FIELDS_READABLE=$dateFieldCount"
Write-Output "TODAY_INSTALLED_OR_UPDATED=$($matches.Count)"
foreach ($info in $matches) {
    $ownerState = if ($info.UpdateOwner -eq "com.android.shell") {
        "SHELL_OWNER"
    } elseif ($info.UpdateOwner -eq "NICHT_ANGEZEIGT") {
        "NO_OWNER_REVIEW"
    } else {
        "OTHER_OWNER_REVIEW"
    }
    Write-Output ("APP|PACKAGE={0}|VERSION={1}|FIRST={2}|LAST={3}|INSTALLER={4}|OWNER={5}|STATE={6}" -f
        $info.Package, $info.Version, $info.FirstInstall, $info.LastUpdate,
        $info.Installer, $info.UpdateOwner, $ownerState)
}
Write-Output "UNREADABLE_PACKAGES=$($unreadable.Count)"
foreach ($name in $unreadable) { Write-Output "UNREADABLE_PACKAGE=$name" }
if ($dateFieldCount -eq 0) {
    Write-Output "RESULT=INCONCLUSIVE_NO_INSTALL_DATES"
} elseif ($unreadable.Count -gt 0) {
    Write-Output "RESULT=PARTIAL_READ_ONLY"
} else {
    Write-Output "RESULT=READ_ONLY_COMPLETE"
}
Write-Output "NOTE=Missing update owner alone does not justify reinstall. Compare only Morphe-modified apps with actual Play offers."
