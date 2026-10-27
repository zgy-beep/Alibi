# 照片画质完善（CameraX 1.5.3 + 高画质管线）独立验收报告

- 验收人：quality-verifier（task-6，独立于实现者）
- 验收时间：2026-10-27 23:16 – 23:35（+08:00）
- 基线：已发布 0.6.2 commit `e603a51`；被验收内容为工作区未提交改动（代码部分未提交，文档由其他成员并行更新，见「范围与工作区状态」）
- 设备：模拟器 `emulator-5580`，Pixel9_API36（Android 16 / API 36，`sdk_gphone64_x86_64`，1080×2424 / 420dpi，系统语言中文）
- 被测包：`com.zgybeep.alibi.debug`，versionName 0.6.2（versionCode 19），由当前工作区源码新构建的 `app-x86_64-debug.apk`（2026-10-27 23:13，`compileDebugKotlin` 对所有源码均为 UP-TO-DATE，与工作区一致；23:19 安装）
- **结论：PASS**（自动化全绿、lint 0 error/0 fatal、release 构建成功；模拟器照片实测量化指标与预期完全一致；视频/悬浮拍照回归通过。另有 2 条非阻塞代码评审批注，见「问题与评审批注」。）

## 1. 自动化结果（真实执行，非缓存）

所有 Gradle 任务串行执行，避免并发。单元测试与 lint 使用 `--rerun-tasks` 强制真实重跑（分别 28/32 个 task 真正执行）；connected 测试每次都会真实下发到设备。

| 检查 | 命令 | 结果 | 证据 |
| --- | --- | --- | --- |
| JVM 单元测试 | `./gradlew :app:testDebugUnitTest --rerun-tasks --console=plain` | **24 / 24 通过**，0 failures、0 errors、0 skipped（`--rerun-tasks`，28 tasks executed，BUILD SUCCESSFUL 43s） | `verify-unittests-rerun.log`、`verify-unit-results/` |
| Instrumentation | `./gradlew :app:connectedDebugAndroidTest --console=plain` | **7 / 7 通过**，0 failures、0 errors、0 skipped，15.349s，设备 `Pixel9_API36(AVD) - 16` | `verify-connected.log`、`verify-connected-results/` |
| Lint | `./gradlew :app:lintDebug --rerun-tasks --console=plain` | **0 Error / 0 Fatal**；188 Warning + 3 Information（均为既有工程告警，改动文件 0 条）；`RestrictedApi` 0 条 | `verify-lint-rerun.log`、`verify-lint-results-debug.xml` |
| Release 构建 | `./gradlew :app:assembleRelease --console=plain` | **BUILD SUCCESSFUL**（1m9s；`compileReleaseKotlin`/`minifyReleaseWithR8`/`packageRelease` 真实执行，产物 2026-10-27 23:18） | `verify-release.log`、`verify-release.rc` |

单元测试明细（解析 `app/build/test-results/testDebugUnitTest/*.xml`）：
`AppSettingsPhotoCompatibilityTest` 6、`ExampleUnitTest` 3、`PhotoCaptureSessionStateTest` 4、**`PhotoCaptureTargetsTest` 3**、`PhotoNamingTest` 2、`PhotoRepositoryCompatibilityTest` 1、`ZoomRequestQueueTest` 5 = 24。

Instrumentation 明细（7 项）：`ExampleInstrumentedTest` 4 项（`photoShortcutSwitchesBlackSessionToFloating`、`audioStartIsIdempotentAndReportsReadyAfterRecorderStarts`、`mainActivityLaunchesWithStaticShortcuts`、`useAppContext`）+ **`PhotoZoomInstrumentedTest` 3 项**（`pinchGestureOnBlackScreenChangesZoomRatio`、`floatingOverlayAcceptsZoomAdjustments`、`rapidZoomInputsAreCoalescedIntoBoundedCameraRequests`）。

Release 产物校验：
- 9 个 APK（各 ABI + universal）均重新生成；`app-universal-release.apk` = 125,356,623 B，sha256 `a2e2ee521bcaffda58c5e9fa938d8d9f169ebc7d82292d3e4d02dfc06fa16d71`；R8 `mapping.txt` 53,686,427 B。
- `apksigner verify` 通过，签名者 `CN=Alibi Release, OU=Android, O=zgy-beep, L=Shanghai, ST=Shanghai, C=CN`，证书 SHA-256 `5f9d20f4…fcb334`；`lintVitalRelease` 通过。
- 从 release APK 内读取 `META-INF/androidx.camera_camera-{core,camera2,video}.version` = **1.5.3**（debug APK 同为 1.5.3），证明升级真实进入产物。

## 2. 独立照片实测（模拟器，与实现者无关的重新拍摄）

方法：`am force-stop` → `run-as` 写入 `files/datastore/settings.json`（`{"hasSeenOnboarding":true,"photoCaptureSettings":{"resolutionPreference":"…"}}`）→ 记录 `am start -a com.zgybeep.alibi.debug.shortcut.TAKE_PHOTO -n com.zgybeep.alibi.debug/app.myzel394.alibi.ShortcutActivity` → 等 `dumpsys window` 出现 `ty=APPLICATION_OVERLAY`（实际 attrs `(891,739) 147x147`，点 (964,812)）→ 等 `files/photos` 新文件稳定后 `exec-out run-as cat` 拉取 → Pillow 读取量化表。设备相机上限 1856×1392。

| 项目 | RECOMMENDED | QUALITY | 预期 | 判定 |
| --- | --- | --- | --- | --- |
| 会话日志 | `mode=2`（ZSL）、`jpegQuality=95`、`qualityPriority=false`、`hqNoiseReduction=false`、`hqEdge=false`；`Capture profile FAST: ZSL=true, fps=[30,30], OIS=false, CAF=true` | `mode=1`（MINIMIZE_LATENCY）、`jpegQuality=100`、`qualityPriority=true`、`hqNoiseReduction=true`、`hqEdge=true`；无 `Capture profile` 行（ZSL/强制帧率已关闭） | ZSL/30fps 仅推荐路径；画质路径非 ZSL | PASS |
| JPEG 尺寸 | 1856×1392 | 1856×1392 | 模拟器上限 1856×1392 | PASS |
| 文件大小 | 61,225 B | 83,408 B | ~60 KB / ~80 KB | PASS |
| 亮度量化表 `lumaQ0[:6]` | `[8, 6, 5, 8, 12, 20]`（JPEG95） | `[1, 1, 1, 1, 1, 1]`（JPEG100） | `[8,6,5,8,12,20]` / `[1,1,1,1,1,1]` | PASS |
| 拍摄时延 `latencyMs` | 441 ms | 478 ms | 记录 | PASS（画质路径首次略慢） |
| 保存耗时 `saveMs` | 33 ms | 29 ms | — | PASS |
| 流情况 | `streamUseCaseMap = {}`（ZSL 路径） | `streamUseCaseMap` 2 个 surface（Preview+ImageCapture，**无 ImageAnalysis**） | 画质路径跳过分析流 | PASS |
| 样例 sha256 前缀 | `c2968bb6c40e61b6…` | `613cbefd72333aef…` | — | 证据留档 |

样例：`verify-recommended.jpg`、`verify-quality.jpg`；元数据 `verify-meta-recommended.json` / `verify-meta-quality.json`；场景日志 `verify-logcat-recommended.txt` / `verify-logcat-quality.txt`；悬浮窗截图 `verify-overlay-recommended.png` / `verify-overlay-quality.png`。

对照：证据目录内既有样例（非本次复拍，仅作基线参照）`sample-old-0.6.2-default.jpg` 61,311 B / Q95、`sample-quality.jpg` 82,296 B / Q100，与本次复拍结论一致。

分辨率提升说明：模拟器虚拟相机最大 1856×1392（4:3）/1280×720（16:9），无法体现 12MP/设备最大尺寸；该部分由代码 + 单测证明——`PhotoCaptureTargetsTest` 3/3 通过，分别断言 RECOMMENDED 4000×3000（4:3）/3840×2160（16:9）、SPEED 1280×960、QUALITY 目标 10000×10000 且 `usesQualityPriority` 仅对 QUALITY 为 true。真机 12MP/最大尺寸收益仍需物理设备复验（见「限制」）。

## 3. 错误与崩溃检查

对整轮验证的完整 logcat（10,201 行，含照片、视频、设置页操作）与两个场景日志扫描：

| 检查项 | 结果 |
| --- | --- |
| `IndexOutOfBoundsException`（1.3.4 在 API36 非 ZSL 会话打开失败的已知问题） | **0** |
| `Photo capture failed` / `PhotoCaptureService` 的 W/E 日志 | **0** |
| `FATAL EXCEPTION` / `ANR in` | **0** |
| `StreamUseCaseUtil` 异常 | **0**；仅 2 条正常 DEBUG 级 `populateSurfaceToStreamUseCaseMapping()`（见 `verify-logcat-summary.txt`） |

## 4. 回归验证

| 回归项 | 方法 | 结果 |
| --- | --- | --- |
| 视频录制 | 首页「录像」卡片 → 准备录制 sheet「开始录制」→ 录制约 13s → 「保存」→ SAF 对话框「保存」 | **通过**：`/sdcard/Download/2026-10-27T15-23-58_13.mp4` = **256,945 B**，`ftyp isom/avc1` 头合法；应用返回首页、无崩溃/ANR；拉取样例 `verify-video-sample.mp4`。录屏/保存过程截图 `verify-video-recording.png`、`verify-video-saved.png`。 |
| 悬浮拍照（click 出图） | TAKE_PHOTO shortcut 悬浮会话，点按 (964,812) | **通过**：RECOMMENDED/QUALITY 两次均一次点按即出图（tapAttempts=1），悬浮窗尺寸/位置 `(891,739) 147x147` 不变，拍后悬浮窗仍在，force-stop 后无残留 overlay。 |
| 相机设置文案 | 设置 →（高级设置）→ 相机设置 | **通过**：截图 `verify-camera-settings.png` 可见中文文案「相机会选择设备支持的最接近分辨率；画质优先还会使用最大支持尺寸、更高画质处理与更高质量 JPEG 编码。」；uiautomator XML `verify-ui-camera-settings.xml`。 |
| CameraX 升级回归 | instrumentation 7/7（含双指缩放、悬浮变焦、请求合并）+ 上述照片/视频实测 | **通过**，未发现升级导致的既有功能破坏。 |

## 5. 代码审查

改动边界（`git diff --name-only`）：`app/build.gradle`、`PhotoCaptureService.kt`、`res/values/strings.xml`、`res/values-zh/strings.xml`、新增 `helpers/PhotoCaptureTargets.kt` 与 `test/.../PhotoCaptureTargetsTest.kt`——与变更摘要一致，无其它源码文件改动。

1. **CameraX 1.5.3 兼容性（通过）**
   - camera-core 1.5.3 AAR 元数据：`minCompileSdk=35`、`minAndroidGradlePluginVersion=8.6.0`；项目 `compileSdk 36`、AGP `8.9.1`、Kotlin `2.0.21`，满足要求。
   - camera-core 1.5.3 Gradle module 要求 `kotlin-stdlib 2.0.21`；依赖解析日志显示运行期 kotlin-stdlib 收敛到 **2.0.21**，与项目一致（`verify-dependencies.log`）。
   - `camera-core/camera2/lifecycle/video/view/extensions` 依赖全部解析为 1.5.3（依赖树 44 处 1.5.3）；debug/release APK 内 1.5.3 版本元数据可查。
   - `@SuppressLint("RestrictedApi")` 标注在 `startSession()` 上，lint 报告 0 条 `RestrictedApi`；`setSoftwareJpegEncoderRequested` 在 1.5.3 中确为 `@RestrictTo(LIBRARY_GROUP)`，抑制范围集中且合理。
2. **画质管线逻辑（通过）**
   - 仅 `usesQualityPriority(QUALITY)` 时：`useFastOptions=false` → ZSL/强制 30fps 关闭；跳过 ImageAnalysis（log 中画质会话仅 2 个 surface，实测 0 条相关异常）；JPEG=100 + 软件 JPEG 编码器；`NOISE_REDUCTION_MODE_HIGH_QUALITY`/`EDGE_MODE_HIGH_QUALITY` 仅在 `CameraCharacteristics` 支持时下发（模拟器两者 supported=true，实测 `hq…=true`，拍摄成功）。
   - RECOMMENDED 保留快速/ZSL 路径（实测 `ZSL=true, fps=[30,30]`，JPEG95 → Q95 表），SPEED 目标不变。
   - `imageAnalysis` 改为可空并在 `releaseCamera()`/回退绑定分支做空安全处理（`imageAnalysis = null`、`listOfNotNull(...)`），无 NPE 路径；回退构建器同样带上画质参数，行为一致。
   - 软件 JPEG 在 CameraX 内部有优雅回退：`enforceSoftwareJpegConstraints()` 在 Extensions 启用或 buffer format 非 JPEG 时只记 warning 并禁用软件编码（不崩溃）；本项目 PhotoCaptureService 未启用 Extensions，本次模拟器走软件编码成功（Q100 表为证）。
   - 字符串：`photo_settings_resolution_description` 在 values / values-zh 同步更新，键一致、未新增/删除键，资源编译与 lint 均无问题。
   - `testOptions.unitTests.returnDefaultValues=true`：**确认必要且理由成立**。CameraX 1.5.3 `androidx.camera.video.Quality` 的类初始化直接构造 `android.util.Size`（javap 反编译确认），普通 JVM + android-36 stub 实测抛 `RuntimeException: Stub!`（`android.util.Size.<init>` ← `Quality.<clinit>`）；而 JVM 单测经 `AppSettings.getDefaultInstance()` → `VideoRecorderSettings.getDefaultInstance()` 的 companion `QUALITY_NAME_QUALITY_MAP` 会触达 `Quality` 类。该开关只影响 JVM 单测，instrumentation/lint/release 不受影响（详见 `verify-testoptions-reason.txt`）。
3. **范围与工作区状态**
   - 代码 diff 仅上述预期文件；`.tmp/` 为未跟踪的开发残留（大量中间日志/样例/反编译 class，非本次提交内容），未纳入 git。
   - 验收期间工作区另有 **文档类** 改动由其他成员并行完成：`README.md`（mtime 23:16:55）与 `docs/plans/black-screen-floating-camera.md`（23:17:00），内容为本功能说明与 CameraX 版本事实更新，属文档，不在本次 app 代码验收范围；本验收者未改动任何现有文档/源码/strings。

## 6. 问题与评审批注（均非阻塞）

1. **[文档级/评审批注] `captureModeFor()` 的注释与 CameraX 1.5.3 实现不完全相符。** 注释称「MAXIMIZE_QUALITY ignores ImageCapture.setJpegQuality() … resulting JPEG quantisation tables are quality 95」。对 1.5.3 反编译显示：`getJpegQualityInternal()` 在显式设置过 JPEG 质量时直接采用用户值；未显式设置时按 capture mode 取默认（MAXIMIZE_QUALITY=100，MINIMIZE_LATENCY/ZSL=95）。模拟器上硬件 JPEG 得到 Q95 更可能是虚拟相机 HAL 忽略 `JPEG_QUALITY` 所致，而非 CameraX 忽略用户设置。该注释不影响已验证行为（软件编码器确实输出 Q100），建议后续把注释改为「模拟器/部分硬件 JPEG 路径不保证质量参数，故画质优先使用软件编码器」。
2. **[轻微行为变化] 非 ZSL 设备上的 RECOMMENDED 现在显式 `setJpegQuality(98)`，而改动前未设置（CameraX 默认为 100）。** 该 98/95/100 分支未在变更摘要中逐条说明；模拟器 RECOMMENDED 走 ZSL（95），未受影响。建议确认 98 是有意为之并可接受（画质略降、体积略减），否则可在后续调整。
3. 模拟器限制：虚拟相机上限 1856×1392（4:3），12MP/10000×10000 目标与“设备最大尺寸”逻辑无法在模拟器上以分辨率体现，仅由代码 + 单测覆盖；真机分辨率、软件 JPEG 性能与真实噪声/色彩仍需物理设备验收。QUALITY 时延 478ms 为模拟器数据，不代表真机。
4. 悬浮窗截图中 RECOMMENDED/QUALITY 两张 `verify-overlay-*.png` 内容相同（桌面背景 + 同一位置悬浮快门，模拟器场景静态），属正常。

## 7. 设备状态恢复

- 验证结束已 `am force-stop com.zgybeep.alibi.debug`（进程不存在，Alibi 悬浮 overlay 计数 0；仅剩系统 SystemUI 的 ShellDropTarget overlay，属系统）。
- `settings.json` 已重置为 `{"hasSeenOnboarding":true}`；debug APK 保留安装（`/data/app/…/com.zgybeep.alibi.debug…/base.apk`，0.6.2/19，CAMERA/POST_NOTIFICATIONS 已授予、SYSTEM_ALERT_WINDOW=allow）。
- 未安装/卸载/改动 release 包 `com.zgybeep.alibi`（仍为 0.6.2/19，lastUpdateTime 09:29:54，保持原状）。
- 测试产生的 `/sdcard/Download/2026-10-27T15-23-58_13.mp4`（256,945 B）保留作为视频回归证据；uiautomator 临时 XML 已清理（`/sdcard` 上其余旧 XML 为历史任务残留，未动）。详见 `verify-device-state.txt`。

## 8. 证据文件清单（本目录 `docs/evidence/photo-quality-2026-10-27/`）

| 文件 | 内容 |
| --- | --- |
| `verify-recommended.jpg` / `verify-quality.jpg` | 独立复拍的 RECOMMENDED/QUALITY 原始 JPEG（1856×1392；61,225 B / 83,408 B） |
| `verify-meta-*.json`、`verify-logcat-recommended.txt`、`verify-logcat-quality.txt` | 每次拍摄的会话/时延/日志/悬浮窗 attrs |
| `verify-overlay-*.png`、`verify-after-capture-*.png` | 悬浮快门会话与拍后截图 |
| `verify-home.png`、`verify-video-recording.png`、`verify-video-saved.png`、`verify-video-sample.mp4` | 视频录制冒烟证据 |
| `verify-camera-settings.png`、`verify-ui-camera-settings.xml`、`verify-ui-home.xml` | 设置页分辨率文案与首页 UI 树 |
| `verify-unittests-rerun.log(.rc)`、`verify-unit-results/` | 单测真实重跑日志与 XML（24/24） |
| `verify-connected.log(.rc)`、`verify-connected-results/` | instrumentation 7/7 日志与 XML |
| `verify-lint-rerun.log(.rc)`、`verify-lint-results-debug.xml`、`verify-lint.log(.rc)` | lint 全量重跑与报告（0 error/0 fatal） |
| `verify-release.log(.rc)` | assembleRelease 成功日志 |
| `verify-dependencies.log`、`verify-testoptions-reason.txt`、`verify-testoptions-experiment.log` | CameraX/Kotlin 解析版本、testOptions 必要性验证 |
| `verify-logcat-summary.txt`、`verify-logcat-full.txt` | 关键日志与整轮完整 logcat（错误扫描 0） |
| `verify-device-state.txt` | 恢复后的设备/包/权限/设置状态 |
| `verify-capture.py` | 本次独立复拍脚本（可复现） |
| `README.md`、`sample-*.jpg` | 实现方原有证据材料（本验收未修改） |

## 9. 验收判定

| 验收项 | 结果 |
| --- | --- |
| 单测（真实 24 项） | PASS |
| connected（7/7，含 PhotoZoomInstrumentedTest 3） | PASS |
| lint Error/Fatal = 0 | PASS（0/0，188 warning 既有；改动文件 0 条） |
| assembleRelease 成功 | PASS |
| RECOMMENDED：Q95 表、~60KB、ZSL/30fps | PASS（61,225 B，`[8,6,5,8,12,20]`，ZSL=true fps=[30,30]） |
| QUALITY：Q100 表、~80KB、软件编码/高画质处理 | PASS（83,408 B，`[1,1,1,1,1,1]`，hqNR/hqEdge=true，无分析流） |
| 错误日志（StreamUseCaseUtil IndexOutOfBounds / Photo capture failed / FATAL / ANR） | PASS（0/0/0/0） |
| 视频录制 + 悬浮拍照回归 | PASS |
| 代码审查与改动边界 | PASS（2 条非阻塞批注） |

**最终结论：PASS。** 未发现阻塞性缺陷；分辨率提升的真机收益与软件 JPEG 真机性能需物理设备后续复验。
