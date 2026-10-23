#requires -Version 5.1
<#
Read-only Amazon Shopping update diagnosis for Pixel 6a.
No install, uninstall, patch, Play setting change, APK pull, or device write.
The report omits the ADB serial.
#>
[CmdletBinding()]
param(
    [string]$AdbPath = "",
    [string]$DeviceSerial = "",
    [switch]$SelfTest
)

$ErrorActionPreference = "Stop"
$packageName = "com.amazon.mShop.android.shopping"

function Get-ExactPackageDetails {
    param(
        [Parameter(Mandatory = $true)][string]$Dump,
        [Parameter(Mandatory = $true)][string]$PackageName
    )
    $headerPattern = '(?m)^[ \t]*Package \[' + [regex]::Escape($PackageName) + '\] \([^\r\n]*\):'
    $header = [regex]::Match($Dump, $headerPattern)
    if (-not $header.Success) {
        throw "Exact Amazon package block not found in dumpsys output."
    }
    $body = $Dump.Substring($header.Index + $header.Length)
    $nextPackage = [regex]::Match($body, '(?m)^[ \t]*Package \[[^\]]+\] \([^\r\n]*\):')
    if ($nextPackage.Success) {
        $body = $body.Substring(0, $nextPackage.Index)
    }
    $versionCode = [regex]::Match($body, '(?m)\bversionCode=(\d+)')
    $versionName = [regex]::Match($body, '(?m)\bversionName=([^\s]+)')
    $updateOwner = [regex]::Match($body, '(?m)\bupdateOwnerPackageName=([^\s]+)')
    return [pscustomobject]@{
        VersionCode = if ($versionCode.Success) { $versionCode.Groups[1].Value } else { "UNBEKANNT" }
        VersionName = if ($versionName.Success) { $versionName.Groups[1].Value } else { "UNBEKANNT" }
        UpdateOwner = if ($updateOwner.Success) { $updateOwner.Groups[1].Value } else { "NICHT_ANGEZEIGT" }
    }
}

if ($SelfTest) {
    $fixture = @'
Packages:
  Package [com.amazon.mShop.android.shopping] (123):
    userId=10001
    versionCode=1243240206 minSdk=26
    versionName=32.18.0.100
    updateOwnerPackageName=com.android.shell
  Package [com.example.other] (456):
    versionCode=999999999
'@
    $parsed = Get-ExactPackageDetails -Dump $fixture -PackageName $packageName
    if ($parsed.VersionCode -ne "1243240206" -or
        $parsed.VersionName -ne "32.18.0.100" -or
        $parsed.UpdateOwner -ne "com.android.shell") {
        throw "FAIL: Exact-package parser self-test."
    }
    Write-Output "PASS: Exact-package parser self-test. No ADB or device operation."
    exit 0
}

if ([string]::IsNullOrWhiteSpace($AdbPath)) {
    $command = Get-Command adb.exe -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -ne $command) {
        $AdbPath = $command.Source
    } else {
        $localData = [Environment]::GetFolderPath("LocalApplicationData")
        $candidates = @(
            (Join-Path $localData "Android\Sdk\platform-tools\adb.exe"),
            (Join-Path $localData "platform-tools\adb.exe"),
            (Join-Path $localData "Morphe Desktop\platform-tools\adb.exe")
        )
        $AdbPath = $candidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
            Select-Object -First 1
    }
}
if ([string]::IsNullOrWhiteSpace($AdbPath) -or -not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
    throw "ADB not found. Rerun with -AdbPath 'C:\path\to\adb.exe'."
}

function Invoke-ReadOnlyAdb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $lines = @(& $AdbPath @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw ("ADB read-only command failed: " + ($lines -join " "))
    }
    return $lines
}

$deviceLines = @(Invoke-ReadOnlyAdb -Arguments @("devices"))
$connectedSerials = @()
foreach ($line in $deviceLines) {
    if ($line -match '^\s*(\S+)\s+device(?:\s|$)') {
        $connectedSerials += $Matches[1]
    }
}
if ($connectedSerials.Count -eq 0) {
    throw "No authorized ADB device. Connect Pixel 6a via USB and allow USB debugging."
}

$pixel6a = @()
foreach ($serial in $connectedSerials) {
    if (-not [string]::IsNullOrWhiteSpace($DeviceSerial) -and $serial -ne $DeviceSerial) {
        continue
    }
    $model = ((Invoke-ReadOnlyAdb -Arguments @("-s", $serial, "shell", "getprop", "ro.product.model")) -join "").Trim()
    if ($model -eq "Pixel 6a") {
        $pixel6a += [pscustomobject]@{ Serial = $serial; Model = $model }
    }
}
if ($pixel6a.Count -ne 1) {
    throw "Expected exactly one authorized Pixel 6a, found $($pixel6a.Count)."
}

$selectedSerial = $pixel6a[0].Serial
$androidVersion = ((Invoke-ReadOnlyAdb -Arguments @("-s", $selectedSerial, "shell", "getprop", "ro.build.version.release")) -join "").Trim()
$androidSdk = ((Invoke-ReadOnlyAdb -Arguments @("-s", $selectedSerial, "shell", "getprop", "ro.build.version.sdk")) -join "").Trim()
$packageLines = @(Invoke-ReadOnlyAdb -Arguments @("-s", $selectedSerial, "shell", "pm", "list", "packages", "-i", $packageName))
$exactPackage = @($packageLines | Where-Object {
    $_ -match ('^package:' + [regex]::Escape($packageName) + '(?:\s|$)')
})
if ($exactPackage.Count -ne 1) {
    throw "Amazon Shopping not uniquely installed under the expected package name."
}
$installerMatch = [regex]::Match([string]$exactPackage[0], '\binstaller=([^\s]+)')
$installer = if ($installerMatch.Success) { $installerMatch.Groups[1].Value } else { "NICHT_ANGEZEIGT" }
$dump = (Invoke-ReadOnlyAdb -Arguments @("-s", $selectedSerial, "shell", "dumpsys", "package", $packageName)) -join [Environment]::NewLine
$details = Get-ExactPackageDetails -Dump $dump -PackageName $packageName

$report = @(
    "MORPHE_AMAZON_READ_ONLY_DIAGNOSTIC=1",
    "Datum=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')",
    "Geraet=Pixel 6a",
    "Android=$androidVersion",
    "AndroidSDK=$androidSdk",
    "Paket=$packageName",
    "VersionName=$($details.VersionName)",
    "VersionCode=$($details.VersionCode)",
    "InstallerOfRecord=$installer",
    "UpdateOwner=$($details.UpdateOwner)",
    "ADB_Serial=NICHT_AUSGEGEBEN",
    "Aktion=READ_ONLY"
) -join [Environment]::NewLine
Write-Output $report
try {
    Set-Clipboard -Value $report -ErrorAction Stop
    Write-Host ""
    Write-Host "Bericht in die Zwischenablage kopiert. Bitte hier in ChatGPT einfuegen."
} catch {
    Write-Host ""
    Write-Host "Bitte den obigen Bericht kopieren und hier in ChatGPT einfuegen."
}
