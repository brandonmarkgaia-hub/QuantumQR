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
function Save-Backup([string]$Root,[string]$Src){
  $dir = Join-Path $Root ".res_backups"
  if (!(Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
  $ts = Get-Date -Format "yyyyMMdd-HHmmss"
  Copy-Item $Src (Join-Path $dir ($ts + "-" + (Split-Path $Src -Leaf))) -Force
}

if (!(Test-Path $Root)) { Fail "Project root not found: $Root" }
$App  = Join-Path $Root 'app'
$Main = Join-Path $App 'src\main'
$Java = Join-Path $Main 'java'
$Mani = Join-Path $Main 'AndroidManifest.xml'
$GradleGroovy = Join-Path $App 'build.gradle'
$GradleKts    = Join-Path $App 'build.gradle.kts'
if (!(Test-Path $App))  { Fail "Missing app module: $App" }
if (!(Test-Path $Main)) { Fail "Missing main source set: $Main" }

# 1) Resolve applicationId (prefer Gradle; fallback to Kotlin packages)
$appId = $null
if (Test-Path $GradleGroovy) {
  $g = Read-AllText $GradleGroovy
  if ($g) {
    $m = [regex]::Match($g, "applicationId\s+['`"]([^'`"]+)['`"]")
    if ($m.Success) { $appId = $m.Groups[1].Value }
    if (-not $appId) {
      $m = [regex]::Match($g, "namespace\s+['`"]([^'`"]+)['`"]")
      if ($m.Success) { $appId = $m.Groups[1].Value }
    }
  }
}
if (-not $appId -and (Test-Path $GradleKts)) {
  $g = Read-AllText $GradleKts
  if ($g) {
    $m = [regex]::Match($g, 'applicationId\s*=\s*"([^"]+)"')
    if ($m.Success) { $appId = $m.Groups[1].Value }
    if (-not $appId) {
      $m = [regex]::Match($g, 'namespace\s*=\s*"([^"]+)"')
      if ($m.Success) { $appId = $m.Groups[1].Value }
    }
  }
}
if (-not $appId -and (Test-Path $Java)) {
  $allKt = @(Get-ChildItem -Path $Java -Recurse -Filter '*.kt' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
  $counts=@{}
  foreach($f in $allKt){
    $t = Read-AllText $f; if ($null -eq $t) { continue }
    $pm = [regex]::Match($t, '^\s*package\s+([A-Za-z0-9_\.]+)', [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if ($pm.Success) {
      $p = $pm.Groups[1].Value.Trim()
      if ($p) { if (-not $counts.ContainsKey($p)) { $counts[$p] = 0 }; $counts[$p]++ }
    }
  }
  if ($counts.Count -gt 0) { $appId = ($counts.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 1).Key }
}
if (-not $appId) { Fail "Could not resolve applicationId/namespace." }
Info "AppId: $appId"

# 2) Ensure CAMERA permission in AndroidManifest.xml
if (!(Test-Path $Mani)) { Fail "AndroidManifest.xml not found: $Mani" }
$mt = Read-AllText $Mani
if ($mt -notmatch '<uses-permission\s+android:name="android\.permission\.CAMERA"') {
  Save-Backup $Root $Mani
  $mt = $mt -replace '(</manifest>\s*)$', "    <uses-permission android:name=""android.permission.CAMERA""/>`r`n`$1"
  Write-TextUtf8NoBom $Mani $mt
  Ok "Added CAMERA permission to manifest (backup saved)."
} else {
  Info "CAMERA permission already present."
}

# 3) Quiet build
$Gradlew = Join-Path $Root 'gradlew.bat'; if (!(Test-Path $Gradlew)) { $Gradlew = Join-Path $Root 'gradlew' }
if (!(Test-Path $Gradlew)) { Fail "gradlew wrapper not found in $Root" }
$logDir = Join-Path $Root '.build_logs'; if (!(Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$buildLog = Join-Path $logDir ("triage-build-$Variant-$ts.log")
$task = if ($Variant -eq 'Release') { ':app:assembleRelease' } else { ':app:assembleDebug' }

Write-Host "Building ($Variant) ... quiet; summary follows." -ForegroundColor DarkCyan
Push-Location $Root
try {
  & $Gradlew $task '--no-daemon' '--stacktrace' 2>&1 | Tee-Object -FilePath $buildLog | Out-Null
  $exit = $LASTEXITCODE
} finally { Pop-Location }
if ($exit -ne 0) { Fail "Build failed. See $buildLog" } else { Ok "Build success." }

# 4) Launch & capture crash snippet (needs adb + a device)
$adb = 'adb'
try { & $adb version | Out-Null } catch { Fail "adb not found in PATH. Open a device/emulator and ensure platform-tools are in PATH." }

& $adb logcat -c | Out-Null
& $adb shell monkey -p $appId -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds 4
$runLog = Join-Path $logDir ("triage-run-$Variant-$ts.log")
& $adb logcat -d > $runLog

$lines = Get-Content $runLog
$idx = ($lines | Select-String -Pattern 'FATAL EXCEPTION' | Select-Object -Last 1).LineNumber
Write-Host ""
if ($idx) {
  $start = [Math]::Max(0, $idx - 5)
  $end   = [Math]::Min($lines.Count - 1, $idx + 80)
  $snippet = $lines[$start..$end] | Where-Object { $_ -match $appId -or $_ -match 'FATAL EXCEPTION' -or $_ -match 'java\.lang\.' -or $_ -match 'Caused by:' }
  if ($snippet.Count -gt 0) {
    Write-Host "=== CRASH SNIPPET ===" -ForegroundColor Red
    $snippet | ForEach-Object { Write-Host ("  " + $_) -ForegroundColor Red }
  } else {
    Write-Host "No package-specific fatal lines extracted; see full run log." -ForegroundColor Yellow
  }
} else {
  Write-Host "No FATAL EXCEPTION found in this run. If it still crashes, open the app manually then re-run." -ForegroundColor Yellow
}

Write-Host "`nLogs:" -ForegroundColor DarkCyan
Write-Host (" - Build: " + $buildLog)
Write-Host (" - Run:   " + $runLog)
