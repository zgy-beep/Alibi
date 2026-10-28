# Alibi 0.6.3 APK build

Build date: 2026-10-28

## Artifacts

| Package | Application ID | Version | Size | SHA-256 |
| --- | --- | --- | ---: | --- |
| `app-universal-release.apk` | `com.zgybeep.alibi` | `0.6.3 (20)` | 125,356,623 B | `20EE30989D0810F40CAF65801AAFE2A35E4EABC0B61A587FC5129448B9ABAA8C` |

The universal APK includes `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

- `minSdkVersion`: 24
- `targetSdkVersion`: 34
- `compileSdkVersion`: 36

Release notes: [Alibi 0.6.3](../../releases/0.6.3.md). Changes since 0.6.2: large recommended stills
(about 12 MP / 8 MP), quality-first JPEG 100 pipeline with high-quality noise
reduction/edge processing, and CameraX 1.3.4 → 1.5.3. See
[photo quality evidence](../photo-quality-2026-10-27/README.md).

## Signing

The release APK verifies with APK Signature Scheme v2 using the dedicated
4096-bit RSA release certificate:

- Certificate DN: `CN=Alibi Release, OU=Android, O=zgy-beep, L=Shanghai, ST=Shanghai, C=CN`
- Certificate SHA-256: `5f9d20f64ae2b40b880cb8719afccf71ff62777ad76ef9565cc33a2ca9fcb334`

The certificate matches the 0.6.1/0.6.2 releases, so an existing
`com.zgybeep.alibi` installation updates in place. The keystore and
`key.properties` are stored outside Git and must be backed up together.

## Device smoke test

Environment: Android 16 / API 36 emulator `Pixel9_API36` (`emulator-5580`),
1080×2424, density 420, Chinese system locale.

- Installed the universal APK with `adb install -r` over `0.6.2 (19)` →
  `Success`; package reports `0.6.3 (20)` and keeps the same application ID.
- Launched the release build and verified the home screen and settings screen.
  Screenshots: `release-home.png`, `release-settings.png`.
- Floating shutter shortcut: recommended capture worked
  (`Photo captured latencyMs=319`), saved 1856×1392 JPEG (61,265 B) to
  MediaStore.
- Selected “画质优先” in the release camera settings
  (`release-settings-quality.png` shows the updated resolution description),
  then captured through the floating shutter:
  `Image capture mode=1 jpegQuality=100 qualityPriority=true hqNoiseReduction=true hqEdge=true`,
  `Photo captured latencyMs=333`, saved JPEG 84,912 B (vs 61,265 B recommended).
  Screenshots: `release-floating-overlay.png`, `release-quality-capture.png`.
- Simulator camera tops out at 1856×1392, so the 12 MP resolution gain itself
  requires a physical device; the JPEG quality/pipeline change is visible in
  file size and encoder logs above.

Automated checks on the same source (from the photo-quality change):

| Check | Result |
| --- | --- |
| `:app:testDebugUnitTest` | 24 / 24 passed |
| `:app:connectedDebugAndroidTest` | 7 / 7 passed on `Pixel9_API36` |
| `:app:lintDebug` | 0 error / 0 fatal (188 existing warnings) |
| Independent verification | PASS ([summary](../photo-quality-2026-10-27/verification-summary.md)) |

## Build command

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```

## GitHub release

- Tag: `v0.6.3`
- Release: <https://github.com/zgy-beep/Alibi/releases/tag/v0.6.3>
- Asset: `app-universal-release.apk`
