param(
  [string]$Root    = 'C:\QRSanitized\QuantumQR',
  [ValidateSet('Debug','Release')][string]$Variant = 'Debug'
)

$ErrorActionPreference = 'Stop'

function Fail($m){ Write-Host "ERROR: $m" -ForegroundColor Red; throw $m }
function Info($m){ Write-Host $m -ForegroundColor Cyan }
function Ok($m){ Write-Host $m -ForegroundColor Green }
function Read-AllText([string]$p){ try { return [IO.File]::ReadAllText($p,[Text.Encoding]::UTF8) } catch { return $null } }

if (!(Test-Path $Root)) { Fail "Project root not found: $Root" }
$Gradlew = Join-Path $Root 'gradlew.bat'
if (!(Test-Path $Gradlew)) { $Gradlew = Join-Path $Root 'gradlew'; if (!(Test-Path $Gradlew)) { Fail "gradlew wrapper not found in $Root" } }

# Prepare log dir/file
$logDir = Join-Path $Root '.build_logs'
if (!(Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$logPath = Join-Path $logDir ("build-$Variant-$ts.log")

# Decide task
$task = if ($Variant -eq 'Release') { ':app:assembleRelease' } else { ':app:assembleDebug' }

# Run quietly; capture both stdout+stderr to log
Write-Host "Building ($Variant) ... this will be quiet; summary follows." -ForegroundColor DarkCyan
Push-Location $Root
try {
  & $Gradlew $task '--no-daemon' '--stacktrace' 2>&1 | Tee-Object -FilePath $logPath | Out-Null
  $exit = $LASTEXITCODE
} finally {
  Pop-Location
}

# Parse log
$log = Read-AllText $logPath
if (-not $log) { Fail "Build log empty: $logPath" }

# Helpers
$errors   = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]

# Common failure markers
$patterns = @(
  '^\s*FAILURE: Build failed.*',
  '^\s*\> .*',                                      # Gradle ">" lines
  '^\s*Execution failed for task.*',
  '^\s*Caused by: .*',
  '^\s*Exception in thread.*',
  '^\s*org\.gradle\..*Exception.*',
  '^\s*A problem occurred evaluating.*',
  '^\s*error:\s+.*',                                # AAPT/Java "error:"
  '^\s*AAPT: error: .*',
  '^\s*e:\s+.*',                                    # Kotlin compiler errors
  '^\s*Unresolved reference: .*',
  '^\s*Duplicate class .*',
  '^\s*Cannot find symbol.*',
  '^\s*Could not resolve .*',
  '^\s*resource .* not found.*'
)

$warnPatterns = @(
  '^\s*warning:\s+.*',
  '^\s*w:\s+.*'                                     # Kotlin warnings
)

# Collect matches (limit to keep it readable)
$lines = $log -split "`r?`n"
foreach($ln in $lines){
  foreach($p in $patterns){
    if ($ln -match $p) { $errors.Add($ln.Trim()) ; break }
  }
  foreach($wp in $warnPatterns){
    if ($ln -match $wp) { $warnings.Add($ln.Trim()) ; break }
  }
}

# Success paths
$apkDir = if ($Variant -eq 'Release') {
  Join-Path $Root 'app\build\outputs\apk\release'
} else {
  Join-Path $Root 'app\build\outputs\apk\debug'
}
$apkPaths = @()
if (Test-Path $apkDir) {
  $apkPaths = @(Get-ChildItem -Path $apkDir -Filter '*.apk' -Recurse -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
}

# Report
Write-Host ""
if ($exit -eq 0) {
  Ok "BUILD RESULT: SUCCESS ($Variant)"
  if ($apkPaths.Count -gt 0) {
    Write-Host "APKs:" -ForegroundColor Green
    $apkPaths | ForEach-Object { Write-Host " - $_" -ForegroundColor Green }
  } else {
    Write-Host "APK not found yet, but Gradle exit code is 0. Check $apkDir." -ForegroundColor Yellow
  }
  Write-Host "`nLog: $logPath"
  exit 0
} else {
  Write-Host "BUILD RESULT: FAILED ($Variant)" -ForegroundColor Red
  Write-Host "`n--- KEY ERRORS (first 80) ---" -ForegroundColor Red
  if ($errors.Count -eq 0) {
    Write-Host "(No error markers parsed; see full log)" -ForegroundColor Yellow
  } else {
    $errors | Select-Object -First 80 | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
  }

  # Show a short cause window if Gradle provided one
  $idx = ($lines | Select-String -Pattern 'FAILURE: Build failed').LineNumber
  if ($idx) {
    $start = [Math]::Max(0, $idx - 5)
    $end   = [Math]::Min($lines.Count - 1, $idx + 50)
    $snippet = $lines[$start..$end] -join "`r`n"
    Write-Host "`n--- FAILURE SNIPPET ---" -ForegroundColor DarkRed
    Write-Host $snippet
  }

  Write-Host "`nWarnings found: $($warnings.Count)  (see log)" -ForegroundColor Yellow
  Write-Host "Log: $logPath"
  exit 1
}
