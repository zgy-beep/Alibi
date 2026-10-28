# 相机扩展（夜景/HDR）证据

对应报告：[相机扩展支持验收](../../reviews/camera-extensions-2026-10-28.md)。

## 当前状态

代码完成，静态/构建验证通过；设备级验证因宿主重启暂停。

| 项目 | 结果 |
| --- | --- |
| Kotlin 编译 | `:app:compileDebugKotlin` 通过 |
| JVM 单元测试 | 27 / 27（新增 `PhotoCaptureExtensionsTest` 3 项） |
| Lint | 0 error / 0 fatal（188 既有 warning） |
| Release 构建 | `:app:assembleRelease` 通过（R8 + 签名） |
| 设备实测 | 未执行（见下） |

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

由于宿主机在启动模拟器时意外重启（Kernel-Power 41，无蓝屏 dump），暂未重新启动模拟器。待执行：

1. 默认「关闭」：拍照正常，无扩展日志；
2. 选择「自动」：确认回报日志中的 `nightAvailable/hdrAvailable/selected`；
3. `selected=OFF` 时照片正常、无崩溃、无绑定错误；
4. 若支持扩展：对比开启前后的噪点、文件大小和拍摄时延。

## 华为 Mate 70 Pro 说明

CameraX Extensions 是 OEM 扩展机制，华为预计不提供 AndroidX CameraX 扩展实现（其 XMAGE 多帧算法不开放给第三方 APK）。因此该机型上“自动”很可能回退标准管线；功能不会崩溃，但不会带来夜景降噪收益。HarmonyOS NEXT 版本无法安装 Android APK；HarmonyOS 4.x 兼容安卓，可安装运行。
