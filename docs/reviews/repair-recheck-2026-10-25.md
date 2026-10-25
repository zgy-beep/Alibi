# 当前修复复核验收

日期：2026-10-25 · 版本：0.6.1 (18) · 基线提交：2ac7fc4，加当前工作区改动。

## 结论

**原先的首页崩溃、非法目录崩溃和本轮发现的两个录音 P1 已在当前工作区修复。** 录音初始化由服务串行拥有，目录清理发生在取得启动所有权之后；首段录音真正开始后才发布 `RECORDING`。Android 16 模拟器中 4 项 instrumentation、13 项快捷入口回归和连续抓拍通过。真实麦克风/相机质量、厂商后台限制和完整兼容矩阵仍需相应真机验证。

本轮随后完成了服务修复、UI 重构、Debug/Release 构建、Android 16 模拟器回归及证据整理。详细结果见[修复与 UI 重构验收](repair-ui-implementation-2026-10-25.md)。

## 本轮验证与证据

| 检查 | 结果 | 证明范围 |
|---|---|---|
| 原修复快照的源文件 SHA-256 | 30/30 一致 | 当前关键实现与原证据对应 |
| 原 Debug / Release APK SHA-256 | 2/2 一致 | 现存包与原设备验收使用的包对应 |
| `:app:testDebugUnitTest :app:lintDebug --rerun-tasks` | BUILD SUCCESSFUL，2m 28s | 本轮实际执行，38 个任务执行 |
| JVM 测试 | 16 通过，0 失败/错误/跳过 | 现有单元测试覆盖范围通过 |
| lint | 0 errors、189 warnings、2 information | 不是零告警；主要是排版、资源及依赖提示 |
| `git diff --check` | 通过 | 有 LF/CRLF 提醒，无空白错误 |
| Android instrumentation | 旧证据 `OK (3 tests)` | 本轮核验记录，未重新执行 |
| Android 16 模拟器回归 | 旧证据 13/13 | 本轮核验脚本和记录，未重新执行 |
| Release 冷启动 | 旧证据约 1,008 ms | 单次旧样本，不能作为帧率或 P95 |

本轮证据：[checks-summary.json](../evidence/repair-recheck-2026-10-25/checks-summary.json)、[gradle-checks.log](../evidence/repair-recheck-2026-10-25/gradle-checks.log)、[hash-verification.json](../evidence/repair-recheck-2026-10-25/hash-verification.json)。原设备证据：[索引](../evidence/shortcut-repair-2026-10-25/README.md)。

graphify 索引早于本次快捷入口改动，只用于定位 Navigation、Theme、PhotoSetupScreen 等既有模块，以下问题均直接核对当前源码。

## 原问题逐项复核

| 原编号 | 本次判定 | 说明 |
|---|---|---|
| S01 快捷方式 ID 冲突 | 通过，限已有验证环境 | 静态资源保留，动态重复注册移除；新测试真实启动首页 |
| S02 非法目录崩溃 | 已复现的非法 URI 路径通过 | 异常被收敛，旧模拟器记录无崩溃；失效 SAF 授权、只读目录、提供方阻塞仍未覆盖 |
| S03 覆盖服务回调/配置 | 部分通过 | 回调覆盖已消除；异步初始化后的竞争处理仍有 R01 |
| S04 主线程 IO/虚假就绪 | 部分通过 | 入口目录构造和删除移到 IO；实际目录初始化、首段启动还有 R02/R03 |
| S05 已有拍照会话切悬浮 | 服务层通过，完整 UI 链待测 | 测试直接向服务发 Intent；未从黑屏页面经桌面实际操作全链路 |
| 绿点 | 录音/拍照运行态通过；完整状态矩阵未通过 | 原停止测试为 force-stop 后重启；正常停止、暂停、保存失败、录像状态仍缺证据 |

## 剩余问题及修复顺序

### R01 · P1 · 初始化竞争会清理共享片段，并可能撤销其他入口启动的服务

定位：[ShortcutActivity.kt](../../app/src/main/java/app/myzel394/alibi/ShortcutActivity.kt) 第 110–127 行；[BatchesFolder.kt](../../app/src/main/java/app/myzel394/alibi/helpers/BatchesFolder.kt) 第 306 行。

入口检查 IDLE 后异步执行 `createAudioFolder().deleteRecordings()`。此期间没有服务级启动占位或互斥。假设 A 等待慢目录时，主界面或另一个入口 B 已经开始录音，A 的清理可能操作同一片段目录；A 返回主线程发现状态不是 IDLE 后，反而调用 `service.destroy()`。该方法会停定时器、撤销前台通知并 stopSelf；是否立即销毁取决于其他绑定，不能认为它只清理 A 自己的失败请求。

证据等级：**静态控制流确认的竞争风险，未在设备制造慢目录复现，不宣称已经发生文件损失。**

修复要求：由服务统一接收启动请求，原子进入 STARTING 并分配请求/会话所有权；复用活动会话的请求直接返回。只有拥有启动权的请求可以初始化/清理自己的目录和释放服务。单纯再加一次状态判断，不能保护 IO 已经执行的删除操作。

回归：可控阻塞目录准备 → B 启动 → 释放 A；断言服务/录音/通知持续、B 的片段没有被 A 删除、配置未改变。还应覆盖 A 取消、超时、失败和 B 暂停。

### R02 · P1 · 返回成功仍早于 MediaRecorder 真正启动

定位：[RecorderService.kt](../../app/src/main/java/app/myzel394/alibi/services/RecorderService.kt) 第 62–78 行；[IntervalRecorderService.kt](../../app/src/main/java/app/myzel394/alibi/services/IntervalRecorderService.kt) 第 50–75 行；[AudioRecorderService.kt](../../app/src/main/java/app/myzel394/alibi/services/AudioRecorderService.kt) 第 42–64 行。

`startRecording()` 调用 `start()` 后马上发布 RECORDING 并返回 true。但 `start()` 只是启动周期任务，真正的 `prepare()` / `MediaRecorder.start()` 在 `startNewCycle()` 异步执行。`prepare()` 的异常不在该函数内部 try 中；`start()` 失败仅调用 `onError()`，没有统一切换失败状态。快捷入口不注册错误回调时，默认回调为空，可能留下“正在运行”的状态而没有有效录音。

证据等级：**源码确认启动确认机制缺口，尚未注入底层失败做设备复现。** 原报告所说“同步启动成功”只覆盖同步初始化，不覆盖实际首段音频启动。

修复要求：首段录音器准备并 start 成功后再发布 RECORDING；对 prepare/start/后续换段失败统一上报 StateFlow 的 ERROR，清理定时器、录音器、通知和麦克风监听。成功确认不能依赖文件存在，也不能用固定延时替代。

回归：注入 prepare 失败、start 失败、换段失败；断言没有假绿点/假成功、资源释放、可重试；正常启动才退出透明入口。

### R03 · P2 · IO 迁移与超时范围仍不完整

`ShortcutActivity.kt:110` 在 bind 完成即移除 8 秒超时，后续目录 IO 没有端到端截止时间。`service.startRecording()` 又同步进入 `IntervalRecorderService.start():65–67` 的 `initFolders()` / `checkIfFolderIsAccessible()`，仍可能在主线程访问 ContentProvider。

证据等级：静态风险；**没有 ANR 或掉帧实测，不能标成已复现 ANR。**

修复要求：在 R01 所有权机制内完成 IO，覆盖配置读取、绑定、目录准备、首段启动的全过程超时；取消后禁止旧任务提交状态。阻塞 ContentProvider 不保证响应协程取消，应设置可用的 provider 取消机制或让迟到结果失效，不能仅套 `withTimeout` 就宣称所有 IO 可中断。

### R04 · P2 · 旧测试的断言强度不足以支持全部验收结论

- `run-repair-audit.py:255–264` 用 force-stop 后重新打开验证绿点消失，不能覆盖正常停止后的状态传播。
- 重复录音只检查 `isForeground=true`；未断言会话 ID、配置、持续增长的文件、回调仍属于主界面。
- 三项 instrumentation 中一项仅检查包名。切换测试调用的是服务 Intent，等待 `isActive` 和 mode，没有要求 READY、实际悬浮显示和拍照成功。
- `BlackPhotoScreen` 在 ON_STOP 停止会话；真实“黑屏 → 桌面 → 快捷入口”可能走停止/重启路径，不应等同于服务内切换测试。

补验：正常停止→不杀进程→观察首页绿点；主界面启动→快捷入口重复→暂停/继续/保存；实际 Launcher 图标菜单；黑屏离开与悬浮切换交错；STARTING/READY/CAPTURING/SAVING/ERROR 各状态。

## UI 与性能验收范围

已查看现有运行页面截图，核对 theme、首页、拍照准备/设置、黑屏手势和悬浮窗实现。现有 M3 基础已具备：颜色/形状/排版 token、120/180/220 ms 动效、浅暗色/动态色、减少动效入口、手机首页三按钮竖排。

尚未统一：悬浮窗仍是原生 TextView/Button 和硬编码色/140 ms 动效；设置弹层混用第三方对话框；全局 MaterialTheme 未配置 expressive motion；运行状态还是单个 Boolean，无法细分初始化、暂停、保存和错误；大字体/动态色/TalkBack 没有完整页面矩阵证据。

本轮没有新的 Perfetto、帧时间、内存或长时间稳定性采样。不能声称“全 App 顺滑、无卡顿、无 ANR”。**189 条 lint warning 也不等于 189 个性能缺陷。**

## 后续门槛

1. R01、R02 先闭环，R03 同阶段完成；再开展 UI 页面迁移，避免以视觉变更掩盖状态错误。
2. R04 补成真实用户操作链，并保存步骤、设备、日志、文件与断言。
3. 对 Android 24/25、28、30、31、33、34、36 分层覆盖；真实手机仅在明确授权后操作。画质、震动、麦克风、锁屏和厂商限制需对应设备验证。
4. 发布前完成主题、字体、手势、运行状态和性能矩阵，门槛见 [统一 UI 设计与实施计划](../plans/unified-ui-design-2026-10-25.md)。

本报告作为原修复报告的复核补充；旧日志和原结论保留，以便追踪差异。
