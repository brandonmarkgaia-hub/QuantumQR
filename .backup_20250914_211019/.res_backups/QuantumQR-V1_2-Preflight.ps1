param(
  [string]$Root = 'C:\QRSanitized\QuantumQR',
  [switch]$DryRun,
  [switch]$Quiet
)

# ---------- util ----------
$ErrorActionPreference = 'Stop'
$Errors   = @()
$Warnings = @()
$Actions  = @()

function Add-Err($m){ $script:Errors   += $m }
function Add-Warn($m){ $script:Warnings += $m }
function Add-Act($m){ $script:Actions  += $m }
function Say($m){ if(-not $Quiet){ Write-Host $m -ForegroundColor Cyan } }
function Read-AllText([string]$p){
  try { return [IO.File]::ReadAllText($p,[Text.Encoding]::UTF8) } catch { return $null }
}

# ---------- 0) layout ----------
if (!(Test-Path $Root)) { Add-Err "Project root not found: $Root" }
$App    = Join-Path $Root 'app'
$Main   = Join-Path $App 'src\main'
$Java   = Join-Path $Main 'java'
$Mani   = Join-Path $Main 'AndroidManifest.xml'
if (!(Test-Path $App))  { Add-Err "Missing app module at $App" }
if (!(Test-Path $Main)) { Add-Err "Missing main source set at $Main" }

# ---------- 1) resolve namespace/package ----------
$pkg = $null
$GradleGroovy = Join-Path $App 'build.gradle'
$GradleKts    = Join-Path $App 'build.gradle.kts'

if (Test-Path $GradleKts) {
  $g = Read-AllText $GradleKts
  if ($g) {
    $m = [regex]::Match($g, 'namespace\s*=\s*"([^"]+)"')
    if ($m.Success){ $pkg = $m.Groups[1].Value }
  }
}
if (-not $pkg -and (Test-Path $GradleGroovy)) {
  $g = Read-AllText $GradleGroovy
  if ($g) {
    $m = [regex]::Match($g, "namespace\s+['""]([^'""]+)['""]")
    if ($m.Success){ $pkg = $m.Groups[1].Value }
  }
}
if (-not $pkg -and (Test-Path $Mani)) {
  $mf = Read-AllText $Mani
  if ($mf) {
    $m = [regex]::Match($mf, 'package="([^"]+)"')
    if ($m.Success){ $pkg = $m.Groups[1].Value }
  }
}

# Fallback: infer from Kotlin file package lines (most frequent)
if (-not $pkg -and (Test-Path $Java)) {
  $AllKt = @(Get-ChildItem -Path $Java -Recurse -Filter '*.kt' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
  $pkgCounts = @{}
  foreach($f in $AllKt){
    $t = Read-AllText $f
    if ($null -eq $t) { continue }
    $pm = [regex]::Match($t, '^\s*package\s+([A-Za-z0-9_\.]+)', 'Multiline')
    if ($pm.Success) {
      $p = $pm.Groups[1].Value.Trim()
      if (-not [string]::IsNullOrWhiteSpace($p)) {
        if (-not $pkgCounts.ContainsKey($p)) { $pkgCounts[$p] = 0 }
        $pkgCounts[$p]++
      }
    }
  }
  if ($pkgCounts.Count -gt 0) {
    $top = $pkgCounts.GetEnumerator() | Sort-Object -Property Value -Descending | Select-Object -First 1
    $pkg = $top.Key
    if (-not $Quiet) { Write-Host "Inferred namespace from Kotlin files: $pkg (count=$($top.Value))" -ForegroundColor DarkCyan }
  }
}

if (-not $pkg) { Add-Err "Could not resolve package/namespace from Gradle/Manifest/Kotlin files." }

# ---------- 2) candidate Activity (score-based) ----------
function Score-Kt([string]$p){
  try { $t = Read-AllText $p } catch { return -1 }
  if ($null -eq $t) { return -1 }
  $s = 0
  if ($t -match 'class\s+\w+Activity\b') { $s+=2 }
  if ($t -match 'androidx\.camera' -or $t -match 'ProcessCameraProvider') { $s+=4 }
  if ($t -match 'PreviewView' -or $t -match 'CameraSelector') { $s+=2 }
  if ($t -match 'bindToLifecycle\(') { $s+=4 }
  if ($t -match 'R\.id\.btnFlash|R\.id\.btnCopy|R\.id\.btnShare|R\.layout\.activity_scanner') { $s+=3 }
  return $s
}

$ActivityFile = $null
$ActivityName = $null
if (Test-Path $Java) {
  $AllKt = @(Get-ChildItem -Path $Java -Recurse -Filter '*.kt' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
  if ($AllKt.Count -eq 0) {
    Add-Err "No Kotlin files found under $Java"
  } else {
    $Scored = @()
    foreach($f in $AllKt){ $Scored += [pscustomobject]@{Path=$f; Score=(Score-Kt $f)} }
    $Best = $Scored | Sort-Object Score -Descending | Select-Object -First 1
    if ($Best.Score -lt 1) {
      $fallback = $AllKt | Where-Object { (Read-AllText $_) -match 'class\s+\w+Activity\b' } | Select-Object -First 1
      if ($fallback) {
        $ActivityFile = $fallback
        Add-Warn "No camera-ish Activity detected; falling back to: $ActivityFile"
      } else {
        Add-Err "Could not locate any Activity class (.kt)."
      }
    } else {
      $ActivityFile = $Best.Path
    }
    if ($ActivityFile) {
      $t = Read-AllText $ActivityFile
      $actMatch = [regex]::Match($t,'class\s+([A-Za-z_]\w*Activity)\b')
      if ($actMatch.Success) { $ActivityName = $actMatch.Groups[1].Value; Say "Target Activity: $ActivityName ($(Split-Path $ActivityFile -Leaf))" }
      else { Add-Err "Target file has no Activity class: $ActivityFile" }
    }
  }
} else {
  Add-Err "Main/java not found: $Java"
}

# ---------- 3) R8 shrink + keep rules ----------
$NeedMinify = $true; $NeedShrink = $true
if (Test-Path $GradleKts) {
  $g = Read-AllText $GradleKts
  if ($g -and $g -match 'isMinifyEnabled\s*=\s*true') { $NeedMinify = $false }
  if ($g -and $g -match 'isShrinkResources\s*=\s*true') { $NeedShrink = $false }
} elseif (Test-Path $GradleGroovy) {
  $g = Read-AllText $GradleGroovy
  if ($g -and $g -match 'minifyEnabled\s+true') { $NeedMinify = $false }
  if ($g -and $g -match 'shrinkResources\s+true') { $NeedShrink = $false }
} else {
  Add-Err "No app/build.gradle(.kts) found."
}
if ($NeedMinify){ Add-Act "Would enable R8: minifyEnabled/isMinifyEnabled (release)." }
if ($NeedShrink){ Add-Act "Would enable resource shrinking (release)." }

$Pro = Join-Path $App 'proguard-rules.pro'
if (Test-Path $Pro) {
  $pTxt = Read-AllText $Pro
  if ($pTxt -notlike '*-keep class com.google.zxing.*')   { Add-Act "Would add ZXing keep rules to proguard-rules.pro." }
  if ($pTxt -notlike '*-keep class androidx.camera.*')    { Add-Act "Would add CameraX keep rules to proguard-rules.pro." }
  if ($pkg -and $pTxt -notlike "*-keep class $pkg.*")     { Add-Act "Would add app package keep rule to proguard-rules.pro." }
} else {
  if ($pkg) { Add-Act "Would create proguard-rules.pro with ZXing/CameraX/$pkg keeps." }
  else { Add-Act "Would create proguard-rules.pro with ZXing/CameraX keeps (namespace pending)." }
}

# ---------- 4) Manifest: VIBRATE + camera.any ----------
if (Test-Path $Mani) {
  $mt = Read-AllText $Mani
  if ($mt -and $mt -notmatch '<uses-permission\s+android:name="android\.permission\.VIBRATE"') {
    Add-Act "Would add <uses-permission android:name=""android.permission.VIBRATE""/> to AndroidManifest.xml."
  }
  if ($mt -and $mt -notmatch '<uses-feature\s+android:name="android\.hardware\.camera\.any"') {
    Add-Act "Would add <uses-feature android:name=""android.hardware.camera.any""/> to AndroidManifest.xml."
  }
} else {
  Add-Err "AndroidManifest.xml not found at $Mani"
}

# ---------- 5) aapt2 gremlin scan (no edits) ----------
$valuesDirs = @()
try {
  $valuesDirs = @(Get-ChildItem -Path (Join-Path $App 'src') -Recurse -Directory | Where-Object { $_.FullName -match '\\res\\values' })
} catch {}

$fmtDupFiles = @()
$braceFiles  = @()
$dupNames    = @{}
$toastDefs   = @()

foreach($d in $valuesDirs){
  $xmls = @(Get-ChildItem -Path $d.FullName -Filter 'strings*.xml' -ErrorAction SilentlyContinue)
  foreach($x in $xmls){
    $txt = Read-AllText $x.FullName
    if ($null -eq $txt) { continue }
    if ($txt -match 'formatted="false".*formatted="false"') { $fmtDupFiles += $x.FullName }
    if ($txt -match '>\s*\{[^<]*\}\s*<') { $braceFiles += $x.FullName }

    $matches = [regex]::Matches($txt, '<string\s+name="([^"]+)"[^>]*>')
    foreach($m in $matches){
      $name = $m.Groups[1].Value
      if (-not $dupNames.ContainsKey($name)) { $dupNames[$name] = @() }
      $dupNames[$name] += $x.FullName
      if ($name -eq 'toast_cant_open') { $toastDefs += $x.FullName }
    }
  }
}

if ($fmtDupFiles.Count -gt 0) { Add-Warn ("Duplicate formatted= attributes in:`n  " + ($fmtDupFiles -join "`n  ")) }
if ($braceFiles.Count  -gt 0) { Add-Warn ("Curly-brace placeholders found (replace with %s or add formatted=""false"") in:`n  " + ($braceFiles -join "`n  ")) }

$dupNameHits = @()
foreach($k in $dupNames.Keys){
  $list = $dupNames[$k] | Select-Object -Unique
  if ($list.Count -gt 1){ $dupNameHits += ("$k  ==>  " + ($list -join ' | ')) }
}
if ($dupNameHits.Count -gt 0){ Add-Warn ("Duplicate <string name> across values folders:`n  " + ($dupNameHits -join "`n  ")) }
if ($toastDefs.Count -gt 1)  { Add-Warn ("Multiple definitions of toast_cant_open:`n  " + (($toastDefs | Select-Object -Unique) -join "`n  ")) }

# BOM check (UTF-8 BOM in values XML)
$BOMFiles = @()
foreach($d in $valuesDirs){
  $xmls = @(Get-ChildItem -Path $d.FullName -Filter '*.xml' -ErrorAction SilentlyContinue)
  foreach($x in $xmls){
    try{
      $bytes = [IO.File]::ReadAllBytes($x.FullName)
      if ($bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191){
        $BOMFiles += $x.FullName
      }
    } catch {}
  }
}
if ($BOMFiles.Count -gt 0){ Add-Warn ("XML files with UTF-8 BOM (prefer no BOM):`n  " + ($BOMFiles -join "`n  ")) }

# ---------- 6) code smell checks ----------
$androidR = @()
if (Test-Path $Java){
  $AllKt = @(Get-ChildItem -Path $Java -Recurse -Filter '*.kt' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
  foreach($f in $AllKt){
    $t = Read-AllText $f
    if ($t -match 'import\s+android\.R\b'){ $androidR += $f }
  }
}
if ($androidR.Count -gt 0){ Add-Warn ("Found 'import android.R' (remove to avoid ID collisions):`n  " + ($androidR -join "`n  ")) }

# ---------- REPORT ----------
if (-not $Quiet){
  Write-Host "=== PREVIEW OF ACTIONS (no changes in -DryRun) ===" -ForegroundColor DarkCyan
  if ($Actions.Count -eq 0){ Write-Host "(none)" } else { $Actions | ForEach-Object { Write-Host "- $_" } }
  Write-Host ""
  Write-Host "=== WARNINGS ===" -ForegroundColor Yellow
  if ($Warnings.Count -eq 0){ Write-Host "(none)" } else { $Warnings | ForEach-Object { Write-Host "- $_" } }
  Write-Host ""
}

Write-Host "=== ERRORS ===" -ForegroundColor Red
if ($Errors.Count -eq 0){ Write-Host "(none)" } else { $Errors | ForEach-Object { Write-Host "- $_" } }

if ($Errors.Count -gt 0){ exit 1 } else { exit 0 }
