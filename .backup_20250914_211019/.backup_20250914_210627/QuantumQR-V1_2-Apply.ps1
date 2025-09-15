param(
  [string]$Root = 'C:\QRSanitized\QuantumQR'
)

# ---------- util ----------
$ErrorActionPreference = 'Stop'
function Fail($m){ Write-Host "ERROR: $m" -ForegroundColor Red; throw $m }
function Info($m){ Write-Host $m -ForegroundColor Cyan }
function Ok($m){ Write-Host $m -ForegroundColor Green }
function Read-AllText([string]$p){ try { return [IO.File]::ReadAllText($p,[Text.Encoding]::UTF8) } catch { return $null } }
function Write-TextUtf8NoBom([string]$Path,[string]$Text){ $enc = New-Object System.Text.UTF8Encoding($false); [IO.File]::WriteAllText($Path,$Text,$enc) }
function Save-ResBackup([string]$Root,[string]$Src){
  $destDir = Join-Path $Root ".res_backups"
  if (!(Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir | Out-Null }
  $ts = Get-Date -Format "yyyyMMdd-HHmmss"
  $name = (Split-Path $Src -Leaf)
  Copy-Item $Src (Join-Path $destDir "$ts-$name") -Force
}

# ---------- 0) layout ----------
if (!(Test-Path $Root)) { Fail "Project root not found: $Root" }
$App    = Join-Path $Root 'app'
$Main   = Join-Path $App 'src\main'
$Java   = Join-Path $Main 'java'
$Mani   = Join-Path $Main 'AndroidManifest.xml'
$GradleGroovy = Join-Path $App 'build.gradle'
$GradleKts    = Join-Path $App 'build.gradle.kts'
if (!(Test-Path $App))  { Fail "Missing app module at $App" }
if (!(Test-Path $Main)) { Fail "Missing main source set at $Main" }
if (!(Test-Path $Java)) { New-Item -ItemType Directory -Path $Java | Out-Null }

# ---------- 1) resolve namespace/package ----------
$pkg = $null
if (Test-Path $GradleKts) {
  $g = Read-AllText $GradleKts
  if ($g) { $m = [regex]::Match($g, 'namespace\s*=\s*"([^"]+)"'); if ($m.Success){ $pkg = $m.Groups[1].Value } }
}
if (-not $pkg -and (Test-Path $GradleGroovy)) {
  $g = Read-AllText $GradleGroovy
  if ($g) { $m = [regex]::Match($g, "namespace\s+['""]([^'""]+)['""]"); if ($m.Success){ $pkg = $m.Groups[1].Value } }
}
if (-not $pkg -and (Test-Path $Mani)) {
  $mf = Read-AllText $Mani
  if ($mf) { $m = [regex]::Match($mf, 'package="([^"]+)"'); if ($m.Success){ $pkg = $m.Groups[1].Value } }
}
if (-not $pkg -and (Test-Path $Java)) {
  $AllKt = @(Get-ChildItem -Path $Java -Recurse -Filter '*.kt' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
  $pkgCounts = @{}
  foreach($f in $AllKt){
    $t = Read-AllText $f
    if ($null -eq $t) { continue }
    $pm = [regex]::Match($t, '^\s*package\s+([A-Za-z0-9_\.]+)', 'Multiline')
    if ($pm.Success) {
      $p = $pm.Groups[1].Value.Trim()
      if (-not [string]::IsNullOrWhiteSpace($p)) { if (-not $pkgCounts.ContainsKey($p)) { $pkgCounts[$p] = 0 }; $pkgCounts[$p]++ }
    }
  }
  if ($pkgCounts.Count -gt 0) { $pkg = ($pkgCounts.GetEnumerator() | Sort-Object -Property Value -Descending | Select-Object -First 1).Key }
}
if (-not $pkg) { Fail "Could not resolve package/namespace from Gradle/Manifest/Kotlin files." }
Info "Namespace: $pkg"

$pkgPath = ($pkg -replace '\.', [IO.Path]::DirectorySeparatorChar)
$CodeDir = Join-Path $Java $pkgPath
if (!(Test-Path $CodeDir)) { New-Item -ItemType Directory -Path $CodeDir -Force | Out-Null }
Ok "Code dir: $CodeDir"

# ---------- 2) Feedback.kt (haptic + beep) ----------
$FeedbackPath = Join-Path $CodeDir 'Feedback.kt'
if (!(Test-Path $FeedbackPath)) {
  $feedback = @'
package __PKG__

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

object Feedback {
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }
    fun success(ctx: Context) {
        val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(30)
        }
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }
}
'@
  $feedback = $feedback.Replace('__PKG__', $pkg)
  Write-TextUtf8NoBom $FeedbackPath $feedback
  Ok "Wrote Feedback.kt"
} else { Info "Feedback.kt exists - skip" }

# ---------- 3) ShareUtils.kt (copy/share) ----------
$SharePath = Join-Path $CodeDir 'ShareUtils.kt'
if (!(Test-Path $SharePath)) {
  $share = @'
package __PKG__

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

object ShareUtils {
    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("QR", text))
    }
    fun share(ctx: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share result"))
    }
}
'@
  $share = $share.Replace('__PKG__', $pkg)
  Write-TextUtf8NoBom $SharePath $share
  Ok "Wrote ShareUtils.kt"
} else { Info "ShareUtils.kt exists - skip" }

# ---------- 4) ProGuard/R8 keep rules ----------
$ProPath = Join-Path $App 'proguard-rules.pro'
$proWanted = @'
# ZXing / JourneyApps
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class com.journeyapps.** { *; }
-dontwarn com.journeyapps.**

# CameraX cushion
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# App package
-keep class __PKG__.** { *; }
'@
$proWanted = $proWanted.Replace('__PKG__', $pkg)

if (Test-Path $ProPath) {
  $cur = Read-AllText $ProPath
  $need = $false
  @('-keep class com.google.zxing.','-keep class androidx.camera.',"-keep class $pkg.") | ForEach-Object { if ($cur -notlike "*$_*") { $need = $true } }
  if ($need) { Write-TextUtf8NoBom $ProPath ($cur.TrimEnd() + "`r`n`r`n" + $proWanted.Trim() + "`r`n"); Ok "Updated proguard-rules.pro" } else { Info "proguard-rules.pro already has keeps - skip" }
} else {
  Write-TextUtf8NoBom $ProPath $proWanted
  Ok "Created proguard-rules.pro"
}

# ---------- 5) Enable shrink in Gradle ----------
if (Test-Path $GradleKts) {
  $g = Read-AllText $GradleKts
  if ($g -notmatch 'isMinifyEnabled\s*=\s*true') {
    $appendKts = @'
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                file("proguard-rules.pro")
            )
        }
    }
}
'@
    Write-TextUtf8NoBom $GradleKts ($g.TrimEnd() + "`r`n`r`n" + $appendKts)
    Ok "Appended shrink config (KTS)"
  } else { Info "R8 shrink already enabled (KTS) - skip" }
} elseif (Test-Path $GradleGroovy) {
  $g = Read-AllText $GradleGroovy
  if ($g -notmatch 'minifyEnabled\s+true') {
    $appendGroovy = @'
android {
    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
'@
    Write-TextUtf8NoBom $GradleGroovy ($g.TrimEnd() + "`r`n`r`n" + $appendGroovy)
    Ok "Appended shrink config (Groovy)"
  } else { Info "R8 shrink already enabled (Groovy) - skip" }
} else { Fail "No app/build.gradle(.kts) found." }

# ---------- 6) Manifest: add VIBRATE + camera.any ----------
if (!(Test-Path $Mani)) { Fail "AndroidManifest.xml not found at $Mani" }
$manifestText = Read-AllText $Mani
if ($manifestText -notmatch '<uses-permission\s+android:name="android\.permission\.VIBRATE"') {
  Save-ResBackup $Root $Mani
  $manifestText = ($manifestText -replace '(</manifest>\s*)$', "    <uses-permission android:name=""android.permission.VIBRATE""/>`r`n`$1")
  Write-TextUtf8NoBom $Mani $manifestText
  Ok "Added VIBRATE permission (backup saved)"
}
if ($manifestText -notmatch '<uses-feature\s+android:name="android\.hardware\.camera\.any"') {
  Save-ResBackup $Root $Mani
  $manifestText = ($manifestText -replace '(</manifest>\s*)$', "    <uses-feature android:name=""android.hardware.camera.any""/>`r`n`$1")
  Write-TextUtf8NoBom $Mani $manifestText
  Ok "Added camera.any feature (backup saved)"
}

# ---------- 7) pick Activity to patch ----------
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
$AllKt = @(Get-ChildItem -Path $Java -Recurse -Filter '*.kt' -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
if ($AllKt.Count -eq 0) { Fail "No Kotlin files found under $Java" }
$Scored = @()
foreach($f in $AllKt){ $Scored += [pscustomobject]@{Path=$f; Score=(Score-Kt $f)} }
$Best = $Scored | Sort-Object Score -Descending | Select-Object -First 1
$ActivityFile = $Best.Path
$t = Read-AllText $ActivityFile
$actMatch = [regex]::Match($t,'class\s+([A-Za-z_]\w*Activity)\b')
if (-not $actMatch.Success) {
  $fallback = $AllKt | Where-Object { (Read-AllText $_) -match 'class\s+\w+Activity\b' } | Select-Object -First 1
  if (-not $fallback) { Fail "Could not locate any Activity class to patch." }
  $ActivityFile = $fallback; $t = Read-AllText $ActivityFile; $actMatch = [regex]::Match($t,'class\s+([A-Za-z_]\w*Activity)\b')
}
$ActivityName = $actMatch.Groups[1].Value
Info "Target Activity: $ActivityName ($(Split-Path $ActivityFile -Leaf))"

# ---------- 8) patch Activity ----------
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
function Ensure-Prop([string]$src,[string]$name,[string]$decl,[string]$className){
  if ($src -match "(?m)^\s*(private\s+)?(lateinit\s+)?(var|val)\s+$name\b") { return $src }
  $classHdr = [regex]::Match($src,"class\s+$className\b[^{]*\{")
  if (-not $classHdr.Success) { return $src }
  $insertAt = $classHdr.Index + $classHdr.Length
  return ($src.Substring(0,$insertAt) + "`r`n    $decl`r`n" + $src.Substring($insertAt))
}
function Ensure-Func([string]$src,[string]$sig,[string]$body){
  if ($src -match "(?s)$sig") { return $src }
  $idx = $src.LastIndexOf('}')
  if ($idx -lt 0) { return $src + "`r`n$body`r`n" }
  return ($src.Substring(0,$idx) + "`r`n$body`r`n" + $src.Substring($idx))
}

$imports = @(
  'import android.Manifest',
  'import android.content.pm.PackageManager',
  'import androidx.activity.result.contract.ActivityResultContracts',
  'import androidx.appcompat.app.AlertDialog',
  'import androidx.core.app.ActivityCompat',
  'import androidx.camera.core.Camera',
  'import androidx.camera.lifecycle.ProcessCameraProvider',
  'import androidx.camera.core.Preview',
  'import androidx.camera.core.ImageAnalysis',
  'import androidx.camera.core.CameraSelector',
  'import androidx.camera.core.TorchState',
  'import androidx.core.content.ContextCompat',
  'import androidx.lifecycle.LifecycleOwner'
)

$t2 = Add-ImportsText $t $imports
$t2 = Ensure-Prop $t2 'camera'         'private var camera: Camera? = null'                        $ActivityName
$t2 = Ensure-Prop $t2 'cameraProvider' 'private var cameraProvider: ProcessCameraProvider? = null' $ActivityName
$t2 = Ensure-Prop $t2 'torchOn'        'private var torchOn: Boolean = false'                      $ActivityName
$t2 = Ensure-Prop $t2 'lastResultText' 'private var lastResultText: String? = null'                $ActivityName

$func_perm = @'
    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCameraSafe() else finish()
    }

    private fun ensureCameraPermission() {
        when {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> startCameraSafe()
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) -> {
                AlertDialog.Builder(this)
                    .setTitle("Camera permission")
                    .setMessage("We need your camera to scan QR codes. No camera = no scans, bru.")
                    .setPositiveButton("Allow") { _, _ -> requestCamera.launch(Manifest.permission.CAMERA) }
                    .setNegativeButton("Not now") { _, _ -> finish() }
                    .show()
            }
            else -> requestCamera.launch(Manifest.permission.CAMERA)
        }
    }
'@
$func_startSafe = @'
    private fun startCameraSafe() {
        // Try to call your existing startCamera() via reflection if present; swallow if absent.
        try {
            val m = this::class.java.getDeclaredMethod("startCamera")
            m.isAccessible = true
            m.invoke(this)
        } catch (_: Throwable) { /* no-op */ }
    }
'@
$func_toggle = @'
    private fun toggleTorch() {
        val c = camera ?: return
        c.cameraControl.enableTorch(!torchOn)
    }

    private fun updateFlashUi(on: Boolean) {
        // If you have fancy UI, adjust here; fallback toggles text
        findViewById<android.widget.Button>(R.id.btnFlash)?.text = if (on) "Flash Off" else "Flash On"
    }

    private fun onScanSuccess(text: String) {
        lastResultText = text
        Feedback.success(this)
        // update your UI/bottom sheet as needed
    }
'@
$onResume = @'
    override fun onResume() {
        super.onResume()
        ensureCameraPermission()
        // Safe wiring (works with or without ViewBinding)
        findViewById<android.widget.Button>(R.id.btnFlash)?.setOnClickListener { toggleTorch() }
        findViewById<android.widget.Button>(R.id.btnCopy)?.setOnClickListener {
            lastResultText?.let { txt -> ShareUtils.copy(this, txt) }
        }
        findViewById<android.widget.Button>(R.id.btnShare)?.setOnClickListener {
            lastResultText?.let { txt -> ShareUtils.share(this, txt) }
        }
    }
'@
$onPause = @'
    override fun onPause() {
        super.onPause()
        try { cameraProvider?.unbindAll() } catch (_: Throwable) {}
    }
'@
$onDestroy = @'
    override fun onDestroy() {
        super.onDestroy()
        try { cameraProvider?.unbindAll() } catch (_: Throwable) {}
    }
'@

$t2 = Ensure-Func $t2 'registerForActivityResult\(' $func_perm
$t2 = Ensure-Func $t2 'fun\s+startCameraSafe\('     $func_startSafe
$t2 = Ensure-Func $t2 'fun\s+toggleTorch\('         $func_toggle
$t2 = Ensure-Func $t2 'override\s+fun\s+onResume\(' $onResume
$t2 = Ensure-Func $t2 'override\s+fun\s+onPause\('  $onPause
$t2 = Ensure-Func $t2 'override\s+fun\s+onDestroy\(' $onDestroy

# Optional: attach torch observer if we can see a bound Camera variable
if ($t2 -notmatch 'cameraInfo\.torchState\.observe' -and $t2 -match '(\w+)\s*=\s*.*bindToLifecycle\(') {
  $localCam = $Matches[1]
  if ($t2 -notmatch "this\.camera\s*=\s*$localCam") {
    $t2 = $t2 -replace '(bindToLifecycle\([^\r\n]+)\)',
      ("`$1)" + "`r`n            this.camera = $localCam`r`n            $localCam.cameraInfo.torchState.observe(this) { state ->`r`n                torchOn = (state == TorchState.ON)`r`n                updateFlashUi(torchOn)`r`n            }")
    Info "Attached torch observer to bound Camera ($localCam)"
  }
}

Write-TextUtf8NoBom $ActivityFile $t2
Ok "Patched $ActivityName"

# ---------- 9) Done ----------
Ok "v1.2 quick slice applied. Build debug and test flashlight/copy/share + haptic/beep on a scan."
