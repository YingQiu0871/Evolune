[CmdletBinding()]
param(
    [string]$Serial = 'emulator-5558',
    [string]$Package = 'io.github.yingqiu0871.evolune',
    [string]$Activity = '.MainActivity',
    [ValidateRange(10, 300)]
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
    [ordered]@{
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

function Get-BackgroundSnapshot {
    $snapshotPid = Get-TaskPid
    $powerText = Invoke-TaskAdbText @('shell', 'dumpsys', 'power')
    $displayText = Invoke-TaskAdbText @('shell', 'dumpsys', 'display')
    $alarmText = Invoke-TaskAdbText @('shell', 'dumpsys', 'alarm')
    $jobsText = Invoke-TaskAdbText @('shell', 'dumpsys', 'jobscheduler')
    $batteryText = Invoke-TaskAdbText @('shell', 'dumpsys', 'batterystats', '--charged')
    $escapedPackage = [regex]::Escape($Package)
    $powerMarker = ($powerText -split "`n" |
        Where-Object { $_ -match 'mWakefulness=|Display Power: state=' } |
        Select-Object -First 4) -join ' | '
    $packageAlarmLines = @($alarmText -split "`n" | Where-Object { $_ -match $escapedPackage })
    $packageJobLines = @($jobsText -split "`n" | Where-Object { $_ -match $escapedPackage })
    $packageBatteryLines = @($batteryText -split "`n" | Where-Object { $_ -match $escapedPackage })
    $wakeLockLines = @($powerText -split "`n" |
        Where-Object { $_ -match 'Wake Locks' -or $_ -match $escapedPackage })
    $displayStateLine = ($displayText -split "`n" |
        Where-Object { $_ -match '^\s*Display State=' -or
            $_ -match '\bstate (?:OFF|DOZE|DOZE_SUSPEND)\b' } |
        Select-Object -First 1)
    $displayScreenOff = [bool]($displayText -match
        '(?m)^\s*Display State=(?:OFF|DOZE|DOZE_SUSPEND)\b' -or
        $displayText -match '\bstate (?:OFF|DOZE|DOZE_SUSPEND)\b')
    [ordered]@{
        capturedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
        pid = $snapshotPid
        processCpu = Get-ProcessCpuSnapshot -ProcessId $snapshotPid
        powerMarkers = $powerMarker
        displayScreenOff = $displayScreenOff
        displayStateLine = if ($displayStateLine) { $displayStateLine.Trim() } else { $null }
        alarmPackageLineCount = $packageAlarmLines.Count
        alarmPackageLines = @($packageAlarmLines | Select-Object -First 20 | ForEach-Object { $_.Trim() })
        jobPackageLineCount = $packageJobLines.Count
        jobPackageLines = @($packageJobLines | Select-Object -First 20 | ForEach-Object { $_.Trim() })
        wakeLockPackageLineCount = $wakeLockLines.Count
        wakeLockPackageLines = @($wakeLockLines | Select-Object -First 20 | ForEach-Object { $_.Trim() })
        batteryPackageLineCount = $packageBatteryLines.Count
        batteryPackageLines = @($packageBatteryLines | Select-Object -First 20 | ForEach-Object { $_.Trim() })
    }
}

$deviceState = (& adb devices | Select-String -Pattern "^$([regex]::Escape($Serial))\s+device$").Line
if (-not $deviceState) {
    throw "Target device '$Serial' is not online."
}

$packageDump = Invoke-TaskAdbText @('shell', 'dumpsys', 'package', $Package)
if ($packageDump -notmatch 'versionName=') {
    throw "Package '$Package' is not installed on '$Serial'."
}
$escapedPackage = [regex]::Escape($Package)

$clockTicksPerSecond = Get-ClockTicksPerSecond
$start = $null
$end = $null
$logLines = @()
$windowStartedAtUtc = $null
$windowEndedAtUtc = $null
$elapsedSeconds = $null
$captureError = $null
$captureStatus = 'INVALID_NOT_CAPTURED'

try {
    Invoke-TaskAdb @('shell', 'svc', 'power', 'stayon', 'false') | Out-Null
    Invoke-TaskAdb @('shell', 'input', 'keyevent', '224') | Out-Null
    Invoke-TaskAdb @('shell', 'wm', 'dismiss-keyguard') | Out-Null
    Invoke-TaskAdb @('shell', 'am', 'force-stop', $Package) | Out-Null
    Invoke-TaskAdb @('shell', 'am', 'start', '-W', '-n', "$Package/$Activity") | Out-Null
    Start-Sleep -Seconds 3
    Invoke-TaskAdb @('shell', 'input', 'keyevent', '3') | Out-Null
    Invoke-TaskAdb @('shell', 'input', 'keyevent', '223') | Out-Null
    Start-Sleep -Seconds 2
    Invoke-TaskAdb @('logcat', '-c') | Out-Null

    $start = Get-BackgroundSnapshot
    $windowStartedAtUtc = (Get-Date).ToUniversalTime()
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    Start-Sleep -Seconds $IdleSeconds
    $stopwatch.Stop()
    $elapsedSeconds = $stopwatch.Elapsed.TotalSeconds
    $windowEndedAtUtc = (Get-Date).ToUniversalTime()
    $end = Get-BackgroundSnapshot
    $logText = Invoke-TaskAdbText @('logcat', '-d', '-v', 'brief', '*:I')
    $logLines = @($logText -split "`n" |
        Where-Object { $_ -match $escapedPackage } |
        ForEach-Object { $_.Trim() })

    $screenOffConfirmed = $start.displayScreenOff -and $end.displayScreenOff
    if (-not $screenOffConfirmed) {
        $captureStatus = 'INVALID_SCREEN_NOT_OFF'
    } elseif ($elapsedSeconds -lt ($IdleSeconds - 2)) {
        $captureStatus = 'INVALID_DURATION'
    } else {
        $captureStatus = 'VALID'
    }
} catch {
    $captureError = $_.Exception.Message
    $captureStatus = 'INVALID_CAPTURE_ERROR'
} finally {
    Invoke-TaskAdb @('shell', 'input', 'keyevent', '224') | Out-Null
    Invoke-TaskAdb @('shell', 'svc', 'power', 'stayon', 'false') | Out-Null
}

$repositoryRoot = (& git rev-parse --show-toplevel).Trim()
$branch = (& git branch --show-current).Trim()
$head = (& git rev-parse HEAD).Trim()
$worktreeStatus = @(& git status --porcelain)
$worktreeDirty = $worktreeStatus.Count -gt 0
$cpuDeltaTicks = $null
$cpuPercent = $null
if ($start.processCpu -and $end.processCpu -and $start.pid -eq $end.pid -and $elapsedSeconds -gt 0) {
    $cpuDeltaTicks = $end.processCpu.totalTicks - $start.processCpu.totalTicks
    $cpuPercent = [math]::Round(
        (100.0 * $cpuDeltaTicks / $clockTicksPerSecond) / $elapsedSeconds,
        2
    )
}

[ordered]@{
    schema = 'evolune.v15c.background.v1'
    measuredAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    repository = $repositoryRoot
    branch = $branch
    head = $head
    worktreeDirty = $worktreeDirty
    device = $Serial
    package = $Package
    scenario = 'screen-off-background-idle'
    capture = [ordered]@{
        status = $captureStatus
        error = $captureError
        requestedIdleSeconds = $IdleSeconds
        durationSeconds = if ($elapsedSeconds) { [math]::Round($elapsedSeconds, 3) } else { $null }
        windowStartedAtUtc = $windowStartedAtUtc
        windowEndedAtUtc = $windowEndedAtUtc
        start = $start
        end = $end
    }
    processCpuWindow = [ordered]@{
        pidBefore = if ($start) { $start.pid } else { $null }
        pidAfter = if ($end) { $end.pid } else { $null }
        clockTicksPerSecond = $clockTicksPerSecond
        deltaTicks = $cpuDeltaTicks
        averageProcessCpuPercent = $cpuPercent
    }
    packageLog = [ordered]@{
        matchingLineCount = $logLines.Count
        lines = @($logLines | Select-Object -First 50)
    }
    note = 'Diagnostic observation only; no battery threshold or optimization claim is implied.'
} | ConvertTo-Json -Depth 8
