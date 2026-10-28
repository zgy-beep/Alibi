# 相机扩展（夜景/HDR）证据

对应报告：[相机扩展支持验收](../../reviews/camera-extensions-2026-10-28.md)。

## 当前状态

代码完成，静态/构建验证通过；华为 Mate 70 Pro 真机实测不支持扩展，回退路径已验证。

| 项目 | 结果 |
| --- | --- |
| Kotlin 编译 | `:app:compileDebugKotlin` 通过 |
| JVM 单元测试 | 27 / 27（新增 `PhotoCaptureExtensionsTest` 3 项） |
| Lint | 0 error / 0 fatal（188 既有 warning） |
| Release 构建 | `:app:assembleRelease` 通过（R8 + 签名） |
| 华为 Mate 70 Pro 实测 | 扩展支持：**无**；自动模式回退拍照正常；照片 3072×4096 JPEG |

### 华为 Mate 70 Pro 实测（2026-10-28）

- 设备：`HUAWEI PLR-AL50`，HarmonyOS `4.3.0.208` / Android 12（API 31），arm64。
- 安装 `app-arm64-v8a-debug.apk`（`com.zgybeep.alibi.debug`），授权相机/悬浮窗。
- 相机设置页显示：**“本机不提供夜景或 HDR 扩展。”**（见 `huawei-extension-support.png` / `.xml`）。
- 选择“自动”后通过悬浮快门拍照：成功保存 `3072×4096` JPEG（约 2.5–4.2 MB），无崩溃/ANR；扩展不可用时正确回退标准管线。
- 华为的日志输出被系统策略裁剪，未取得 `Camera extensions requested=…` 原始 logcat；以设置页实时检测结果和实际拍照结果作为回退证据。

## 关键日志格式

启用扩展后，拍照会话启动时会输出：

```text
Camera extensions requested=AUTO nightAvailable=<bool> hdrAvailable=<bool> selected=<AUTO|NIGHT|HDR|OFF>
Camera extensions unavailable or timed out; using standard camera
```

- `selected=OFF`：设备不支持（或初始化失败），已回退标准管线；
- 扩展绑定失败：`Camera extension binding failed; using standard camera`；
- 扩展生效时关闭闪光灯，并跳过不支持 ImageAnalysis 的附加流。

## 设备级验证待办

1. ✅ 华为 Mate 70 Pro：扩展不支持，自动模式回退拍照正常（见上）；
2. ⏳ 其他支持 CameraX 扩展的机型：验证夜景/HDR 实际画质、速度与分辨率限制；
3. ⏳ 模拟器：不提供厂商扩展，只能验证回退路径；宿主机上次启动模拟器时意外重启（Kernel-Power 41），暂未重试。

## 华为 Mate 70 Pro 说明

CameraX Extensions 是 OEM 扩展机制，华为预计不提供 AndroidX CameraX 扩展实现（其 XMAGE 多帧算法不开放给第三方 APK）。因此该机型上“自动”很可能回退标准管线；功能不会崩溃，但不会带来夜景降噪收益。HarmonyOS NEXT 版本无法安装 Android APK；HarmonyOS 4.x 兼容安卓，可安装运行。
