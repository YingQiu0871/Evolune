param(
    [string]$RunsDir = "C:\Users\1\AppData\Local\Temp\opencode\d09-runs",
    [string]$OutDir = "C:\Users\1\AppData\Local\Temp\opencode\d09-analysis"
)
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null

function Parse-Loads($path) {
    $loads = New-Object System.Collections.ArrayList
    $cur = $null
    foreach ($raw in Get-Content $path) {
        $line = $raw -replace '^.*D09Phase\(\s*\d+\): ', ''
        if ($line -match '^P-1 vm-load-start t=(\d+)') {
            $cur = @{ Start = [long]$Matches[1]; Phases = @{}; Counts = @{}; Thread = "" }
            continue
        }
        if ($null -eq $cur) { continue }
        if ($line -match '^P0 range-enter t=(\d+) thread=(\S+)') {
            $cur.Phases["P0"] = [long]$Matches[1]
            $cur.Thread = $Matches[2]
        }
        elseif ($line -match '^P1 plans-query-start t=(\d+)') { $cur.Phases["P1s"] = [long]$Matches[1] }
        elseif ($line -match '^P1 plans-query-end t=(\d+) cpu=(\d+) plans=(\d+)') {
            $cur.Phases["P1e"] = [long]$Matches[1]; $cur.Phases["P1cpu"] = [long]$Matches[2]; $cur.Counts["plans"] = [int]$Matches[3]
        }
        elseif ($line -match '^P2 generation-start t=(\d+)') { $cur.Phases["P2s"] = [long]$Matches[1] }
        elseif ($line -match '^P2 generation-end t=(\d+) cpu=(\d+) occurrences=(\d+)') {
            $cur.Phases["P2e"] = [long]$Matches[1]; $cur.Phases["P2cpu"] = [long]$Matches[2]; $cur.Counts["occurrences"] = [int]$Matches[3]
        }
        elseif ($line -match '^P3 channelA-start t=(\d+)') { $cur.Phases["P3s"] = [long]$Matches[1] }
        elseif ($line -match '^P3 channelA-end t=(\d+) cpu=(\d+) rows=(\d+)') {
            $cur.Phases["P3e"] = [long]$Matches[1]; $cur.Phases["P3cpu"] = [long]$Matches[2]; $cur.Counts["chanA"] = [int]$Matches[3]
        }
        elseif ($line -match '^P4 channelB-start t=(\d+)') { $cur.Phases["P4s"] = [long]$Matches[1] }
        elseif ($line -match '^P4 channelB-end t=(\d+) cpu=(\d+) rows=(\d+)') {
            $cur.Phases["P4e"] = [long]$Matches[1]; $cur.Phases["P4cpu"] = [long]$Matches[2]; $cur.Counts["chanB"] = [int]$Matches[3]
        }
        elseif ($line -match '^P5 union-start t=(\d+)') { $cur.Phases["P5s"] = [long]$Matches[1] }
        elseif ($line -match '^P5 union-end t=(\d+) cpu=(\d+) union=(\d+)') {
            $cur.Phases["P5e"] = [long]$Matches[1]; $cur.Phases["P5cpu"] = [long]$Matches[2]; $cur.Counts["union"] = [int]$Matches[3]
        }
        elseif ($line -match '^P6 projection-start t=(\d+)') { $cur.Phases["P6s"] = [long]$Matches[1] }
        elseif ($line -match '^P6 projection-end t=(\d+) cpu=(\d+) entries=(\d+)') {
            $cur.Phases["P6e"] = [long]$Matches[1]; $cur.Phases["P6cpu"] = [long]$Matches[2]; $cur.Counts["entries"] = [int]$Matches[3]
        }
        elseif ($line -match '^P7 grouping-start t=(\d+)') { $cur.Phases["P7s"] = [long]$Matches[1] }
        elseif ($line -match '^P7 grouping-end t=(\d+) cpu=(\d+) days=(\d+)') {
            $cur.Phases["P7e"] = [long]$Matches[1]; $cur.Phases["P7cpu"] = [long]$Matches[2]; $cur.Counts["days"] = [int]$Matches[3]
        }
        elseif ($line -match '^P8 range-exit t=(\d+)') { $cur.Phases["P8"] = [long]$Matches[1] }
        elseif ($line -match '^P9 vm-publish t=(\d+) thread=(\S+) days=(\d+)') {
            $cur.Phases["P9"] = [long]$Matches[1]
            [void]$loads.Add($cur)
            $cur = $null
        }
    }
    return $loads
}

$scenarios = @(
    @{ Name = "first";   File = "first-100-p3";     Events = 100;   Plans = 3;  Take = "first" },
    @{ Name = "first";   File = "first-1000-p3";    Events = 1000;  Plans = 3;  Take = "first" },
    @{ Name = "first";   File = "first-1000-p1";    Events = 1000;  Plans = 1;  Take = "first" },
    @{ Name = "first";   File = "first-1000-p8";    Events = 1000;  Plans = 8;  Take = "first" },
    @{ Name = "month";   File = "month-100-p3";     Events = 100;   Plans = 3;  Take = "second" },
    @{ Name = "month";   File = "month-1000-p3";    Events = 1000;  Plans = 3;  Take = "second" },
    @{ Name = "reentry"; File = "reentry-100-p3";   Events = 100;   Plans = 3;  Take = "second" },
    @{ Name = "reentry"; File = "reentry-1000-p3";  Events = 1000;  Plans = 3;  Take = "second" },
    @{ Name = "first";   File = "first-10000-p3";   Events = 10000; Plans = 3;  Take = "first" }
)

$csv = New-Object System.Collections.ArrayList
[void]$csv.Add("scenario,events,plans,rep,plans_query_wall_ms,generation_wall_ms,chanA_wall_ms,chanB_wall_ms,union_wall_ms,projection_wall_ms,grouping_wall_ms,readrange_wall_ms,vmload_wall_ms,generation_cpu_ms,chanA_cpu_ms,chanB_cpu_ms,union_cpu_ms,projection_cpu_ms,grouping_cpu_ms,occurrences,chanA_rows,chanB_rows,union_events,entries,days,main_contiguous_ms")

$stats = @{}
foreach ($s in $scenarios) {
    $path = Join-Path $RunsDir ($s.File + ".txt")
    if (-not (Test-Path $path)) { Write-Host "MISSING $path"; continue }
    $loads = Parse-Loads $path
    $measured = @()
    if ($s.Take -eq "first") {
        $measured = $loads
    } else {
        for ($i = 1; $i -lt $loads.Count; $i += 2) { $measured += $loads[$i] }
    }
    $rep = 0
    $key = "$($s.Name)|$($s.Events)|$($s.Plans)"
    $stats[$key] = @{}
    foreach ($l in $measured) {
        $p = $l.Phases
        $ms = { param($a, $b) ([Math]::Round(($b - $a) / 1e6, 3)) }
        $p1 = & $ms $p["P1s"] $p["P1e"]
        $p2 = & $ms $p["P2s"] $p["P2e"]
        $p3 = & $ms $p["P3s"] $p["P3e"]
        $p4 = & $ms $p["P4s"] $p["P4e"]
        $p5 = & $ms $p["P5s"] $p["P5e"]
        $p6 = & $ms $p["P6s"] $p["P6e"]
        $p7 = & $ms $p["P7s"] $p["P7e"]
        $rr = & $ms $p["P0"] $p["P8"]
        $vl = & $ms $l.Start $p["P9"]
        $c2 = [Math]::Round(($p["P2cpu"] - $p["P1cpu"]) / 1e6, 3)
        $c3 = [Math]::Round(($p["P3cpu"] - $p["P2cpu"]) / 1e6, 3)
        $c4 = [Math]::Round(($p["P4cpu"] - $p["P3cpu"]) / 1e6, 3)
        $c5 = [Math]::Round(($p["P5cpu"] - $p["P4cpu"]) / 1e6, 3)
        $c6 = [Math]::Round(($p["P6cpu"] - $p["P5cpu"]) / 1e6, 3)
        $c7 = [Math]::Round(($p["P7cpu"] - $p["P6cpu"]) / 1e6, 3)
        $contig = [Math]::Round(($p["P7e"] - $p["P5s"]) / 1e6, 3)
        [void]$csv.Add("$($s.Name),$($s.Events),$($s.Plans),$rep,$p1,$p2,$p3,$p4,$p5,$p6,$p7,$rr,$vl,$c2,$c3,$c4,$c5,$c6,$c7,$($l.Counts["occurrences"]),$($l.Counts["chanA"]),$($l.Counts["chanB"]),$($l.Counts["union"]),$($l.Counts["entries"]),$($l.Counts["days"]),$contig")
        foreach ($kv in @{ p1 = $p1; p2 = $p2; p3 = $p3; p4 = $p4; p5 = $p5; p6 = $p6; p7 = $p7; rr = $rr; vl = $vl; c2 = $c2; c3 = $c3; c4 = $c4; c5 = $c5; c6 = $c6; c7 = $c7; contig = $contig }.GetEnumerator()) {
            if (-not $stats[$key].ContainsKey($kv.Key)) { $stats[$key][$kv.Key] = New-Object System.Collections.ArrayList }
            [void]$stats[$key][$kv.Key].Add([double]$kv.Value)
        }
        $rep++
    }
    Write-Host "$($s.File): measured loads=$($measured.Count)"
}

[System.IO.File]::WriteAllText((Join-Path $OutDir "raw-history-profile.csv"), (($csv -join "`n") + "`n"), (New-Object System.Text.UTF8Encoding($false)))

$lines = New-Object System.Collections.ArrayList
[void]$lines.Add("D-09 phase statistics (ms). n = measured loads per scenario.")
[void]$lines.Add("Fields: scenario|events|plans -> phase: min / mean / median / p95 / max")
foreach ($key in ($stats.Keys | Sort-Object)) {
    [void]$lines.Add("")
    [void]$lines.Add("== $key ==")
    foreach ($phase in @("p1", "p2", "p3", "p4", "p5", "p6", "p7", "rr", "vl", "c2", "c3", "c4", "c5", "c6", "c7", "contig")) {
        if (-not $stats[$key].ContainsKey($phase)) { continue }
        $a = $stats[$key][$phase] | Sort-Object
        $n = $a.Count
        $mean = [Math]::Round(($a | Measure-Object -Average).Average, 3)
        $median = if ($n % 2 -eq 1) { $a[[int]($n / 2)] } else { [Math]::Round(($a[$n / 2 - 1] + $a[$n / 2]) / 2, 3) }
        $p95 = $a[[int][Math]::Min($n - 1, [Math]::Ceiling(0.95 * $n) - 1)]
        [void]$lines.Add(("{0}: {1} / {2} / {3} / {4} / {5}" -f $phase, $a[0], $mean, $median, $p95, $a[$n - 1]))
    }
}
[System.IO.File]::WriteAllText((Join-Path $OutDir "phase-statistics.txt"), (($lines -join "`n") + "`n"), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "written: raw-history-profile.csv, phase-statistics.txt"
