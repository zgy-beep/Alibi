# 设置页统一 UI 验收

日期：2026-10-27 · 版本：0.6.1 (18) · 环境：Android 16 / API 36 模拟器 `Pixel9_API36`，序列号固定为 `emulator-5580`。

## 结论

通用设置页已按相机设置页的风格完成统一：两页使用同一套共享组件、页面边距、分区标题、分组面板、行内排版与分隔线；独立验证确认两页的设计 token 像素级一致（20 dp 页面边距、16 dp 分组间距、24 dp 面板圆角、行内标题/说明层级、小标题栏）。Debug 构建、16 项 JVM 测试、4 项 instrumentation 测试和 lint 通过；第三方设置弹窗的视觉适配不在本次范围，见“剩余事项”。

## 改造内容

### 共享组件

新增 [SettingsRows.kt](../../app/src/main/java/app/myzel394/alibi/ui/components/atoms/SettingsRows.kt)，作为所有设置界面的统一行组件：

- `AlibiSettingRow`：标题 + 当前值 + 说明 + 可选 chevron，整行可点击（值行/导航行）。
- `AlibiSwitchSetting`：整行可切换，尾部开关只作展示，避免行与开关各触发一次。
- `AlibiChoiceSetting`：标题、说明与 M3 分段单选（从相机设置页私有实现提升为共享组件）。
- `AlibiSettingsNotice`：录制中告警等提示的统一封装。

[ExampleListRoulette.kt](../../app/src/main/java/app/myzel394/alibi/ui/components/atoms/ExampleListRoulette.kt) 由横向滚动的文字按钮改为自动换行的 AssistChip，所有快捷值在窄屏和大字体下都能完整显示。

### 通用设置页

[SettingsScreen.kt](../../app/src/main/java/app/myzel394/alibi/ui/screens/SettingsScreen.kt) 重写为与相机设置页相同的结构：

- `AlibiTopAppBar` 小标题栏（替代 `LargeTopAppBar`），标题内保留运行状态点。
- `LazyColumn`，内容边距 20 dp、顶部 12 dp、底部 32 dp，分组间距 16 dp。
- 页面说明 + 分区标题/说明 + `AlibiSectionPanel` 面板 + 行间 `HorizontalDivider`。
- 分区：外观（主题、系统配色、减少动画、语言）、录制（最大时长、片段时长、即时删除、文件名格式、片段文件夹）、通知与安全（自定义通知、应用锁）、高级（开关 + 音频/视频/拍照/设置备份）、关于。
- 原首页信息层级与状态语义保持不变：录制中告警、应用锁不可用/未录入提示、拍照会话中禁用文件夹与导入导出。

### 设置分项

`ui/components/SettingsScreen/Tiles/` 下 16 个分项从旧 `SettingsTile`（前置图标 + 右侧按钮）迁移为共享行组件：11 个值行（弹窗/sheet 触发原样保留）、3 个开关行、1 个导航行、导入/导出改为两行；业务逻辑、数据写入、对话框参数、`enabled` 条件均未改变。`ThemeSelector`、`InAppLanguagePicker` 改为共享行；语言行直接显示当前应用语言。

### 相机设置页

[PhotoCaptureSettingsScreen.kt](../../app/src/main/java/app/myzel394/alibi/ui/screens/PhotoCaptureSettingsScreen.kt) 删除私有 `ChoiceSetting`/`SwitchSetting`，14 处调用改为共享组件，视觉与原实现一致，保证设置页与相机设置页不会再次分叉。

### 资源与清理

- `values/strings.xml`、`values-zh/strings.xml` 各新增 14 个 `settings_*` 字符串（分区标题/说明、页面说明、主题行等），键集合一致且全部被引用。
- 删除 `atoms/SettingsTile.kt`、`Tiles/AboutTile.kt`、`Tiles/SectionTitle.kt`；全局无旧组件引用。

## 视觉一致性（独立验证量化）

| 设计 token | 设置页 | 相机设置页 | 结论 |
|---|---|---|---|
| 页面内容左边距 | 53 px = 20 dp | 53 px = 20 dp | 一致 |
| 面板左边界/描边 | 53 px | 53 px | 一致 |
| 分区标题 x | 64 px | 64 px | 一致 |
| 行内标题 x | 95 px | 95 px | 一致 |
| 分组间距 | 42 px = 16 dp | 16 dp | 一致 |
| 面板圆角 | 24 dp（`AlibiShapes.large`） | 同左 | 一致 |
| 行间隔线 | `outlineVariant` | 同组件 | 一致 |
| 标题栏 | 小标题栏，返回 [43,195][106,258]、标题 [148,185] | 同坐标结构 | 一致 |
| 深/浅色 | 页面底亮度 248.9 / 19.1 | 深色页底 19.1 | 均生效 |

## 自动化与设备结果

| 检查 | 结果 |
|---|---|
| `:app:assembleDebug` | 通过 |
| JVM 单元测试 | 16 / 16 通过，0 失败/错误/跳过 |
| Android instrumentation | 4 / 4 通过（`Pixel9_API36 - 16`） |
| lint | 0 error / 0 fatal；191 warning（既有 TypographyEllipsis、UnusedResources 等；本次新增仅 1 条旧字符串未使用） |
| 值行交互 | 最大时长数字弹窗、文件名格式 sheet、片段文件夹 sheet、语言列表弹窗均正常打开/关闭 |
| 开关行 | 减少动画持久化（退出重进仍 ON）、使用系统配色即时生效并恢复默认 |
| 导航行 | 自定义通知、关于 Alibi 子页进出正常 |
| 高级开关 | ON 显示四个高级分组并持久化 `showAdvancedSettings`；OFF 分组消失 |
| 录制中告警 | 开始音频录制后设置页顶部出现告警 |
| 应用锁 | `NONE_ENROLLED` 警告与 disabled 状态与旧实现一致 |
| 稳定性 | 全流程 logcat 无 FATAL EXCEPTION / ANR |

证据：[设置页统一 UI 证据](../evidence/settings-ui-2026-10-27/README.md)、[独立验证摘要](../evidence/settings-ui-2026-10-27/verification-summary.md)。

## 边界与未覆盖

- 第三方设置弹窗（`sheets-compose-dialogs` 的 DurationDialog/ListDialog/InputDialog，以及文件名格式、文件夹选择等自绘 sheet）仍沿用原实现；本次只统一页面表面与设置行。
- 系统字体 130%/200%、TalkBack、横屏/宽屏、动态色至少三组壁纸色、真实设备未纳入本次矩阵；截图为模拟器渲染。
- `appLockSupport == UNAVAILABLE` 分支、拍照会话中导入导出/片段文件夹的 disabled 状态为代码审查等价，未在该分支实机触发。
- 运行 `:app:connectedDebugAndroidTest` 会卸载 APK 并清空应用数据，后续验证前需重装/恢复设置。

## 剩余事项

- P1（Minor）：旧字符串 `R.string.ui_settings_option_saveFolder_action_select_label` 在迁移后不再被引用，可后续清理或复用。
- 第三方设置弹窗逐项适配/替换、全页面字体与读屏矩阵、性能采样仍按[统一 UI 设计计划](../plans/unified-ui-design-2026-10-25.md)第 8 节继续验收。
- 本次不授权发布、版本升级或 Git 推送。
