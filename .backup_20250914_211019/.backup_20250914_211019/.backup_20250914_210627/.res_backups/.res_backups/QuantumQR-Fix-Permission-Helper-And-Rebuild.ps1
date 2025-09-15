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

# --- pick the most camera-ish Activity to patch
function Score-Kt([string]$p){
  try { $t = Read-AllText $p } catch { return -1 }
  if ($null -eq $t) { return -1 }
  $s = 0
  if ($t -match 'class\s+\w+Activity\b') { $s+=2 }
  if ($t -match 'androidx\.camera' -or $t -match 'ProcessCameraProvider') { $s+=4 }
  if ($t -match 'PreviewView' -or $t -match 'CameraSelector') { $s+=2 }
  if ($t -match 'bindToLifecycle\(') { $s+=4 }
  if ($t -match 'onScanSuccess\(' -or $t -match 'btnFlash|btnCopy|btnShare') { $s+=2 }
  return $s
}
$AllKt = @(Get-ChildItem -Path $Java -Recurse -Filter '*.kt' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
if ($AllKt.Count -eq 0) { Fail "No Kotlin files found under $Java" }
$Best = ($AllKt | ForEach-Object { [pscustomobject]@{Path=$_; Score=(Score-Kt $_)} }) | Sort-Object Score -Descending | Select-Object -First 1
$ActivityFile = $Best.Path
$t = Read-AllText $ActivityFile
if (-not $t) { Fail "Cannot read $ActivityFile" }
Info "Target Activity: $ActivityFile"

# --- ensure imports
function Add-ImportsText([string]$src,[string[]]$imports){
  $need = @(); foreach($i in $imports){ if ($src -notlike "*$i*") { $need += $i } }
  if ($need.Count -eq 0) { return $src }
  $block = ($need -join "`r`n") + "`r`n"
  $m = [regex]::Match($src, '^\s*package\s+[^\r\n]+', 'Multiline')
  if ($m.Success) { $i = $m.Index + $m.Length; return ($src.Substring(0,$i) + "`r`n" + $block + $src.Substring($i)) }
  $m2 = [regex]::Match($src, '^\s*import\s+[^\r\n]+', 'Multiline')
  if ($m2.Success) { $i = $m2.Index; return ($src.Substring(0,$i) + $block + $src.Substring($i)) }
  $m3 = [regex]::Match($src, '^\s*class\s+', 'Multiline')
  if ($m3.Success) { $i = $m3.Index; return ($src.Substring(0,$i) + $block + $src.Substring($i)) }
  return ($block + $src)
}
$imports = @(
  'import android.Manifest',
  'import android.content.pm.PackageManager',
  'import androidx.activity.result.contract.ActivityResultContracts',
  'import androidx.appcompat.app.AlertDialog',
  'import androidx.core.app.ActivityCompat',
  'import androidx.core.content.ContextCompat'
)
$t = Add-ImportsText $t $imports

# --- ensure functions
function Ensure-Func([string]$src,[string]$sig,[string]$body){
  if ($src -match "(?s)$sig") { return $src }
  $idx = $src.LastIndexOf('}')
  if ($idx -lt 0) { return $src + "`r`n$body`r`n" }
  return ($src.Substring(0,$idx) + "`r`n$body`r`n" + $src.Substring($idx))
}
# startCameraSafe if missing
$startSafe = @'
    private fun startCameraSafe() {
        try {
            val m = this::class.java.getDeclaredMethod("startCamera")
            m.isAccessible = true
            m.invoke(this)
        } catch (_: Throwable) { /* no-op */ }
    }
'@
$t = Ensure-Func $t 'fun\s+startCameraSafe\(' $startSafe

# permission launcher + ensureCameraPermission if missing
$permBlock = @'
    private val qrRequestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCameraSafe() else finish()
    }

    private fun ensureCameraPermission() {
        when {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                startCameraSafe()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) -> {
                AlertDialog.Builder(this)
                    .setTitle("Camera permission")
                    .setMessage("We need your camera to scan QR codes.")
                    .setPositiveButton("Allow") { _, _ -> qrRequestCameraPermission.launch(Manifest.permission.CAMERA) }
                    .setNegativeButton("Not now") { _, _ -> finish() }
                    .show()
            }
            else -> qrRequestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }
'@
$t = Ensure-Func $t 'fun\s+ensureCameraPermission\(' $permBlock

# write back
Write-TextUtf8NoBom $ActivityFile $t
Ok "Added permission helper(s) where missing."

# --- quiet rebuild with compact summary
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
} finally { Pop-Location }

$log = Read-AllText $logPath
if (-not $log) { Fail "Build log empty: $logPath" }

$errors   = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]
$lines = $log -split "`r?`n"
$errPatterns = @('^\s*FAILURE: Build failed.*','^\s*\> .*','^\s*Execution failed for task.*','^\s*Caused by: .*','^\s*error:\s+.*','^\s*AAPT: error: .*','^\s*e:\s+.*','^\s*Unresolved reference: .*','^\s*Duplicate class .*','^\s*Cannot find symbol.*','^\s*Could not resolve .*','^\s*resource .* not found.*')
$warnPatterns = @('^\s*warning:\s+.*','^\s*w:\s+.*')
foreach($ln in $lines){ foreach($p in $errPatterns){ if ($ln -match $p) { $errors.Add($ln.Trim()); break } }; foreach($wp in $warnPatterns){ if ($ln -match $wp) { $warnings.Add($ln.Trim()); break } } }

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
  if ($errors.Count -eq 0) { Write-Host " - (No error markers parsed; see full log)" -ForegroundColor Yellow }
  else { ($errors | Select-Object -First 80) | ForEach-Object { Write-Host (" - " + $_) -ForegroundColor Red } }
  Write-Host ("`nWarnings found: " + $warnings.Count + "  (see log)") -ForegroundColor Yellow
  Write-Host ("Log: " + $logPath)
  exit 1
}
