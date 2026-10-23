#requires -Version 5.1
<#
Read-only Amazon Shopping update diagnosis for Pixel 6a or Pixel 9.
Works with already paired and connected Wireless ADB; USB is not required.
No install, uninstall, patch, Play setting change, APK pull, or device write.
The report omits the ADB serial.
#>
[CmdletBinding()]
param(
    [string]$AdbPath = "",
    [string]$DeviceSerial = "",
    [ValidateSet("Pixel 6a", "Pixel 9")][string]$TargetModel = "Pixel 9",
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

function Invoke-ReadOnlyAdb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    # Windows PowerShell 5.1 wraps native stderr as NativeCommandError when
    # 2>&1 is used. ADB legitimately writes its first daemon-start notice there.
    # Capture stderr without letting ErrorActionPreference=Stop abort a
    # successful native command; still reject every non-zero exit code.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $lines = @(& $AdbPath @Arguments 2>&1 | ForEach-Object { [string]$_ })
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) {
        throw ("ADB read-only command failed (exit $exitCode): " + ($lines -join " "))
    }
    return $lines
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
    # Native stderr may be informational (ADB daemon startup). It must not
    # become a terminating PowerShell error while exit-code failure still fails.
    $AdbPath = $env:ComSpec
    if ([string]::IsNullOrWhiteSpace($AdbPath)) {
        throw "FAIL: Windows command processor not available for native stderr self-test."
    }
    $mockOutput = @(Invoke-ReadOnlyAdb -Arguments @("/d", "/c", "echo daemon-start 1>&2 && echo ADB_OK"))
    if (-not ($mockOutput -match "ADB_OK")) {
        throw "FAIL: Native stderr startup message prevented a successful command."
    }
    $rejected = $false
    try {
        $null = Invoke-ReadOnlyAdb -Arguments @("/d", "/c", "echo rejected 1>&2 && exit /b 7")
    } catch {
        $rejected = $_.Exception.Message -match "exit 7"
    }
    if (-not $rejected) {
        throw "FAIL: Non-zero native exit code was not rejected."
    }
    Write-Output "PASS: Exact-package parser and native-stderr self-tests. No ADB or device operation."
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
            (Join-Path $localData "Morphe Desktop\platform-tools\adb.exe"),
            "C:\Android\sdk\platform-tools\adb.exe",
            "C:\Program Files\Android\platform-tools\adb.exe"
        )
        $AdbPath = $candidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
            Select-Object -First 1
    }
}
if ([string]::IsNullOrWhiteSpace($AdbPath) -or -not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
    throw "ADB not found. Rerun with -AdbPath 'C:\path\to\adb.exe'."
}

$deviceLines = @(Invoke-ReadOnlyAdb -Arguments @("devices"))
$connectedSerials = @()
foreach ($line in $deviceLines) {
    if ($line -match '^\s*(\S+)\s+device(?:\s|$)') {
        $connectedSerials += $Matches[1]
    }
}
if ($connectedSerials.Count -eq 0) {
    throw "No authorized ADB device. Ensure Wireless ADB is paired and connected in Morphe Desktop, then rerun."
}

$matchingDevices = @()
foreach ($serial in $connectedSerials) {
    if (-not [string]::IsNullOrWhiteSpace($DeviceSerial) -and $serial -ne $DeviceSerial) {
        continue
    }
    $model = ((Invoke-ReadOnlyAdb -Arguments @("-s", $serial, "shell", "getprop", "ro.product.model")) -join "").Trim()
    if ($model -eq $TargetModel) {
        $matchingDevices += [pscustomobject]@{ Serial = $serial; Model = $model }
    }
}
if ($matchingDevices.Count -ne 1) {
    throw "Expected exactly one authorized $TargetModel, found $($matchingDevices.Count)."
}

$selectedSerial = $matchingDevices[0].Serial
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

$playStoreVersion = "UNBEKANNT"
$playStoreVersionCode = "UNBEKANNT"
try {
    $playDump = (Invoke-ReadOnlyAdb -Arguments @("-s", $selectedSerial, "shell", "dumpsys", "package", "com.android.vending")) -join [Environment]::NewLine
    $playDetails = Get-ExactPackageDetails -Dump $playDump -PackageName "com.android.vending"
    $playStoreVersion = $playDetails.VersionName
    $playStoreVersionCode = $playDetails.VersionCode
} catch {
    # Play Store diagnostics are supplementary; never block Amazon report.
}

$adbTransport = if ($selectedSerial.Contains(":") -or $selectedSerial -like "adb-*-*") { "WIRELESS" } else { "USB_ODER_ANDERES" }

$report = @(
    "MORPHE_AMAZON_READ_ONLY_DIAGNOSTIC=1",
    "Datum=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')",
    "Geraet=$TargetModel",
    "Android=$androidVersion",
    "AndroidSDK=$androidSdk",
    "Paket=$packageName",
    "VersionName=$($details.VersionName)",
    "VersionCode=$($details.VersionCode)",
    "InstallerOfRecord=$installer",
    "UpdateOwner=$($details.UpdateOwner)",
    "PlayStoreVersion=$playStoreVersion",
    "PlayStoreVersionCode=$playStoreVersionCode",
    "ADB_Transport=$adbTransport",
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
