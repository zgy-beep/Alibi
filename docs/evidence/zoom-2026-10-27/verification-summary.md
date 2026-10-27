# 独立验收摘要：双指缩放延迟优化 + 悬浮快门变焦

- 任务：`task-5`（独立验收，负责人 `zoom-verifier`）
- 日期：2026-10-27 17:0x（Asia/Shanghai，模拟器时钟为 UTC）
- 环境：Windows / bash；模拟器 `emulator-5580` · Pixel9_API36 · Android 16 / API 36 · 1080×2424 · density 420 (2.625×) · 中文系统
- 应用：`com.zgybeep.alibi.debug` 0.6.1；CameraX 1.3.4；设备相机 `zoomRatioRange=[1.0,10.0]`
- 被测工作区：`git status` 未提交改动（10 个 M + 3 个新源码/测试文件 + 2 个新文档目录），与任务描述一致
- 被测 APK：手动验证前设备上 `base.apk` 的 md5 = `131936b4ad1663685b41f6797d4ab95a`，与工作区
  `app/build/outputs/apk/debug/app-x86_64-debug.apk` 完全一致（验证收尾重装包同哈希）

## 结论：**PASS**

自动化测试全绿（单元 21/21、instrumentation 7/7、lint 0 error / 0 fatal），
悬浮窗变焦面板、停止/释放、rememberZoom 开/关、位置持久化等手动关键路径全部通过；
代码审查未发现阻塞缺陷，有 4 条低风险观察（见 §5，均不影响验收）。

## 1. 自动化测试（严格串行：unit → connected → lint）

| 命令 | 结果 | 真实数字来源 |
| --- | --- | --- |
| `./gradlew :app:testDebugUnitTest`（删输出 + `--no-build-cache`，1 executed） | BUILD SUCCESSFUL | `app/build/test-results/testDebugUnitTest/*.xml`：**21 tests / 0 failures / 0 errors / 0 skipped**（16 原有 + 5 `ZoomRequestQueueTest`） |
| `./gradlew :app:connectedDebugAndroidTest` | BUILD SUCCESSFUL in 35s | `app/build/outputs/androidTest-results/connected/debug/*.xml`：**7 tests / 0 failures / 0 errors / 0 skipped**（`ExampleInstrumentedTest` 4 + `PhotoZoomInstrumentedTest` 3） |
| `./gradlew :app:lintDebug`（`--stop` 清 lint-cache 后强制重跑分析，2m31s，7 tasks executed） | BUILD SUCCESSFUL | `app/build/reports/lint-results-debug.xml`：**Error=0，Fatal=0**；191 issues = 188 Warning + 3 Information |

instrumentation 逐项（全部 PASS）：

```text
PASS ExampleInstrumentedTest.photoShortcutSwitchesBlackSessionToFloating      0.577s
PASS ExampleInstrumentedTest.audioStartIsIdempotentAndReportsReadyAfterRecorderStarts 0.783s
PASS ExampleInstrumentedTest.mainActivityLaunchesWithStaticShortcuts          2.728s
PASS ExampleInstrumentedTest.useAppContext                                    0.247s
PASS PhotoZoomInstrumentedTest.pinchGestureOnBlackScreenChangesZoomRatio      4.463s
PASS PhotoZoomInstrumentedTest.floatingOverlayAcceptsZoomAdjustments          2.320s
PASS PhotoZoomInstrumentedTest.rapidZoomInputsAreCoalescedIntoBoundedCameraRequests 2.388s
```

日志：`verify-unit-test.log`、`verify-unit-test-rerun.log`、`verify-unit-test-results.txt`、
`verify-connected-androidTest.log`、`verify-connected-test-results.txt`、`verify-lint.log`、`verify-lint-results.txt`。

## 2. 真实 telemetry（`adb logcat` 从本次运行提取，`PhotoZoomTest` tag）

```text
09:08:16.364 PhotoZoomTest: pinch before=1.25 after=3.9772725 inputs=24 applies=2 stalenessMs=202
09:08:19.626 PhotoZoomTest: floating overlay rememberZoom stored=1.8
09:08:22.430 PhotoZoomTest: coalescing inputs=60 applies=6 coalesced=59 finalRatio=5.8915944 stalenessMs=33
```

CameraX 逐次完成日志（`PhotoCaptureService: zoom applied target=… latencyMs=…`）在
`verify-connected-logcat.txt`；本次模拟器单次 `setZoomRatio` future 完成耗时约 103–451ms，
60 次 60Hz 输入只产生 6 次请求（59 次被最新值合并），最终倍率与手势累计目标一致。
`inputs=24 → applies=2` 与文档 “[24 次手势采样只产生 2 次请求]” 一致；本次 `before=1.25`
是因为手动 rememberZoom 验证留下 `lastZoomRatio=1.25` 且测试安装保留了应用数据，测试断言仍通过。

> 文档 `docs/reviews/zoom-overlay-2026-10-27.md` / evidence README 记录作者运行时为
> “60→7 次、滞后 183ms”；本次独立复跑为 “60→6 次、滞后 33ms”。这类时序/请求数随设备
> 状态波动，断言只要求 applies < inputs、coalesced > 0、staleness < 1000ms，均满足；
> 量级与设计一致，不构成文档不一致缺陷。

## 3. 悬浮窗手动验证（截图/日志均为本次独立操作，`verify-*` 前缀）

1) 启动 shortcut 悬浮会话（`am start -a …shortcut.TAKE_PHOTO -n …/ShortcutActivity`，等待 6s）：
   `APPLICATION_OVERLAY mAttrs={(618,739)(147x147)}` = 56dp，READY 绿色状态；相机隐私指示器出现。
2) 长按 900ms 展开：窗口 `(618,739)(462x581)`，462px=176dp 与 `PANEL_WIDTH_DP` 一致；
   面板含 “− / 1.0× / ＋” 48dp 行、对数 SeekBar、打开 Alibi/停止/关闭。
3) 变焦实测（每次都有 `zoom applied` 日志佐证）：
   - ＋ → 1.25（面板 1.3×）→ ＋＋ → 2.0×；
   - 点击滑杆右端 → 10.0×，**＋ 按钮禁用（alpha 0.45）**；
   - 滑杆拖动 0→50%（800ms）：CameraX 仅 4 次请求（1.0→1.20→1.99→3.18），面板 3.2×；
   - − → 2.55×；点击倍率文字 → 重置 **1.0×，− 按钮禁用**；
   - 点击“关闭” → 窗口恢复 `(618,739)(147x147)`（56dp）。
4) 原有点击/拖动/长按/位置持久化回归：点击快门成功出片
   `files/photos/alibi-photo_20261027_090125_672_09c6889d.jpg`（“Photo captured latencyMs=445”）；拖动到 (327,1127)，
   `shared_prefs/photo_overlay.xml` 写入 x=327/y=1127 且未误拍；新位置长按正常；
   “打开 Alibi” 打开 MainActivity；状态色/透明度外观正常。
5) 停止与释放：“停止”后 overlay 消失、`CameraDevice.onClosed()`、服务结束、无 FATAL/ANR；
   重新启动会话正常恢复。会话运行中 `force-stop` 后同样无 overlay/服务/相机残留。
6) rememberZoom：
   - **默认关闭**：全新数据下悬浮变焦到 1.25× 后 `files/datastore/settings.json` **仍不存在**
     （未写入 `lastZoomRatio`）→ 符合预期；
   - **开启**（应用内“记住缩放倍率”开关写入 `rememberZoom:true`）：悬浮 ＋ → 1.25× 后 400ms
     debounce 写入 `"lastZoomRatio":1.25`；停止→重启会话后面板恢复显示 **1.3×**（读回 1.25）→ E2E 通过。

证据：`verify-floating-*.png`、`verify-open-app.png`、`verify-force-stop.png`、
`verify-dumpsys-*.txt`、`verify-seekbar-drag-logcat.txt`、详细记录见 `verify-manual-verification.txt`。

## 4. 代码审查（重点回归风险）

| 检查点 | 结论 |
| --- | --- |
| latest-wins 队列正确性 | `submit/complete` 同步且语义正确：在途只保留最新值，完成时立即应用；单元测试 5 项覆盖；实测 59/60 输入被合并 |
| stop/release 与 zoom 竞争 | `releaseCamera()` 会 `zoomRequests.reset()` + 取消 400ms remember job；释放后旧 future 回调只可能调用 `applyNextZoomRequest()`，此时 pending=null 为 no-op；stop/force-stop 实测无崩溃、相机正常释放 |
| zoom 轻量状态 vs 全量 publish 覆盖 | `requestZoom` 只 `state = state.copy(zoomRatio=…)` 不 publish；全部 17 处 `publish(...)` 调用都以调用时刻最新 `state` 为基础，不会用旧倍率覆盖；`PhotoCaptureModel` 同时由 `onStateChanged`（全量）和 `onZoomChanged`（轻量）更新，二者一致 |
| 初始倍率路径 | 会话启动时 `lastZoomRatio` 直接 `cameraControl.setZoomRatio`（未经过队列）；只在 READY 瞬间发生，无用户可见影响（观察项 O1） |
| BlackPhotoScreen 等价性 | 手势 `detectTransformGestures → zoomBy` 未变；倍率提示改 `snapshotFlow + collectLatest` 等价；remember 改 `debounce(450)` 等价；指示器“恰好回到 1.0× 时可能停留”与旧实现同模式，非本次引入（O3） |
| 悬浮面板 UI 同步 | `updateZoom` 缓存值/未 attach 时渲染；拖动时禁止回写进度；倍率文本、滑杆、边界禁用与服务状态一致（手动验证逐项确认） |
| 原有交互回归 | 点击拍照、拖动、长按、位置持久化、状态色、透明度、accessibility 原有动作均保留；面板高度由固定 168dp 改为内容测量（462×581）并 clamp 到屏幕 70%，收起恢复 56dp（实测） |
| strings values/values-zh | 两侧各 **388** 个 `<string>` 键，集合完全相等；新增 7 个 zoom 相关键两侧齐全；lint 无新增 UnusedResources |
| 文档与截图 | README/plan/review/evidence 共 13 个相对引用全部存在；文档结论数字（21/7/0 error/188 warning）与本次实测一致 |
| 越界改动 | 无。`git status` 仅任务列出的 10 个 M + 3 个新源码/测试文件 + evidence/review 文档 |

无障碍变焦动作（`ACTION_ZOOM_IN/OUT/RESET`）仅做了代码审查（逻辑正确、只有变焦可用时才暴露），
未用 TalkBack 实机操作（见 §5 未覆盖项）。

## 5. 问题列表（均非阻塞，无 FAIL 项）

- **O1（低）**：会话初始倍率 `cameraControl.setZoomRatio(initialZoom)` 直接在相机初始化时调用，
  未经过 `ZoomRequestQueue`；仅在 READY 瞬间存在“队列外请求”，实测无影响。建议后续统一走队列（可选）。
- **O2（低）**：`ZoomRequestQueue.reset()` 与旧会话 in-flight future 回调存在极窄竞争：旧回调可能在
  “停止后立刻开新会话且新请求在途”时提前清掉 `inFlight`，短时削弱合并（不会写错倍率、未复现用户可见问题）。
  建议 future 回调带 session/generation 校验（可选加固）。
- **O3（观察）**：黑屏倍率提示在“倍率恰好回到 1.0×”时依赖取消语义，理论上可能保留提示；与改动前行为一致，
  非本次回归；自动化与手动均未触发。
- **O4（低）**：rememberZoom 的 400ms debounce 在“缩放后 400ms 内立即停止会话”的边缘场景会取消最后一次写入；
  属 debounce 预期行为，正常操作（等待 >400ms）已验证写入成功。
- lint 变更文件相关仅 3 条：`PhotoCaptureModel.kt:32` Information（建议 `mutableFloatStateOf`）、
  `PhotoOverlayController.kt:681/725` 既有风格 Warning（UseKtx），无 Error/Fatal。

## 6. 未覆盖/环境说明

- 物理真机矩阵（U05 手感、厂商后台限制）未执行，仍按 plan 待真机验证。
- 中文 locale 下手动验证；其它语言由 388 键集合一致 + 英文默认值兜底。
- `connectedDebugAndroidTest` 结束后 AGP 卸载 app 并清空数据（预期）。验证收尾已重装
  `app-x86_64-debug.apk`（md5 同被测包），重新授权 CAMERA / POST_NOTIFICATIONS / SYSTEM_ALERT_WINDOW，
  冷启动 MainActivity 正常（无 FATAL），随后 force-stop，模拟器处于干净可用状态。

## 7. 证据索引（`docs/evidence/zoom-2026-10-27/`）

- 自动化：`verify-unit-test.log`、`verify-unit-test-rerun.log`、`verify-unit-test-results.txt`、
  `verify-connected-androidTest.log`、`verify-connected-test-results.txt`、`verify-connected-logcat.txt`、
  `verify-lint.log`、`verify-lint-results.txt`
- 静态检查：`verify-static-checks.txt`（diff 范围、字符串键集合、文档引用、@Test 统计、APK 哈希）
- 手动：`verify-manual-verification.txt`、`verify-pre-state.png`、`verify-floating-collapsed.png`、
  `verify-floating-panel.png`、`verify-floating-zoomed-1.png`、`verify-floating-zoomed-2.png`、
  `verify-floating-seekbar-max.png`、`verify-floating-seekbar-drag.png`、`verify-floating-zoom-reset.png`、
  `verify-floating-collapsed-return.png`、`verify-floating-dragged.png`、`verify-floating-panel-dragged.png`、
  `verify-floating-capture.png`、`verify-floating-stopped.png`、`verify-floating-restarted.png`、
  `verify-force-stop.png`、`verify-open-app.png`、`verify-onboarding-retention.png`、`verify-app-home.png`、
  `verify-floating-remember-on-panel.png`、`verify-floating-remembered.png`、`verify-floating-zoom-restored.png`、
  `verify-dumpsys-collapsed.txt`、`verify-dumpsys-panel.txt`、`verify-dumpsys-dragged.txt`、
  `verify-dumpsys-stopped.txt`、`verify-seekbar-drag-logcat.txt`
