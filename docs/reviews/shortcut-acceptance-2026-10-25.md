# 桌面快捷启动与运行状态验收报告

> **修复状态（2026-10-25）**：S01–S05 已在当前工作区修复，并完成 Android 16 模拟器回归。最新结果见[桌面快捷启动修复验收报告](shortcut-repair-2026-10-25.md)。下文保留首次验收基线，便于复核崩溃证据和修复原因。

日期：2026-10-25（Asia/Shanghai）  
验收对象：当前工作区中新增的桌面快捷录音、悬浮拍照入口和应用标题栏运行绿点。  
版本：`0.6.1 (18)`；基线提交：`2ac7fc4`。本报告对应尚未提交的工作区改动，不代表该 Git 提交已经包含新增功能。

## 1. 验收结论

**整体未通过，暂不具备发布条件。**

在 Android 16 / API 36 模拟器中确认：权限齐全时，两个快捷入口可以不显示主界面而启动前台服务；录音产生音频文件，悬浮按钮可以拍照并保存 JPEG。长按桌面应用图标也实际显示了两个快捷菜单项。

但 Debug 和 Release 均存在 **P0 首页启动崩溃**。首次权限不足时，快捷入口会回到首页并触发相同崩溃；应用内运行绿点、返回首页和页面切换的界面验收因此被阻塞。另外，异常保存目录配置会导致快捷录音入口在服务连接回调中崩溃，已单独复现。

本轮仅增加验收文档、复现脚本和证据，未修复产品代码，未修改版本号，未提交、推送或发布。

## 2. 范围、环境和判定口径

| 项目 | 记录 |
| --- | --- |
| 主机 | Windows / PowerShell，项目目录 `T:\Github\Alibi` |
| 模拟器 | `Pixel9_API36`，序列号 `emulator-5580` |
| 系统 | Android 16，API 36，x86_64；`ro.kernel.qemu=1` |
| 摄像头和音频 | 模拟摄像头；模拟器禁用主机音频。验证文件生成，不评价真实画质、声音或震动手感 |
| Debug 包名 | `com.zgybeep.alibi.debug` |
| Release 包名 | `com.zgybeep.alibi` |
| SDK | minSdk 24 / targetSdk 34 / compileSdk 36 |
| 安装包 | 当前工作区构建的 x86_64 Debug 与 Release；详见证据目录中的 SHA-256 |
| 设备边界 | 全部设备命令明确指定 `emulator-5580`；脚本检查模拟器属性，未安装到或操作物理手机 |

“通过”仅表示本报告所述环境和步骤验证通过。“代码确认”表示源码中已存在相应逻辑，但不能替代完整设备交互验证。“阻塞”和“未测”均不计入通过项。

已有 graphify 索引生成于 2026-10-24，未包含本次新增入口。本轮仅用其定位既有模块，新增实现、结论和问题定位均核对了当前源码。24 个改动源文件的哈希保存在 `source-sha256.json`。

## 3. 自动化和包检查

| 检查 | 结果 | 说明 |
| --- | --- | --- |
| Debug / Release 构建 | 通过 | Gradle `BUILD SUCCESSFUL` |
| JVM 单元测试 | 通过 | 强制重跑测试任务：16 tests，0 failures，0 errors，0 skipped |
| Android 测试 APK 编译 | 通过 | 仅编译，未把示例包名测试算成功能设备验收 |
| lintDebug | 通过 | 0 errors、189 warnings；不代表运行时没有崩溃 |
| Debug / Release 快捷方式资源 | 通过 | 分别指向各自包名，目标类均为 `app.myzel394.alibi.ShortcutActivity` |
| Release 签名 | 通过 | `apksigner verify --print-certs` 验证成功，证书摘要与既有发布证书一致 |
| 工作区差异空白检查 | 通过 | `git diff --check` 未发现空白错误 |

Release 证书 SHA-256：

```text
5f9d20f64ae2b40b880cb8719afccf71ff62777ad76ef9565cc33a2ca9fcb334
```

单元测试主要覆盖配置兼容、拍照状态/预算、命名和应用标识，没有覆盖系统快捷方式发布规则、Activity 生命周期和服务回调所有权。因此，16 项单元测试通过与下述运行崩溃同时成立。

## 4. 功能验收矩阵

| 编号 | 场景 | 结果 | 实际观察与限制 |
| --- | --- | --- | --- |
| A01 | 系统注册两项静态快捷方式 | 通过 | `dumpsys shortcut` 中存在录音和拍照入口及正确的目标 Intent |
| A02 | 桌面长按图标显示快捷菜单 | 通过 | 实际长按，菜单出现“后台开始音频录制”和“后台启动悬浮拍照”；有截图与 UI XML |
| A03 | 正常打开 Debug 首页 | **失败** | `IllegalArgumentException: Manifest shortcut ID=background_photo_capture may not be manipulated via APIs` |
| A04 | 正常打开 Release 首页 | **失败** | 同样的快捷方式 ID 冲突崩溃 |
| A05 | 无麦克风权限时快捷录音 | **失败** | 未启动录音服务；转入首页后因 S01 崩溃，无法完成授权闭环 |
| A06 | 无相机及悬浮权限时快捷拍照 | **失败** | 未启动拍照服务；转入首页后因 S01 崩溃 |
| A07 | 已授权、进程停止后快捷录音 | 通过 | 音频前台服务运行；未出现 MainActivity；生成非零大小的 `1.aac` |
| A08 | 录音中再次触发快捷录音 | 部分通过 | 本次未崩溃，服务继续运行、未出现首页；配置和回调是否被改写见 S03，不能认定完整幂等 |
| A09 | 已授权、进程停止后快捷拍照 | 通过 | 拍照前台服务运行；桌面出现悬浮按钮；没有 MainActivity |
| A10 | 拍照会话中重复相同快捷入口 | 通过（同模式） | 本次未崩溃，服务继续运行；黑屏与悬浮模式互切不在本项通过范围内 |
| A11 | 点击悬浮按钮拍照保存 | 通过 | 生成 59,812 B JPEG 和照片索引；日志记录 `Photo saved: request=1 saveMs=42` |
| A12 | 未知快捷 Action | 通过 | Activity 退出；未启动音频或拍照服务，未出现首页、未崩溃 |
| A13 | 保存目录配置为非 tree URI | **失败** | `Invalid URI: not-a-tree-uri`，异常发生在服务连接回调，见 S02 |
| A14 | 系统顶部相机隐私标识 | 通过 | 后台拍照时截图中可见系统绿色相机标识 |
| A15 | 应用内标题栏绿点显示与消失 | **阻塞** | 已核对状态汇总及组件接入；首页崩溃导致无法验证实际 UI 更新 |
| A16 | 返回首页、设置等页面切换流畅 | **阻塞** | 正常首页不能启动，不能签发流畅性结论 |
| A17 | 启动速度、帧时间、ANR、长时间功耗 | **未测** | 本轮没有专项性能采样或长时间稳定性测试 |

设备入口探针使用资源中相同的 Action、目标组件和 `0x1000c000` flags，经 ADB 启动；长按菜单显示另用 Launcher 实际操作验证。**尚未逐项通过 Launcher 菜单点击重跑全部正反场景**，也没有覆盖厂商 Launcher。

## 5. 问题清单和修复优先级

### S01 · P0 · 静态与动态快捷方式 ID 冲突，首页启动即崩溃

状态：**Debug、Release 均在模拟器复现。**

定位：

- `app/src/main/java/app/myzel394/alibi/AppShortcuts.kt:10`、`:11`：动态入口 ID 与静态资源相同。
- `app/src/main/java/app/myzel394/alibi/AppShortcuts.kt:35`：调用 `setDynamicShortcuts()`。
- `app/src/main/java/app/myzel394/alibi/MainActivity.kt:35`：在 `onCreate()` 中直接调用，异常未被处理。
- `app/src/main/res/xml/shortcuts.xml` 及 Debug 对应资源：已声明相同 ID 的静态快捷方式。

最小复现：

```powershell
adb -s emulator-5580 shell am start -W -n com.zgybeep.alibi.debug/app.myzel394.alibi.MainActivity
adb -s emulator-5580 logcat -d -b crash
```

系统拒绝通过动态 API 操作 manifest 定义的不可变快捷方式，异常沿调用栈使首页启动失败。Release 的压缩和签名并未消除该问题。

建议：当前两项固定入口优先保留静态声明，移除对相同 ID 的动态发布；确有动态入口需求时采用独立 ID 并防止菜单重复。快捷方式维护失败应记录日志，不能阻断首页。

回归要求：Debug / Release 新安装、覆盖安装、首次启动及权限不足回退均能进入首页；静态菜单仍可见，且没有重复项。

证据：[Debug 崩溃](../evidence/shortcut-acceptance-2026-10-25/main-launch-crash.txt)、[Release 崩溃](../evidence/shortcut-acceptance-2026-10-25/release-main-crash.txt)。

### S02 · P1 · 异常保存目录使快捷录音在服务连接回调中崩溃

状态：**负向配置测试已复现；未将其表述为正常目录必现问题。**

定位：`ShortcutActivity.kt:110` 调用 `createAudioFolder()`；`:178` 使用 `DocumentFile.fromTreeUri()`。这段代码在异步 `onServiceConnected()` 中执行，不受外层启动/绑定服务的 `try/catch` 保护。

复现条件：仅在模拟器测试数据中设置 `saveFolder="not-a-tree-uri"`，授予录音权限后触发快捷录音，得到 `IllegalArgumentException: Invalid URI`。探针通过 `finally` 恢复原模拟器设置。

建议：在服务回调内部处理 URI 解析、持久授权失效、不可写目录和清理旧文件的异常；向用户提供可恢复的目录选择入口，并结束未成功启动的服务。不得以启动调用成功作为录音成功依据。

回归要求：异常 URI、失效 SAF 授权和不可写目录均不崩溃、不遗留启动中的前台服务，并能重新选择目录。

证据：[目录异常崩溃](../evidence/shortcut-acceptance-2026-10-25/invalid-folder-crash.txt)、[复现脚本](../evidence/shortcut-acceptance-2026-10-25/probe-invalid-folder.py)。

### S03 · P1 · 快捷录音覆盖活动服务的配置和单一回调

状态：**代码确认；首页恢复后的交错场景仍待设备验证。**

定位：`ShortcutActivity.kt:109–124` 在检查 `service.state` 之前，无条件重设 settings、batchesFolder、onError、onBatchesFolderNotAccessible 和 onStateChange。`BaseRecorderModel.kt:72–83` 也使用相同的单一回调槽。

影响：已有主界面订阅服务时，再触发快捷入口会覆盖原订阅；透明 Activity 退出时只解绑服务，未释放这些引用。服务后续仍可能持有已销毁的 Activity，或者把状态/错误发给旧入口。重复启动实测“没有崩溃”不足以排除这些问题。

建议：启动和配置归服务统一管理；已运行时不重设目录及设置。状态通过可独立取消的订阅或 StateFlow 观察，Activity 销毁时只取消自己的订阅；避免直接清空其他页面拥有的回调。

回归要求：主界面开始录音 → 返回桌面 → 重复快捷启动 → 暂停/继续/错误处理 → 回到首页；核对 UI、通知、文件目录和服务状态一致，已销毁入口不再被服务引用。

### S04 · P2 · 主线程目录操作和启动就绪判断仍有风险

状态：**代码风险，尚无帧时间或 ANR 复现数据。**

`ShortcutActivity.kt:110` 的目录创建/解析、`:127` 的 `clearAllRecordings()` 位于主线程服务回调中；自定义目录和媒体目录可能触发同步文件或 ContentProvider 操作。慢目录、大量旧片段下可能延迟入口响应。

拍照入口在 `startForegroundService()` 返回后立即退出，没有等待相机 READY；录音入口在 RECORDING 回调后固定等待 750ms，而 `RecorderService.startRecording()` 在底层录制实际启动之前就发布 RECORDING。成功路径在本模拟器通过，但延迟等待不能替代真实就绪确认，也不能覆盖后续异步失败。

建议：将目录验证/清理移至 IO 调度器，增加真实启动成功、失败和超时状态；在目标 Android 版本验证透明 Activity 到前台服务的启动时序。

### S05 · P2 · 已有拍照会话时快捷入口的模式切换需补验

状态：**代码风险；因首页崩溃未完成用户操作链复现。**

`PhotoCaptureService.kt:623` 的 `startSession()` 在已有 session 或 active 状态时直接返回，发生在读取 `FLOATING` 和 `startInBackground` 之前。因此“黑屏会话已在运行，再点击快速拍照”可能继续保留原模式，无法按入口意图切换悬浮拍照。

建议：明确已运行时的入口行为，统一执行模式切换和可见性更新；不要只覆盖会话不存在的冷启动分支。回归应包括 READY、STARTING、CAPTURING、SAVING 和 ERROR 状态。

## 6. 需求语义和未完成覆盖

“不打开 App 就直接开始”在本实现中指 **不显示主界面**：系统仍短暂启动透明 `ShortcutActivity`，由它触发前台服务。已授权的本模拟器路径通过；首次没有权限时必须完成系统授权，而当前回退链受 S01 阻塞。

目前“后台录制”入口具体实现为 **录音**，没有新增后台录像快捷方式。“快速拍照”先启动悬浮会话，实际拍照由点击悬浮按钮触发，不是点击快捷菜单后立刻自动拍下一张照片。若需求包含后台录像或立即自动拍照，不能据现有实现认定完成。

应用内标题绿点与系统隐私标识需要分别验收：前者由 Compose 组件绘制，后者是系统相机/麦克风状态提示。本次仅拍到了系统相机标识。应用绿点还需验证录音、录像、拍照、暂停、停止、保存中和错误状态；当前音视频判断使用“是否持有服务”，需确认停止后的显示语义。

下列覆盖仍未完成：Android 7.1–15、Android 7.0 兼容、厂商后台管理、通知权限拒绝、权限运行中撤销、真实镜头/麦克风、前后摄切换、震动手感、锁屏和熄屏恢复、30 分钟稳定性、内存泄漏采样、TalkBack、大字体、暗色和动态色。不得据此报告承诺“全设备无卡顿、无 ANR”。

## 7. 修复顺序和重新验收门槛

1. **先修 S01**，恢复 Debug / Release 首页及权限引导，新增能实际启动 MainActivity 的回归检查。
2. **修 S02、S03**，确保异常目录可恢复，服务不被重复入口改写配置，也不持有已销毁入口回调。
3. **处理 S04、S05**，明确就绪、失败、超时与已有会话的快捷入口行为，消除主线程目录操作。
4. 重跑本报告 A01–A16，通过 Launcher 菜单点击回归；完成返回首页后的绿点显示/消失验证。
5. 对目标系统和设备补充前台服务启动限制、异常恢复与性能采样后，再签发发布验收结论。

本轮发现了两类可复现崩溃，其他风险须按上述用例复验。本报告不包含已经修复这些问题的承诺。

## 8. 证据索引

完整证据目录：[shortcut-acceptance-2026-10-25](../evidence/shortcut-acceptance-2026-10-25/README.md)。

- 构建与单元测试：`gradle-checks.log`、`unit-tests-rerun.log`、5 份 `TEST-*.xml`。
- 静态检查与包信息：`lint-results-debug.txt`、`debug-shortcuts-xmltree.txt`、`release-shortcuts-xmltree.txt`、`release-badging.txt`、`release-signature.txt`。
- 环境与复现：`device-environment.txt`、`run-emulator-audit.py`、`probe-invalid-folder.py`、`emulator-results.json`。
- 首页及权限失败：`main-launch-crash.txt`、`release-main-crash.txt`、`missing-*-crash.txt`。
- 后台服务和产物：`audio-cold-services.txt`、`audio-cold-files.txt`、`photo-tap-services.txt`、`photo-tap-files.txt`、`photo-tap-logcat.txt`。
- 界面证据：`launcher-shortcut-menu.png`、`launcher-shortcut-menu.xml`、`photo-cold-screen.png`。
- 快照追溯：`base-commit.txt`、`source-sha256.json`、`apk-sha256.json`、`worktree-status.txt`。
