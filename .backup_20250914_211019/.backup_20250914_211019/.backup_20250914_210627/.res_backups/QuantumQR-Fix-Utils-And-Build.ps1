param(
  [string]$Root    = 'C:\QRSanitized\QuantumQR',
  [ValidateSet('Debug','Release')][string]$Variant = 'Debug'
)

$ErrorActionPreference = 'Stop'
function Fail($m){ Write-Host "ERROR: $m" -ForegroundColor Red; throw $m }
function Info($m){ Write-Host $m -ForegroundColor Cyan }
function Ok($m){ Write-Host $m -ForegroundColor Green }
function Read-AllText([string]$p){ try { return [IO.File]::ReadAllText($p,[Text.Encoding]::UTF8) } catch { return $null } }
function Write-TextUtf8NoBom([string]$Path,[string]$Text){ $enc = New-Object System.Text.UTF8Encoding($false); [IO.File]::WriteAllText($Path,$Text,$enc) }

if (!(Test-Path $Root)) { Fail "Project root not found: $Root" }
$App    = Join-Path $Root 'app'
$Main   = Join-Path $App 'src\main'
$Java   = Join-Path $Main 'java'
$Mani   = Join-Path $Main 'AndroidManifest.xml'
$GradleGroovy = Join-Path $App 'build.gradle'
$GradleKts    = Join-Path $App 'build.gradle.kts'
if (!(Test-Path $App))  { Fail "Missing app module: $App" }
if (!(Test-Path $Main)) { Fail "Missing main source set: $Main" }
if (!(Test-Path $Java)) { New-Item -ItemType Directory -Path $Java | Out-Null }

# 1) Resolve namespace (Gradle -> Manifest -> infer from Kotlin)
$pkg = $null
if (Test-Path $GradleKts)    { $g = Read-AllText $GradleKts; if ($g){ $m = [regex]::Match($g,'namespace\s*=\s*"([^"]+)"'); if($m.Success){$pkg=$m.Groups[1].Value} } }
if (-not $pkg -and (Test-Path $GradleGroovy)) { $g = Read-AllText $GradleGroovy; if ($g){ $m = [regex]::Match($g,"namespace\s+['""]([^'""]+)['""]"); if($m.Success){$pkg=$m.Groups[1].Value} } }
if (-not $pkg -and (Test-Path $Mani))        { $mf = Read-AllText $Mani; if ($mf){ $m = [regex]::Match($mf,'package="([^"]+)"'); if($m.Success){$pkg=$m.Groups[1].Value} } }
if (-not $pkg) {
  $AllKt = @(Get-ChildItem -Path $Java -Recurse -Filter '*.kt' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
  $counts=@{}
  foreach($f in $AllKt){
    $t=Read-AllText $f; if($null -eq $t){continue}
    $pm=[regex]::Match($t,'^\s*package\s+([A-Za-z0-9_\.]+)','Multiline')
    if($pm.Success){ $p=$pm.Groups[1].Value.Trim(); if($p){ if(-not $counts.ContainsKey($p)){$counts[$p]=0}; $counts[$p]++ } }
  }
  if($counts.Count -gt 0){ $pkg = ($counts.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 1).Key }
}
if (-not $pkg) { Fail "Could not resolve package/namespace." }
Info "Namespace: $pkg"

$pkgPath = ($pkg -replace '\.', [IO.Path]::DirectorySeparatorChar)
$CodeDir = Join-Path $Java $pkgPath
if (!(Test-Path $CodeDir)) { New-Item -ItemType Directory -Path $CodeDir -Force | Out-Null }

# 2) Fix misplaced/duplicate utils (Feedback.kt, ShareUtils.kt)
$backupDir = Join-Path $Root '.code_backups'
if (!(Test-Path $backupDir)) { New-Item -ItemType Directory -Path $backupDir | Out-Null }
$ts = Get-Date -Format "yyyyMMdd-HHmmss"

$targets = @('Feedback.kt','ShareUtils.kt')
foreach($name in $targets){
  $all = @(Get-ChildItem -Path $Java -Recurse -Filter $name -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
  if ($all.Count -eq 0) { continue }

  # Keep/create the one in CodeDir
  $desired = Join-Path $CodeDir $name
  if (-not (Test-Path $desired)) {
    # move the first found to desired, the rest to backups
    Move-Item -Path $all[0] -Destination $desired -Force
    Ok "Moved $name to $desired"
    if ($all.Count -gt 1){
      $rest = $all[1..($all.Count-1)]
      foreach($r in $rest){
        $dest = Join-Path $backupDir ($ts + '-' + (Split-Path $r -Leaf))
        Move-Item -Path $r -Destination $dest -Force
        Info "Backed up duplicate $name from $r -> $dest"
      }
    }
  } else {
    # desired exists; any others go to backup
    foreach($r in $all){
      if ($r -ne $desired){
        $dest = Join-Path $backupDir ($ts + '-' + (Split-Path $r -Leaf))
        Move-Item -Path $r -Destination $dest -Force
        Info "Backed up stray $name from $r -> $dest"
      }
    }
  }

  # Normalize package line in the desired file
  $txt = Read-AllText $desired
  if ($null -eq $txt) { Fail "Could not read $desired" }
  if ($txt -match '^\s*package\s+([^\r\n]+)'){
    $txt = [regex]::Replace($txt,'^\s*package\s+[^\r\n]+',"package $pkg", 'Multiline')
  } else {
    $txt = "package $pkg`r`n`r`n" + $txt
  }
  Write-TextUtf8NoBom $desired $txt
  Ok "Normalized package in $name"
}

# 3) Build quietly and summarize (reusing our build-report style)
$Gradlew = Join-Path $Root 'gradlew.bat'
if (!(Test-Path $Gradlew)) { $Gradlew = Join-Path $Root 'gradlew'; if (!(Test-Path $Gradlew)) { Fail "gradlew wrapper not found in $Root" } }

$logDir = Join-Path $Root '.build_logs'
if (!(Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
$logPath = Join-Path $logDir ("build-$Variant-$ts.log")
$task = if ($Variant -eq 'Release') { ':app:assembleRelease' } else { ':app:assembleDebug' }

Write-Host "Building ($Variant) ... quiet mode; summary follows." -ForegroundColor DarkCyan
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

$patterns = @(
  '^\s*FAILURE: Build failed.*',
  '^\s*\> .*',
  '^\s*Execution failed for task.*',
  '^\s*Caused by: .*',
  '^\s*Exception in thread.*',
  '^\s*org\.gradle\..*Exception.*',
  '^\s*A problem occurred evaluating.*',
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
  foreach($p in $patterns){ if ($ln -match $p) { $errors.Add($ln.Trim()); break } }
  foreach($wp in $warnPatterns){ if ($ln -match $wp) { $warnings.Add($ln.Trim()); break } }
}

$apkDir = if ($Variant -eq 'Release') { Join-Path $Root 'app\build\outputs\apk\release' } else { Join-Path $Root 'app\build\outputs\apk\debug' }
$apkPaths = @()
if (Test-Path $apkDir){ $apkPaths = @(Get-ChildItem -Path $apkDir -Filter '*.apk' -Recurse -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }) }

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
  if ($errors.Count -eq 0) { Write-Host "(No error markers parsed; see full log)" -ForegroundColor Yellow }
  else { $errors | Select-Object -First 80 | ForEach-Object { Write-Host " - $_" -ForegroundColor Red } }
  Write-Host "`nWarnings found: $($warnings.Count)  (see log)" -ForegroundColor Yellow
  Write-Host "Log: $logPath"
  exit 1
}
