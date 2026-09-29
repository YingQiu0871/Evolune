[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ArtifactDir,   # output folder of scripts/release_verify.ps1
    [Parameter(Mandatory)][string]$Version,       # e.g. 1.9.2
    [Parameter(Mandatory)][string]$Commit,        # the main merge commit the tag must point at
    [string]$NotesFile,                           # release notes markdown (optional)
    [string]$ExpectedCertSha256 = 'b9b6b955',
    [switch]$Publish                              # without it: dry run, nothing is pushed or created
)

# Release publishing for a version already built and verified by release_verify.ps1:
#   1. re-hash the APKs and check them against SHA256SUMS.txt and the signing certificate
#   2. create an annotated tag v<Version> on <Commit> and push only that tag
#   3. create the GitHub Release with the two APKs and SHA256SUMS.txt
#   4. download the assets back and compare hashes / signatures with the local copies
# No signing secrets are used here. Never moves or overwrites an existing tag or Release.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$tag = "v$Version"
$names = "Evolune-Phone-$tag.apk", "Evolune-Wear-$tag.apk", 'SHA256SUMS.txt'

function Invoke-Native {
    param([Parameter(Mandatory)][string]$FilePath, [Parameter(Mandatory)][string[]]$Arguments)
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $FilePath $($Arguments -join ' ')" }
}

function Get-Sha256([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }

$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
if (-not $sdkRoot) { throw 'ANDROID_SDK_ROOT / ANDROID_HOME is not set.' }
$apksigner = Get-ChildItem -Path (Join-Path $sdkRoot 'build-tools') -Filter 'apksigner.bat' -Recurse -ErrorAction SilentlyContinue |
    Sort-Object { [version]($_.Directory.Name -replace '[^0-9.].*$', '') } -Descending | Select-Object -First 1
if (-not $apksigner) { throw 'apksigner.bat not found under ANDROID_SDK_ROOT\build-tools.' }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'GitHub CLI (gh) not found.' }

function Test-ApkSignature([string]$Path) {
    $out = & $apksigner.FullName verify --print-certs $Path
    if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed: $Path" }
    $line = $out | Where-Object { $_ -match 'certificate SHA-256 digest' } | Select-Object -First 1
    $cert = ($line -split ':', 2)[1].Trim().ToLowerInvariant()
    if (-not $cert.StartsWith($ExpectedCertSha256.ToLowerInvariant())) {
        throw "Certificate mismatch for ${Path}: $cert"
    }
}

# 1. Local verification
foreach ($n in $names) {
    if (-not (Test-Path -LiteralPath (Join-Path $ArtifactDir $n) -PathType Leaf)) { throw "Missing $n in $ArtifactDir" }
}
$expected = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $ArtifactDir 'SHA256SUMS.txt')) {
    if ($line -match '^([0-9a-f]{64})  (.+)$') { $expected[$Matches[2]] = $Matches[1] }
}
foreach ($n in $names[0..1]) {
    $path = Join-Path $ArtifactDir $n
    if ($expected[$n] -ne (Get-Sha256 $path)) { throw "Hash of $n does not match SHA256SUMS.txt." }
    Test-ApkSignature $path
}
Write-Host 'Local APKs match SHA256SUMS.txt and the release certificate.'

# Tag and Release must not exist yet (never move or rebuild a published tag).
$fullCommit = (& git -C $repoRoot rev-parse --verify "$Commit^{commit}").Trim()
if ($LASTEXITCODE -ne 0) { throw "Unknown commit: $Commit" }
if (& git -C $repoRoot tag --list $tag) { throw "Tag $tag already exists locally." }
if (& git -C $repoRoot ls-remote --tags origin "refs/tags/$tag") { throw "Tag $tag already exists on origin." }
& gh release view $tag 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) { throw "GitHub Release $tag already exists." }

Write-Host "Plan: annotated tag $tag -> $fullCommit; Release $tag with $($names -join ', ')"
if (-not $Publish) {
    Write-Host 'Dry run only. Re-run with -Publish to push the tag and create the Release.'
    return
}
$answer = Read-Host "Publish $tag to origin? Type the version ($Version) to confirm"
if ($answer -ne $Version) { throw 'Not confirmed; nothing was published.' }

# 2. Tag (only this one ref is pushed)
Invoke-Native git @('-C', $repoRoot, 'tag', '-a', $tag, $fullCommit, '-m', "Evolune $tag")
Invoke-Native git @('-C', $repoRoot, 'push', 'origin', "refs/tags/$tag")

# 3. Release
$ghArgs = @('release', 'create', $tag, '--verify-tag', '--title', "Evolune $tag") +
    ($names | ForEach-Object { Join-Path $ArtifactDir $_ })
if ($NotesFile) { $ghArgs += @('--notes-file', $NotesFile) } else { $ghArgs += @('--notes', "Evolune $tag") }
Invoke-Native gh $ghArgs

# 4. Download back and compare
$back = Join-Path ([System.IO.Path]::GetTempPath()) "evolune-readback-$tag-$(Get-Date -Format yyyyMMddHHmmss)"
New-Item -ItemType Directory -Force -Path $back | Out-Null
Invoke-Native gh @('release', 'download', $tag, '--dir', $back)
foreach ($n in $names) {
    if ((Get-Sha256 (Join-Path $ArtifactDir $n)) -ne (Get-Sha256 (Join-Path $back $n))) {
        throw "Read-back hash mismatch for $n."
    }
}
foreach ($n in $names[0..1]) { Test-ApkSignature (Join-Path $back $n) }
Write-Host "Read-back verified: 3/3 assets match, APK signatures valid. Downloaded to $back"
Write-Host "Release: $(& gh release view $tag --json url --jq .url)"
