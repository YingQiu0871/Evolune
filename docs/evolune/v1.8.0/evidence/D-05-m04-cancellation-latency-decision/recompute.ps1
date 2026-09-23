param(
    [string]$Csv = "$PSScriptRoot/raw/d05-m04-results.csv"
)

$rows = Import-Csv -LiteralPath $Csv | ForEach-Object {
    [pscustomobject]@{
        group = $_.group
        point = $_.point
        start = [double]$_.finalStartNs / 1e6
        runtime = [double]$_.finalRuntimeNs / 1e6
        publication = [double]$_.finalPublicationLatencyNs / 1e6
        drain = [double]$_.obsoleteDrainNs / 1e6
        peak = [int]$_.peakConcurrency
    }
}

function Get-Stats($Items, $Property) {
    $values = @($Items | ForEach-Object { [double]$_.$Property } | Sort-Object)
    $n = $values.Count
    $mean = ($values | Measure-Object -Average).Average
    $median = if ($n % 2) { $values[[int]($n / 2)] } else { ($values[$n / 2 - 1] + $values[$n / 2]) / 2 }
    $p95 = $values[[Math]::Min($n - 1, [Math]::Ceiling(0.95 * $n) - 1)]
    [pscustomobject]@{
        n = $n
        min = [Math]::Round($values[0], 3)
        max = [Math]::Round($values[-1], 3)
        mean = [Math]::Round($mean, 3)
        median = [Math]::Round($median, 3)
        p95 = [Math]::Round($p95, 3)
    }
}

foreach ($group in 'A', 'B', 'C', 'D') {
    $set = @($rows | Where-Object group -eq $group)
    foreach ($metric in 'start', 'runtime', 'publication', 'drain', 'peak') {
        [pscustomobject]@{ group = $group; metric = $metric; stats = (Get-Stats $set $metric | ConvertTo-Json -Compress) }
    }
}
