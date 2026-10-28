# Alibi 0.6.8 APK evidence

Build date: 2026-10-29（Asia/Shanghai）

## Artifacts

| Package | Application ID | Version | Size | SHA-256 |
| --- | --- | --- | ---: | --- |
| `app-arm64-v8a-release.apk` | `com.zgybeep.alibi` | `0.6.8 (25)` | 31,001,277 B | `5a5b23cabd4e189548d317f802c48927f4c191b0b87906f7745d75d0fa582608` |
| `app-armeabi-v7a-release.apk` | `com.zgybeep.alibi` | `0.6.8 (25)` | 32,006,404 B | `55fa1705026680ee8cf43a22d35490d8ccd622596770f0a6037e6c133a32cb47` |
| `app-x86_64-release.apk` | `com.zgybeep.alibi` | `0.6.8 (25)` | 39,279,915 B | `7fc1ccf6de808c90f0328869795bed651cfd5d4f8023798ca15b1855693cd6ff` |
| `app-x86-release.apk` | `com.zgybeep.alibi` | `0.6.8 (25)` | 34,269,669 B | `1cdddf200290fb12d17cdb53bc37d17b0456b0d1af3db6d4ef3f0899d5ea884d` |

`app-universal-release.apk`（125 MB）只用于本次构建校验，**不上传**到 GitHub Release。

- `minSdkVersion`: 24；`targetSdkVersion`: 34；`compileSdkVersion`: 36
- CameraX 1.5.3；Kotlin 2.0.21

Changes since 0.6.7:
- 新增可选的「标准相机」界面（首页入口），拍照/录像模式均常显全屏取景器。
- 拍照：点按对焦/测光（聚焦圈显示结果）、双指变焦、快门闪光反馈、闪光灯 off/auto/on 快捷切换、多摄像头设备可切换前后摄、网格跟随拍照设置、最近照片入口、离开前台自动停止会话。
- 录像：录制前实时预览（可预选倍率）、录制中保持取景器可见、录制中双指变焦（作用于成片）、点按对焦、暂停/继续、计时器、停止后走原有保存管线。
- 底层：`PhotoCaptureMode.STANDARD`、运行时镜头/闪光重绑（`reconfigure`）、对焦 API、倍率范围状态；`VideoRecorderService` 绑定 Preview、支持录制中平滑变焦与对焦、清空取景器不中断录制；保存回调上移到导航层供两种界面共用。
- 修复：标准相机计时器按秒显示（此前恒为 00:00）；单摄像头设备隐藏无法使用的镜头切换按钮；顶部/底部控件加渐变底色保证白图标在明亮画面上可读。

## Signing

`apksigner verify --print-certs` 通过（APK Signature Scheme v2），证书与 0.6.1–0.6.7 相同，可原地升级：

- DN: `CN=Alibi Release, OU=Android, O=zgy-beep, L=Shanghai, ST=Shanghai, C=CN`
- Certificate SHA-256: `5f9d20f64ae2b40b880cb8719afccf71ff62777ad76ef9565cc33a2ca9fcb334`

## Verification

环境：`Pixel9_API36` AVD（Android 16 / API 36，x86_64），1080×2424 px，density 420；**单个虚拟后置摄像头（无前置、无闪光灯硬件）**，JPEG 上限 1856×1392。

| Check | Result |
| --- | --- |
| JVM 单测 `:app:testDebugUnitTest` | 50 / 50 通过（12 个测试类） |
| `:app:lintDebug` | 0 error / 0 fatal（224 warning，均为既有工程告警） |
| `:app:assembleRelease` | BUILD SUCCESSFUL（R8 + 签名 + ABI splits） |
| 插桩 `PhotoZoomInstrumentedTest` | 3 / 3（变焦合并、黑屏捏合、悬浮窗变焦） |
| 插桩 `VideoZoomInstrumentedTest` | 2 / 2（预览捏合、录制起始倍率） |
| 插桩 `StandardCameraInstrumentedTest` | 2 / 2：`STANDARD` 会话 READY + 对焦回调 + 倍率范围校验 + 运行时光重绑；录制清空取景器后仍在录制、录制中 `zoomBy` 促使相机侧 zoomState 变化 |
| Release APK 安装 | `adb install -r app-x86_64-release.apk` 成功；`versionCode=25`、`versionName=0.6.8` |
| 首页入口 | 「标准相机」行可见，点击进入相机界面（`home.png`） |
| 标准相机拍照 | 取景器实时、控件可读、点按聚焦圈可见（`standard-photo.png`、`standard-focus.png`）；快门拍摄保存 `alibi-photo_20261028_173527_955_d4ead70d.jpg`，84,505 B，1856×1392 |
| 标准相机录像 | 录制前预览（`standard-video-preview.png`）；录制中取景器可见、计时器 00:10（`standard-recording-timer.png`）；停止并保存 `alibi-recording-video_recordings-1 (1).mp4`，466,672 B，15.5 s，1280×720；保存后回到预览（`standard-after-save.png`） |
| 关于页版本 | `v0.6.8 (25)`（`about.png`）；`aapt2 dump badging` 显示 `launchable-activity … icon='res/BW.xml'`（非 0x0），5 种密度 `ic_launcher(_round).png` 资产仍在 |

## 截图索引

| 文件 | 说明 |
| --- | --- |
| `home.png` | 首页：新增「标准相机」入口 |
| `standard-photo.png` | 标准相机·拍照模式：全屏取景 + 顶部/底部控件（含渐变底色） |
| `standard-focus.png` | 点按取景器后的对焦圈 |
| `standard-video-preview.png` | 标准相机·录像模式（录制前预览，红色录制按钮） |
| `standard-recording-timer.png` | 录制中：实时取景器、计时器 00:10、暂停/停止 |
| `standard-after-save.png` | 停止保存后回到录制前预览（录制按钮恢复） |
| `about.png` | 关于页 `v0.6.8 (25)` |

## 复现命令

```bash
./gradlew :app:testDebugUnitTest --console=plain
./gradlew :app:lintDebug --console=plain
./gradlew :app:assembleRelease --console=plain
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.myzel394.alibi.PhotoZoomInstrumentedTest,app.myzel394.alibi.VideoZoomInstrumentedTest,app.myzel394.alibi.StandardCameraInstrumentedTest \
  --console=plain

adb -s emulator-5554 install -r app/build/outputs/apk/release/app-x86_64-release.apk
adb -s emulator-5554 shell am start -n com.zgybeep.alibi/app.myzel394.alibi.MainActivity
# 首页 → 标准相机 → 拍照/录像
```

## 边界（未验证项，必须如实说明）

- **未连接物理真机**：全部设备验收在 `Pixel9_API36` 模拟器完成；真机的对焦速度/焦点圈反馈、
  闪光灯行为、多摄像头切换、旋转与厂商后台限制均未验证。
- 模拟器只有**一个后置虚拟摄像头**：因此镜头切换按钮被隐藏（正确的单摄行为），前后摄
  切换代码路径由插桩测试在存在前置摄像头时才断言，另用闪光重绑覆盖同一重绑机制；
  真机前后摄切换待验证。
- 模拟器虚拟相机无闪光灯硬件：闪光灯快捷切换只验证了重绑不崩溃，实际补光效果未验证。
- 模拟器无法注入双指手势：标准相机界面的捏合手势未直接截图验证；其调用的变焦管线
  （`PhotoCaptureModel.zoomBy` / 录像 `zoomBy`）已由插桩测试覆盖，真机手感待验证。
- 旋转屏幕时标准相机界面按竖屏布局设计，横屏/旋转重建未做专项验收。
- Android 7–15 其余版本兼容性未回归。
