# Alibi repository memory / 项目约定

This file is the durable instruction sheet for every agent working in this
repository. Read it before changing code.

## 发布规则（强制）

1. **每一次用户可见的修复或功能改动，都必须提升版本号并打包发布**，不能只提交代码、不能只留在工作区。
   - Patch 版本：`0.6.x`，`versionCode` 每次 +1。
   - 当前已发布：`0.6.4 (21)`，tag `v0.6.4`，
     <https://github.com/zgy-beep/Alibi/releases/tag/v0.6.4>。
2. 发布流程固定为：
   - 在 `app/build.gradle` 提升 `versionCode` / `versionName`；
   - 新增 `docs/releases/<version>.md` 发布说明；
   - `:app:testDebugUnitTest`、`:app:lintDebug`（0 error）、`:app:assembleRelease`；
   - 有设备时做真机 smoke test，证据放 `docs/evidence/apk-<version>/`
     （APK SHA-256、签名、截图、设备结果、SHA256SUMS）；
   - 提交，打 `v<version>` tag，推送 `master` 和 tag；
   - 用 `gh release create` 上传 `app-universal-release.apk`，验证 asset digest
     与 Latest 状态。
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

## 验证门槛

- JVM 单测全绿；`:app:lintDebug` 0 error/0 fatal。
- `:app:assembleRelease`（R8 + 签名）成功。
- 相机/权限/悬浮窗改动尽量做真机或 instrumentation 验证；若设备不可用，
  必须在 evidence 中明确写出“未执行”的原因，不能声称已验证。
