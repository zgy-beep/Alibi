# Alibi 0.6.5 APK

Build date: 2026-10-28

## Artifact

| Package | Application ID | Version | Size | SHA-256 |
| --- | --- | --- | ---: | --- |
| `app-universal-release.apk` | `com.zgybeep.alibi` | `0.6.5 (22)` | 125,397,091 B | `74CECE79C50059AF1013200571496ED3D9CFA1C1F8AD9E5CB27CF99318ED9CE2` |

Universal APK includes `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

- `minSdkVersion`: 24
- `targetSdkVersion`: 34
- `compileSdkVersion`: 36

Changes since 0.6.4:
- Settings architecture refactored to align with camera settings: audio recording, video recording, and storage & backup are now converged into dedicated subpages (`AudioSettingsScreen`, `VideoSettingsScreen`, `StorageSettingsScreen`).
- Main settings page simplified into a clean high-level navigation hub.
- Redesigned modern Material 3 About page featuring app highlights, 100% offline privacy commitments, version info copy action, and official repository links, removing obsolete upstream cryptocurrency donations and GPG keys.

Release notes: [Alibi 0.6.5](../../releases/0.6.5.md).

## Signing

`apksigner verify` passes (APK Signature Scheme v2) with the dedicated release
certificate:

- DN: `CN=Alibi Release, OU=Android, O=zgy-beep, L=Shanghai, ST=Shanghai, C=CN`
- Certificate SHA-256: `5f9d20f64ae2b40b880cb8719afccf71ff62777ad76ef9565cc33a2ca9fcb334`

The certificate matches 0.6.1–0.6.4, so existing installations update in place.

## Verification

| Check | Result |
| --- | --- |
| JVM unit tests | 30 / 30 passed (`:app:testDebugUnitTest`) |
| Lint | 0 error / 0 fatal (`:app:lintDebug`) |
| `:app:assembleRelease` | BUILD SUCCESSFUL (R8 + signing) |
| APK metadata | `versionCode=22`, `versionName=0.6.5`, four ABIs |
| Release APK device smoke | **Not executed**: no device connected via ADB at build time. |

## GitHub release

- Tag: `v0.6.5`
- Release: <https://github.com/zgy-beep/Alibi/releases/tag/v0.6.5>
- Asset: `app-universal-release.apk`
