[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('empty', 'steady', 'dense')]
    [string]$Dataset,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [DateTimeOffset]$ReferenceUtc = [DateTimeOffset]::Parse('2026-09-04T12:00:00Z')
)

$ErrorActionPreference = 'Stop'

$events = [System.Collections.Generic.List[object]]::new()
$slotTimes = @('03:15', '09:00', '21:00')
$planCount = 0
$dayCount = 0

switch ($Dataset) {
    'empty' {
        $planCount = 0
        $dayCount = 0
    }
    'steady' {
        $planCount = 1
        $dayCount = 30
    }
    'dense' {
        $planCount = 3
        $dayCount = 90
    }
}

$sequence = 1
for ($planIndex = 0; $planIndex -lt $planCount; $planIndex++) {
    for ($dayIndex = 0; $dayIndex -lt $dayCount; $dayIndex++) {
        $date = $ReferenceUtc.UtcDateTime.Date.AddDays(-($dayCount - 1 - $dayIndex))
        foreach ($slotTime in $slotTimes) {
            $instant = $date.Add([TimeSpan]::Parse($slotTime))
            $timeH = ($instant - [DateTime]::UnixEpoch).TotalHours
            $eventId = '00000000-0000-4000-8000-{0:x12}' -f $sequence
            $events.Add([ordered]@{
                id = $eventId
                route = 'injection'
                ester = 'EV'
                timeH = $timeH
                doseMG = 2.0
                extras = [ordered]@{}
            })
            $sequence++
        }
    }
}

$document = [ordered]@{
    meta = [ordered]@{
        version = 1
        exportedAt = $ReferenceUtc.UtcDateTime.ToString('o')
    }
    weight = 62.0
    events = $events
    labResults = @()
    doseTemplates = @()
}

$parent = Split-Path -Parent $OutputPath
if ($parent -and -not (Test-Path -LiteralPath $parent)) {
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
}

$document | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding UTF8

[ordered]@{
    dataset = $Dataset
    referenceUtc = $ReferenceUtc.UtcDateTime.ToString('o')
    planCount = $planCount
    dayCount = $dayCount
    slotCountPerPlan = $slotTimes.Count
    eventCount = $events.Count
    outputPath = (Resolve-Path -LiteralPath $OutputPath).Path
} | ConvertTo-Json
