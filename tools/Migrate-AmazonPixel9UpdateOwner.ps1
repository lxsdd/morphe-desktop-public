#requires -Version 5.1
<#
Pixel 9 Amazon Shopping: one-time, explicitly destructive update-ownership migration.
Uses wireless ADB. Pixel 6a is never selected.
Default: READ-ONLY. -Apply -AcknowledgeDataLoss:
  1. Verify exact Pixel 9, Amazon package/version and installed APK hash.
  2. Pull the currently installed base.apk to private LocalAppData and verify its SHA-256.
  3. Fully uninstall Amazon (LOCAL APP DATA WILL BE LOST).
  4. Reinstall those exact APK bytes with Android --update-ownership.
  5. Verify installed version, hash, and update owner; preserve the PC backup.
No universal "Disable Play Store updates" patch is used.
Google Play update-offer disappearance must be checked separately on-device.
#>
[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$AcknowledgeDataLoss,
    [switch]$SelfTest,
    [string]$AdbPath = ""
)

$ErrorActionPreference = "Stop"
$packageName = "com.amazon.mShop.android.shopping"
$expectedVersionName = "32.18.0.100"
$expectedVersionCode = "1243240206"
$expectedSha256 = "21b86a1de84173e06960a5fa6438bbcc72718d901991fb340f4bece4dac7c69b"
$targetModel = "Pixel 9"
$targetDevice = "tokay"
$expectedOwner = "com.android.shell"

function Parse-AmazonDump {
    param([string]$Dump)
    $header = [regex]::Match($Dump, '(?m)^[ \t]*Package \[' + [regex]::Escape($packageName) + '\] \([^\r\n]*\):')
    if (-not $header.Success) { throw "Exact Amazon package block missing." }
    $body = $Dump.Substring($header.Index + $header.Length)
    $next = [regex]::Match($body, '(?m)^[ \t]*Package \[[^\]]+\] \([^\r\n]*\):')
    if ($next.Success) { $body = $body.Substring(0, $next.Index) }
    $versionCode = [regex]::Match($body, '(?m)\bversionCode=(\d+)')
    $versionName = [regex]::Match($body, '(?m)\bversionName=([^\s]+)')
    $owner = [regex]::Match($body, '(?m)\bupdateOwnerPackageName=([^\s]+)')
    return [pscustomobject]@{
        VersionName = if ($versionName.Success) { $versionName.Groups[1].Value } else { "UNBEKANNT" }
        VersionCode = if ($versionCode.Success) { $versionCode.Groups[1].Value } else { "UNBEKANNT" }
        UpdateOwner = if ($owner.Success) { $owner.Groups[1].Value } else { "NICHT_ANGEZEIGT" }
    }
}

function Parse-Sha256 {
    param([string]$Text)
    $match = [regex]::Match($Text, '(?im)^\s*([0-9a-f]{64})(?:\s|$)')
    if (-not $match.Success) { throw "Could not read a valid SHA-256 result." }
    return $match.Groups[1].Value.ToLowerInvariant()
}

function Assert-ExpectedBaseline {
    param($Snapshot, [string]$Hash)
    if ($Snapshot.VersionName -ne $expectedVersionName -or
        $Snapshot.VersionCode -ne $expectedVersionCode) {
        throw "Amazon version differs from qualified baseline. No uninstall performed."
    }
    if ($Snapshot.UpdateOwner -notin @("null", "NICHT_ANGEZEIGT")) {
        throw "Update owner is no longer missing ($($Snapshot.UpdateOwner)). No uninstall performed."
    }
    if ($Hash -ne $expectedSha256) {
        throw "Installed Amazon APK hash differs from qualified baseline. No uninstall performed."
    }
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
    $state = Parse-AmazonDump -Dump $fixture
    Assert-ExpectedBaseline -Snapshot $state -Hash $expectedSha256
    $h = Parse-Sha256 -Text ($expectedSha256 + "  /data/app/example/base.apk")
    if ($h -ne $expectedSha256) { throw "SHA parser self-test failed." }
    if ($targetModel -ne "Pixel 9" -or $targetDevice -ne "tokay") {
        throw "Exact target self-test failed."
    }
    Write-Output "PASS: Pixel 9 exact-target, Amazon version/owner and SHA-256 parser self-tests. No device operation."
    exit 0
}

if ($Apply -and -not $AcknowledgeDataLoss) {
    throw "Refusing destructive operation. Use -Apply -AcknowledgeDataLoss only after accepting loss of local Amazon app data."
}
if ($AcknowledgeDataLoss -and -not $Apply) {
    throw "-AcknowledgeDataLoss requires -Apply."
}

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $previousPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5.1 can misclassify normal ADB daemon stderr as a terminating error.
        $ErrorActionPreference = "Continue"
        $lines = @(& $AdbPath @Arguments 2>&1 | ForEach-Object { [string]$_ })
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($code -ne 0) {
        throw ("ADB failed (exit $code): " + ($lines -join " "))
    }
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
    $device = ((Invoke-Adb -Arguments @("-s", $serial, "shell", "getprop", "ro.product.device")) -join "").Trim()
    if ($model -eq $targetModel -and $device -eq $targetDevice) { $pixel9 += $serial }
}
if ($pixel9.Count -ne 1) {
    throw "Expected exactly one authorized Pixel 9 (tokay), found $($pixel9.Count). No change made."
}
$selectedSerial = $pixel9[0]

function Read-AmazonSnapshot {
    $packageLines = @(Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "pm", "list", "packages", $packageName))
    $exactLines = @($packageLines | Where-Object {
        $_ -match ('^package:' + [regex]::Escape($packageName) + '$')
    })
    if ($exactLines.Count -ne 1) { throw "Amazon is not uniquely installed. No uninstall performed." }
    $dump = (Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "dumpsys", "package", $packageName)) -join [Environment]::NewLine
    return Parse-AmazonDump -Dump $dump
}

function Get-RemoteBaseApkPath {
    $pathLines = @(Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "pm", "path", $packageName))
    $paths = @($pathLines | Where-Object { $_ -match '^package:/' })
    if ($paths.Count -ne 1) {
        throw "Expected one base APK and zero splits, found $($paths.Count). No uninstall performed."
    }
    $path = ([string]$paths[0]).Substring("package:".Length).Trim()
    if (-not $path.EndsWith("/base.apk", [StringComparison]::Ordinal)) {
        throw "Unexpected APK path. No uninstall performed."
    }
    return $path
}

function Get-RemoteHash {
    param([string]$Path)
    $result = (Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "sha256sum", $Path)) -join [Environment]::NewLine
    return Parse-Sha256 -Text $result
}

$before = Read-AmazonSnapshot
$remoteBase = Get-RemoteBaseApkPath
$beforeHash = Get-RemoteHash -Path $remoteBase
Assert-ExpectedBaseline -Snapshot $before -Hash $beforeHash
$help = (Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "pm", "help")) -join [Environment]::NewLine
if ($help -notmatch [regex]::Escape("--update-ownership")) {
    throw "Android does not advertise --update-ownership. No uninstall performed."
}
$users = (Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "pm", "list", "users")) -join [Environment]::NewLine
$userMatches = @([regex]::Matches($users, 'UserInfo\{(\d+):'))
if ($userMatches.Count -ne 1 -or $userMatches[0].Groups[1].Value -ne "0") {
    throw "Multiple or unexpected Android users/profiles. No uninstall performed."
}
Write-Output "MORPHE_AMAZON_OWNER_MIGRATION=1"
Write-Output "TARGET=Pixel 9 (tokay)"
Write-Output "PACKAGE=$packageName"
Write-Output "VERSION=$($before.VersionName)"
Write-Output "VERSION_CODE=$($before.VersionCode)"
Write-Output "BASE_APK_SHA256=$beforeHash"
Write-Output "UPDATE_OWNER_BEFORE=$($before.UpdateOwner)"
Write-Output "ADB_TRANSPORT=WIRELESS_OR_EXISTING_AUTHORIZED"
Write-Output "ADB_SERIAL=NICHT_AUSGEGEBEN"
if (-not $Apply) {
    Write-Output "MODE=READ_ONLY_PREFLIGHT"
    Write-Output "APP_DATA_CHANGED=NO"
    Write-Output "NEXT=Run with -Apply -AcknowledgeDataLoss for one-time full uninstall and first install with update ownership."
    exit 0
}

# No device mutation occurs before a verified local copy of the exact installed APK exists.
$localData = [Environment]::GetFolderPath("LocalApplicationData")
$privateDir = Join-Path $localData ("Morphe\private-recovery\Amazon-Pixel9-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Path $privateDir -Force | Out-Null
$localApk = Join-Path $privateDir "Amazon-32.18.0.100-original-installed.apk"
$null = Invoke-Adb -Arguments @("-s", $selectedSerial, "pull", $remoteBase, $localApk)
if (-not (Test-Path -LiteralPath $localApk -PathType Leaf)) {
    throw "APK backup was not created. No uninstall performed."
}
$localHash = (Get-FileHash -LiteralPath $localApk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($localHash -ne $expectedSha256) {
    throw "Pulled APK hash mismatch. No uninstall performed. Backup directory: $privateDir"
}
Write-Output "PRIVATE_APK_BACKUP_PC=$localApk"
Write-Output "APK_BACKUP_SHA256=$localHash"

# Recheck target identity and APK immediately before the irreversible operation.
$modelNow = ((Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "getprop", "ro.product.model")) -join "").Trim()
$deviceNow = ((Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "getprop", "ro.product.device")) -join "").Trim()
if ($modelNow -ne $targetModel -or $deviceNow -ne $targetDevice) {
    throw "Target identity changed. No uninstall performed."
}
$stateNow = Read-AmazonSnapshot
$pathNow = Get-RemoteBaseApkPath
$hashNow = Get-RemoteHash -Path $pathNow
Assert-ExpectedBaseline -Snapshot $stateNow -Hash $hashNow

# Stage and checksum the exact backup on-device BEFORE deleting the installed package.
# A transfer failure therefore cannot leave Amazon uninstalled.
$remoteTemp = "/data/local/tmp/morphe-amazon-owner-$([guid]::NewGuid().ToString('N')).apk"
try {
    $null = Invoke-Adb -Arguments @("-s", $selectedSerial, "push", $localApk, $remoteTemp)
    $stagedHash = Get-RemoteHash -Path $remoteTemp
    if ($stagedHash -ne $expectedSha256) {
        throw "Staged APK hash mismatch. No uninstall performed."
    }
    Write-Output "STAGED_APK_SHA256=$stagedHash"
    Write-Output "MODE=FULL_UNINSTALL_AND_FRESH_INSTALL"
    Write-Output "APP_LOCAL_DATA_LOSS=YES"
    $removed = @(Invoke-Adb -Arguments @("-s", $selectedSerial, "uninstall", $packageName))
    if (-not ($removed -match '(?i)^Success
        $installed = @(Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "pm", "install", "-r", "-d", "--update-ownership", $remoteTemp))
        if (-not ($installed -match '(?i)^Success$')) {
            throw "Package Manager did not confirm success."
        }
    } catch {
        $primaryFailure = $_.Exception.Message
        Write-Output "OWNERSHIP_INSTALL=FAILED"
        Write-Output "RECOVERY=ATTEMPT_STANDARD_REINSTALL"
        try {
            $restored = @(Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "pm", "install", "-r", "-d", $remoteTemp))
            if (-not ($restored -match '(?i)^Success$')) { throw "Package Manager did not confirm restore." }
            Write-Output "RECOVERY=STANDARD_REINSTALL_SUCCEEDED_WITHOUT_OWNER_GUARANTEE"
        } catch {
            Write-Output "RECOVERY=FAILED"
            throw "Owner install failed: $primaryFailure. Standard restore failed: $($_.Exception.Message). Private APK backup: $localApk"
        }
        throw "Owner install failed: $primaryFailure. Amazon APK restored without ownership guarantee. Private APK backup: $localApk"
    }
} finally {
    try {
        $null = Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "rm", "-f", $remoteTemp)
    } catch {
        Write-Output "TEMP_APK_CLEANUP=FAILED"
    }
}

$after = Read-AmazonSnapshot
$afterPath = Get-RemoteBaseApkPath
$afterHash = Get-RemoteHash -Path $afterPath
Write-Output "VERSION_AFTER=$($after.VersionName)"
Write-Output "VERSION_CODE_AFTER=$($after.VersionCode)"
Write-Output "BASE_APK_SHA256_AFTER=$afterHash"
Write-Output "UPDATE_OWNER_AFTER=$($after.UpdateOwner)"
Write-Output "APK_BYTES_IDENTICAL=$(if ($afterHash -eq $expectedSha256) { 'YES' } else { 'NO' })"
Write-Output "PRIVATE_APK_BACKUP_PC=$localApk"
Write-Output "ADB_SERIAL=NICHT_AUSGEGEBEN"
if ($after.VersionName -ne $expectedVersionName -or
    $after.VersionCode -ne $expectedVersionCode -or
    $afterHash -ne $expectedSha256) {
    throw "Post-install APK identity mismatch. Do not proceed with Play checks until reviewed."
}
if ($after.UpdateOwner -ne $expectedOwner) {
    throw "Update ownership not established. Do not claim Play protection."
}
Write-Output "RESULT=FRESH_INSTALL_OWNER_ESTABLISHED"
Write-Output "NEXT=Open Play Store updates on Pixel 9, refresh, and report whether Amazon remains offered."
)) {
        throw "Uninstall was not confirmed. Installation not attempted."
    }
    Write-Output "UNINSTALL=SUCCESS"
    try {
        $installed = @(Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "pm", "install", "-r", "-d", "--update-ownership", $remoteTemp))
        if (-not ($installed -match '(?i)^Success$')) {
            throw "Package Manager did not confirm success."
        }
    } catch {
        $primaryFailure = $_.Exception.Message
        Write-Output "OWNERSHIP_INSTALL=FAILED"
        Write-Output "RECOVERY=ATTEMPT_STANDARD_REINSTALL"
        try {
            $restored = @(Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "pm", "install", "-r", "-d", $remoteTemp))
            if (-not ($restored -match '(?i)^Success$')) { throw "Package Manager did not confirm restore." }
            Write-Output "RECOVERY=STANDARD_REINSTALL_SUCCEEDED_WITHOUT_OWNER_GUARANTEE"
        } catch {
            Write-Output "RECOVERY=FAILED"
            throw "Owner install failed: $primaryFailure. Standard restore failed: $($_.Exception.Message). Private APK backup: $localApk"
        }
        throw "Owner install failed: $primaryFailure. Amazon APK restored without ownership guarantee. Private APK backup: $localApk"
    }
} finally {
    try {
        $null = Invoke-Adb -Arguments @("-s", $selectedSerial, "shell", "rm", "-f", $remoteTemp)
    } catch {
        Write-Output "TEMP_APK_CLEANUP=FAILED"
    }
}

$after = Read-AmazonSnapshot
$afterPath = Get-RemoteBaseApkPath
$afterHash = Get-RemoteHash -Path $afterPath
Write-Output "VERSION_AFTER=$($after.VersionName)"
Write-Output "VERSION_CODE_AFTER=$($after.VersionCode)"
Write-Output "BASE_APK_SHA256_AFTER=$afterHash"
Write-Output "UPDATE_OWNER_AFTER=$($after.UpdateOwner)"
Write-Output "APK_BYTES_IDENTICAL=$(if ($afterHash -eq $expectedSha256) { 'YES' } else { 'NO' })"
Write-Output "PRIVATE_APK_BACKUP_PC=$localApk"
Write-Output "ADB_SERIAL=NICHT_AUSGEGEBEN"
if ($after.VersionName -ne $expectedVersionName -or
    $after.VersionCode -ne $expectedVersionCode -or
    $afterHash -ne $expectedSha256) {
    throw "Post-install APK identity mismatch. Do not proceed with Play checks until reviewed."
}
if ($after.UpdateOwner -ne $expectedOwner) {
    throw "Update ownership not established. Do not claim Play protection."
}
Write-Output "RESULT=FRESH_INSTALL_OWNER_ESTABLISHED"
Write-Output "NEXT=Open Play Store updates on Pixel 9, refresh, and report whether Amazon remains offered."
