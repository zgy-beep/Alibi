# 华为高分辨率画质模式修复证据

对应报告：[高分辨率设备画质模式分辨率修复](../../reviews/huawei-photo-quality-resolution-2026-10-28.md)。

设备：`HUAWEI PLR-AL50`，HarmonyOS `4.3.0.208` / Android 12（API 31），arm64；应用 `com.zgybeep.alibi.debug`（0.6.3，工作区源码构建）。

## 修复前（0.6.3 行为：软件 JPEG）

| 配置 | 输出 | 字节 | 量化表首行 |
| --- | --- | ---: | --- |
| 推荐 | 3072×4096（12.6MP） | 4,212,239 / 3,991,686 | JPEG 100 |
| 画质优先（软件 JPEG） | 3264×2448（8.0MP）；EXIF 旋转后 2448×3264 | 3,527,532 / 3,667,893 | JPEG 100 |

## 修复后（硬件 JPEG，按硬件上限选择）

| 配置 | 输出 | 字节 | 量化表首行 |
| --- | --- | ---: | --- |
| 画质优先（硬件 JPEG） | **6144×8192（50MP）** | **14,696,183** | JPEG 100 |
| 推荐 | 3072×4096（12.6MP） | 约 4 MB | JPEG 100 |

相机设置页同时显示扩展支持检测结果：“本机不提供夜景或 HDR 扩展。”，选择“自动”后拍照正常回退。

调用链：`queryMaxHardwareJpegPixels()` 读取 `SCALER_STREAM_CONFIGURATION_MAP.getOutputSizes(ImageFormat.JPEG)` 的最大面积；`shouldUseSoftwareJpegEncoder(qualityPriority, maxHardwareJpegPixels)` 仅在画质优先且上限 ≤ 8MP 时返回 true。华为上限 6144×8192 → 硬件 JPEG；模拟器上限 1856×1392 → 软件 JPEG。

## 自动化

| 检查 | 结果 |
| --- | --- |
| JVM 单测 | 30 / 30（新增 `PhotoCaptureJpegEncoderTest` 3 项） |
| Kotlin 编译 | 通过 |
| Lint | 0 error / 0 fatal（188 warning） |
| Release 构建 | 通过（R8 + 签名） |

## 复现

```powershell
# 华为 Mate 70 Pro，debug 包
adb shell "run-as com.zgybeep.alibi.debug sh -c 'cat > files/datastore/settings.json'" <<< `
  '{"hasSeenOnboarding":true,"photoCaptureSettings":{"extensionMode":"OFF","resolutionPreference":"QUALITY"}}'
adb shell am start -a com.zgybeep.alibi.debug.shortcut.TAKE_PHOTO `
  -n com.zgybeep.alibi.debug/app.myzel394.alibi.ShortcutActivity
# 等待悬浮按钮出现后点按，run-as 拉取 files/photos 最新 JPEG
```

用 Pillow 读取尺寸与 `im.quantization[0]` 即可核验。

## 边界

- 该修复尚未进入已发布的 0.6.3；已发布版本在华为上的画质优先仍为 8MP，需要下一个版本（0.6.4）才能生效。
- 高像素画质模式文件约 14.7MB，保存时间/存储占用增加。
- 华为夜景/HDR 扩展不可用，本修复不涉及多帧降噪。
