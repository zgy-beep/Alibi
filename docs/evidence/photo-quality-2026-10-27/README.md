# 照片画质完善证据

对应报告：[照片画质完善验收](../../reviews/photo-quality-2026-10-27.md)。

环境：Android 16 / API 36 模拟器 `Pixel9_API36`，序列号 `emulator-5580`；1080×2424，density 420；系统语言中文；应用包 `com.zgybeep.alibi.debug`；基线版本 0.6.2 (19)。模拟器相机最大输出为 1856×1392（4:3）/1280×720（16:9），因此分辨率提升只能在代码与单测层面对应真机；本目录的 JPEG 样例用于验证编码质量与处理路径。

## 问题定位

1. 默认“推荐”分辨率目标原为 1920×1440（4:3）/1920×1080（16:9），在真机上通常落在约 200–300 万像素，而系统相机默认保存约 1200 万像素。
2. 默认走快速抓拍：ZSL（设备支持时）/30fps/`CAPTURE_MODE_MINIMIZE_LATENCY`，JPEG 质量约 95，优先速度而非画质。
3. CameraX 基础管线是单帧 Bayer→JPEG；系统相机的 HDR、夜景、多帧降噪、锐化/超分等计算摄影无法直接获得。
4. 应用没有点按对焦/曝光补偿；连续 AF 在弱光或近距离可能对不到主体。
5. 保存链路是原始 JPEG 字节拷贝（内部存储/MediaStore/SAF），不会二次压缩，排除保存降质。

## 本次改动

- **CameraX 1.3.4 → 1.5.3**：1.5.3 满足项目 compileSdk 36 / AGP 8.9.1 / Kotlin 2.0.21 要求；修复了 1.3.4 在 API 36 上非 ZSL 拍摄打开会话失败（`StreamUseCaseUtil` IndexOutOfBounds）的问题，使“画质优先”可以使用稳定、可验证的拍摄管线。
- **分辨率目标提升**（`helpers/PhotoCaptureTargets.kt`，含单测）：
  - 速度优先：1280×960 / 1280×720；
  - 推荐：4000×3000（4:3）/3840×2160（16:9），约 12MP / 8MP；
  - 画质优先：目标 10000×10000，配合 `CLOSEST_HIGHER_THEN_LOWER` 选择设备支持的最大尺寸。
- **画质优先处理管线**：JPEG 100、软件 JPEG 编码器（硬件 JPEG 路径会忽略 `setJpegQuality`）、不强制 ZSL/30fps、设备支持时启用 `NOISE_REDUCTION_MODE_HIGH_QUALITY` 与 `EDGE_MODE_HIGH_QUALITY`、跳过附加的 ImageAnalysis 流（预览仍驱动 AE/AF）。
- 设置页“照片分辨率”说明更新（中/英）；`testOptions.unitTests.returnDefaultValues = true` 以适配 CameraX 1.5.3 的 JVM 测试初始化。
- 默认“推荐”仍保留快速/ZSL 路径，避免为了画质牺牲日常抓拍速度；需要画质时选择“画质优先”。
- 编码质量分配：画质优先 JPEG 100；推荐 + ZSL 路径沿用 95；推荐 + 非 ZSL 设备显式 98（高于 CameraX 默认 95，兼顾质量与体积）。
- CameraX 1.5.3 硬件 JPEG 路径在 API 36 模拟器上即使请求 JPEG 100 / MAXIMIZE_QUALITY 仍输出质量 95 的量化表；因此画质模式改用软件 JPEG 编码器以获得确定的 JPEG 100，代码注释已按该实测结果修正。

## JPEG 样例与量化结果

样例均为模拟器同场景拍摄（1856×1392）。JPEG 亮度量化表可客观反映编码质量：JPEG 100 的首行全为 1，JPEG 95 为 `[8,6,5,8,12,20]`。

| 样例 | 配置 | 大小 | 亮度量化表前 6 项 | 结论 |
| --- | --- | ---: | --- | --- |
| `sample-old-0.6.2-default.jpg` | 0.6.2 默认（CameraX 1.3.4，ZSL，JPEG95） | 61,311 B | `8, 6, 5, 8, 12, 20` | 旧默认 |
| `sample-recommended.jpg` | 新默认“推荐”（CameraX 1.5.3，ZSL，JPEG95） | 59,862 B | `8, 6, 5, 8, 12, 20` | 快速路径不变，分辨率目标提升只在真机体现 |
| `sample-quality.jpg` | “画质优先”（JPEG100 + 软件编码 + 高质量降噪/锐化） | 82,296 B | `1, 1, 1, 1, 1, 1` | 编码质量显著提高 |
| `sample-quality-hardware-jpeg.jpg` | “画质优先”但未启用软件 JPEG（对照） | 59,862 B | `8, 6, 5, 8, 12, 20` | 硬件 JPEG 忽略 `setJpegQuality(100)`，因此必须用软件编码器 |

拍摄时延（模拟器，仅作路径参考，不代表真机）：推荐/ZSL 路径约 242–470 ms；画质优先约 248–1075 ms（首次更慢），符合“画质优先更慢”的预期。

## 自动化结果

| 检查 | 结果 |
| --- | --- |
| JVM 单元测试 | 24 / 24（原 21 + `PhotoCaptureTargetsTest` 3） |
| Android instrumentation | 7 / 7（含双指缩放、变焦合并、悬浮拍照/变焦） |
| lint | 0 error / 0 fatal（188 warning，既有工程告警） |
| `:app:assembleRelease` | 通过（CameraX 1.5.3 + R8；universal APK 125,356,623 B，签名验证通过） |
| 视频录制冒烟 | 首页录像 → 保存 mp4；本机 126 KB，独立验收 256,945 B，编码器正常释放 |
| 独立验收（quality-verifier） | **PASS**：JVM 24/24、connected 7/7、lint 0 error、release 成功；独立复拍 RECOMMENDED 61,225 B / lumaQ0 95、QUALITY 83,408 B / lumaQ0 100，latency 441/478 ms；无 IndexOutOfBounds/失败/FATAL/ANR |

独立验证摘要（含量化数据、视频/悬浮回归、release 校验）：[verification-summary.md](verification-summary.md)。

## 复现

```bash
./gradlew :app:assembleDebug
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-x86_64-debug.apk
adb -s emulator-5580 shell pm grant com.zgybeep.alibi.debug android.permission.CAMERA
adb -s emulator-5580 shell appops set com.zgybeep.alibi.debug SYSTEM_ALERT_WINDOW allow
# 写入 settings.json：resolutionPreference = RECOMMENDED / QUALITY
# 通过 shortcut TAKE_PHOTO 启动悬浮拍照，点按悬浮按钮拍照，run-as 拉取 files/photos/*.jpg
# 用 Pillow 检查 JPEG 量化表与文件大小
```

## 边界

- 模拟器相机最高 1856×1392，真机上的 12MP/最大分辨率效果需在物理设备验证；本文的分辨率收益以目标代码与单测为证据，不用模拟器截图冒充真机画质。
- 对比系统相机仍有计算摄影差距：HDR、夜景多帧、降噪/锐化算法和 AI 处理无法通过 CameraX 基础单帧管线完全复现。
- 未实现点按对焦/曝光补偿；弱光、近距离对焦仍可能不如系统相机。
- 软件 JPEG 编码使用 CameraX 的受限 API（已集中 `@SuppressLint("RestrictedApi")` 并注明原因）；仅“画质优先”启用。
- 证据中的样例是模拟器虚拟场景，不代表真机传感器色彩/噪声表现。
