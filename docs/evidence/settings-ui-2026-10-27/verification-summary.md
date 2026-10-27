# 设置页统一 UI 改造 — 独立验证摘要

- 验证日期：2026-10-27（模拟器内部时钟为 UTC 08:2x）
- 验证者：settings-verifier（独立验收，非实现者）
- 工作区：`T:\Github\Alibi`（git 工作区未提交）
- 被测状态：设置页统一 UI 实现完成、未提交的工作区（26 个已跟踪文件变更 + 新增 `SettingsRows.kt`）
- 结论：**通过（PASS）**。构建/单测/连接测试/lint 全绿（lint error=0），两页设计 token 像素级一致，深浅色、弹窗/sheet/开关/导航/高级分组/持久化、录制中告警均实测通过；未发现阻断性缺陷。仅有 1 个非阻断的旧字符串孤儿化警告（见问题列表）。

## 1. 环境

| 项 | 值 |
| --- | --- |
| 模拟器 | `emulator-5580`（Pixel9_API36，Android 16 / API 36，adb 显式 `-s emulator-5580`） |
| 屏幕 | 1080×2424 px，density 420（2.625×），系统语言中文，系统 night mode=1 |
| 应用包 | `com.zgybeep.alibi.debug`，Version 0.6.1 (18) |
| APK | `app/build/outputs/apk/debug/app-x86_64-debug.apk`（60,680,492 B，`adb install -r` Success） |
| 数据基线（首次安装+onboarding 后） | `{"hasSeenOnboarding":true,"maxDuration":1800000,"saveFolder":"_'media"}` |
| 验证结束时数据 | 与基线完全一致（见 §4 恢复） |

> 说明：`:app:connectedDebugAndroidTest` 结束后 AGP 会卸载被测 APK，**应用数据被清空**（任务提示“保留应用数据”实际不成立）。因此验证前重新 `install -r` 并重新完成 onboarding，之后所有状态均相对上述基线记录并恢复。

## 2. 自动化命令与真实数字

Gradle 任务逐个串行执行，无并发。

### 2.1 构建
`./gradlew :app:assembleDebug --console=plain` → `BUILD SUCCESSFUL`（全 up-to-date，APK 有效）。

### 2.2 单元测试
`./gradlew :app:testDebugUnitTest --console=plain` → `BUILD SUCCESSFUL`。
解析 `app/build/test-results/testDebugUnitTest/*.xml`（5 个结果文件）：

| 测试类 | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| app.myzel394.alibi.AppSettingsPhotoCompatibilityTest | 6 | 0 | 0 | 0 |
| app.myzel394.alibi.ExampleUnitTest | 3 | 0 | 0 | 0 |
| app.myzel394.alibi.PhotoCaptureSessionStateTest | 4 | 0 | 0 | 0 |
| app.myzel394.alibi.PhotoNamingTest | 2 | 0 | 0 | 0 |
| app.myzel394.alibi.PhotoRepositoryCompatibilityTest | 1 | 0 | 0 | 0 |
| **合计** | **16** | **0** | **0** | **0**（16 passed） |

### 2.3 连接测试（模拟器）
`./gradlew :app:connectedDebugAndroidTest --console=plain` → `BUILD SUCCESSFUL`。
Gradle 输出：`Starting 4 tests on Pixel9_API36(AVD) - 16` / `Finished 4 tests`。
解析 `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel9_API36(AVD) - 16-_app-.xml`：

| 测试类 | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| app.myzel394.alibi.ExampleInstrumentedTest | 4 | 0 | 0 | 0 |

### 2.4 Lint
`./gradlew :app:lintDebug --console=plain` → `BUILD SUCCESSFUL`。
报告：`app/build/reports/lint-results-debug.xml`（另有 .html/.txt，16:14 生成）：

| 严重级 | 数量 |
| --- | --- |
| Error / Fatal | **0** |
| Warning | 191 |
| Information | 2 |

Warning 构成（全部为既有工程告警，新增 `SettingsRows.kt` 上无任何 lint 问题）：
`TypographyEllipsis 92`、`UnusedResources 28`、`GradleDependency 22`、`UnusedAttribute 12`、`UseKtx 9`、`ComposableNaming 7`、`VectorRaster 5`、`ConfigurationScreenWidthHeight 3`、`ModifierParameter 3`、`Typos 2`、`PluralsCandidate 2`、其余各 1。

本次改动相关的新增告警只有 1 条：旧字符串 `R.string.ui_settings_option_saveFolder_action_select_label` 在迁移后不再被引用（HEAD 版 `SaveFolderTile.kt:265` 使用过），见问题列表 P1。

## 3. 视觉一致性量化（设置页 vs 相机设置页）

方法：`MSYS_NO_PATHCONV=1 adb -s emulator-5580 shell "uiautomator dump /sdcard/ui.xml"` + `adb pull` 解析 Compose 语义节点 bounds；截图用 `adb exec-out screencap`，用 Pillow 采样像素。

| 设计 token | 期望 | 设置页（浅/深） | 相机设置页（浅/深） | 结论 |
| --- | --- | --- | --- | --- |
| 页面内容左边距（20dp） | ≈53 px | 53 / 53 | 53 / 53（且面板描边像素位于 x=53,54） | 一致 ✓ |
| 面板左边界（20dp 边距） | ≈53 px | 53 / 53（整宽可点击行 bounds 左缘） | 53 / 53（描边像素采样） | 一致 ✓ |
| 分区标题 x（+4dp） | ≈64 px | 64 / 64（“外观”“录制”） | 64 / 64（“拍摄”） | 一致 ✓ |
| 行内标题 x（+16dp） | ≈95 px | 95 / 95（“主题”“最大时长”…） | 95 / 95（“相机”“拍摄策略”…） | 一致 ✓ |
| 小标题栏（非大号折叠标题） | 返回箭头与标题同一行、无 LargeTopAppBar | 返回 [43,195][106,258]，标题 [148,185][262,267] | 返回 [43,195][106,258]，标题 [148,185][376,267] | 一致 ✓（标题起点 x=148、顶部 y=185 完全相同；两页均用共享 `AlibiTopAppBar`，标题内保留运行状态点） |
| 分组间距 16dp | ≈42 px | 说明文字底 403 → 下一分区标题顶 445 = 42 px | 页面同样由 `Arrangement.spacedBy(16.dp)` 实现 | 一致 ✓ |
| 面板圆角 | 24dp | `ui/theme/Shape.kt:11` `large = RoundedCornerShape(24.dp)`；截图四角为圆弧 | 同左 | 一致 ✓ |
| 面板描边 | outlineVariant 50% | 浅色 x=53,54 像素 (221,220,229) vs 页面底 (250,248,255)；深色 (48,48,56) vs (18,19,24) | 深色 (48,48,56) vs 底 (18,19,24)，浅色同设置页 | 存在 ✓ |
| 行间隔线 | outlineVariant | 浅色 y=1404/1405 采样 (197,198,208)；深色 (69,70,79) | 同组件 `HorizontalDivider()`（面板行间） | 存在 ✓ |
| 深/浅色实际生效 | — | 浅色页面底亮度 248.9；深色 19.1（截图采样） | 深色页底亮度 19.1 | ✓ |

证据（`docs/evidence/settings-ui-2026-10-27/`）：
`verify-settings-light.png|xml`（设置页浅色）、`verify-settings-dark.png|xml`（设置页深色）、`verify-camera-light.png|xml`（相机设置页浅色）、`verify-camera-dark.png|xml`（相机设置页深色）、`verify-settings-light-mid.png|xml`（设置页中部浅色）、`verify-notifications-security-dark.png`。

## 4. 交互回归实测

| 类型 | 操作 | 结果 | 证据 |
| --- | --- | --- | --- |
| 值行·数字弹窗 | 设置页“最大时长” | 打开数字时间选择弹窗（00h 30m，取消/确定），取消后关闭 | `verify-maxduration-dialog.png|xml` |
| 值行·底部 sheet | “文件名格式” | 打开底部 sheet：绝对开始 / 录制开始 / 节省时间；返回键关闭 | `verify-filename-sheet.png|xml` |
| 值行·底部 sheet | “片段文件夹” | 打开底部 sheet：使用加密的内部存储 / 使用 DCIM 文件夹 / 选择自定义位置；返回键关闭 | `verify-savefolder-sheet.png|xml` |
| 值行·列表弹窗 | “语言” | 打开列表单选弹窗（德语/英语/土耳其语/中文…，取消/确定）；未改变语言 | `verify-language-dialog.png|xml` |
| 开关行 | “减少动画”开 | 立即 ON；`settings.json` 出现 `"reduceMotionEnabled":true`；返回首页再进入设置仍为 ON（持久化） | `.tmp/verify/rm_on.xml`、`rm_reenter.xml`、§4.1 JSON |
| 开关行 | “使用系统配色”关→开 | OFF：`"dynamicColorsEnabled":false` 且开关关闭；恢复 ON 后字段回到默认（与基线一致） | `dc_off.xml`、`dc_on.xml`、§4.1 |
| 高级开关 | “高级设置”开 | 音频录制/视频录制/拍照/设置备份分组立即出现；`settings.json` 出现 `"showAdvancedSettings":true`；退出再入设置仍展开 | `verify-advanced-on.png|xml`、`verify-advanced-sections.png|xml` |
| 高级开关 | “高级设置”关 | 上述高级分组全部消失；字段回到默认 false（JSON 中不再出现） | `verify-advanced-off.png|xml` |
| 导航行 | “自定义通知” | 进入子页（“不要暴露自己”/“创建自定义通知”），返回键回到设置页 | `verify-nav-custom-notification.png|xml` |
| 导航行 | “关于 Alibi” | 进入子页（Version 0.6.1 (18)/支持 Alibi），返回键回到设置页 | `verify-nav-about.png|xml` |
| 运行中告警 | 开始音频录制后打开设置 | 顶部出现告警“您正在录制中 / 您的更改将在下次启动录制时被应用”（`isRuntimeActive` 条件生效） | `verify-session-warning.png|xml` |
| 应用锁条件 | 设备未设置锁屏（NONE_ENROLLED） | 显示“启用应用锁”及警告“请先选择密码或生物识别解锁方式”，开关 disabled（与旧实现一致；UNAVAILABLE 分支为纯代码审查） | `verify-notifications-security-dark.png` |
| 稳定性 | 全流程操作后检查 logcat | `adb logcat -b all -d` 过滤 `FATAL EXCEPTION|ANR in|E AndroidRuntime`：**0 条**（crash buffer 亦为空） | `.tmp/verify/logcat-crash-check.txt`（0 行） |

### 4.1 持久化实测（`run-as cat files/datastore/settings.json`）

- 基线：`{"hasSeenOnboarding":true,"maxDuration":1800000,"saveFolder":"_'media"}`
- 减少动画 ON：`…,"reduceMotionEnabled":true,…`
- 主题切深色：`…,"theme":"DARK",…`
- 高级设置 ON：`…,"showAdvancedSettings":true,…`
- 使用系统配色 OFF：`…,"dynamicColorsEnabled":false,…`
- 恢复后（验证结束）：`{"hasSeenOnboarding":true,"maxDuration":1800000,"saveFolder":"_'media"}`（主题回“跟随系统”、减少动画关、系统配色恢复默认、高级设置关；与基线逐字节一致）

测试产生的临时录制已用“删除录制→删除”丢弃，未落下文件；App 停留在首页。

## 5. 代码审查

### 5.1 改动范围
- 实现改动共 26 个已跟踪文件 + 1 个新文件，全部落在设置页范围：
  - `Tiles/` 16 个 tile 修改 + `AboutTile.kt`/`SectionTitle.kt` 删除；`atoms/SettingsTile.kt` 删除
  - `SettingsScreen/atoms/ThemeSelector.kt`、`InAppLanguagePicker.kt`
  - `components/atoms/ExampleListRoulette.kt`、新增 `components/atoms/SettingsRows.kt`
  - `screens/SettingsScreen.kt`、`screens/PhotoCaptureSettingsScreen.kt`
  - `res/values/strings.xml`、`res/values-zh/strings.xml`（各 +14 行、0 删除）
  - **无**其它 app 源码/资源文件被改。
- 验证期间 Lead 另行修改了文档 `README.md` 与 `docs/plans/unified-ui-design-2026-10-25.md`（更新截图引用/方案状态，属文档整理，非本次实现范围）；验证者未修改任何源码/strings/README/评审文档。
- `grep -rn "SettingsTile" app/src/main/java` → 空；`SectionTitle`、`AboutTile` → 空。

### 5.2 字符串资源
- `values` 与 `values-zh` 均为 381 个唯一 `<string>`，两侧键集合完全一致（差集为空）；XML 可解析（`ElementTree` 通过，且 `processDebugResources` 成功）。
- 新增 14 个 `settings_*` 键在 values/values-zh 同时存在；14 个新键在 Kotlin 中全部有引用，**无未使用新增键**。
- 逐键复查：`settings_page_description / settings_appearance_description / settings_theme_title / settings_theme_description / settings_section_recording(_description) / settings_section_notifications_security(_description) / settings_section_advanced(_description) / settings_advanced_description / settings_section_backup(_description) / settings_section_about` 均被 `stringResource(R.string.…)` 使用。

### 5.3 Tiles 逐文件等价性（16 个修改文件全查）
- 对话框调用全部保持原样：MaxKeeper `DurationDialog/InputDialog/ListDialog/Sheet`、SaveFolder 的文件夹选择/帮助 sheet、FilenameFormat 的 sheet、ImportExport 的 AlertDialog、EnableAppLock 的 `AppLockHelper.authenticate` 等，参数与回调未变。
- 数据写入全部保持原逻辑/原 setter：`setMaxDuration/setIntervalDuration/setDeleteRecordingsImmediately(...not())/setFilenameFormat/setSaveFolder/setAudioRecorderSettings(...)/setVideoRecorderSettings(...)/setShowAllMicrophones`；EnableAppLock 仍为“认证成功后才切换 `setAppLockSettings(default / null)`”；ImportExport 仍为 `exportToString`+临时文件/`openFile(json)`。
- 可见性/可用性条件保持：`SaveFolderTile(enabled=!photoCapture.isInSession)`、`ImportExport(enabled=!photoCapture.isInSession)` 原样传入；EnableAppLock 开关 `enabled = supportType == AVAILABLE`；NONE_ENROLLED 警告仍显示；`appLockSupport == UNAVAILABLE` 早退分支未变。
- 交互差异（均为增强、非回归）：值行由“仅右侧数值按钮可点”改为整行 `onClick` 打开同一对话框；`AlibiSwitchSetting` 整行可切换（Switch 回调仍写同一值）；`ExampleListRoulette` 由 LazyRow 文本按钮改为 FlowRow AssistChip（回调仍为 `onItemSelected`，且该组件仅被设置页 Tiles 使用）；SaveFolder 原“已选择：X”文本改由行尾 value 展示（信息未丢失）。
- 相机设置页：删除私有 `ChoiceSetting/SwitchSetting`，14 处调用全部改为共享 `AlibiChoiceSetting/AlibiSwitchSetting` 且 `enabled` 参数全部保留（含镜像开关 `enabled && lensFacing==...` 条件）；共享组件与原私有实现逻辑一致（着色/布局相同，仅补 `fillMaxWidth`、整行 toggleable）。

### 5.4 SettingsScreen 条件与旧实现对照
| 条件 | 旧实现 | 新实现 | 等价 |
| --- | --- | --- | --- |
| 录制中告警 | `audioRecorder.isInRecording || videoRecorder.isInRecording || photoCapture.isInSession` → MessageBox(WARNING) | 同一 OR 表达式 → `AlibiSettingsNotice`（同一 MessageBox WARNING 封装） | ✓ |
| 应用锁 | 始终组合 `EnableAppLockTile`，tile 内 `UNAVAILABLE` 早退 | `if (supportType !== UNAVAILABLE)` 组合，tile 早退保留 | ✓ |
| 高级分组 | `AnimatedVisibility(settings.showAdvancedSettings)` | `if (settings.showAdvancedSettings)` 条件 item | ✓ |
| 高级开关写入 | `it.setShowAdvancedSettings(it.showAdvancedSettings.not())` | `it.setShowAdvancedSettings(enabled)`（toggleable 新值） | ✓ |
| SaveFolder enabled | `enabled = !photoCapture.isInSession` | 同上 | ✓ |
| ImportExport enabled | `enabled = !photoCapture.isInSession` | 同上 | ✓ |
| 相机设置入口 | `FilledTonalButton(onClick=onNavigateToPhotoCaptureSettings)` | `AlibiSettingRow(onClick=onNavigateToPhotoCaptureSettings)`（同一导航回调） | ✓ |
| 关于入口 | `AboutTile(onNavigateToAboutScreen)`（仅导航） | `AlibiSettingRow(onClick=onNavigateToAboutScreen)` | ✓ |
| 标题栏运行状态 | LargeTopAppBar + TitleWithRunningStatus | AlibiTopAppBar +（内部仍用 TitleWithRunningStatus，实测运行点可见） | ✓（视觉按需求改为小标题栏） |

## 6. 问题列表（均非阻断）

- **P1（Minor，建议后续清理）**：迁移到共享组件后，旧字符串 `R.string.ui_settings_option_saveFolder_action_select_label`（原 `SaveFolderTile` 右侧“选择”按钮文案，HEAD 版 `SaveFolderTile.kt:265` 引用）变为未使用，lint 报 `UnusedResources` warning（该键仍存在于各 locale）。不影响功能/构建/测试，可后续删除或复用。
- **P2（Info，环境/流程）**：`:app:connectedDebugAndroidTest` 运行后 AGP 卸载了 debug APK，应用数据被清空，与任务提示“保留应用数据”不符；本次验证重装 APK、重新完成 onboarding 后记录并恢复基线数据。若后续验收依赖既有设置状态，需在跑连接测试前备份 `files/datastore/settings.json`。
- **P3（Info，覆盖边界）**：`appLockSupport == UNAVAILABLE` 分支、以及“导入/导出、片段文件夹在拍照会话进行中禁用”的状态为代码审查等价（本机为 NONE_ENROLLED 且未启动拍照会话，未做该分支的实机触发）；录制中告警已实机验证。
- 未发现 FATAL EXCEPTION / ANR；未发现视觉 token 偏差、功能行为回归或新旧页面不一致。

## 7. 复现命令

```bash
# 构建 / 安装
./gradlew :app:assembleDebug --console=plain
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-x86_64-debug.apk

# 测试（串行执行）
./gradlew :app:testDebugUnitTest --console=plain        # 16 tests / 0 fail
./gradlew :app:connectedDebugAndroidTest --console=plain # 4 tests / 0 fail
./gradlew :app:lintDebug --console=plain                 # 0 error / 191 warning

# UI dump / 截图
MSYS_NO_PATHCONV=1 adb -s emulator-5580 shell "uiautomator dump /sdcard/ui.xml"
MSYS_NO_PATHCONV=1 adb -s emulator-5580 pull /sdcard/ui.xml ./.tmp/ui.xml
adb -s emulator-5580 exec-out screencap -p > out.png

# 读取持久化状态
adb -s emulator-5580 shell "run-as com.zgybeep.alibi.debug cat files/datastore/settings.json"
```

## 8. 证据清单（`docs/evidence/settings-ui-2026-10-27/`）

| 文件 | 说明 |
| --- | --- |
| `verify-settings-light.png|xml` | 设置页浅色（设计 token 基准） |
| `verify-settings-dark.png|xml` | 设置页深色 |
| `verify-camera-light.png|xml` | 相机设置页浅色 |
| `verify-camera-dark.png|xml` | 相机设置页深色 |
| `verify-settings-light-mid.png|xml` | 设置页中部（通知与安全/高级/关于） |
| `verify-advanced-on.png|xml` | 高级开关 ON 后分组展开 |
| `verify-advanced-sections.png|xml` | 高级分组（视频/拍照/备份） |
| `verify-advanced-off.png|xml` | 高级开关 OFF 后分组消失 |
| `verify-session-warning.png|xml` | 录制中打开设置的告警 |
| `verify-maxduration-dialog.png|xml` | 最大时长数字弹窗 |
| `verify-filename-sheet.png|xml` | 文件名格式底部 sheet |
| `verify-savefolder-sheet.png|xml` | 片段文件夹底部 sheet |
| `verify-language-dialog.png|xml` | 语言列表弹窗 |
| `verify-nav-custom-notification.png|xml` | 自定义通知子页 |
| `verify-nav-about.png|xml` | 关于 Alibi 子页 |
| `verify-notifications-security-dark.png` | 通知与安全分区（应用锁 NONE_ENROLLED 警告） |

其它：`.tmp/verify/logcat-crash-check.txt`（0 行）、`.tmp/verify/*.xml`（开关/持久化中间 dump）。
