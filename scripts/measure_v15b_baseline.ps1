[CmdletBinding()]
param(
    [string]$Serial = 'emulator-5558',
    [string]$Package = 'io.github.yingqiu0871.evolune',
    # The debug applicationId has a suffix, while the Activity class remains in the
    # production namespace. Use the fully-qualified class so both variants resolve.
    [string]$Activity = 'io.github.yingqiu0871.evolune.MainActivity',
    [ValidateNotNullOrEmpty()]
    [string]$Scenario = 'home',
    [ValidateRange(1, 100)]
    [int]$ColdStartRuns = 20,
    [ValidateRange(0, 100)]
    [int]$WarmStartRuns = 20,
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

function Invoke-TaskAdbText {
    param([string[]]$Arguments)

    return (Invoke-TaskAdb $Arguments) -join "`n"
}

function Get-TaskPid {
    $pidText = (Invoke-TaskAdbText @('shell', 'pidof', $Package)).Trim()
    if (-not $pidText) {
        return $null
    }
    return ($pidText -split '\s+')[0]
}

function Get-ForegroundState {
    $activityText = Invoke-TaskAdbText @('shell', 'dumpsys', 'activity', 'activities')
    $windowText = Invoke-TaskAdbText @('shell', 'dumpsys', 'window', 'windows')
    $escapedPackage = [regex]::Escape($Package)
    $resumedLine = ($activityText -split "`n" |
        Where-Object { $_ -match 'mResumedActivity|topResumedActivity' } |
        Select-Object -First 1)
    $focusLine = ($windowText -split "`n" |
        Where-Object { $_ -match 'mCurrentFocus|mFocusedApp' } |
        Select-Object -First 1)
    if (-not $focusLine) {
        $focusLine = ($activityText -split "`n" |
            Where-Object { $_ -match 'mFocusedApp' } |
            Select-Object -First 1)
    }

    [ordered]@{
        activityResumed = [bool]($resumedLine -and $resumedLine -match $escapedPackage)
        windowFocused = [bool]($focusLine -and $focusLine -match $escapedPackage)
        activityResumedLine = if ($resumedLine) { $resumedLine.Trim() } else { $null }
        currentFocusLine = if ($focusLine) { $focusLine.Trim() } else { $null }
    }
}

function Get-UiScreenState {
    $dumpPath = '/sdcard/evolune_v15b_ui.xml'
    Invoke-TaskAdb @('shell', 'uiautomator', 'dump', $dumpPath) | Out-Null
    $uiText = Invoke-TaskAdbText @('shell', 'cat', $dumpPath)
    $escapedPackage = [regex]::Escape($Package)
    $markers = [regex]::Matches($uiText, 'text="([^"]+)"') |
        ForEach-Object { $_.Groups[1].Value } |
        Where-Object { $_ } |
        Select-Object -First 20
    $packageMarker = 'package="' + $escapedPackage + '"'
    [ordered]@{
        # The top app-bar and bottom navigation both expose "主页". On some
        # Compose/Release builds the UI dump can omit one of those labels while
        # retaining the Home-only concentration card, so use its stable ASCII
        # unit token as the primary Home assertion. The script is also run by
        # Windows PowerShell, whose legacy source encoding can corrupt Chinese
        # literals in this UTF-8 file.
        homeVisible = [bool](@($markers | Where-Object { $_ -match 'pg/mL' }).Count)
        packageVisible = [bool]($uiText -match $packageMarker)
        visibleTextMarkers = @($markers)
    }
}

function Get-ProcessCpuSnapshot {
    param([string]$ProcessId)

    if (-not $ProcessId) {
        return $null
    }

    $statText = (Invoke-TaskAdbText @('shell', 'cat', "/proc/$ProcessId/stat")).Trim()
    $statMatch = [regex]::Match($statText, '^\d+\s+\(.*\)\s+(.+)$')
    if (-not $statMatch.Success) {
        return $null
    }

    $fields = $statMatch.Groups[1].Value -split '\s+'
    if ($fields.Count -lt 13) {
        return $null
    }

    [pscustomobject]@{
        pid = $ProcessId
        userTicks = [long]$fields[11]
        systemTicks = [long]$fields[12]
        totalTicks = [long]$fields[11] + [long]$fields[12]
    }
}

function Get-ClockTicksPerSecond {
    $clockText = (Invoke-TaskAdbText @('shell', 'getconf', 'CLK_TCK')).Trim()
    if ($clockText -match '^\d+$') {
        return [int]$clockText
    }
    return 100
}

$deviceState = (& adb devices | Select-String -Pattern "^$([regex]::Escape($Serial))\s+device$").Line
if (-not $deviceState) {
    throw "Target device '$Serial' is not online."
}

$packageDump = Invoke-TaskAdbText @('shell', 'dumpsys', 'package', $Package)
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

$warmStartTimes = [System.Collections.Generic.List[int]]::new()
$warmStartTimeMetrics = [System.Collections.Generic.List[string]]::new()
$warmStartProcessStable = $true
$warmStartExpectedPid = $null
for ($run = 1; $run -le $WarmStartRuns; $run++) {
    $pidBeforeWarmStart = Get-TaskPid
    Invoke-TaskAdb @('shell', 'input', 'keyevent', '3') | Out-Null
    Start-Sleep -Milliseconds 300
    $launchText = (Invoke-TaskAdb @('shell', 'am', 'start', '-W', '-n', "$Package/$Activity")) -join "`n"
    $totalTime = Get-TaskLine -Text $launchText -Pattern '^TotalTime:\s*(\d+)'
    $waitTime = Get-TaskLine -Text $launchText -Pattern '^WaitTime:\s*(\d+)'
    if ($waitTime) {
        $warmStartTimes.Add([int]$waitTime)
        $warmStartTimeMetrics.Add('WaitTime')
    } elseif ($totalTime) {
        $warmStartTimes.Add([int]$totalTime)
        $warmStartTimeMetrics.Add('TotalTime')
    }
    $pidAfterWarmStart = Get-TaskPid
    if (-not $warmStartExpectedPid) {
        $warmStartExpectedPid = $pidBeforeWarmStart
    }
    if (-not $pidBeforeWarmStart -or -not $pidAfterWarmStart -or
        $pidBeforeWarmStart -ne $pidAfterWarmStart -or
        $pidBeforeWarmStart -ne $warmStartExpectedPid -or
        $pidAfterWarmStart -ne $warmStartExpectedPid) {
        $warmStartProcessStable = $false
    }
    Invoke-TaskAdb @('shell', 'input', 'keyevent', '3') | Out-Null
    Start-Sleep -Milliseconds 300
}

$sortedWarmStartTimes = @($warmStartTimes | Sort-Object)
$warmMedianMs = if ($sortedWarmStartTimes.Count -eq 0) {
    $null
} elseif ($sortedWarmStartTimes.Count % 2 -eq 0) {
    ($sortedWarmStartTimes[$sortedWarmStartTimes.Count / 2 - 1] +
        $sortedWarmStartTimes[$sortedWarmStartTimes.Count / 2]) / 2
} else {
    $sortedWarmStartTimes[[math]::Floor($sortedWarmStartTimes.Count / 2)]
}
$warmAverageMs = if ($warmStartTimes.Count -gt 0) {
    [math]::Round(($warmStartTimes | Measure-Object -Average).Average, 1)
} else {
    $null
}

$dataState = [ordered]@{
    mode = 'PRESERVED'
    packageCleared = $false
    packageUninstalled = $false
    mutations = @('force-stop', 'start-activity', 'home', 'wake-screen')
    note = 'The harness does not clear or uninstall application data.'
}

$clockTicksPerSecond = Get-ClockTicksPerSecond
$captureError = $null
$captureStatus = 'INVALID_NOT_CAPTURED'
$startState = $null
$endState = $null
$startScreenState = $null
$endScreenState = $null
$taskPidBefore = $null
$taskPidAfter = $null
$cpuBefore = $null
$cpuAfter = $null
$cpuDeltaTicks = $null
$cpuPercent = $null
$elapsedSeconds = $null
$windowStartedAtUtc = $null
$windowEndedAtUtc = $null
$gfxText = ''
$frameCount = 0
$jankyFrames = 0
$frameP50 = $null
$frameP90 = $null
$frameP95 = $null
$frameP99 = $null
$compositionLogText = ''
$compositionLogLines = 0
$compositionCounts = [ordered]@{
    HomeScreenContent = $null
    ConcentrationChart = $null
}

Invoke-TaskAdb @('shell', 'svc', 'power', 'stayon', 'true') | Out-Null
try {
    Invoke-TaskAdb @('shell', 'input', 'keyevent', '224') | Out-Null
    Invoke-TaskAdb @('shell', 'wm', 'dismiss-keyguard') | Out-Null
    Invoke-TaskAdb @('logcat', '-c') | Out-Null
    Invoke-TaskAdb @('shell', 'am', 'force-stop', $Package) | Out-Null
    Invoke-TaskAdb @('shell', 'am', 'start', '-W', '-n', "$Package/$Activity") | Out-Null
    Start-Sleep -Seconds 2

    $warmState = Get-ForegroundState
    if (-not ($warmState.activityResumed -and $warmState.windowFocused)) {
        throw 'The application was not both resumed and focused during prewarm.'
    }

    Invoke-TaskAdb @('shell', 'dumpsys', 'gfxinfo', $Package, 'reset') | Out-Null
    $startState = Get-ForegroundState
    $startScreenState = Get-UiScreenState
    $taskPidBefore = Get-TaskPid
    $cpuBefore = Get-ProcessCpuSnapshot -ProcessId $taskPidBefore
    $windowStartedAtUtc = (Get-Date).ToUniversalTime()
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    Start-Sleep -Seconds $IdleSeconds
    $stopwatch.Stop()
    $elapsedSeconds = $stopwatch.Elapsed.TotalSeconds
    $windowEndedAtUtc = (Get-Date).ToUniversalTime()
    $endState = Get-ForegroundState
    $endScreenState = Get-UiScreenState
    $taskPidAfter = Get-TaskPid
    $cpuAfter = Get-ProcessCpuSnapshot -ProcessId $taskPidAfter
    $gfxText = Invoke-TaskAdbText @('shell', 'dumpsys', 'gfxinfo', $Package)
    $compositionLogText = Invoke-TaskAdbText @('logcat', '-d', '-s', 'EvoluneCompose:I', '*:S')

    foreach ($line in ($compositionLogText -split "`n")) {
        $compositionMatch = [regex]::Match($line, 'surface=([^\s]+)\s+count=(\d+)')
        if (-not $compositionMatch.Success) {
            continue
        }
        $compositionLogLines++
        $surface = $compositionMatch.Groups[1].Value
        if ($compositionCounts.Contains($surface)) {
            $compositionCounts[$surface] = [long]$compositionMatch.Groups[2].Value
        }
    }

    $frameValue = Get-TaskLine -Text $gfxText -Pattern '^\s*Total frames rendered:\s*(\d+)'
    $jankyValue = Get-TaskLine -Text $gfxText -Pattern '^\s*Janky frames:\s*(\d+)'
    $frameCount = if ($frameValue) { [int]$frameValue } else { 0 }
    $jankyFrames = if ($jankyValue) { [int]$jankyValue } else { 0 }
    $frameP50 = Get-TaskLine -Text $gfxText -Pattern '^\s*50th percentile:\s*([\d.]+)ms'
    $frameP90 = Get-TaskLine -Text $gfxText -Pattern '^\s*90th percentile:\s*([\d.]+)ms'
    $frameP95 = Get-TaskLine -Text $gfxText -Pattern '^\s*95th percentile:\s*([\d.]+)ms'
    $frameP99 = Get-TaskLine -Text $gfxText -Pattern '^\s*99th percentile:\s*([\d.]+)ms'
    if ($frameCount -le 0) {
        $frameP50 = $null
        $frameP90 = $null
        $frameP95 = $null
        $frameP99 = $null
    }

    if ($cpuBefore -and $cpuAfter -and $taskPidBefore -eq $taskPidAfter -and $elapsedSeconds -gt 0) {
        $cpuDeltaTicks = $cpuAfter.totalTicks - $cpuBefore.totalTicks
        $cpuPercent = [math]::Round((100.0 * $cpuDeltaTicks / $clockTicksPerSecond) / $elapsedSeconds, 2)
    }

    $foregroundValid = $startState.activityResumed -and $startState.windowFocused -and
        $endState.activityResumed -and $endState.windowFocused
    $homeValid = $startScreenState.homeVisible -and $endScreenState.homeVisible
    if (-not $foregroundValid) {
        $captureStatus = 'INVALID_FOREGROUND_NOT_CONFIRMED'
    } elseif (-not $homeValid) {
        $captureStatus = 'INVALID_HOME_NOT_CONFIRMED'
    } elseif ($frameCount -le 0) {
        $captureStatus = 'INVALID_NO_FRAME_SAMPLES'
    } elseif (-not $cpuBefore -or -not $cpuAfter) {
        $captureStatus = 'INVALID_CPU_WINDOW_UNAVAILABLE'
    } elseif ($taskPidBefore -ne $taskPidAfter) {
        $captureStatus = 'INVALID_PROCESS_RESTARTED'
    } else {
        $captureStatus = 'VALID'
    }
} catch {
    $captureError = $_.Exception.Message
    $captureStatus = 'INVALID_CAPTURE_ERROR'
} finally {
    Invoke-TaskAdb @('shell', 'svc', 'power', 'stayon', 'false') | Out-Null
}

$repositoryRoot = (& git rev-parse --show-toplevel).Trim()
$branchText = (& git branch --show-current)
$branch = if ($branchText) { $branchText.Trim() } else { '(detached)' }
$head = (& git rev-parse HEAD).Trim()
$worktreeStatus = @(& git status --porcelain)
$worktreeDirty = $worktreeStatus.Count -gt 0

[ordered]@{
    schema = 'evolune.v15b.baseline.v2'
    measuredAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    repository = $repositoryRoot
    branch = $branch
    head = $head
    worktreeDirty = $worktreeDirty
    device = $Serial
    package = $Package
    versionName = $versionName
    versionCode = $versionCode
    scenario = $Scenario
    dataState = $dataState
    capture = [ordered]@{
        status = $captureStatus
        error = $captureError
        windowStartedAtUtc = $windowStartedAtUtc
        windowEndedAtUtc = $windowEndedAtUtc
        durationSeconds = if ($elapsedSeconds) { [math]::Round($elapsedSeconds, 3) } else { $null }
        startForeground = $startState
        endForeground = $endState
        startScreen = $startScreenState
        endScreen = $endScreenState
    }
    coldStart = [ordered]@{
        requestedRuns = $ColdStartRuns
        completedRuns = $coldStartTimes.Count
        timesMs = @($coldStartTimes)
        medianMs = $medianMs
        averageMs = $averageMs
        minMs = if ($sortedTimes.Count -gt 0) { $sortedTimes[0] } else { $null }
        maxMs = if ($sortedTimes.Count -gt 0) { $sortedTimes[$sortedTimes.Count - 1] } else { $null }
    }
    warmStart = [ordered]@{
        requestedRuns = $WarmStartRuns
        completedRuns = $warmStartTimes.Count
        timesMs = @($warmStartTimes)
        medianMs = $warmMedianMs
        averageMs = $warmAverageMs
        minMs = if ($sortedWarmStartTimes.Count -gt 0) { $sortedWarmStartTimes[0] } else { $null }
        maxMs = if ($sortedWarmStartTimes.Count -gt 0) {
            $sortedWarmStartTimes[$sortedWarmStartTimes.Count - 1]
        } else { $null }
        timeMetrics = @($warmStartTimeMetrics | Sort-Object -Unique)
        processStable = $warmStartProcessStable
        status = if ($WarmStartRuns -eq 0) {
            'NOT_REQUESTED'
        } elseif ($warmStartTimes.Count -ne $WarmStartRuns) {
            'INVALID_INCOMPLETE_SAMPLES'
        } elseif (-not $warmStartProcessStable) {
            'INVALID_PROCESS_RESTARTED'
        } else {
            'VALID'
        }
    }
    idleFrameObservation = [ordered]@{
        durationSeconds = $IdleSeconds
        totalFramesRendered = [int]$frameCount
        jankyFrames = [int]$jankyFrames
        frameTimeMs = [ordered]@{
            p50 = if ($frameP50) { [double]$frameP50 } else { $null }
            p90 = if ($frameP90) { [double]$frameP90 } else { $null }
            p95 = if ($frameP95) { [double]$frameP95 } else { $null }
            p99 = if ($frameP99) { [double]$frameP99 } else { $null }
        }
        cpuWindow = [ordered]@{
            pidBefore = $taskPidBefore
            pidAfter = $taskPidAfter
            clockTicksPerSecond = $clockTicksPerSecond
            processTicksBefore = if ($cpuBefore) { $cpuBefore.totalTicks } else { $null }
            processTicksAfter = if ($cpuAfter) { $cpuAfter.totalTicks } else { $null }
            deltaTicks = $cpuDeltaTicks
            averageProcessCpuPercent = $cpuPercent
        }
        status = $captureStatus
    }
    compositionObservation = [ordered]@{
        status = if ($compositionLogLines -gt 0) { 'VALID_DEBUG_LOG' } else { 'NOT_AVAILABLE' }
        logTag = 'EvoluneCompose'
        logLines = $compositionLogLines
        successfulCompositionCounts = $compositionCounts
        note = 'Debug-only SideEffect counters; absent from Release builds and not a frame-time measurement.'
    }
} | ConvertTo-Json -Depth 6
