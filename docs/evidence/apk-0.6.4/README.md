# Alibi 0.6.4 APK

Build date: 2026-10-28

## Artifact

| Package | Application ID | Version | Size | SHA-256 |
| --- | --- | --- | ---: | --- |
| `app-universal-release.apk` | `com.zgybeep.alibi` | `0.6.4 (21)` | 125,395,559 B | `108606E71264DC7222732851A20922AB6D76CE4A04CA4386595CF385E4776DCA` |

Universal APK includes `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

- `minSdkVersion`: 24
- `targetSdkVersion`: 34
- `compileSdkVersion`: 36

Changes since 0.6.3: optional vendor night/HDR camera extensions with automatic
fallback and a support status row, plus the high-resolution quality fix that
keeps hardware JPEG (50 MP class) instead of capping quality mode to ~8 MP.
Release notes: [Alibi 0.6.4](../../releases/0.6.4.md).

## Signing

`apksigner verify` passes (APK Signature Scheme v2) with the dedicated release
certificate:

- DN: `CN=Alibi Release, OU=Android, O=zgy-beep, L=Shanghai, ST=Shanghai, C=CN`
- Certificate SHA-256: `5f9d20f64ae2b40b880cb8719afccf71ff62777ad76ef9565cc33a2ca9fcb334`

The certificate matches 0.6.1/0.6.2/0.6.3, so existing installations update in
place.

## Verification

| Check | Result |
| --- | --- |
| JVM unit tests | 30 / 30 passed |
| Lint | 0 error / 0 fatal (188 existing warnings) |
| `:app:assembleRelease` | BUILD SUCCESSFUL (R8 + signing) |
| APK metadata | `versionCode=21`, `versionName=0.6.4`, four ABIs |
| Huawei Mate 70 Pro smoke (debug build) | Extensions unsupported → automatic fallback; quality mode 6144×8192 (50 MP) JPEG 100 |
| Release APK device smoke | **Not executed**: phone disconnected from ADB at 15:04 (no devices). Debug build from the same source was tested on the phone before disconnect. |

Device validation details:

- Huawei `PLR-AL50`, HarmonyOS `4.3.0.208` / Android 12 (API 31).
- Extension status row: “本机不提供夜景或 HDR 扩展” → automatic fallback works.
- Quality profile: `6144×8192` (50 MP), 14,696,183 B, JPEG 100 quantization
  table, vs 3264×2448 before the fix.
- See [camera extensions evidence](../camera-extensions-2026-10-28/README.md)
  and [Huawei quality fix](../huawei-photo-quality-2026-10-28/README.md).

## GitHub release

- Tag: `v0.6.4`
- Release: <https://github.com/zgy-beep/Alibi/releases/tag/v0.6.4>
- Asset: `app-universal-release.apk`
