# 设置页统一 UI 证据

对应报告：[设置页统一 UI 验收](../../reviews/settings-ui-unification-2026-10-27.md)。

环境：Android 16 / API 36，模拟器 `Pixel9_API36`，固定序列号 `emulator-5580`；1080×2424 px，density 420（2.625×）；系统语言中文；应用包 `com.zgybeep.alibi.debug`；版本 0.6.1 (18)。所有 ADB 命令显式使用该序列号，未连接或操作物理设备。

## 改造前后

- `before-settings.png`：改造前通用设置页（`LargeTopAppBar`、FilterChip、带前置图标与右侧按钮的旧 Tile、无执行分区面板）。
- `before-camera-settings.png`：改造前相机设置页（本次变化很小，是设置页对齐的视觉基准）。
- `settings-top.png`：改造后设置页浅色顶部：小标题栏、页面说明、外观分区、设置面板与分隔线。
- `settings-mid.png`：录制分区中段：值行 + 右侧当前值 + chevron、快捷选择 chip、开关行。
- `settings-advanced-top.png`、`settings-advanced-audio.png`、`settings-advanced-video.png`、`settings-advanced-photo.png`：高级开关展开后的音频/视频/拍照/设置备份分组。
- `settings-dark-top.png`、`settings-dark-mid.png`：深色主题下的同一页面。
- `camera-settings-light.png`：同一次构建的相机设置页，用于对照共享组件与设计 token 的一致性。

## 验证证据

- `verify-settings-light.png` / `.xml`、`verify-settings-dark.png` / `.xml`：独立验证者采集的设置页浅/深色基准。
- `verify-camera-light.png` / `.xml`、`verify-camera-dark.png` / `.xml`：相机设置页浅/深色对照。
- `verify-settings-light-mid.png`、`verify-notifications-security-dark.png`：页面中部与通知/安全分区。
- `verify-advanced-on.png`、`verify-advanced-sections.png`、`verify-advanced-off.png`：高级开关开/关后分组显隐。
- `verify-session-warning.png`：录制中打开设置时的告警。
- `verify-maxduration-dialog.png`、`verify-filename-sheet.png`、`verify-savefolder-sheet.png`、`verify-language-dialog.png`：值行打开的弹窗/sheet。
- `verify-nav-custom-notification.png`、`verify-nav-about.png`：导航行进入子页。
- `verification-summary.md`：独立验证摘要（自动化命令与真实数字、视觉量化、交互回归、代码审查、问题列表）。
- `verify-duration-dialog.png`、`verify-savefolder-help.png`：实现者补充的行交互证据。

## 验证摘要

- 构建：`:app:assembleDebug` 通过；JVM 单元测试 16 / 16 通过；模拟器 instrumentation 4 / 4 通过；lint Error/Fatal = 0（Warning 191，均为既有工程告警；本次实现新增告警仅 1 条未使用旧字符串）。
- 视觉量化（设置页 vs 相机设置页，浅/深色）：页面内容左边距 53 px = 20 dp、分区标题 x=64 px、行内标题 x=95 px、分组间距 42 px = 16 dp；面板 24 dp 圆角、`outlineVariant` 描边与行间隔线均存在；两页标题栏均为小标题栏（返回箭头与标题同一行，无大号折叠标题）。
- 交互：值行 4 项、开关行 2 项、导航行 2 项、高级开关显隐与持久化、录制中告警、应用锁 `NONE_ENROLLED` 警告均实测通过；全流程 logcat 无 FATAL EXCEPTION / ANR。
- 截图仅代表模拟器渲染；未验证真实设备字体缩放、震动、厂商后台限制或相机画质。第三方设置弹窗的视觉适配不在本次范围，见报告“剩余事项”。

## 复现命令

```bash
# 构建与安装
./gradlew :app:assembleDebug --console=plain
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-x86_64-debug.apk

# 测试（串行执行）
./gradlew :app:testDebugUnitTest --console=plain        # 16 tests / 0 fail
./gradlew :app:connectedDebugAndroidTest --console=plain # 4 tests / 0 fail
./gradlew :app:lintDebug --console=plain                 # 0 error / 191 warning

# UI dump / 截图
MSYS_NO_PATHCONV=1 adb -s emulator-5580 shell "uiautomator dump /sdcard/ui.xml"
MSYS_NO_PATHCONV=1 adb -s emulator-5580 pull /sdcard/ui.xml ./ui.xml
adb -s emulator-5580 exec-out screencap -p > out.png

# 持久化状态
adb -s emulator-5580 shell "run-as com.zgybeep.alibi.debug cat files/datastore/settings.json"
```

注意：`:app:connectedDebugAndroidTest` 结束后 AGP 会卸载 debug APK 并清空应用数据；需要保留设置状态时先备份 `files/datastore/settings.json`。
