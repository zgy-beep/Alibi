# Alibi 0.6.7 APK evidence

Build date: 2026-10-28（Asia/Shanghai）

## Artifacts

| Package | Application ID | Version | Size | SHA-256 |
| --- | --- | --- | ---: | --- |
| `app-arm64-v8a-release.apk` | `com.zgybeep.alibi` | `0.6.7 (24)` | 30,939,341 B | `df6cfa32b72c72e13017f410cdbf98c8fbdc053106d388160aa8d6b5f8c2ea69` |
| `app-armeabi-v7a-release.apk` | `com.zgybeep.alibi` | `0.6.7 (24)` | 31,944,468 B | `b4e2bbb170f8489c3b8d6d3c7c71a5aafc5b2811ccf46e6656b07c394f2a40ff` |
| `app-x86_64-release.apk` | `com.zgybeep.alibi` | `0.6.7 (24)` | 39,217,979 B | `49a73a3dd7c74431416578ff31d93a3fe2797297001ce401103907f54eb431f7` |
| `app-x86-release.apk` | `com.zgybeep.alibi` | `0.6.7 (24)` | 34,207,733 B | `1bd3fd52ee5076bb2ddb87dd302cba1f3ca0f5c410c07f33caa7e18ef5c69eff` |

`app-universal-release.apk`（125 MB）只用于本次构建校验，**不上传**到 GitHub Release。

- `minSdkVersion`: 24
- `targetSdkVersion`: 34
- `compileSdkVersion`: 36
- CameraX: 1.5.3；Kotlin 2.0.21

Changes since 0.6.6:
- 录像模式新增常驻取景预览 + 双指变焦，倍率经启动 Intent 带入录制首帧。
- 拍照双指变焦改为逐帧缓动 + latest-wins 请求合并；绝对倍率输入立即生效；悬浮窗倍率更新节流。
- 拍照变焦指示器独立重组；两指轻点不再误触发拍照。
- 拍照取景器与录像取景器分辨率提升（拍照 1440×1080 / 1920×1080；录像 1280×960）。
- 新增「视频防抖」设置（默认开启，仅在设备支持 Camera2 防抖 ON 模式时请求）。

Release notes: [Alibi 0.6.7](../../releases/0.6.7.md)。

## Signing

`apksigner verify --print-certs` 通过（APK Signature Scheme v2），证书与 0.6.1–0.6.6 相同，可原地升级：

- DN: `CN=Alibi Release, OU=Android, O=zgy-beep, L=Shanghai, ST=Shanghai, C=CN`
- Certificate SHA-256: `5f9d20f64ae2b40b880cb8719afccf71ff62777ad76ef9565cc33a2ca9fcb334`

## Verification

环境：`Pixel9_API36` AVD（Android 16 / API 36，x86_64），1080×2424 px，density 420；虚拟后置相机 `zoomRatioRange = [1.0, 10.0]`，最大 JPEG 1856×1392。

| Check | Result |
| --- | --- |
| JVM 单测 `:app:testDebugUnitTest` | 50 / 50 通过（12 个测试类；含 `SmoothZoomTest` 7、`ZoomUiUpdateThrottleTest` 8、`VideoZoomSettingsTest` 5） |
| `:app:lintDebug` | 0 error / 0 fatal（215 warning，均为既有工程告警） |
| `:app:assembleRelease` | BUILD SUCCESSFUL（R8 + 签名；含 ABI splits） |
| 插桩测试 `PhotoZoomInstrumentedTest` | 3 / 3 通过：高频变焦合并（动态）、黑屏双指捏合、悬浮窗变焦 + rememberZoom |
| 插桩测试 `VideoZoomInstrumentedTest` | 2 / 2 通过：预览双指捏合上报倍率、录制服务按所选倍率启动（相机侧 `zoomState` 校验） |
| 插桩测试结果文件 | [`connected-test-results.xml`](connected-test-results.xml)（5 tests，0 failures） |
| Release APK 安装 | `adb install -r app-x86_64-release.apk` 成功；`versionCode=24`、`versionName=0.6.7` |
| 桌面图标 | Launcher 应用列表可见 Alibi 图标（见 `launcher-drawer.png`）；`aapt2 dump badging` 显示 `launchable-activity` 的 `icon='res/BW.xml'`（非 0x0）；5 种密度 `ic_launcher(_round).png` 实体资产齐全 |
| 关于页版本 | 显示 `v0.6.7 (24)`（见 `about.png`），设置页版本行同样为 `v0.6.7`（见 `settings-scrolled.png`） |
| 拍照 smoke | 黑屏快门 → 上滑显示取景器 → 点按拍照 → 保存成功：`alibi-photo_20261028_150140_046_6f54b136.jpg`，84,323 B，1856×1392（模拟器相机上限） |
| 录像 smoke | 准备面板常驻预览（`release-video-sheet.png`）→ 开始录制 6 s → 保存成功：`alibi-recording-video_recordings-1 (1).mp4`，259,797 B，6.5 s，1280×720 |
| 视频设置 | 「视频防抖」开关默认开启（`video-settings.png`） |
| 双指变焦视觉证据 | 插桩测试捏合过程中截屏显示 `3.0×` 倍率指示（`video-preview-pinch.png`） |

## 截图索引

| 文件 | 说明 |
| --- | --- |
| `release-home.png` | Release APK 首页 |
| `launcher-drawer.png` | Launcher 应用列表中的 Alibi 图标 |
| `release-video-sheet.png` | 录像准备面板：新增常驻取景预览 + 双指缩放提示 |
| `release-video-recording.png` | 录像进行中（00:0x） |
| `release-after-save.png` | 录像保存后回到首页（出现「历史文件」入口） |
| `video-preview-pinch.png` | 插桩捏合测试中的实时倍率指示 `3.0×` |
| `photo-preview-revealed.png` | 黑屏拍照页面上滑显示取景器（1080 级预览） |
| `photo-black.png` | 黑屏拍照初始状态 |
| `photo-setup.png` | 拍照准备页 |
| `recent-photos.png` | 最近照片列表（含刚拍摄的 JPEG） |
| `video-settings.png` | 视频录制设置：新增「视频防抖」 |
| `settings-scrolled.png` | 设置页底部（关于 Alibi v0.6.7） |
| `about.png` | 关于页版本 `v0.6.7 (24)` |

## 复现命令

```bash
./gradlew :app:testDebugUnitTest --console=plain
./gradlew :app:lintDebug --console=plain
./gradlew :app:assembleRelease --console=plain
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.myzel394.alibi.PhotoZoomInstrumentedTest,app.myzel394.alibi.VideoZoomInstrumentedTest \
  --console=plain

adb -s emulator-5554 install -r app/build/outputs/apk/release/app-x86_64-release.apk
adb -s emulator-5554 shell am start -n com.zgybeep.alibi/app.myzel394.alibi.MainActivity
```

## 边界（未验证项，必须如实说明）

- **未连接物理真机**：本轮全部设备验收在 `Pixel9_API36` 模拟器完成；真机上的变焦跟手度、
  90/120 Hz 表现、视频防抖实际效果、高像素相机分辨率、厂商后台限制均未验证。
- 模拟器相机 `setZoomRatio` 单次完成耗时约 0.2 s，手势"流畅度"只能证明请求合并与缓动管线正确，
  不代表真机手感；AGENTS.md 要求的华为 Mate 70 Pro 高像素画质验收本轮未执行（相机 JPEG/
  分辨率管线本轮未改动）。
- 视频防抖仅在设备报告 Camera2 `CONTROL_VIDEO_STABILIZATION_MODE_ON` 时请求；模拟器虚拟相机
  是否实际启用防抖无法从画面判断，防抖画质差异需真机验收。
- 悬浮窗（FLOATING）拍照模式本轮通过插桩测试验证倍率与 rememberZoom，未再逐个截图；
  2 处轻点误拍防护为新增行为，插桩测试未覆盖"两指轻点不拍照"的场景（真机手动验证待补）。
- Android 7–15 的其余版本兼容性未回归。
