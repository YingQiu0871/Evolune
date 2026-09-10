[CmdletBinding()]
param(
    [string]$KeystorePath = 'C:\Users\1\Downloads\evolune-release-new.p12',
    [string]$KeyAlias = 'Evolune'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repoRoot 'gradlew.bat'
$javaHome = 'C:\Program Files\Android\Android Studio\jbr'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$artifactDir = Join-Path $repoRoot "release-artifacts\v1.6.0-rc\$timestamp"
$transcriptPath = Join-Path $artifactDir 'BUILD-RAW.log'
$transcriptStarted = $false
$storePassword = $null
$keyPassword = $null

function ConvertTo-PlainText {
    param([Parameter(Mandatory)][Security.SecureString]$SecureValue)
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

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

try {
    if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
        throw "Gradle wrapper not found: $gradle"
    }
    if (-not (Test-Path -LiteralPath $javaHome -PathType Container)) {
        throw "Android Studio JDK not found: $javaHome"
    }
    if (-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) {
        throw "Release keystore not found: $KeystorePath"
    }
    if ([string]::IsNullOrWhiteSpace($KeyAlias)) {
        throw 'KeyAlias must not be empty.'
    }

    $secureStorePassword = Read-Host 'Enter release keystore password' -AsSecureString
    $secureKeyPassword = Read-Host 'Enter release key password' -AsSecureString
    $storePassword = ConvertTo-PlainText $secureStorePassword
    $keyPassword = ConvertTo-PlainText $secureKeyPassword

    $env:JAVA_HOME = $javaHome
    $env:EVOLUNE_KEYSTORE_PATH = (Resolve-Path -LiteralPath $KeystorePath).Path
    $env:EVOLUNE_KEYSTORE_PASSWORD = $storePassword
    $env:EVOLUNE_KEY_ALIAS = $KeyAlias
    $env:EVOLUNE_KEY_PASSWORD = $keyPassword

    New-Item -ItemType Directory -Path $artifactDir -Force | Out-Null
    Set-Location -LiteralPath $repoRoot
    Start-Transcript -LiteralPath $transcriptPath -Force | Out-Null
    $transcriptStarted = $true

    Write-Host '========== 1/5 Version, tests, lint, and signed Release build =========='
    Invoke-Native $gradle @(
        'validateEvoluneIdentityAndVersioning',
        ':experience-core:test',
        ':app:testDebugUnitTest',
        ':wear:testDebugUnitTest',
        ':app:lintRelease',
        ':wear:lintRelease',
        ':app:assembleRelease',
        ':wear:assembleRelease',
        '--no-daemon',
        '--rerun-tasks'
    )

    Write-Host '========== 2/5 Copy immutable candidate artifacts =========='
    $phoneSource = Join-Path $repoRoot 'app\build\outputs\apk\release\app-release.apk'
    $wearSource = Join-Path $repoRoot 'wear\build\outputs\apk\release\wear-release.apk'
    if (-not (Test-Path -LiteralPath $phoneSource -PathType Leaf)) {
        throw "Phone Release APK missing: $phoneSource"
    }
    if (-not (Test-Path -LiteralPath $wearSource -PathType Leaf)) {
        throw "Wear Release APK missing: $wearSource"
    }
    $phoneCandidate = Join-Path $artifactDir 'Evolune-Phone-v1.6.0-RC.apk'
    $wearCandidate = Join-Path $artifactDir 'Evolune-Wear-v1.6.0-RC.apk'
    Copy-Item -LiteralPath $phoneSource -Destination $phoneCandidate -Force
    Copy-Item -LiteralPath $wearSource -Destination $wearCandidate -Force

    Write-Host '========== 3/5 Locate apksigner and verify both APKs =========='
    $sdkRoot = if ($env:ANDROID_HOME) {
        $env:ANDROID_HOME
    } elseif ($env:ANDROID_SDK_ROOT) {
        $env:ANDROID_SDK_ROOT
    } else {
        Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    }
    $apksigner = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Directory |
        Sort-Object { [version]$_.Name } -Descending |
        ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
        Select-Object -First 1
    if (-not $apksigner) {
        throw "apksigner.bat not found under $sdkRoot\build-tools"
    }

    # Windows PowerShell 5.1 converts native stderr into ErrorRecord objects. apksigner may
    # emit harmless JVM warnings there, so keep them in the audit output and decide success
    # exclusively from the native exit code.
    $savedErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $phoneVerify = & $apksigner verify --verbose --print-certs $phoneCandidate 2>&1
    $phoneVerifyExitCode = $LASTEXITCODE
    $wearVerify = & $apksigner verify --verbose --print-certs $wearCandidate 2>&1
    $wearVerifyExitCode = $LASTEXITCODE
    $ErrorActionPreference = $savedErrorActionPreference
    if ($phoneVerifyExitCode -ne 0) { throw 'Phone APK signature verification failed.' }
    if ($wearVerifyExitCode -ne 0) { throw 'Wear APK signature verification failed.' }
    $phoneVerify | Set-Content -LiteralPath (Join-Path $artifactDir 'PHONE-APKSIGNER.txt') -Encoding utf8
    $wearVerify | Set-Content -LiteralPath (Join-Path $artifactDir 'WEAR-APKSIGNER.txt') -Encoding utf8
    $phoneVerify | ForEach-Object { Write-Host $_ }
    $wearVerify | ForEach-Object { Write-Host $_ }

    $digestPattern = 'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)'
    $phoneDigestMatch = [regex]::Match(($phoneVerify -join "`n"), $digestPattern)
    $wearDigestMatch = [regex]::Match(($wearVerify -join "`n"), $digestPattern)
    if (-not $phoneDigestMatch.Success -or -not $wearDigestMatch.Success) {
        throw 'Unable to read signer SHA-256 from apksigner output.'
    }
    $phoneSigner = $phoneDigestMatch.Groups[1].Value.ToUpperInvariant()
    $wearSigner = $wearDigestMatch.Groups[1].Value.ToUpperInvariant()
    if ($phoneSigner -ne $wearSigner) {
        throw "Phone and Wear signer mismatch: $phoneSigner != $wearSigner"
    }

    Write-Host '========== 4/5 Hash and build identity =========='
    $phoneHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $phoneCandidate).Hash
    $wearHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $wearCandidate).Hash
    @(
        "$phoneHash *Evolune-Phone-v1.6.0-RC.apk",
        "$wearHash *Evolune-Wear-v1.6.0-RC.apk"
    ) | Set-Content -LiteralPath (Join-Path $artifactDir 'SHA256SUMS.txt') -Encoding ascii

    $branch = (& git branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read Git branch.' }
    $head = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read Git HEAD.' }
    $status = & git status --short
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read Git status.' }
    @(
        'versionName=1.6.0',
        'phoneVersionCode=101060000',
        'wearVersionCode=1101060000',
        'variant=release',
        "branch=$branch",
        "head=$head",
        "signerSha256=$phoneSigner",
        "phoneSha256=$phoneHash",
        "wearSha256=$wearHash",
        'worktreeStatusBegin',
        $status,
        'worktreeStatusEnd'
    ) | Set-Content -LiteralPath (Join-Path $artifactDir 'BUILD-IDENTITY.txt') -Encoding utf8

    Write-Host '========== 5/5 Candidate ready =========='
    Write-Host "Artifact directory: $artifactDir"
    Write-Host "Phone SHA-256: $phoneHash"
    Write-Host "Wear SHA-256:  $wearHash"
    Write-Host "Signer SHA-256: $phoneSigner"
} finally {
    if ($transcriptStarted) {
        Stop-Transcript | Out-Null
    }
    Remove-Item Env:EVOLUNE_KEYSTORE_PATH -ErrorAction SilentlyContinue
    Remove-Item Env:EVOLUNE_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:EVOLUNE_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:EVOLUNE_KEY_PASSWORD -ErrorAction SilentlyContinue
    $storePassword = $null
    $keyPassword = $null
}
