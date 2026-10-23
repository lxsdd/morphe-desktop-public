#requires -Version 5.1
<#
Pixel 9 only: controlled installer-of-record experiment for patched Amazon Shopping.
Default mode is read-only. -Apply changes only PackageManager installer metadata.
It does NOT install/uninstall an APK, clear data, or set Android update ownership.
Use only after comparing read-only reports with Pixel 6a.
#>
[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$SelfTest,
    [string]$AdbPath = ""
)

$ErrorActionPreference = "Stop"
$packageName = "com.amazon.mShop.android.shopping"
$expectedVersionName = "32.18.0.100"
$expectedVersionCode = "1243240206"
$targetModel = "Pixel 9"
$shellInstaller = "com.android.shell"

function Get-ExactAmazonState {
    param([string]$PackageLine, [string]$Dump)
    if ($PackageLine -notmatch ('^package:' + [regex]::Escape($packageName) + '(?:\s|$)')) {
        throw "Exact Amazon package line missing."
    }
    $installerMatch = [regex]::Match($PackageLine, '\binstaller=([^\s]+)')
    $installer = if ($installerMatch.Success) { $installerMatch.Groups[1].Value } else { "NICHT_ANGEZEIGT" }

    $header = [regex]::Match($Dump, '(?m)^[ \t]*Package \[' + [regex]::Escape($packageName) + '\] \([^\r\n]*\):')
    if (-not $header.Success) { throw "Exact Amazon package dump block missing." }
    $body = $Dump.Substring($header.Index + $header.Length)
    $next = [regex]::Match($body, '(?m)^[ \t]*Package \[[^\]]+\] \([^\r\n]*\):')
    if ($next.Success) { $body = $body.Substring(0, $next.Index) }
    $versionCode = [regex]::Match($body, '(?m)\bversionCode=(\d+)')
    $versionName = [regex]::Match($body, '(?m)\bversionName=([^\s]+)')
    $owner = [regex]::Match($body, '(?m)\bupdateOwnerPackageName=([^\s]+)')
    return [pscustomobject]@{
        Installer = $installer
        VersionName = if ($versionName.Success) { $versionName.Groups[1].Value } else { "UNBEKANNT" }
        VersionCode = if ($versionCode.Success) { $versionCode.Groups[1].Value } else { "UNBEKANNT" }
        UpdateOwner = if ($owner.Success) { $owner.Groups[1].Value } else { "NICHT_ANGEZEIGT" }
    }
}

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $previousPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5.1 treats informational ADB daemon stderr as
        # NativeCommandError if ErrorActionPreference is Stop.
        $ErrorActionPreference = "Continue"
        $lines = @(& $AdbPath @Arguments 2>&1 | ForEach-Object { [string]$_ })
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($code -ne 0) {
        throw ("ADB command failed (exit $code): " + ($lines -join " "))
    }
    return $lines
}

if ($SelfTest) {
    $fixture = @'
Packages:
  Package [com.amazon.mShop.android.shopping] (123):
    versionCode=1243240206 minSdk=26
    versionName=32.18.0.100
  Package [com.other] (99):
    versionCode=2147483647
    updateOwnerPackageName=com.android.vending
'@
    $state = Get-ExactAmazonState -PackageLine "package:com.amazon.mShop.android.shopping installer=null" -Dump $fixture
    if ($state.VersionCode -ne $expectedVersionCode -or
        $state.VersionName -ne $expectedVersionName -or
        $state.Installer -ne "null" -or
        $state.UpdateOwner -ne "NICHT_ANGEZEIGT") {
        throw "FAIL: Exact Amazon parser self-test."
    }
    $models = @(@("Pixel 6a", "Pixel 9") | Where-Object { $_ -eq $targetModel })
    if ($models.Count -ne 1 -or $models[0] -ne "Pixel 9") {
        throw "FAIL: Target-model selection self-test."
    }
    Write-Output "PASS: Pixel 9 exact-target and Amazon package parser self-tests. No ADB/device operation."
    exit 0
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
    throw "ADB not found. Specify -AdbPath or use the same ADB executable as Morphe Desktop."
}

$deviceLines = @(Invoke-Adb -Arguments @("devices"))
$serials = @()
foreach ($line in $deviceLines) {
    if ($line -match '^\s*(\S+)\s+device(?:\s|$)') { $serials += $Matches[1] }
}
$pixel9 = @()
foreach ($serial in $serials) {
    $model = ((Invoke-Adb -Arguments @("-s", $serial, "shell", "getprop", "ro.product.model")) -join "").Trim()
    if ($model -eq $targetModel) { $pixel9 += $serial }
}
if ($pixel9.Count -ne 1) {
    throw "Expected exactly one authorized Pixel 9 over ADB, found $($pixel9.Count). No change made."
}
$selectedSerial = $pixel9[0]

function Read-AmazonState {
    $packageLines = @(Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "pm", "list", "packages", "-i", $packageName))
    $exactLines = @($packageLines | Where-Object {
        $_ -match ('^package:' + [regex]::Escape($packageName) + '(?:\s|$)')
    })
    if ($exactLines.Count -ne 1) { throw "Amazon package not uniquely installed. No change made." }
    $dump = (Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "dumpsys", "package", $packageName)) -join [Environment]::NewLine
    return Get-ExactAmazonState -PackageLine ([string]$exactLines[0]) -Dump $dump
}

$before = Read-AmazonState
if ($before.VersionName -ne $expectedVersionName -or $before.VersionCode -ne $expectedVersionCode) {
    throw "Amazon version differs from the qualified baseline. No change made."
}
if ($before.Installer -notin @("null", "NICHT_ANGEZEIGT")) {
    throw "Installer is no longer null/unknown ($($before.Installer)). No change made."
}
if ($before.UpdateOwner -notin @("null", "NICHT_ANGEZEIGT")) {
    throw "Update owner changed ($($before.UpdateOwner)). No change made."
}

Write-Output "TARGET=Pixel 9"
Write-Output "PACKAGE=$packageName"
Write-Output "VERSION=$($before.VersionName)"
Write-Output "VERSION_CODE=$($before.VersionCode)"
Write-Output "INSTALLER_BEFORE=$($before.Installer)"
Write-Output "UPDATE_OWNER_BEFORE=$($before.UpdateOwner)"
Write-Output "ADB_SERIAL=NICHT_AUSGEGEBEN"
if (-not $Apply) {
    Write-Output "MODE=READ_ONLY_PREFLIGHT"
    Write-Output "NEXT=Rerun with -Apply to set installer-of-record to com.android.shell on Pixel 9 only."
    Write-Output "NOTE=Update owner remains unset; Play Store behavior is not guaranteed to change."
    exit 0
}

$help = (Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "pm", "help")) -join [Environment]::NewLine
if ($help -notmatch '(?m)\bset-installer\b') {
    throw "This Android PackageManager does not advertise set-installer. No change made."
}

$change = @(Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "pm", "set-installer", $packageName, $shellInstaller))
if (-not ($change -match '(?i)^Success$')) {
    throw "PackageManager did not confirm success. Verify device state before any retry."
}
$after = Read-AmazonState
Write-Output "MODE=INSTALLER_METADATA_ONLY"
Write-Output "INSTALLER_AFTER=$($after.Installer)"
Write-Output "UPDATE_OWNER_AFTER=$($after.UpdateOwner)"
Write-Output "APK_REINSTALL=NO"
Write-Output "APP_DATA_CHANGED=NO"
Write-Output "ADB_SERIAL=NICHT_AUSGEGEBEN"
if ($after.Installer -ne $shellInstaller) {
    throw "Post-check mismatch: installer was not confirmed as com.android.shell."
}
Write-Output "RESULT=INSTALLER_ATTRIBUTION_CHANGED"
Write-Output "NEXT=Check whether Google Play still offers an Amazon update; do not infer success from attribution alone."
