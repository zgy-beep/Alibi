# Alibi repository memory / 项目约定

This file is the durable instruction sheet for every agent working in this
repository. Read it before changing code.

## 发布规则（强制）

1. **每一次用户可见的修复或功能改动，都必须提升版本号并打包发布**，不能只提交代码、不能只留在工作区。
   - Patch 版本：`0.6.x`，`versionCode` 每次 +1。
   - 当前已发布：`0.6.7 (24)`，tag `v0.6.7`，
     <https://github.com/zgy-beep/Alibi/releases/tag/v0.6.7>。
2. 发布流程固定为：
   - 在 `app/build.gradle` 提升 `versionCode` / `versionName`；
   - 新增 `docs/releases/<version>.md` 发布说明；
   - `:app:testDebugUnitTest`、`:app:lintDebug`（0 error）、`:app:assembleRelease`；
   - 有设备时做真机 smoke test，证据放 `docs/evidence/apk-<version>/`
     （APK SHA-256、签名、截图、设备结果、SHA256SUMS）；
   - 提交，打 `v<version>` tag，推送 `master` 和 tag；
   - 严禁上传 `app-universal-release.apk`（体积高达 125MB，过大臃肿）；发布时批量上传各架构的小包（`app-arm64-v8a-release.apk`、`app-armeabi-v7a-release.apk`、`app-x86_64-release.apk`、`app-x86-release.apk` 等，单包仅约 30MB），验证 asset digest 与 Latest 状态。
3. 发布时如果仓库的 release workflows 仍因缺少 CI secrets 无法运行，先临时
   `gh workflow disable` 两个 release workflow，手动发布完成后立即
   `gh workflow enable`，并确认 Actions 运行数不变。
4. 不得提交 `key.properties`、`alibi-release.jks`、`.tmp/`、构建产物或任何密钥。

## 项目固定事实

- 应用：Alibi（本仓库），applicationId `com.zgybeep.alibi`；debug 为
  `com.zgybeep.alibi.debug`。
- Toolchain：compileSdk 36、targetSdk 34、minSdk 24、AGP 8.9.1、
  Kotlin 2.0.21、CameraX 1.5.3。
- 发布签名证书 SHA-256：
  `5f9d20f64ae2b40b880cb8719afccf71ff62777ad76ef9565cc33a2ca9fcb334`
  （与 0.6.1–0.6.4 相同，可原地升级）。
- 远端：<https://github.com/zgy-beep/Alibi>，主分支 `master`。
- 发布地址：<https://github.com/zgy-beep/Alibi/releases>。

## 相机相关约定

- 照片质量相关改动必须在真机（至少一台高像素设备）验证分辨率、JPEG 量化表、
  文件大小和回退路径；模拟器相机上限 1856×1392，不能代表真机分辨率。
- 华为 Mate 70 Pro（PLR-AL50，HarmonyOS 4.3 / Android 12）实测：
  - 不支持 CameraX 夜景/HDR 扩展，自动模式必须回退标准管线；
  - 硬件 JPEG 支持 6144×8192（50MP），软件 JPEG 会限制到约 8MP；
  - “画质优先”使用硬件 JPEG 上限判断，保留最大分辨率。
- 变更 `PhotoCaptureService` 后复查：ZSL/扩展回退、分析流、软件 JPEG 条件、
  flash、保存路径和内部存储/MediaStore 行为。
- 变焦约定（0.6.7 起）：
  - 拍照与录像共用的平滑变焦逻辑在 `services/SmoothZoom.kt`（纯 Kotlin、帧率无关缓动）
    与 `services/ZoomRequestQueue.kt`（latest-wins 合并，最多一个 `setZoomRatio` 在途）。
  - 相对输入（双指捏合）用 `scaleBy` 在 target 上累加，由 ~16 ms ticker 逐帧推进；
    绝对输入（悬浮窗滑杆/按钮、恢复上次倍率）必须 `snapTo` 立即生效，不能缓动。
  - 录像倍率经 `VideoRecorderModel.handleIntent` 的 `"initialZoom"` extra 传入
    `VideoRecorderService`，在 `CameraControl.applyInitialZoom()` 中于首帧录制前应用；
    录像界面的 `CameraPreview` 由 `cameraKey`（镜头 id）做重组 key，不能用
    `CameraSelector` 实例（它没有 `equals()`）。
  - 变焦只更新独立状态/回调，不得 `publish()` 全量会话状态；`PhotoZoomInstrumentedTest`
    和 `VideoZoomInstrumentedTest` 是这条管线的回归测试。
- 视频防抖默认开启，但只在相机报告 Camera2
  `CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES` 含 ON 时才调用
  `VideoCapture.Builder.setVideoStabilizationEnabled(true)`。
- 取景器分辨率显式指定（拍照 1440×1080 / 1920×1080，录像 1280×960），
  不要再依赖 CameraX 的 640×480 默认值；绑定失败的回退链必须保留。

## 验证门槛与验收流程（强制）

发布前**必须在真机（优先）或虚拟机（AVD，如 `Pixel9_API36`）进行实际安装与功能烟雾验收**，不能仅凭 JVM 单测或编译通过即发布：

1. **构建与静态质检**：
   - JVM 单测全绿（`:app:testDebugUnitTest`）；
   - `:app:lintDebug` 0 error / 0 fatal；
   - `:app:assembleRelease`（R8 混淆 + 签名）成功。
2. **设备端安装与桌面图标验收**：
   - **ADB 安装**：使用 `adb install -r <apk>` 推送至设备；
   - **桌面图标与 Launcher 验证**：
     - 检查入口 `MainActivity` 必须显式声明 `android:icon` 和 `android:roundIcon`，确保 `query-activities` 查得的 `icon` 绝不能为 `0x0`；
     - 检查所有密度（hdpi/mdpi/xhdpi/xxhdpi/xxxhdpi）必须同时具备 `ic_launcher.png` 与 `ic_launcher_round.png` 实体位图资产；
     - 在桌面检查是否有 Alibi 图标，点击能否成功冷启动；若存在多用户/隐私空间，需确认机主用户（User 0）正常安装。
3. **核心功能与页面验收**：
   - 核心模式（录音、录像、拍照）及悬浮窗交互正常；
   - 设置中枢（`SettingsScreen`）各二级模块（音频、视频、相机、存储与备份）跳转无崩溃，参数生效正常；
   - 关于页面（`AboutScreen`）版本显示、版本号点击复制、隐私承诺与开源链接正常。
4. **证据留存**：
   - 截取真机运行界面与关键设置截图，存入 `docs/evidence/apk-<version>/`，同步输出 `SHA256SUMS.txt` 与测试说明。
   - 若真实设备确实断开且虚拟机不可用，必须在 evidence 中明确写出“未执行”的原因，绝不能声称已验证。
