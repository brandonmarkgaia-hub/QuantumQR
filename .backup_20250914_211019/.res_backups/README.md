
# QuantumQR — Ad-Free QR & Barcode Scanner (Android)

A modern, premium QR & barcode scanner built with Kotlin, CameraX, and ML Kit.
- ⚡ Fast scanning of **all major formats** (QR, Data Matrix, Aztec, PDF417, UPC/EAN, Code 128/39, etc.)
- 🔦 Flashlight toggle, batch mode (continuous scan), scan history
- 🧰 Built-in **QR generator** (save PNG to gallery)
- 🔒 No ads. Minimal permissions. Opens links via Chrome Custom Tabs (with confirmation toggle)
- 📄 CSV export of scan history

## Requirements
- Android Studio Giraffe or newer (AGP 8.6, Kotlin 1.9.24)
- Android SDK 34
- JDK 17

## Build
1. Open the folder in Android Studio (`File > Open > QuantumQR`).
2. Let Gradle sync.
3. Run on a device (Android 6.0+).

## Release AAB (for Play Console)
1. Replace the temporary signing config:
   - Create a keystore: **Build > Generate Signed App Bundle / APK…**
   - Choose **App Bundle**, create new keystore, set passwords and key alias.
   - Save it securely. Update `app/build.gradle` to reference your **release** signing config instead of `debug`.
2. Build the release bundle: **Build > Build Bundle(s) / APK(s) > Build Bundle(s)**.
   - Output: `app/build/outputs/bundle/release/app-release.aab`
3. Upload `.aab` to Play Console, complete content rating, privacy policy URL, and listing.

## Package Name
`com.firstfreight.quantumqr` (change in `app/build.gradle` and `AndroidManifest.xml` if needed).

## Privacy
- Only **CAMERA** permission is required for scanning.
- Gallery import uses the system picker, avoiding broad storage access on modern Android.
- No analytics/ads SDKs included.

## Roadmap / TODO
- Safe Browsing URL check (Google API) with opt‑in toggle.
- iOS port (Swift + AVFoundation + VisionKit equivalent).
- Cloud backup for history (Google Drive).

## Notes
- ML Kit barcode-scanning v17.2.0 is used.
- Batch mode toggles de‑duplication; in batch, multiple codes can be scanned sequentially.
