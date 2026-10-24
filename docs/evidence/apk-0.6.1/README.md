# Alibi 0.6.1 APK build

Build date: 2026-10-25

## Artifacts

| Package | Application ID | Version | Size | SHA-256 |
| --- | --- | --- | ---: | --- |
| `app-universal-debug.apk` | `com.zgybeep.alibi.debug` | `0.6.1 (18)` | 145,587,849 B | `AC27D0F265A7B5172CB50984C20068F03F8EFC64283A75ECDBA2044733366D48` |
| `app-universal-release.apk` | `com.zgybeep.alibi` | `0.6.1 (18)` | 125,156,793 B | `734B5BE51F45FC8C3C2A6E7A5BCFE8B03F547C121313FD5CA705D317E21351C4` |

Both APKs use the universal ABI and include `arm64-v8a`, `armeabi-v7a`,
`x86`, and `x86_64`.

- `minSdkVersion`: 24
- `targetSdkVersion`: 34
- `compileSdkVersion`: 36

## Signing

Both APKs verify with APK Signature Scheme v2.

The release APK uses the dedicated 4096-bit RSA release certificate:

- Certificate DN: `CN=Alibi Release, OU=Android, O=zgy-beep, L=Shanghai, ST=Shanghai, C=CN`
- Certificate SHA-256: `5f9d20f64ae2b40b880cb8719afccf71ff62777ad76ef9565cc33a2ca9fcb334`

The release keystore and `key.properties` are stored outside Git and must be
backed up together.

## Build command

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```
