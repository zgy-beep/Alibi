# Alibi 0.6.2 APK build

Build date: 2026-10-27

## Artifacts

| Package | Application ID | Version | Size | SHA-256 |
| --- | --- | --- | ---: | --- |
| `app-universal-release.apk` | `com.zgybeep.alibi` | `0.6.2 (19)` | 125,191,605 B | `2B0E33B4D19EC49917A536605426C7FF885ABDB0DD02D35715F8D307A9956477` |

The universal APK includes `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

- `minSdkVersion`: 24
- `targetSdkVersion`: 34
- `compileSdkVersion`: 36

Release notes: [Alibi 0.6.2](../../releases/0.6.2.md).

## Signing

The release APK verifies with APK Signature Scheme v2 using the dedicated
4096-bit RSA release certificate:

- Certificate DN: `CN=Alibi Release, OU=Android, O=zgy-beep, L=Shanghai, ST=Shanghai, C=CN`
- Certificate SHA-256: `5f9d20f64ae2b40b880cb8719afccf71ff62777ad76ef9565cc33a2ca9fcb334`

The certificate matches the 0.6.1 release, so an existing `com.zgybeep.alibi`
installation updates in place. The keystore and `key.properties` are stored
outside Git and must be backed up together.

## Device smoke test

Environment: Android 16 / API 36 emulator `Pixel9_API36` (`emulator-5580`),
1080×2424, density 420, Chinese system locale.

- Installed the universal release APK over the previous `0.6.1 (18)` release
  with `adb install -r` → `Success`; package reports `0.6.2 (19)` and keeps the
  same application ID.
- Completed onboarding on the release build and verified the home screen,
  unified settings screen, and the floating shutter zoom panel from a launcher
  shortcut. Screenshots: `release-home.png`, `release-settings.png`,
  `release-floating-overlay.png`, `release-floating-panel.png`,
  `release-floating-zoomed.png`.
- Home screenshot contains the storage warning shown by the emulator image
  (low free space); it is an environment warning, not a build defect.

Automated checks run on the same source (debug variant):

| Check | Result |
| --- | --- |
| `:app:testDebugUnitTest` | 21 / 21 passed |
| `:app:connectedDebugAndroidTest` | 7 / 7 passed on `Pixel9_API36` |
| `:app:lintDebug` | 0 error / 0 fatal (188 warnings, existing engineering warnings) |
| `:app:assembleRelease` | passed |

## Build command

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```

## GitHub release

- Tag: `v0.6.2`
- Release: <https://github.com/zgy-beep/Alibi/releases/tag/v0.6.2>
- Asset: `app-universal-release.apk`
