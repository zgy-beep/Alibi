# 桌面快捷启动修复验收报告

日期：2026-10-25（Asia/Shanghai）  
初始报告：[桌面快捷启动与运行状态验收报告](shortcut-acceptance-2026-10-25.md)  
版本：`0.6.1 (18)`，本轮未修改版本号。  
结论：**S01–S05 已修复；Android 16 指定模拟器回归全部通过。**

## 1. 修复结果

| 问题 | 修复 | 回归结果 |
| --- | --- | --- |
| S01 静态与动态快捷方式 ID 冲突 | 保留 manifest 静态入口，移除对相同 ID 的动态注册；新增实际启动 `MainActivity` 并校验 manifest shortcuts 的设备测试 | Debug、Release 首页均正常启动 |
| S02 异常保存目录崩溃 | 服务连接后先确认 IDLE，再在 `Dispatchers.IO` 创建目录并删除旧片段；失败时销毁未成功的服务并回到授权主界面。首页低存储检查、历史可用性判断、录音模型和设置页打开目录均增加安全失败路径 | 非 tree URI 不再使快捷入口或首页崩溃，主界面正常出现 |
| S03 快捷入口覆盖活动服务回调 | 透明快捷 Activity 不再设置或清空录音服务回调；已有会话只完成入口并退出，录音启动通过同步结果确认 | 录音运行中重复快捷入口保持前台服务和会话 |
| S04 主线程目录操作与虚假就绪 | 目录解析和旧片段清理由主线程移出；`startRecording()` 仅在底层同步启动成功后返回 true，失败时服务状态保持 IDLE | 后台录音生成 AAC；冷启动、重复入口通过 |
| S05 已有拍照会话不能切换悬浮模式 | 先解析入口模式和后台可见性，再处理已有会话；STARTING/READY/CAPTURING/SAVING 状态均允许应用目标模式，就绪发布时读取当前 session | 黑屏拍照会话经快捷入口切换为悬浮窗口 |
| 运行绿点语义 | 音视频状态由“是否已绑定服务”改为真实 RECORDING/PAUSED 状态 | 录音和拍照运行时可见；停止后隐藏 |

相关实现：

- [AppShortcuts.kt](../../app/src/main/java/app/myzel394/alibi/AppShortcuts.kt)
- [ShortcutActivity.kt](../../app/src/main/java/app/myzel394/alibi/ShortcutActivity.kt)
- [RecorderService.kt](../../app/src/main/java/app/myzel394/alibi/services/RecorderService.kt)
- [PhotoCaptureService.kt](../../app/src/main/java/app/myzel394/alibi/services/PhotoCaptureService.kt)
- [LowStorageInfo.kt](../../app/src/main/java/app/myzel394/alibi/ui/components/RecorderScreen/atoms/LowStorageInfo.kt)
- [AppSettings.kt](../../app/src/main/java/app/myzel394/alibi/db/AppSettings.kt)
- [Navigation.kt](../../app/src/main/java/app/myzel394/alibi/ui/Navigation.kt)
- [设备回归测试](../../app/src/androidTest/java/app/myzel394/alibi/ExampleInstrumentedTest.kt)

## 2. 自动化和构建

| 检查 | 结果 |
| --- | --- |
| Debug Kotlin 和设备测试 APK 构建 | 通过 |
| Release 构建与 R8 | 通过 |
| JVM 单元测试 | 16 tests，0 failures，0 errors，0 skipped |
| Android 设备测试 | 3 tests，`OK (3 tests)` |
| lintDebug | 0 errors，189 warnings |
| `git diff --check` | 通过 |

设备测试实际启动 `MainActivity`，读取 `ShortcutManager.manifestShortcuts`，并校验两个固定入口 ID。第三项测试在应用进程内启动黑屏拍照会话，再发送快捷拍照 Intent，验证已有会话切换为 `FLOATING`。这三项测试能直接覆盖本次首页异常和黑屏到悬浮的模式切换。

## 3. 模拟器回归

环境：`Pixel9_API36`，Android 16 / API 36 / x86_64，序列号明确指定为 `emulator-5580`，`ro.kernel.qemu=1`。设备自动化 13 项全部通过：

1. Debug APK 安装。
2. Debug 首页在静态快捷方式存在时正常启动。
3. 缺少麦克风权限的快捷录音回到主界面，无崩溃。
4. 缺少相机或悬浮权限的快捷拍照回到主界面，无崩溃。
5. 已授权快捷录音启动前台服务，不显示主界面，并生成 AAC 文件。
6. 录音运行时首页显示“Alibi 正在运行”语义绿点。
7. 录音中重复快捷入口保持服务前台运行。
8. 录音服务停止后首页不显示运行绿点。
9. 冷启动快捷拍照显示悬浮窗口，不显示主界面。
10. 拍照运行时首页显示运行绿点。
11. 点击悬浮按钮后保存 JPEG，并更新照片索引。
12. 非 tree URI 保存目录不再使快捷入口或首页崩溃，能返回主界面。
13. Release 首页正常启动。

黑屏到悬浮的模式切换由应用内设备测试验证，未放宽服务的 `exported=false` 安全边界。本次自动化场景无 `FATAL EXCEPTION`。照片日志记录 `Photo saved: request=1 saveMs=34`，文件为 60,046 B JPEG。最终 Release 又单独冷启动复验，耗时 1,008 ms，crash buffer 为空。证据见[修复回归目录](../evidence/shortcut-repair-2026-10-25/README.md)。

## 4. 边界和剩余风险

“不打开 App 就开始”仍指不显示主界面；Android 会短暂运行透明 `ShortcutActivity` 来启动前台服务。这是系统权限模型和前台服务启动规则下的实现方式。

当前快捷“后台录制”是音频录音。如果需要后台录像，需要单独增加录像入口和权限流程。快捷拍照会启动悬浮会话，拍照动作由悬浮按钮完成，不会自动连续拍摄。

本轮只覆盖 Android 16 模拟器。真实相机画质、麦克风效果、震动、厂商后台限制、锁屏、功耗、长时间稳定性以及 Android 7.1–15 仍需在对应真实设备验证；模拟器结果不用于承诺全设备流畅。

本轮没有修改版本号，没有提交、推送或发布。构建产物是工作区当前代码生成的验收包。
