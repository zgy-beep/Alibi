# 修复与 UI 重构验收

日期：2026-10-25 · 版本：0.6.1 (18) · 环境：Android 16 / API 36 模拟器 `Pixel9_API36`，序列号固定为 `emulator-5580`。

## 结论

当前工作区已修复复核报告中的录音启动问题，并完成计划中的核心 UI 路径。Debug、Release 构建成功；4 项 instrumentation、16 项 JVM 测试和 13 项快捷入口设备回归通过。验收范围限于模拟器，不包含真实相机/麦克风质量、震动幅度、锁屏、厂商后台限制或 Android 7.1–15 兼容结论。

## 稳定性修复

- 录音服务增加 `STARTING`、`ERROR`，启动通过互斥门闩串行执行。重复请求复用在途或活动会话，不再由第二个入口清空目录或撤销服务。
- 目录初始化、片段清理、首段 `MediaRecorder.prepare/start` 在服务启动流程的 IO 段执行。首段成功后才发布 `RECORDING` 和成功绿点。
- 快捷入口的绑定、目录准备、前台服务和首段启动共享 8 秒截止时间。取消后会在启动录音前检查协程是否仍有效，迟到任务不能继续启动。
- `MediaRecorder` 初始化、启动和换段失败进入 `ERROR`，停止周期任务并调用现有错误处理；失败的临时录音器会被释放。
- 摄像服务的所有等待者在其失败时收到完成异常，避免启动状态无限悬挂。

相关实现：[RecorderService.kt](../../app/src/main/java/app/myzel394/alibi/services/RecorderService.kt)、[IntervalRecorderService.kt](../../app/src/main/java/app/myzel394/alibi/services/IntervalRecorderService.kt)、[AudioRecorderService.kt](../../app/src/main/java/app/myzel394/alibi/services/AudioRecorderService.kt)、[ShortcutActivity.kt](../../app/src/main/java/app/myzel394/alibi/ShortcutActivity.kt)。

## UI 重构

- 建立共享布局尺寸、表面层级和悬浮窗样式 token；补齐浅/暗色 `surfaceContainer` 系列和统一 120/180/220/260 ms 动效尺度。
- 首页在所有宽度下采用三入口竖排。录音和录像使用同尺寸中性卡，拍照作为唯一强调入口；缓存区域从大块空卡压缩为摘要卡。
- 拍照准备页改为“先选模式，再执行一个主动作”，配置摘要、运行会话、设置、图库和停止操作分区展示。
- 新增共享标题栏、状态标记、分组和可选模式卡；关于、通知、照片、设置和相机设置统一标题栏结构。
- 标题栏状态区分准备、运行和暂停，并通过文本语义描述；悬浮快门复用同一色值、圆角和减少动效规则。
- 黑屏页面保持纯黑预览，没有常驻标题或拍照文字。点击拍摄、连续抓拍、单指透明度和双指缩放逻辑保持原实现。

设计与实施映射：[统一 UI 设计](../plans/unified-ui-design-2026-10-25.md)、[页面设计稿](../design/unified-ui-2026-10-25.png)。

## 自动化与设备结果

| 检查 | 结果 |
|---|---|
| JVM 单元测试 | 16 / 16 通过 |
| Android instrumentation | 4 / 4 通过，0 失败/错误/跳过 |
| 新增录音测试 | 启动后确认真实 recorder 和 `RECORDING`；第二次启动不重新执行目录准备 |
| 快捷入口回归 | 13 / 13 通过，包含后台录音、重复入口、悬浮拍照、异常目录和 Release 首页 |
| 连续黑屏抓拍 | 5 次请求全部曝光并保存，曝光延迟 240–362 ms，保存 45–108 ms |
| 黑屏长按返回 | 通过，返回首页 |
| Debug / Release 构建 | 通过 |
| Release 签名 | 通过，证书 SHA-256 `5f9d20f64ae2b40b880cb8719afccf71ff62777ad76ef9565cc33a2ca9fcb334`，沿用既有 Alibi 发布证书 |
| lint | 0 errors；warnings 属于既有排版、未用资源和依赖提示，仍有存量告警 |

模拟器延迟仅用于确认队列和保存链路，不代替真实设备快门手感或帧率结论。证据：[UI 重构证据](../evidence/ui-rework-2026-10-25/README.md)、[快捷入口回归摘要](../evidence/shortcut-repair-2026-10-25/repair-summary.txt)。

## 剩余验收

第三方设置弹层仍需逐项视觉核对；大字、读屏、三组动态色、横屏/宽屏和减少动效尚无完整矩阵截图。帧时、冻结帧、30 分钟稳定性、内存泄漏、真实震动、锁屏恢复和厂商后台策略也需要目标设备。版本号保持 `0.6.1 (18)`。
