# 相机扩展（夜景/HDR）支持验收

日期：2026-10-28 · 状态：**代码完成，静态验证通过；设备级验收待执行**（宿主机在启动模拟器时意外重启，见文末）· 基线：`0.6.3 (20)` 已发布，相关改动未发布。

## 结论

已实现可选的 CameraX 厂商扩展支持：

- 新增拍照设置「相机扩展」，可选 **关闭（默认）/ 自动 / 夜景 / HDR**；
- 自动模式按 夜景 → HDR → 标准管线 的顺序选择设备实际支持的扩展；
- 启动拍照会话时检测当前镜头是否支持所选扩展；不支持、初始化失败或 3 秒超时都会**自动回退标准管线**；
- 扩展生效期间关闭 ZSL/强制帧率、关闭闪光灯、按厂商能力跳过 ImageAnalysis，并且不再向扩展请求叠加高质量降噪/锐化等 Camera2 参数，避免覆盖厂商处理；
- 扩展绑定失败时捕获异常并回退标准相机选择器。

这解决的是“能不能用上厂商多帧夜景/HDR”的问题，**不保证所有机型都支持**；华为 Mate 70 Pro 是否提供 AndroidX CameraX 厂商扩展尚未确认（见边界）。

## 实现

| 文件 | 改动 |
| --- | --- |
| `db/AppSettings.kt` | 新增 `PhotoCaptureExtensionMode`（`AUTO/OFF/NIGHT/HDR`），`PhotoCaptureSettings.extensionMode` 默认 `OFF` |
| `helpers/PhotoCaptureExtensions.kt` | 纯函数 `resolvePhotoCaptureExtension()`：自动优先级与显式模式回退 |
| `services/PhotoCaptureService.kt` | 会话开始时通过 `ExtensionsManager` 检测并绑定扩展选择器；3 秒初始化超时；扩展模式降级 profile；失败回退；日志记录 `requested/available/selected` |
| `ui/screens/PhotoCaptureSettingsScreen.kt` | 相机设置新增「相机扩展」分段选项 |
| `values/strings.xml`、`values-zh/strings.xml` | 新增设置标题、说明和四个选项文案 |

行为细节：

- 默认关闭，不改变已有用户和速度优先路径的行为；启用“自动”后才会尝试厂商扩展。
- 扩展可用时：`useFastOptions = false`（无 ZSL、无固定帧率）、闪光灯关闭、`ImageAnalysis` 仅在厂商声明支持时绑定。
- 扩展不可用时：完全走原有标准管线，日志 `CameraX extensions unavailable or timed out; using standard camera` 或 `selected=OFF`。
- 绑定异常时：记录 `Camera extension binding failed; using standard camera`，用标准选择器重绑。

## 验证

| 检查 | 结果 |
| --- | --- |
| `:app:compileDebugKotlin` | 通过 |
| `:app:testDebugUnitTest` | **27 / 27 通过**（新增 `PhotoCaptureExtensionsTest` 3 项：自动优先级、显式模式回退、关闭模式） |
| `:app:lintDebug` | **0 error / 0 fatal**（188 既有 warning） |
| `:app:assembleRelease` | 通过（R8 + 签名） |
| 设备实测 | **未执行**：模拟器启动时宿主 Windows 意外重启（Kernel-Power 41，无蓝屏 dump），为避免再次中断，设备验证暂停 |

设备验证清单（待执行）：

1. 安装 debug/release 包，打开相机设置确认「相机扩展」行与文案；
2. 默认「关闭」时拍照，logcat 无扩展初始化日志、行为与 0.6.3 一致；
3. 开启「自动」：logcat 出现 `Camera extensions requested=AUTO nightAvailable=… hdrAvailable=… selected=…`；
4. 若 `selected=OFF`：确认照片正常、无崩溃、无 `Camera extension` 绑定错误；
5. 若设备支持：分别在白天/暗光比较开启前后的文件大小、噪点与拍摄时延，并确认 HDR 不破坏高光/阴影。

## 边界

- **华为 Mate 70 Pro**：CameraX Extensions 依赖 OEM 提供 AndroidX 扩展实现；华为设备不在 CameraX 测试列表内，且华为使用自有 XMAGE/CameraKit 体系，预计**不会支持**该扩展。若不支持，开启“自动”会回退标准管线，不会报错，但也不会有夜景降噪收益。若手机升级到 HarmonyOS NEXT（纯血鸿蒙），APK 本身无法安装；HarmonyOS 4.x 兼容安卓，可运行本应用。
- 扩展支持与否依赖具体机型和系统版本，只有真机日志能确认；模拟器不提供厂商扩展，只能验证回退路径。
- 扩展模式通常会降低拍摄速度；夜景/多帧效果和分辨率限制由厂商实现决定，可能不支持最高分辨率。
- 华为的 XMAGE 多帧算法只对华为相机/HMS 应用开放，第三方 CameraX 应用无法调用；这是平台限制。

## 状态说明

宿主机在 2026-10-28 13:30 启动模拟器等待开机时意外重启（Kernel-Power 41，无 BugCheck/minidump），`emulator-5580` 未运行。重新启动模拟器有再次中断的风险，设备级验证需要用户确认后再进行，或改在真机执行。

相关发布：已发布的 `0.6.3` 不包含本功能；本改动尚未提交/发布。
