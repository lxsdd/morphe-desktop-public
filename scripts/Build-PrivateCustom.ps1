# Build a private local JAR from an explicitly checked-out and qualified source SHA.
# This script NEVER installs an APK, accesses ADB or uploads any binary.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-fA-F]{40}$')]
    [string]$ExpectedCommit,
    [string]$Destination = (Join-Path ([Environment]::GetFolderPath('UserProfile')) 'Downloads\MorpheCustomLocal')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$wrapper = Join-Path $root 'gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) {
    throw 'Gradle wrapper not found. Use the checked-out source repository.'
}
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git is required to verify the source commit.' }
$head = (& git -C $root rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $head -ne $ExpectedCommit.ToLowerInvariant()) {
    throw "The source SHA does not match the expected commit. HEAD=$head expected=$ExpectedCommit"
}
$status = @(& git -C $root status --porcelain --untracked-files=no)
if ($LASTEXITCODE -ne 0 -or $status.Count -gt 0) {
    throw 'Tracked source files are modified. Build aborted to preserve SHA provenance.'
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java/JDK 21 was not found.' }
$javaVersion = (& java -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "21\.|openjdk version "21\.') {
    throw "JDK 21 is required. Installed Java: $javaVersion"
}
Push-Location $root
try {
    & $wrapper shadowJar --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE)" }
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $root 'build\libs') -File -Filter '*-all.jar')
    if ($jars.Count -ne 1 -or $jars[0].Length -lt 1MB) {
        throw "Expected exactly one nonempty fat JAR, found $($jars.Count)"
    }
    Add-Type -AssemblyName System.IO.Compression
    $zip = [IO.Compression.ZipFile]::OpenRead($jars[0].FullName)
    try {
        if (-not ($zip.Entries | Where-Object { $_.FullName -eq 'app/morphe/MorpheLauncherKt.class' })) {
            throw 'Morphe Desktop entrypoint missing from JAR.'
        }
    } finally { $zip.Dispose() }
    [IO.Directory]::CreateDirectory($Destination) | Out-Null
    $output = Join-Path $Destination ("morphe-desktop-custom-" + $head.Substring(0,12) + ".jar")
    Copy-Item -LiteralPath $jars[0].FullName -Destination $output -Force
    $sha256 = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
    $report = "SOURCE_SHA=$head`nSHA256=$sha256`nFILE=$output`nQUALIFICATION=LOCAL_BUILD_ONLY_NOT_RELEASE_APPROVED`n"
    Set-Content -LiteralPath (Join-Path $Destination 'SHA256-and-provenance.txt') -Value $report -Encoding UTF8
    Write-Host $report
    Write-Host 'PASS: private local JAR created; no upload, release, or connected-device operation.'
} finally { Pop-Location }
