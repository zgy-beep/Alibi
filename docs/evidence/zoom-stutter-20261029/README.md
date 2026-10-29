# 双指缩放卡顿诊断（2026-10-29）

设备：华为 Mate 70 Pro（PLR-AL50），当前源码 0.6.8 (25)。用户反馈：所有带取景画面的缩放均卡顿。

## 已确认的主要瓶颈

`SmoothZoom` 约每 16 ms 计算一次倍率，但 `ZoomRequestQueue` 仅允许一个 CameraX 请求在途。以下三处都在 `setZoomRatio()` 返回的 Future 完成后，才调用 `complete()` 放行下一个倍率：

- `services/PhotoCaptureService.kt`：`applyZoomRequest()`。
- `ui/components/RecorderScreen/atoms/CameraPreview.kt`：`applyZoomRequest()`。
- `services/VideoRecorderService.kt`：`CameraControl.applyZoomRequest()`。

CameraX API 文档说明，该 Future 等到 repeating capture result 包含请求的倍率才完成；新倍率可以取消前一个尚未完成的请求。当前实现把这个硬件反馈等待作为下一次提交的门槛。

官方依据：<https://developer.android.com/reference/androidx/camera/core/CameraControl#setZoomRatio(float)>

因此计算平滑并不等于实际提交连续：等待期间只有最后一个倍率保留，其他中间倍率全部被覆盖，反馈越慢，下一次跳变越大。

## 真机数据

使用当前源码构建 Debug APK 和测试 APK，安装至机主 User 0，运行既有 `PhotoZoomInstrumentedTest#rapidZoomInputsAreCoalescedIntoBoundedCameraRequests`。该测试使用 BLACK_SCREEN 相机会话，并按约 16 ms 间隔注入 60 次相对缩放。

| 采样 | 输入次数 | CameraX 提交数 | 被合并的 ticker 请求数 | 各请求完成耗时 |
| --- | ---: | ---: | ---: | --- |
| 第一组完整日志 | 60 | 4 | 73 | 522 / 537 / 735 / 327 ms |
| 重复采样 | 60 | 4 | 74 | 497 / 445 / 712 / 472 ms |

第一组实际发送的倍率为 `1.007 → 2.359 → 5.728 → 5.892`，而 UI 内部已经计算过许多中间倍率。最终倍率正确，但这条管线的提交明显呈大步跳变。

原始数据见 `baseline-stream-logcat.txt`、`photo-repeat-logcat.txt`。耗时指调用到主线程完成监听器运行的时间，包含相机结果反馈及回调调度；本次没有进一步拆分传感器和主线程各自耗时，也没有测量全部预览帧的呈现时间。

## 测试为何未拦截

- 拍照测试通过：只要求最终倍率正确、请求数比输入数少、存在合并及提交时的输入陈旧度小于 1000 ms。它没有限制相邻有效变焦提交间隔。
- `VideoZoomInstrumentedTest#pinchOnVideoPreviewReportsZoomChanges` 也通过：UI 报告倍率 `1.0 → 2.9166665`。该断言来自手势目标回调，不能证明相机帧连续缩放。
- 录像预览和录制中变焦均查到相同的串行等待逻辑；本次没有分别测出它们的硬件请求完成耗时，不能把拍照的具体数值直接套用到录像。

## 修复方向与范围

需要调整“等待硬件确认后才发下一次”的节流策略：按合理帧节奏合并最新倍率，处理 CameraX 新请求替代旧请求时的正常取消，并验证最终倍率收敛。项目约定中的“最多一个 setZoomRatio 在途”需要结合本次证据一并重新评估；仅调整缓动系数不能消除这个等待瓶颈。

修复验收应覆盖实际 camera capture result 的倍率变化间隔、真实取景画面以及录像成片，不能只断言 UI 倍率变化。绝对倍率输入、相机切换/停止时的回调失效处理和最终倍率仍需保留。

本次为诊断：未修改生产代码，未提升版本或发布 APK。诊断构建成功；以上两项定向真机测试通过。未执行完整功能回归、lint 或 Release 构建。

调试期间为防日志环形缓冲被覆盖，临时扩大 logcat 缓冲；取证后已恢复为原来的 256 KiB，并停止诊断服务与日志采集。机主空间的临时 Debug 应用和测试包已移除，正式版保持安装。
