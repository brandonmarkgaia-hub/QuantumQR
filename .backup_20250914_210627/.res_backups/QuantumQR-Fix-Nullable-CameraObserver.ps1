param(
  [string]$Root = 'C:\QRSanitized\QuantumQR',
  [ValidateSet('Debug','Release')][string]$Variant = 'Debug'
)

$ErrorActionPreference = 'Stop'

function Fail($m){ Write-Host "ERROR: $m" -ForegroundColor Red; throw $m }
function Info($m){ Write-Host $m -ForegroundColor Cyan }
function Ok($m){ Write-Host $m -ForegroundColor Green }
function Read-AllText([string]$p){ try { return [IO.File]::ReadAllText($p,[Text.Encoding]::UTF8) } catch { return $null } }
function Write-TextUtf8NoBom([string]$Path,[string]$Text){ $enc = New-Object System.Text.UTF8Encoding($false); [IO.File]::WriteAllText($Path,$Text,$enc) }

if (!(Test-Path $Root)) { Fail "Project root not found: $Root" }
$App  = Join-Path $Root 'app'
$Main = Join-Path $App 'src\main'
$Java = Join-Path $Main 'java'
if (!(Test-Path $Java)) { Fail "Missing java dir: $Java" }

# --- Find the most camera-ish Activity file
function Score-Kt([string]$p){
  try { $t = Read-AllText $p } catch { return -1 }
  if ($null -eq $t) { return -1 }
  $s = 0
  if ($t -match 'class\s+\w+Activity\b') { $s+=2 }
  if ($t -match 'androidx\.camera' -or $t -match 'ProcessCameraProvider') { $s+=4 }
  if ($t -match 'PreviewView' -or $t -match 'CameraSelector') { $s+=2 }
  if ($t -match 'bindToLifecycle\(') { $s+=4 }
  if ($t -match 'cameraInfo\.torchState\.observe\(this\)') { $s+=3 }
  return $s
}

$AllKt = @(Get-ChildItem -Path $Java -Recurse -Filter '*.kt' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
if ($AllKt.Count -eq 0) { Fail "No Kotlin files found under $Java" }
$Best = ($AllKt | ForEach-Object { [pscustomobject]@{Path=$_; Score=(Score-Kt $_)} }) | Sort-Object Score -Descending | Select-Object -First 1
$ActivityFile = $Best.Path
$t = Read-AllText $ActivityFile
if (-not $t) { Fail "Cannot read $ActivityFile" }
Info "Patching file: $ActivityFile"

# --- Replace any '<var>.cameraInfo.torchState.observe(this) { ... }' with safe-call on this.camera
#     Regex is singleline to capture the whole block.
$pattern = '(?s)[A-Za-z_]\w*\.cameraInfo\.torchState\.observe\(this\)\s*\{.*?\}'
$replacement = @'
this.camera?.cameraInfo?.torchState?.observe(this) { state ->
                torchOn = (state == TorchState.ON)
                updateFlashUi(torchOn)
            }
'@

if ($t -match $pattern) {
  $patched = [regex]::Replace($t, $pattern, $replacement)
  if ($patched -ne $t) {
    Write-TextUtf8NoBom $ActivityFile $patched
    Ok "Replaced nullable-unsafe torch observer with safe-call."
  } else {
    Info "Pattern matched but produced no change."
  }
} else {
  Info "No torch observer pattern found; nothing to change."
}

# --- Quiet rebuild with compact summary
$Gradlew = Join-Path $Root 'gradlew.bat'
if (!(Test-Path $Gradlew)) { $Gradlew = Join-Path $Root 'gradlew' }
if (!(Test-Path $Gradlew)) { Fail "gradlew wrapper not found in $Root" }

$logDir = Join-Path $Root '.build_logs'
if (!(Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$logPath = Join-Path $logDir ("build-$Variant-$ts.log")
$task = if ($Variant -eq 'Release') { ':app:assembleRelease' } else { ':app:assembleDebug' }

Write-Host "Building ($Variant) ... quiet; summary follows." -ForegroundColor DarkCyan
Push-Location $Root
try {
  & $Gradlew $task '--no-daemon' '--stacktrace' 2>&1 | Tee-Object -FilePath $logPath | Out-Null
  $exit = $LASTEXITCODE
} finally {
  Pop-Location
}

$log = Read-AllText $logPath
if (-not $log) { Fail "Build log empty: $logPath" }

$errors   = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]
$lines = $log -split "`r?`n"

$errPatterns = @(
  '^\s*FAILURE: Build failed.*',
  '^\s*\> .*',
  '^\s*Execution failed for task.*',
  '^\s*Caused by: .*',
  '^\s*error:\s+.*',
  '^\s*AAPT: error: .*',
  '^\s*e:\s+.*',
  '^\s*Unresolved reference: .*',
  '^\s*Duplicate class .*',
  '^\s*Cannot find symbol.*',
  '^\s*Could not resolve .*',
  '^\s*resource .* not found.*'
)
$warnPatterns = @('^\s*warning:\s+.*','^\s*w:\s+.*')

foreach($ln in $lines){
  foreach($p in $errPatterns){ if ($ln -match $p) { $errors.Add($ln.Trim()); break } }
  foreach($wp in $warnPatterns){ if ($ln -match $wp) { $warnings.Add($ln.Trim()); break } }
}

Write-Host ""
if ($exit -eq 0) {
  Ok "BUILD RESULT: SUCCESS ($Variant)"
  $apkDir = if ($Variant -eq 'Release') { Join-Path $Root 'app\build\outputs\apk\release' } else { Join-Path $Root 'app\build\outputs\apk\debug' }
  if (Test-Path $apkDir) {
    $apks = @(Get-ChildItem -Path $apkDir -Filter '*.apk' -Recurse -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
    if ($apks.Count -gt 0) {
      Write-Host "APKs:" -ForegroundColor Green
      $apks | ForEach-Object { Write-Host (" - " + $_) -ForegroundColor Green }
    }
  }
  Write-Host ("`nLog: " + $logPath)
  exit 0
} else {
  Write-Host "BUILD RESULT: FAILED ($Variant)" -ForegroundColor Red
  Write-Host "`n--- KEY ERRORS (first 80) ---" -ForegroundColor Red
  if ($errors.Count -eq 0) {
    Write-Host " - (No error markers parsed; see full log)" -ForegroundColor Yellow
  } else {
    ($errors | Select-Object -First 80) | ForEach-Object {
      Write-Host (" - " + $_) -ForegroundColor Red
    }
  }
  Write-Host ("`nWarnings found: " + $warnings.Count + "  (see log)") -ForegroundColor Yellow
  Write-Host ("Log: " + $logPath)
  exit 1
}
