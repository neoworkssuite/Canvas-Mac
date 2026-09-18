Clear-Host
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$jdk = Get-ChildItem (Join-Path $projectRoot "work\jdk17") -Directory | Select-Object -First 1
if (-not $jdk) { throw "Bundled JDK 17 was not found under work\jdk17." }
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$projectRoot\work"

& ".\gradlew.bat" --no-problems-report ":windowsApp:createDistributable"
if ($LASTEXITCODE -ne 0) { throw "NeoCanvas Windows build failed." }

$application = Join-Path $projectRoot "windowsApp\build\compose\binaries\main\app\NeoCanvas\NeoCanvas.exe"
if (-not (Test-Path -LiteralPath $application)) { throw "Runnable NeoCanvas application was not created at $application" }
Start-Process -FilePath $application
