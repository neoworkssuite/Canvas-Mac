param(
    [string]$AppPath = '',
    [string]$OutputPath = '.\windows-acceptance-report.txt'
)

$ErrorActionPreference = 'Stop'
$lines = [System.Collections.Generic.List[string]]::new()
function Add-Line([string]$Value = '') { $lines.Add($Value) | Out-Null }

Add-Line 'NeoCanvas Windows Acceptance Probe'
Add-Line ('Generated: ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss K'))
Add-Line ''

$os = Get-CimInstance Win32_OperatingSystem
Add-Line ('OS: ' + $os.Caption)
Add-Line ('Version: ' + $os.Version)
Add-Line ('Build: ' + $os.BuildNumber)
Add-Line ('Architecture: ' + $env:PROCESSOR_ARCHITECTURE)
Add-Line ''

Add-Line 'Displays / GPUs:'
Get-CimInstance Win32_VideoController | ForEach-Object {
    Add-Line ('- ' + $_.Name + ' | ' + $_.CurrentHorizontalResolution + 'x' + $_.CurrentVerticalResolution)
}
Add-Line ''

Add-Line 'Likely pen / tablet / touch devices:'
$devices = Get-PnpDevice -PresentOnly -ErrorAction SilentlyContinue |
    Where-Object { $_.FriendlyName -match 'Pen|Stylus|Wacom|Surface|Touch|Digitizer' }
if ($devices) {
    $devices | ForEach-Object { Add-Line ('- ' + $_.Class + ' | ' + $_.FriendlyName + ' | ' + $_.Status) }
} else {
    Add-Line '- none detected by name'
}
Add-Line ''

Add-Line 'NeoCanvas file association:'
$association = cmd /c 'assoc .neocanvas' 2>&1
Add-Line (($association | Out-String).Trim())
Add-Line ''

if ([string]::IsNullOrWhiteSpace($AppPath)) {
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA 'Programs\NeoCanvas\NeoCanvas.exe'),
        (Join-Path $env:ProgramFiles 'NeoCanvas\NeoCanvas.exe'),
        (Join-Path $env:ProgramFiles 'NeoWorksSuite\NeoCanvas\NeoCanvas.exe')
    )
    $AppPath = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}

Add-Line ('AppPath: ' + $(if ($AppPath) { $AppPath } else { '<not found>' }))
if ($AppPath -and (Test-Path $AppPath)) {
    Add-Line 'Packaged self-test:'
    $selfTest = & $AppPath --windows-self-test 2>&1
    $exitCode = $LASTEXITCODE
    $selfTest | ForEach-Object { Add-Line ([string]$_) }
    Add-Line ('ExitCode: ' + $exitCode)
    if ($exitCode -ne 0 -or (($selfTest -join "`n") -notmatch 'NEOCANVAS_WINDOWS_SELF_TEST=PASS')) {
        Add-Line 'RESULT: FAIL'
        $lines | Set-Content -Encoding UTF8 $OutputPath
        throw 'NeoCanvas packaged self-test failed.'
    }
    Add-Line 'RESULT: SELF-TEST PASS'
} else {
    Add-Line 'RESULT: APP NOT FOUND — install the acceptance build, then rerun this probe.'
}

$lines | Set-Content -Encoding UTF8 $OutputPath
Write-Host ('Acceptance report: ' + (Resolve-Path $OutputPath))
