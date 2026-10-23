#requires -Version 5.1
<#
Read-only comparison of installed Amazon Shopping base APK bytes on Pixel 6a
and Pixel 9. Works over already connected Wireless ADB. It never pulls an
APK to the PC, writes to a device, or prints ADB serials or APK paths.
Only adb devices, getprop, pm path and sha256sum are executed.
#>
[CmdletBinding()]
param(
    [string]$AdbPath = "",
    [switch]$SelfTest
)

$ErrorActionPreference = "Stop"
$packageName = "com.amazon.mShop.android.shopping"
$targetModels = @("Pixel 6a", "Pixel 9")

function Get-ExactApkPaths {
    param([Parameter(Mandatory = $true)][string[]]$Lines)
    $paths = @()
    foreach ($line in $Lines) {
        if ($line -notmatch '^package:(/[^\s]+)\s*$') {
            continue
        }
        $path = $Matches[1]
        # Paths are passed to an Android shell. Reject shell metacharacters
        # rather than attempting to escape device-supplied path strings.
        if ($path -cnotmatch '^/[A-Za-z0-9._~+=/-]+$') {
            throw "APK path contains unsafe characters. No hash command run."
        }
        $paths += $path
    }
    if ($paths.Count -lt 1) {
        throw "PackageManager returned no installed APK paths for Amazon."
    }
    $basePaths = @($paths | Where-Object { $_ -cmatch '/base\.apk$' })
    if ($basePaths.Count -ne 1) {
        throw "Expected one installed Amazon base.apk, found $($basePaths.Count)."
    }
    return [pscustomobject]@{
        BasePath = [string]$basePaths[0]
        SplitCount = $paths.Count - 1
    }
}

function Get-Sha256FromOutput {
    param([Parameter(Mandatory = $true)][string[]]$Lines)
    $hashLines = @($Lines | Where-Object {
        $_ -match '^[a-fA-F0-9]{64}\s+\S+\s*$'
    })
    if ($hashLines.Count -ne 1) {
        throw "Expected one SHA-256 result from Android, found $($hashLines.Count)."
    }
    $result = [regex]::Match([string]$hashLines[0], '^([a-fA-F0-9]{64})\s+\S+')
    return $result.Groups[1].Value.ToLowerInvariant()
}

function Invoke-ReadOnlyAdb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    # Windows PowerShell 5.1 otherwise promotes informational ADB daemon
    # startup stderr to a terminating NativeCommandError.
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
    $paths = Get-ExactApkPaths -Lines @(
        "package:/data/app/~~xABC/com.amazon.mShop.android.shopping-123/base.apk",
        "package:/data/app/~~xABC/com.amazon.mShop.android.shopping-123/split_config.arm64_v8a.apk"
    )
    if ($paths.SplitCount -ne 1 -or $paths.BasePath -cnotmatch '/base\.apk$') {
        throw "FAIL: exact base APK path parsing"
    }
    $rejected = $false
    try {
        $null = Get-ExactApkPaths -Lines @(
            "package:/data/app/com.amazon/base.apk;id"
        )
    } catch { $rejected = $true }
    if (-not $rejected) { throw "FAIL: unsafe shell path accepted" }

    $sampleHash = ("a" * 64)
    $parsedHash = Get-Sha256FromOutput -Lines @(
        "* daemon started successfully",
        "$sampleHash  /data/app/any/base.apk"
    )
    if ($parsedHash -cne $sampleHash) { throw "FAIL: SHA-256 parser" }

    $rejected = $false
    try {
        $null = Get-Sha256FromOutput -Lines @("not a digest")
    } catch { $rejected = $true }
    if (-not $rejected) { throw "FAIL: missing digest accepted" }

    Write-Output "PASS: Amazon base-APK path, shell-safety and SHA-256 parser self-tests. No ADB or device operation."
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
        $AdbPath = $candidates |
            Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
            Select-Object -First 1
    }
}
if ([string]::IsNullOrWhiteSpace($AdbPath) -or
    -not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
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
    throw "No connected ADB device. Connect paired Wireless ADB devices first."
}

$selected = @{}
foreach ($serial in $connectedSerials) {
    $model = ((Invoke-ReadOnlyAdb -Arguments @(
        "-s", $serial, "shell", "getprop", "ro.product.model"
    )) -join "").Trim()
    if ($targetModels -notcontains $model) { continue }
    if ($selected.ContainsKey($model)) {
        throw "More than one $model is connected. Specify only the intended devices."
    }
    $selected[$model] = $serial
}
if ($selected.Count -eq 0) {
    throw "Neither Pixel 6a nor Pixel 9 is connected through ADB."
}

$fingerprints = @{}
foreach ($model in $targetModels) {
    if (-not $selected.ContainsKey($model)) { continue }
    $serial = [string]$selected[$model]
    $pathLines = @(Invoke-ReadOnlyAdb -Arguments @(
        "-s", $serial, "shell", "pm", "path", $packageName
    ))
    $apkPaths = Get-ExactApkPaths -Lines $pathLines
    $hashLines = @(Invoke-ReadOnlyAdb -Arguments @(
        "-s", $serial, "shell", "sha256sum", $apkPaths.BasePath
    ))
    $fingerprints[$model] = [pscustomobject]@{
        BaseSHA256 = Get-Sha256FromOutput -Lines $hashLines
        SplitCount = $apkPaths.SplitCount
    }
}

$report = @(
    "MORPHE_AMAZON_INSTALLED_APK_COMPARE=1",
    "Datum=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')",
    "Paket=$packageName",
    "Aktion=READ_ONLY",
    "ADB_Serial=NICHT_AUSGEGEBEN",
    "APK_Pull=NO",
    "APK_Install=NO",
    "Device_Data_Changed=NO"
)
foreach ($model in $targetModels) {
    $label = if ($model -eq "Pixel 6a") { "Pixel6a" } else { "Pixel9" }
    if ($fingerprints.ContainsKey($model)) {
        $item = $fingerprints[$model]
        $report += "$($label)_BaseApkSHA256=$($item.BaseSHA256)"
        $report += "$($label)_SplitCount=$($item.SplitCount)"
    } else {
        $report += "$($label)_Status=NICHT_VERBUNDEN"
    }
}
if ($fingerprints.Count -eq 2) {
    $same = $fingerprints["Pixel 6a"].BaseSHA256 -ceq $fingerprints["Pixel 9"].BaseSHA256
    $report += "BaseApkIdentisch=$(if ($same) { 'JA' } else { 'NEIN' })"
    $report += "Hinweis=Gleiche base.apk-Hashes bedeuten gleiche Datei-Bytes, nicht gleichen Play-Store-Zustand."
} else {
    $report += "BaseApkIdentisch=NICHT_VERGLEICHBAR"
    $report += "Hinweis=Beide Pixel ueber Wireless ADB verbinden und erneut starten."
}
$result = $report -join [Environment]::NewLine
Write-Output $result
try {
    Set-Clipboard -Value $result -ErrorAction Stop
    Write-Host ""
    Write-Host "Bericht in Zwischenablage. Bitte hier einfuegen."
} catch {
    Write-Host ""
    Write-Host "Bitte den Bericht hier einfuegen."
}
