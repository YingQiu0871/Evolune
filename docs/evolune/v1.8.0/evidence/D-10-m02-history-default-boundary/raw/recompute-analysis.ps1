param(
    [string]$RunsDir = "C:\Users\1\AppData\Local\Temp\opencode\d10-runs",
    [string]$OutDir = "C:\Users\1\AppData\Local\Temp\opencode\d10-analysis"
)
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null

function Parse-Loads($path) {
    $loads = New-Object System.Collections.ArrayList
    $cur = $null
    foreach ($raw in Get-Content $path) {
        $line = $raw -replace '^.*D10Phase\(\s*\d+\): ', ''
        if ($line -match '^P-1 vm-load-start t=(\d+)') {
            $cur = @{ Start = [long]$Matches[1]; Ph = @{}; Threads = @{}; Counts = @{} } ; continue
        }
        if ($null -eq $cur) { continue }
        if ($line -match '^P0 range-enter t=(\d+) thread=(\S+)') { $cur.Ph["P0"] = [long]$Matches[1]; $cur.Threads["P0"] = $Matches[2] }
        elseif ($line -match '^P1 plans-end t=(\d+) cpu=(\d+) thread=(\S+)') { $cur.Ph["P1e"] = [long]$Matches[1]; $cur.Ph["P1cpu"] = [long]$Matches[2]; $cur.Threads["P1e"] = $Matches[3] }
        elseif ($line -match '^P2 generation-end t=(\d+) cpu=(\d+) thread=(\S+) occurrences=(\d+)') { $cur.Ph["P2e"] = [long]$Matches[1]; $cur.Ph["P2cpu"] = [long]$Matches[2]; $cur.Threads["P2e"] = $Matches[3]; $cur.Counts["occ"] = [int]$Matches[4] }
        elseif ($line -match '^P3 channelA-end t=(\d+) cpu=(\d+) thread=(\S+) rows=(\d+)') { $cur.Ph["P3e"] = [long]$Matches[1]; $cur.Ph["P3cpu"] = [long]$Matches[2]; $cur.Threads["P3e"] = $Matches[3]; $cur.Counts["chanA"] = [int]$Matches[4] }
        elseif ($line -match '^P4 channelB-end t=(\d+) cpu=(\d+) thread=(\S+) rows=(\d+)') { $cur.Ph["P4e"] = [long]$Matches[1]; $cur.Ph["P4cpu"] = [long]$Matches[2]; $cur.Threads["P4e"] = $Matches[3]; $cur.Counts["chanB"] = [int]$Matches[4] }
        elseif ($line -match '^P5 union-start t=(\d+) thread=(\S+) isMain=(\S+)') { $cur.Ph["P5s"] = [long]$Matches[1]; $cur.Threads["P5s"] = $Matches[2]; $cur.Threads["P5isMain"] = $Matches[3] }
        elseif ($line -match '^P5 union-end t=(\d+) cpu=(\d+) thread=(\S+) union=(\d+)') { $cur.Ph["P5e"] = [long]$Matches[1]; $cur.Ph["P5cpu"] = [long]$Matches[2]; $cur.Threads["P5e"] = $Matches[3]; $cur.Counts["union"] = [int]$Matches[4] }
        elseif ($line -match '^P6 projection-end t=(\d+) cpu=(\d+) thread=(\S+) entries=(\d+)') { $cur.Ph["P6e"] = [long]$Matches[1]; $cur.Ph["P6cpu"] = [long]$Matches[2]; $cur.Threads["P6e"] = $Matches[3]; $cur.Counts["entries"] = [int]$Matches[4] }
        elseif ($line -match '^P7 grouping-end t=(\d+) cpu=(\d+) thread=(\S+) days=(\d+)') { $cur.Ph["P7e"] = [long]$Matches[1]; $cur.Ph["P7cpu"] = [long]$Matches[2]; $cur.Threads["P7e"] = $Matches[3]; $cur.Counts["days"] = [int]$Matches[4] }
        elseif ($line -match '^P8 range-exit t=(\d+) thread=(\S+)') { $cur.Ph["P8"] = [long]$Matches[1]; $cur.Threads["P8"] = $Matches[2] }
        elseif ($line -match '^P9 vm-publish t=(\d+) thread=(\S+) days=(\d+)') {
            $cur.Ph["P9"] = [long]$Matches[1]; $cur.Threads["P9"] = $Matches[2]
            [void]$loads.Add($cur); $cur = $null
        }
    }
    return $loads
}

$scenarios = @(
    @{ Name = "first";   File = "first-1000-p3";   Events = 1000;  Plans = 3; Take = "first" },
    @{ Name = "month";   File = "month-1000-p3";   Events = 1000;  Plans = 3; Take = "second" },
    @{ Name = "reentry"; File = "reentry-1000-p3"; Events = 1000;  Plans = 3; Take = "second" },
    @{ Name = "first";   File = "first-1000-p8";   Events = 1000;  Plans = 8; Take = "first" },
    @{ Name = "first";   File = "first-100-p3";    Events = 100;   Plans = 3; Take = "first" },
    @{ Name = "first";   File = "first-10000-p3";  Events = 10000; Plans = 3; Take = "first" }
)

$csv = New-Object System.Collections.ArrayList
[void]$csv.Add("scenario,events,plans,rep,main_cpu_generation_mapping_ms,worker_projection_cpu_ms,worker_grouping_cpu_ms,worker_tail_wall_ms,vm_load_wall_ms,dispatch_gap_ms,publication_ms,pure_tail_thread,queens_tail_on_main")
foreach ($s in $scenarios) {
    $path = Join-Path $RunsDir ($s.File + ".txt")
    if (-not (Test-Path $path)) { Write-Host "MISSING $path"; continue }
    $loads = Parse-Loads $path
    $measured = if ($s.Take -eq "first") { $loads } else { @(for ($i = 1; $i -lt $loads.Count; $i += 2) { $loads[$i] }) }
    $rep = 0
    foreach ($l in $measured) {
        $p = $l.Ph
        $ms = { param($a, $b) ([Math]::Round(($b - $a) / 1e6, 3)) }
        $mainCpu = [Math]::Round(($p["P4cpu"] - $p["P1cpu"]) / 1e6, 3)
        $projCpu = [Math]::Round(($p["P6cpu"] - $p["P5cpu"]) / 1e6, 3)
        $groupCpu = [Math]::Round(($p["P7cpu"] - $p["P6cpu"]) / 1e6, 3)
        $tailWall = & $ms $p["P5s"] $p["P8"]
        $vmWall = & $ms $l.Start $p["P9"]
        $dispatch = & $ms $p["P4e"] $p["P5s"]
        $pub = & $ms $p["P8"] $p["P9"]
        $tailThreads = @("P5s", "P5e", "P6e", "P7e", "P8") | ForEach-Object { $l.Threads[$_] } | Sort-Object -Unique
        $tailOnMain = if ($tailThreads -contains "main") { "YES" } else { "NO" }
        $tailThread = ($tailThreads -join "|")
        $queriesOnMain = @("P1e", "P2e", "P3e", "P4e") | ForEach-Object { $l.Threads[$_] } | Sort-Object -Unique
        $qMain = if (($queriesOnMain | Where-Object { $_ -ne "main" }).Count -eq 0) { "NO" } else { "YES" }
        [void]$csv.Add("$($s.Name),$($s.Events),$($s.Plans),$rep,$mainCpu,$projCpu,$groupCpu,$tailWall,$vmWall,$dispatch,$pub,$tailThread,$tailOnMain")
        $rep++
    }
    Write-Host "$($s.File): measured=$($measured.Count)"
}
[System.IO.File]::WriteAllText((Join-Path $OutDir "raw-d10-profile.csv"), (($csv -join "`n") + "`n"), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "written raw-d10-profile.csv"
