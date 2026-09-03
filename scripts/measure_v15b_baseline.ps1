[CmdletBinding()]
param(
    [string]$Serial = 'emulator-5558',
    [string]$Package = 'io.github.yingqiu0871.evolune',
    [string]$Activity = '.MainActivity',
    [ValidateRange(1, 100)]
    [int]$ColdStartRuns = 20,
    [ValidateRange(5, 300)]
    [int]$IdleSeconds = 60
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw 'adb was not found on PATH.'
}

function Invoke-TaskAdb {
    param([string[]]$Arguments)

    & adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

function Get-TaskLine {
    param(
        [string]$Text,
        [string]$Pattern
    )

    $match = [regex]::Match($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if ($match.Success) {
        return $match.Groups[1].Value
    }
    return $null
}

$deviceState = (& adb devices | Select-String -Pattern "^$([regex]::Escape($Serial))\s+device$").Line
if (-not $deviceState) {
    throw "Target device '$Serial' is not online."
}

$packageDump = (Invoke-TaskAdb @('shell', 'dumpsys', 'package', $Package)) -join "`n"
$versionName = Get-TaskLine -Text $packageDump -Pattern 'versionName=([^\s]+)'
$versionCode = Get-TaskLine -Text $packageDump -Pattern 'versionCode=(\d+)'
if (-not $versionName) {
    throw "Package '$Package' is not installed on '$Serial'."
}

$coldStartTimes = [System.Collections.Generic.List[int]]::new()
for ($run = 1; $run -le $ColdStartRuns; $run++) {
    Invoke-TaskAdb @('shell', 'am', 'force-stop', $Package) | Out-Null
    $launchText = (Invoke-TaskAdb @('shell', 'am', 'start', '-W', '-n', "$Package/$Activity")) -join "`n"
    $totalTime = Get-TaskLine -Text $launchText -Pattern '^TotalTime:\s*(\d+)'
    if ($totalTime) {
        $coldStartTimes.Add([int]$totalTime)
    }
    Start-Sleep -Milliseconds 300
}

$sortedTimes = @($coldStartTimes | Sort-Object)
$medianMs = if ($sortedTimes.Count -eq 0) {
    $null
} elseif ($sortedTimes.Count % 2 -eq 0) {
    ($sortedTimes[$sortedTimes.Count / 2 - 1] + $sortedTimes[$sortedTimes.Count / 2]) / 2
} else {
    $sortedTimes[[math]::Floor($sortedTimes.Count / 2)]
}
$averageMs = if ($coldStartTimes.Count -gt 0) {
    [math]::Round(($coldStartTimes | Measure-Object -Average).Average, 1)
} else {
    $null
}

Invoke-TaskAdb @('shell', 'am', 'force-stop', $Package) | Out-Null
Invoke-TaskAdb @('shell', 'am', 'start', '-W', '-n', "$Package/$Activity") | Out-Null
Start-Sleep -Seconds 2
Invoke-TaskAdb @('shell', 'dumpsys', 'gfxinfo', $Package, 'reset') | Out-Null
$taskPid = ((Invoke-TaskAdb @('shell', 'pidof', $Package)) -join ' ').Trim()
$cpuBefore = if ($taskPid) {
    ((Invoke-TaskAdb @('shell', 'top', '-b', '-n', '1', '-o', 'PID,CPU,ARGS')) |
        Select-String -SimpleMatch $Package | Select-Object -First 1).Line
} else {
    $null
}
Start-Sleep -Seconds $IdleSeconds
$gfxText = (Invoke-TaskAdb @('shell', 'dumpsys', 'gfxinfo', $Package)) -join "`n"
$cpuAfter = ((Invoke-TaskAdb @('shell', 'top', '-b', '-n', '1', '-o', 'PID,CPU,ARGS')) |
    Select-String -SimpleMatch $Package | Select-Object -First 1).Line
$frameCount = Get-TaskLine -Text $gfxText -Pattern '^Total frames rendered:\s*(\d+)'
$jankyFrames = Get-TaskLine -Text $gfxText -Pattern '^Janky frames:\s*(\d+)'

$repositoryRoot = (& git rev-parse --show-toplevel).Trim()
$branch = (& git branch --show-current).Trim()
$head = (& git rev-parse HEAD).Trim()

[ordered]@{
    schema = 'evolune.v15b.baseline.v1'
    measuredAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    repository = $repositoryRoot
    branch = $branch
    head = $head
    device = $Serial
    package = $Package
    versionName = $versionName
    versionCode = $versionCode
    coldStart = [ordered]@{
        requestedRuns = $ColdStartRuns
        completedRuns = $coldStartTimes.Count
        timesMs = @($coldStartTimes)
        medianMs = $medianMs
        averageMs = $averageMs
        minMs = if ($sortedTimes.Count -gt 0) { $sortedTimes[0] } else { $null }
        maxMs = if ($sortedTimes.Count -gt 0) { $sortedTimes[$sortedTimes.Count - 1] } else { $null }
    }
    idleFrameObservation = [ordered]@{
        durationSeconds = $IdleSeconds
        totalFramesRendered = if ($frameCount) { [int]$frameCount } else { 0 }
        jankyFrames = if ($jankyFrames) { [int]$jankyFrames } else { 0 }
        cpuBefore = $cpuBefore
        cpuAfter = $cpuAfter
        status = if ($frameCount -and [int]$frameCount -gt 0) { 'VALID' } else { 'INVALID_NO_FRAME_SAMPLES' }
    }
} | ConvertTo-Json -Depth 6
