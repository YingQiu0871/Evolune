[CmdletBinding()]
param(
    [ValidateSet('CreateAvd', 'Apply', 'Verify')]
    [string]$Action = 'Verify',
    [string]$AvdName = 'evolune-test-api35',
    [string]$Locale = 'zh-CN',
    [string]$Size = '1080x2400'
)

# Standard Evolune androidTest environment: API 35, zh-CN, 1080x2400 (Pixel 7 profile).
# See docs/evolune/TESTING.md.
#   -Action CreateAvd : create the standard AVD (needs cmdline-tools and the API 35 x86_64 system image)
#   -Action Apply     : apply locale/size/animation settings to the connected emulator
#   -Action Verify    : fail unless the connected device matches the standard (default)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
if (-not $sdkRoot) { throw 'ANDROID_SDK_ROOT / ANDROID_HOME is not set.' }
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'

function Get-DeviceState {
    $loc = (& $adb shell getprop persist.sys.locale).Trim()
    if (-not $loc) { $loc = (& $adb shell getprop ro.product.locale).Trim() }
    $sz = ((& $adb shell wm size) -join '') -replace '.*?(\d+x\d+)\s*$', '$1'
    [pscustomobject]@{
        Locale = $loc
        Size   = $sz.Trim()
        Sdk    = (& $adb shell getprop ro.build.version.sdk).Trim()
    }
}

switch ($Action) {
    'CreateAvd' {
        $avdmanager = Get-ChildItem -Path (Join-Path $sdkRoot 'cmdline-tools') -Filter 'avdmanager.bat' -Recurse |
            Select-Object -First 1
        if (-not $avdmanager) { throw 'avdmanager.bat not found under cmdline-tools.' }
        'no' | & $avdmanager.FullName create avd -n $AvdName -k 'system-images;android-35;google_apis;x86_64' -d pixel_7 --force
        if ($LASTEXITCODE -ne 0) { throw 'avdmanager create avd failed.' }
        Write-Host "Created AVD $AvdName. Boot it, then run: scripts\android_test_env.ps1 -Action Apply"
    }
    'Apply' {
        & $adb root | Out-Null
        & $adb shell wm size $Size
        & $adb shell wm density 420
        foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') {
            & $adb shell settings put global $k 0
        }
        & $adb shell "setprop persist.sys.locale $Locale; setprop ctl.restart zygote"
        Start-Sleep -Seconds 5
        do { Start-Sleep -Seconds 2 } until ((& $adb shell getprop sys.boot_completed).Trim() -eq '1')
        Start-Sleep -Seconds 10
        Get-DeviceState | Format-List
    }
    'Verify' {
        $s = Get-DeviceState
        $s | Format-List
        $problems = @()
        if ($s.Locale -ne $Locale) { $problems += "locale is '$($s.Locale)', expected '$Locale'" }
        if ($s.Size -ne $Size) { $problems += "screen is '$($s.Size)', expected '$Size'" }
        if ($problems) { throw "Non-standard test environment: $($problems -join '; '). Run -Action Apply." }
        Write-Host 'Standard androidTest environment confirmed.'
    }
}
