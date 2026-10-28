# 照片画质完善验收

日期：2026-10-27 · 版本：基于已发布 0.6.2 (19) 的工作区改动（未发布） · 环境：Android 16 / API 36 模拟器 `Pixel9_API36`，序列号 `emulator-5580`；CameraX 1.5.3。

> **2026-10-28 后续真机修复**：华为 Mate 70 Pro 实测发现软件 JPEG 会把画质模式限制到约 8MP；现已按硬件 JPEG 最大分辨率选择编码器（高像素设备使用硬件 JPEG，保留 50MP），详见[高分辨率设备画质模式分辨率修复](huawei-photo-quality-resolution-2026-10-28.md)。0.6.3 已发布包不包含该修复。

## 结论

照片画质的三个可控制因素已处理：默认分辨率目标从约 2–3 MP 提升到 12 MP / 8 MP，新增“画质优先”高画质编码路径（JPEG 100、软件 JPEG、设备支持的高质量降噪/锐化），并升级 CameraX 到 1.5.3 以修复 API 36 上非 ZSL 拍摄无法打开会话的问题。模拟器实测画质模式量化表达到 JPEG 100（推荐路径为 95），文件与处理路径符合预期；JVM 24/24、instrumentation 7/7、lint 0 error、release 构建通过，视频录制与悬浮拍照回归正常。系统相机的 HDR/夜景/多帧计算摄影与点按对焦仍无法通过当前 CameraX 基础管线等价复现，物理真机画质矩阵尚未执行。

## 原因分析

1. **默认分辨率目标偏低**：原“推荐”目标为 1920×1440 / 1920×1080，真机通常只保存约 200–300 万像素；系统相机默认一般是 1200 万像素，放大细节差距明显。
2. **默认快拍优先**：FAST/ZSL/30fps + `CAPTURE_MODE_MINIMIZE_LATENCY` + JPEG 95，优先响应速度而非画质。
3. **CameraX 基础单帧管线**：没有系统相机的多帧 HDR、夜景、降噪/锐化/AI 处理；这是第三方相机 API 的边界。
4. **缺少点按对焦/曝光补偿**：连续 AF 在弱光/近距离仍可能对不到主体。
5. **保存链路无二次压缩**：内部存储/MediaStore/SAF 都是原始 JPEG 字节拷贝，因此质量损失不来自保存环节。

## 改动内容

### CameraX 升级

- CameraX 1.3.4 → **1.5.3**（`app/build.gradle`）。
- 兼容性：CameraX 1.5.3 的 AAR 要求 `minCompileSdk=35`、AGP ≥ 8.6.0，kotlin-stdlib 2.0.21；项目为 compileSdk 36、AGP 8.9.1、Kotlin 2.0.21，满足要求。最新稳定版 1.6.2 需要 kotlin-stdlib 2.1.20，需另行升级 Kotlin/Compose 插件，本次不混入。
- 1.5.3 修复了 1.3.4 在 API 36 模拟器上非 ZSL `ImageCapture` 打开会话抛 `StreamUseCaseUtil` IndexOutOfBounds 的问题，使“画质优先”的稳定非 ZSL 路径可用。
- 回归：视频录制（CameraX Video）保存 mp4 成功；悬浮拍照、双指缩放、变焦合并 instrumentation 全通过。

### 分辨率目标（[PhotoCaptureTargets.kt](../../app/src/main/java/app/myzel394/alibi/helpers/PhotoCaptureTargets.kt)）

- 速度优先：1280×960（4:3）/1280×720（16:9）。
- 推荐：4000×3000（4:3）/3840×2160（16:9），约 12MP / 8MP。
- 画质优先：目标 10000×10000，配合 `ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER` 选择设备支持的最大尺寸。
- 新增 `PhotoCaptureTargetsTest`（3 项），覆盖三档目标与画质优先判定。

### 画质优先拍摄管线（[PhotoCaptureService.kt](../../app/src/main/java/app/myzel394/alibi/services/PhotoCaptureService.kt)）

- `resolutionPreference=QUALITY` 时：
  - JPEG 质量显式 100，并请求软件 JPEG 编码器（API 36 模拟器硬件 JPEG 在请求 100/MAXIMIZE 时仍输出质量 95 量化表，软件编码器可稳定输出 100）；
  - 关闭 ZSL 和强制 30fps，使用 `CAPTURE_MODE_MINIMIZE_LATENCY` 的稳定非 ZSL 路径；
  - 设备支持时通过 Camera2Interop 设置 `NOISE_REDUCTION_MODE_HIGH_QUALITY` 与 `EDGE_MODE_HIGH_QUALITY`；
  - 跳过额外 ImageAnalysis 流（仅 Preview + ImageCapture 两个 surface；预览仍驱动 AE/AF）。
- 推荐路径保持快速/ZSL/JPEG 95；推荐 + 非 ZSL 设备显式 JPEG 98（高于 CameraX 默认 95）。
- 设置页“照片分辨率”说明文案同步更新（中/英）。
- JVM 测试配置增加 `testOptions.unitTests.returnDefaultValues = true`：CameraX 1.5.3 的 Video `Quality` 静态初始化在 JVM 测试中会触碰 `android.util.Size`，仅影响单元测试环境。

## 验证

| 检查 | 结果 |
| --- | --- |
| JVM 单元测试 | 24 / 24（原 21 + `PhotoCaptureTargetsTest` 3） |
| Android instrumentation | 7 / 7（4 原有 + 双指缩放/悬浮变焦/变焦合并 3 项） |
| lint | 0 error / 0 fatal（188 既有 warning；`RestrictedApi` 0，已集中 `@SuppressLint("RestrictedApi")` 并注释原因） |
| `:app:assembleRelease` | 通过（R8 + 签名；universal APK 125,356,623 B，签名验证通过；APK 内 CameraX 元数据 1.5.3） |
| 画质实测（模拟器 1856×1392 上限） | 推荐：61,225 B，亮度量化表 `[8,6,5,8,12,20]`（JPEG95）；画质优先：83,408 B，量化表 `[1,1,1,1,1,1]`（JPEG100）；拍摄时延 441 / 478 ms |
| 视频回归 | 首页录像 → 保存 mp4（独立验收 256,945 B），编码器正常释放，无崩溃/ANR |
| 稳定性 | logcat 无 `StreamUseCaseUtil` IndexOutOfBounds、无 `Photo capture failed`、无 FATAL/ANR |

独立验收（quality-verifier，未参与实现）复跑全部测试、独立重新拍摄并核对 JPEG 量化表、视频与悬浮回归，结论 **PASS**；详见[独立验证摘要](../evidence/photo-quality-2026-10-27/verification-summary.md)。证据样例与复现命令见[照片画质完善证据](../evidence/photo-quality-2026-10-27/README.md)。

## 边界与未覆盖

- 模拟器相机上限为 1856×1392，12MP/最大分辨率收益只能在物理真机验证；本文以目标代码与单测作为分辨率证据，不以模拟器截图冒充真机画质。
- 与系统相机仍有计算摄影差距（HDR、夜景多帧、降噪/锐化算法、AI 场景优化）；当前实现改善的是分辨率、编码质量与 ISP 降噪/锐化档位，不是完整的多帧管线。
- 未实现点按对焦/曝光补偿；弱光与近距离对焦仍可能不如系统相机。
- 软件 JPEG 使用 CameraX 受限 API，已限制在“画质优先”路径并加注释；若未来升级 CameraX 需重新评估该 API。
- 本次未改版本号、未打包发布；已发布 0.6.2 仍是旧画质行为。真机颜色/噪声/手感矩阵、90/120 Hz、以及是否发布 0.6.3 均需用户确认。
- CameraX 1.6.2 需要 Kotlin 2.1.20 及以上，本次仅升到 1.5.3，升级 1.6 需单独回归。

## 非阻塞评审批注处理

- `captureModeFor()` 注释已按实测改为“API 36 模拟器上硬件 JPEG 即使请求 100/MAXIMIZE 仍输出 95 量化表”，不再声称 CameraX 忽略显式质量。
- 非 ZSL 推荐的 JPEG 98 为有意设置（高于 CameraX 默认 95），已在证据与本文档说明。
- `README.md` / `docs/plans/black-screen-floating-camera.md` 的并行文档改动已核对，与本实现一致。
