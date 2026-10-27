# 变焦延迟优化与悬浮快门变焦验收

日期：2026-10-27 · 版本：0.6.1 (18) · 环境：Android 16 / API 36 模拟器 `Pixel9_API36`，序列号固定为 `emulator-5580`；CameraX 1.3.4。

## 结论

黑屏双指缩放的相机请求已改为 latest-wins 合并：连续手势不再堆积 `CameraControl.setZoomRatio()` 任务，UI 不再为每次缩放重发全量会话状态。模拟器上 60 次 60 Hz 变焦输入被合并为 7 次 CameraX 请求，最新请求相对手势输入的滞后为 183 ms；双指捏合可在真实黑屏页面改变倍率。悬浮快门长按面板新增 −/倍率/＋、对数滑杆、点击重置和无障碍变焦动作，支持边界禁用、随内容自适应高度，并让“记住变焦”对悬浮模式生效。Debug 构建、21 项 JVM 测试、7 项 instrumentation 测试和 lint 通过。

## 问题分析

旧实现的缩放链路：

1. `BlackPhotoScreen` 的 `detectTransformGestures` 每个指针事件调用 `photoCapture.zoomBy()`；
2. `PhotoCaptureService.zoomBy()` 每次都 `cameraControl.setZoomRatio(target)` 并 `publish()` 全量 `PhotoCaptureSessionState`；
3. CameraX 1.3.4 中每次 `ZoomControl.setZoomRatio()` 都会向相机控制执行器提交一次任务并触发 `updateSessionConfigSynchronous()`，future 需等相机结果回来才完成；
4. 60–120 Hz 手势造成请求堆积、预览落后手指；同时每次缩放都触发整页状态发布、`LaunchedEffect(zoomRatio)` 重建和悬浮窗刷新。

## 改造内容

### 服务端请求合并（[PhotoCaptureService.kt](../../app/src/main/java/app/myzel394/alibi/services/PhotoCaptureService.kt)、[ZoomRequestQueue.kt](../../app/src/main/java/app/myzel394/alibi/services/ZoomRequestQueue.kt)）

- 新增 `ZoomRequestQueue`（latest-wins）：同一时刻最多一个 `setZoomRatio` 在途；在途期间只保留最新目标，完成时立即应用最新值。
- 支持 `zoomBy(scaleFactor)`（黑屏双指）与 `setZoomRatio(absolute)`（悬浮面板滑杆/按钮）。
- 缩放目标仍按设备 `minZoomRatio/maxZoomRatio` 钳制；目标变化小于 `0.005` 时忽略。
- 缩放只做“轻量状态更新 + 回调 + 悬浮窗倍率同步”，不再 `publish()` 全量状态，避免逐帧重组与悬浮窗刷新。
- 相机释放时清空合并队列与待持久化任务；每个会话重置 telemetry。

### UI 状态分离（[PhotoCaptureModel.kt](../../app/src/main/java/app/myzel394/alibi/ui/models/PhotoCaptureModel.kt)、[BlackPhotoScreen.kt](../../app/src/main/java/app/myzel394/alibi/ui/screens/BlackPhotoScreen.kt)）

- 新增独立的 `PhotoCaptureModel.zoomRatio` 状态，服务通过 `onZoomChanged` 只推送倍率；会话状态的其他字段不再因缩放而变化。
- 黑屏倍率提示改为 `snapshotFlow + collectLatest`，不再为每次缩放重启 `LaunchedEffect`；“记住变焦”改为 `debounce(450ms)` 后写入，行为与原来一致。
- 语言/提示仍只在手势期间短暂显示，不影响快门区域。

### 悬浮快门变焦面板（[PhotoOverlayController.kt](../../app/src/main/java/app/myzel394/alibi/services/PhotoOverlayController.kt)、[Tokens.kt](../../app/src/main/java/app/myzel394/alibi/ui/theme/Tokens.kt)）

- 长按面板顶部新增变焦区：− 48 dp 按钮、当前倍率（可点击重置到最小倍率）、＋ 48 dp 按钮；下方为对数映射 `SeekBar`（`ratio = min·(max/min)^linear`）。
- 倍率到边界时对应按钮禁用并降低 alpha；相机不支持变焦（`max-min < 0.05`）时隐藏变焦区，显示“当前相机不支持变焦”，面板高度按内容自适应。
- 面板宽度使用 `AlibiOverlayStyle.PANEL_WIDTH_DP`，高度改为测量内容（上限屏幕 70%），展开/收起恢复 56 dp；保留原有点击拍照、拖动定位、长按、收起、状态颜色、透明度与位置持久化。
- `updateZoom` 防御式更新视图：视图未创建时缓存值，`show()` 后渲染；用户拖动滑杆期间不回写进度，避免与服务回调互相打架。
- 无障碍：shutter 新增“变焦放大/缩小/重置”自定义动作（仅变焦可用时暴露），保留原有“打开应用/停止”动作。
- 悬浮模式的 `rememberZoom` 通过 `EXTRA_REMEMBER_ZOOM` 传入会话配置，400 ms debounce 写入 `lastZoomRatio`；黑屏模式仍由页面原有逻辑负责。

### 资源

- 新增 `photo_overlay_zoom_title/in/out/reset/value/description/unavailable` 字符串（values + values-zh）。
- `AlibiOverlayStyle` 新增 `PANEL_WIDTH_DP`、`ZOOM_STEP_FACTOR`、`ZOOM_VALUE_TEXT_SIZE_SP`、`ZOOM_SEEK_BAR_MAX`。

## 验证

| 检查 | 结果 |
| --- | --- |
| `:app:assembleDebug` | 通过 |
| JVM 单元测试 | 21 / 21 通过（16 原有 + 5 `ZoomRequestQueueTest`） |
| Android instrumentation | 7 / 7 通过（4 原有 + 3 `PhotoZoomInstrumentedTest`） |
| lint | 0 error / 0 fatal（188 warning，均为既有工程告警） |
| 双指捏合（自动化） | 真实黑屏页面注入捏合：1.0× → 3.18×，24 次手势采样只产生 2 次 CameraX 请求 |
| 高频变焦合并（自动化） | 60 次 60 Hz 输入 → 6–7 次请求（59 次被替换），最终 5.89×，最新请求滞后 33–183 ms（随相机管线时序波动） |
| 悬浮面板（模拟器截图） | 1.0× → 2.4× → 10.0×（上限，＋禁用）→ 点击倍率重置 1.0× → 收起回 56 dp |
| rememberZoom | 悬浮会话 1.8× 后 DataStore `lastZoomRatio=1.8` |
| 稳定性 | instrumentation 全流程无 FATAL EXCEPTION / ANR |

独立验证（fresh 上下文，未参与实现）复跑 JVM 21/21、instrumentation 7/7、lint 0 error，并手动验证悬浮面板 −/＋/滑杆/重置/停止/拖动/位置持久化、rememberZoom 开关与重启恢复、会话重启和 force-stop 清理；结论 PASS，未发现阻塞缺陷。详见[独立验证摘要](../evidence/zoom-2026-10-27/verification-summary.md)。

复现命令、截图与日志见[变焦优化与悬浮窗变焦证据](../evidence/zoom-2026-10-27/README.md)。新增测试：[ZoomRequestQueueTest](../../app/src/test/java/app/myzel394/alibi/ZoomRequestQueueTest.kt)、[PhotoZoomInstrumentedTest](../../app/src/androidTest/java/app/myzel394/alibi/PhotoZoomInstrumentedTest.kt)。

## 边界与未覆盖

- 模拟器相机每次缩放请求完成需要 213–283 ms（见 `overlay-zoom-logcat.txt`），这是相机管线的固有耗时；本次优化解决的是请求堆积与 UI 抖动，不能据此承诺真机毫秒级跟手。物理真机双指手感、90/120 Hz 表现仍按[黑屏与悬浮拍照计划](../plans/black-screen-floating-camera.md) U05 验收。
- 未进行帧级延迟测量（无高速摄像/Perfetto 采样）；本文的“滞后”指标是最新一次手势输入到 CameraX 请求开始应用的间隔，不是预览延迟。
- 悬浮面板截图为深色桌面背景；大字体、TalkBack 实际朗读、横屏和厂商后台限制未覆盖。
- 展开面板时窗口宽度变大，`clampToScreen` 会把靠右的按钮向左推并保存位置（沿用原有展开逻辑）；收起后不会回到展开前坐标。
- 已知非阻塞边界：若变焦后 400 ms 内立即停止会话，最后一次“记住变焦”写入可能被取消（O4）；会话初始倍率直接下发而不经过合并队列（O1）；重置与极旧 in-flight 请求的理论窗口（O2）未复现。独立验证建议后续用 generation token 收敛 O2。
- 本次不涉及发布、版本升级或 Git 推送，版本号保持 `0.6.1 (18)`。
