# Alibi 0.6.0 APK build

Build date: 2026-10-25

## Artifacts

| Package | Application ID | Version | Size | SHA-256 |
| --- | --- | --- | ---: | --- |
| `app-universal-debug.apk` | `app.myzel394.alibi.debug` | `0.6.0 (17)` | 145,587,837 B | `CC278B489E7A66219CF406ADA7375BA1C6C5CBA3A990679D0FB3A357939E20A1` |
| `app-universal-release.apk` | `app.myzel394.alibi` | `0.6.0 (17)` | 125,156,785 B | `51943E5B6DEB26DAC897238365E08709109F9792E02E9533418C4F5A5B870AB0` |

Both APKs use the universal ABI and include `arm64-v8a`, `armeabi-v7a`,
`x86`, and `x86_64`.

- `minSdkVersion`: 24
- `targetSdkVersion`: 34
- `compileSdkVersion`: 36

## Signing

Both APKs verify with APK Signature Scheme v2.

The debug APK uses the Android Debug certificate. The release APK uses a
dedicated 4096-bit RSA release certificate:

- Certificate DN: `CN=Alibi Release, OU=Android, O=zgy-beep, L=Shanghai, ST=Shanghai, C=CN`
- Certificate SHA-256: `5f9d20f64ae2b40b880cb8719afccf71ff62777ad76ef9565cc33a2ca9fcb334`

The release keystore and `key.properties` are stored outside Git and must be
backed up together. Losing them prevents future updates to installations signed
with this certificate.

## Build command

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease
```
