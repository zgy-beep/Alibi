# Alibi 0.6.6 APK

Build date: 2026-10-28

## Artifacts (Architecture Splits)

| Package | Application ID | Version | Size | SHA-256 |
| --- | --- | --- | ---: | --- |
| `app-arm64-v8a-release.apk` | `com.zgybeep.alibi` | `0.6.6 (23)` | 30,937,745 B | `6B1D9CA30EC5F4CD9804C298EE8340EEA95932AE25F2839587DBC8469A13573E` |
| `app-armeabi-v7a-release.apk` | `com.zgybeep.alibi` | `0.6.6 (23)` | 31,942,872 B | `0E4CB3E6F8CC50F354E87301F4589D6C10FC74DAE43DDB0E7975E4B26D2A652D` |
| `app-x86_64-release.apk` | `com.zgybeep.alibi` | `0.6.6 (23)` | 39,216,383 B | `82995A350EADD6DB99D58B50728A00CB3A47966208B5B74FAE155905B5B24B9D` |
| `app-x86-release.apk` | `com.zgybeep.alibi` | `0.6.6 (23)` | 34,206,137 B | `05A1B5540DF50B41B49271A8997746A443DA0A9E739B00FF5D963B51354EAE52` |

- `minSdkVersion`: 24
- `targetSdkVersion`: 34
- `compileSdkVersion`: 36

Changes since 0.6.5:
- Fixed launcher icon visibility on OEM launchers (such as Huawei HarmonyOS UniHomeLauncher):
  - Explicitly specified `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"` on `MainActivity` in `AndroidManifest.xml` (preventing `icon=0x0` launcher resolution).
  - Added missing `ic_launcher_round.png` assets across all mipmap density directories (hdpi, mdpi, xhdpi, xxhdpi, xxxhdpi).
  - Verified on Huawei Mate 70 Pro (PLR-AL50, HarmonyOS 4.3 / Android 12) via ADB.

Release notes: [Alibi 0.6.6](../../releases/0.6.6.md).

## Signing

`apksigner verify` passes (APK Signature Scheme v2) with the dedicated release
certificate:

- DN: `CN=Alibi Release, OU=Android, O=zgy-beep, L=Shanghai, ST=Shanghai, C=CN`
- Certificate SHA-256: `5f9d20f64ae2b40b880cb8719afccf71ff62777ad76ef9565cc33a2ca9fcb334`

The certificate matches 0.6.1–0.6.5, so existing installations update in place.

## Verification

| Check | Result |
| --- | --- |
| JVM unit tests | 30 / 30 passed (`:app:testDebugUnitTest`) |
| Lint | 0 error / 0 fatal (`:app:lintDebug`) |
| `:app:assembleRelease` | BUILD SUCCESSFUL (R8 + signing) |
| APK metadata | `versionCode=23`, `versionName=0.6.6`, four ABIs |
| Huawei Mate 70 Pro smoke | Installed via ADB, verified launcher icon resolution `icon=0x7f0d0000`, Settings screen navigation, and redesigned About screen. |

## GitHub release

- Tag: `v0.6.6`
- Release: <https://github.com/zgy-beep/Alibi/releases/tag/v0.6.6>
- Assets:
  - `app-arm64-v8a-release.apk`
  - `app-armeabi-v7a-release.apk`
  - `app-x86_64-release.apk`
  - `app-x86-release.apk`
