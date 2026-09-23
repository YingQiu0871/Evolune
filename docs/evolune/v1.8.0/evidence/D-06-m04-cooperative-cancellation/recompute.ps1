param(
    [string]$EvidenceRoot = $PSScriptRoot
)

$csvPath = Join-Path $EvidenceRoot 'measurements/d06-m04-results.csv'
$outPath = Join-Path $EvidenceRoot 'measurements/recomputed-statistics.txt'
$rows = @(Import-Csv -LiteralPath $csvPath)

function Get-Percentile([double[]]$Values, [double]$Probability) {
    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 0) { return 0.0 }
    $rank = [math]::Ceiling($Probability * $sorted.Count)
    return [double]$sorted[[math]::Max(0, $rank - 1)]
}

function Format-Stats([object[]]$Group, [string]$Property) {
    $values = @($Group | ForEach-Object { [double]($_.$Property) })
    $measure = $values | Measure-Object -Minimum -Maximum -Average
    [pscustomobject]@{
        Metric = $Property
        N = $values.Count
        Min = $measure.Minimum
        Max = $measure.Maximum
        Mean = $measure.Average
        Median = Get-Percentile $values 0.50
        P95 = Get-Percentile $values 0.95
    }
}

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add('D-06 INDEPENDENTLY RECOMPUTED DEVICE STATISTICS')
$lines.Add('===============================================')
$lines.Add('Source: measurements/d06-m04-results.csv')
$lines.Add('Percentile method: nearest-rank, ceil(p*N), one-based index.')
$lines.Add('All latency values are milliseconds.')
$lines.Add('')

foreach ($groupName in @('A', 'C')) {
    $group = @($rows | Where-Object { $_.group -eq $groupName })
    $lines.Add("GROUP $groupName - n=$($group.Count), valid=$(@($group | Where-Object valid -eq 'true').Count)")
    $lines.Add('metric,n,min,max,mean,median,p95')
    foreach ($property in @('finalStartMs', 'finalRuntimeMs', 'finalPublicationLatencyMs', 'obsoleteDrainMs')) {
        $stats = Format-Stats $group $property
        $lines.Add(('{0},{1},{2:F4},{3:F4},{4:F4},{5:F4},{6:F4}' -f $stats.Metric,$stats.N,$stats.Min,$stats.Max,$stats.Mean,$stats.Median,$stats.P95))
    }
    $lines.Add(('peakConcurrency max={0}; engineOnMain={1}; obsoleteCancelled min={2}; obsoleteCompleted max={3}' -f
        (($group | Measure-Object peakConcurrency -Maximum).Maximum),
        (@($group | Where-Object engineOnMain -eq 'True').Count),
        (($group | Measure-Object obsoleteCancelled -Minimum).Minimum),
        (($group | Measure-Object obsoleteCompleted -Maximum).Maximum)))
    $lines.Add('')
}

$c = @($rows | Where-Object { $_.group -eq 'C' })
$baseline = 704.005
$cMean = ($c | Measure-Object finalStartMs -Average).Average
$improvement = (1.0 - ($cMean / $baseline)) * 100.0
$lines.Add(('C successor-start improvement versus D-05 baseline 704.005 ms: {0:F4}% (mean {1:F4} ms).' -f $improvement,$cMean))
$lines.Add('D-05 80% ceiling: 140.801 ms.')
$lines.Add(('C mean passes ceiling: {0}.' -f ($cMean -le 140.801)))

[System.IO.File]::WriteAllText($outPath, ($lines -join [Environment]::NewLine) + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
