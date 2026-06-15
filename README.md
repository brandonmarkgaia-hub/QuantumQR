# QuantumQR — Ad-Free QR & Barcode Scanner (Android)

A modern, privacy-first QR & barcode scanner built with Kotlin, CameraX, and ML Kit.

- Fast scanning of all major formats (QR, Data Matrix, Aztec, PDF417, UPC/EAN, Code 128/39, and more)
- Flashlight toggle, batch mode (continuous scan), and scan history
- Built-in QR generator (save PNG to gallery)
- No ads and no tracking. Minimal permissions. Opens links via Chrome Custom Tabs (with an optional confirmation toggle)
- CSV export of scan history

## Requirements

- Android Studio (latest stable recommended)
- JDK 17
- Android SDK 34
- Minimum device: Android 7.0 (API 24)

## Tech stack

- Kotlin
- CameraX 1.3.x
- Google ML Kit barcode-scanning 17.3.0
- ZXing (QR generation)
- Room (scan history)

## Build (debug)

1. Open the project in Android Studio (File > Open > QuantumQR).
2. Let Gradle sync.
3. Run on a device or emulator (Android 7.0+).

## Release AAB (for Play Console)

The project currently builds with the default debug signing. Before publishing,
create your own release signing config:

1. In Android Studio: Build > Generate Signed App Bundle / APK…
2. Choose **App Bundle**, create a new keystore, and set the passwords and key alias.
3. Store the keystore securely and back it up. You cannot update the app on Play
   without it.
4. Reference your release signing config in `app/build.gradle` (keep secrets in
   `keystore.properties` / `local.properties`, which are git-ignored).
5. Build the bundle: Build > Build Bundle(s) / APK(s) > Build Bundle(s).
6. Output: `app/build/outputs/bundle/release/app-release.aab`
7. Upload the `.aab` to the Play Console and complete the content rating,
   privacy policy URL, Data Safety form, and store listing.

See `STORE_LISTING.md` for draft store-listing copy and a pre-launch checklist.

## Application ID

`com.quantumqr`

This is the permanent package name used in `app/build.gradle` (`applicationId`)
and the `namespace`. It cannot be changed once the app is published.

## Privacy

- Only the CAMERA permission is required for scanning (plus VIBRATE for haptic feedback).
- Gallery import uses the system picker, avoiding broad storage access on modern Android.
- No analytics and no ads SDKs are included.
- Keep these claims in sync with the AndroidManifest and the Play Data Safety form.

## Roadmap

- Opt-in Safe Browsing URL check (Google API).
- WiFi / contact / calendar QR parsing with quick actions.
- Cloud backup for history.
- iOS port (Swift + AVFoundation + VisionKit equivalent).

## Notes

- Batch mode toggles de-duplication; in batch, multiple codes can be scanned sequentially.
- Backup/scratch folders are intentionally excluded via `.gitignore`.
