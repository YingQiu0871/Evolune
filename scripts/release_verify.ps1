[CmdletBinding()]
param(
    [string]$ExpectedCertSha256 = 'b9b6b955'
)

# Builds signed Phone/Wear Release APKs from the four approved environment
# variables and prints APK / certificate SHA-256 fingerprints.
# Passwords are only read by Gradle from the environment; this script never
# echoes, logs, or writes them.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repoRoot 'gradlew.bat'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$artifactDir = Join-Path $repoRoot "release-artifacts\v1.9.1-rc\$timestamp"
$reportPath = Join-Path $artifactDir 'RELEASE-VERIFY.txt'

function Invoke-Native {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter(Mandatory)][string[]]$Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE`: $FilePath $($Arguments -join ' ')"
    }
}

# Only check presence; never print values.
$required = 'EVOLUNE_KEYSTORE_PATH', 'EVOLUNE_KEYSTORE_PASSWORD', 'EVOLUNE_KEY_ALIAS', 'EVOLUNE_KEY_PASSWORD'
$missing = $required | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) }
if ($missing) {
    throw "Missing environment variables: $($missing -join ', ')"
}
if (-not (Test-Path -LiteralPath $env:EVOLUNE_KEYSTORE_PATH -PathType Leaf)) {
    throw 'EVOLUNE_KEYSTORE_PATH does not point to an existing file.'
}
if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw "Gradle wrapper not found: $gradle"
}

$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
$apksigner = $null
if ($sdkRoot) {
    $apksigner = Get-ChildItem -Path (Join-Path $sdkRoot 'build-tools') -Filter 'apksigner.bat' -Recurse -ErrorAction SilentlyContinue |
        Sort-Object { [version]($_.Directory.Name -replace '[^0-9.].*$', '') } -Descending |
        Select-Object -First 1
}
if (-not $apksigner) {
    throw 'apksigner.bat not found under ANDROID_SDK_ROOT\build-tools.'
}

New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null

Push-Location $repoRoot
try {
    Invoke-Native $gradle @('--no-daemon', ':app:assembleRelease', ':wear:assembleRelease')
} finally {
    Pop-Location
}

$apks = @(
    Get-ChildItem -Path (Join-Path $repoRoot 'app\build\outputs\apk\release') -Filter '*.apk'
    Get-ChildItem -Path (Join-Path $repoRoot 'wear\build\outputs\apk\release') -Filter '*.apk'
)
if ($apks.Count -ne 2) {
    throw "Expected 2 release APKs, found $($apks.Count)."
}

$lines = @("Evolune v1.9.1 release verification $timestamp")
foreach ($apk in $apks) {
    Copy-Item -LiteralPath $apk.FullName -Destination $artifactDir
    $apkHash = (Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $certOutput = & $apksigner.FullName verify --print-certs $apk.FullName
    if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed: $($apk.Name)" }
    $certLine = $certOutput | Where-Object { $_ -match 'certificate SHA-256 digest' } | Select-Object -First 1
    $certHash = ($certLine -split ':', 2)[1].Trim().ToLowerInvariant()
    $match = $certHash.StartsWith($ExpectedCertSha256.ToLowerInvariant())
    $lines += ''
    $lines += "APK: $($apk.Name)"
    $lines += "  apk sha256 : $apkHash"
    $lines += "  cert sha256: $certHash"
    $lines += "  cert matches release identity prefix '$ExpectedCertSha256': $match"
    if (-not $match) { $lines += '  WARNING: signing certificate differs from the release identity.' }
}

$lines | Set-Content -LiteralPath $reportPath -Encoding UTF8
$lines | ForEach-Object { Write-Host $_ }
Write-Host ''
Write-Host "Report and APK copies: $artifactDir"
