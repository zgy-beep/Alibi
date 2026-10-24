# Alibi 0.6.0 APK build

Build date: 2026-10-25

## Artifacts

| Package | Application ID | Version | Size | SHA-256 |
| --- | --- | --- | ---: | --- |
| `app-universal-debug.apk` | `app.myzel394.alibi.debug` | `0.6.0 (17)` | 145,587,837 B | `CC278B489E7A66219CF406ADA7375BA1C6C5CBA3A990679D0FB3A357939E20A1` |
| `app-universal-release-unsigned.apk` | `app.myzel394.alibi` | `0.6.0 (17)` | 125,144,497 B | `FD750FC2EC4F40633CE92DBAD8853A3BC90F1CD07E91D6075FFD895B39450369` |

Both APKs use the universal ABI and include `arm64-v8a`, `armeabi-v7a`,
`x86`, and `x86_64`.

- `minSdkVersion`: 24
- `targetSdkVersion`: 34
- `compileSdkVersion`: 36

## Signing

The debug APK verifies with APK Signature Scheme v2 using the Android Debug
certificate.

The release APK is intentionally unsigned. No `key.properties` or release
keystore is present in the repository. A production key must be configured
before the release APK can be installed as a production build or attached to a
public release.

## Build command

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease
```
