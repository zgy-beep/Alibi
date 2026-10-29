# Alibi 0.6.9 (26) 验收记录

日期：2026-10-29。真机：华为 Mate 70 Pro / PLR-AL50，Android 12 / API 31，机主 User 0。
本次修复三条共用变焦路径：PhotoCaptureService、录像准备 CameraPreview、VideoRecorderService。

## 原因与修复

旧队列等待 setZoomRatio Future 的 capture-result 确认后才提交下一倍率。0.6.8 实测
60 次输入仅产生 4 次提交，完成耗时 327–735 ms，倍率大步跳变；原始诊断见
`../zoom-stutter-20261029/`。这些耗时是请求完成耗时，不能与下面的传感器帧间隔当作同一指标比较。

新队列按单调时钟每 32 ms 接纳当前最新缓动倍率，不等待硬件确认。绝对倍率及收尾帧立即提交，
CameraX 负责替代未完成的旧请求。旧 Future 完成回调不再驱动队列，切换相机后不会回放旧目标。
相对输入仍在 target 上累积，不全量 publish 会话状态；取景器仍以镜头 ID 做 key。
诊断回调仅安装在 Debug 构建，Release 的同名辅助类为空实现。

## 构建

- `:app:testDebugUnitTest`：52 tests，0 failure / error / skipped。
- `:app:lintDebug`：0 error / fatal，224 warning、2 information（现有全项目告警）。
- `:app:assembleRelease`：成功，R8 混淆及原发布证书签名。
- 最终生产源码全量检查见 `build-checks.txt`；随后仅改进测试启动等待，重新构建测试及 lint，见 `test-startup-build.txt`。
- 四个架构包均通过 apksigner，证书 SHA-256：
  `5f9d20f64ae2b40b880cb8719afccf71ff62777ad76ef9565cc33a2ca9fcb334`。
- 每包大小、SHA-256 见 `artifacts.json`；签名见 `signature.txt`。不发布 universal APK。

## 设备缩放测试

最终源码运行 PhotoZoomInstrumentedTest（5）、VideoZoomInstrumentedTest（3）、
StandardCameraInstrumentedTest（2）：**OK (10 tests)，29.446 s**。
原始测试与日志见 `instrumentation.txt`、`instrumentation-logcat.txt`。

以下数据来自 Camera2 SENSOR_TIMESTAMP + CONTROL_ZOOM_RATIO / SCALER_CROP_REGION，
不使用提前更新的 UI 或 CameraInfo.zoomState 充当硬件生效证据。
变化数包含开始基准帧；间隔及最大步长统计移动区间中央 80%，排除启动和停稳尾部。

| 路径 | 记录帧 | 倍率变化数 | 中央变化数 | P95 间隔 ms | 最大相对步长 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 黑屏拍照 | 54 | 44 | 31 | 64.097 | 4.17% |
| 悬浮拍照 | 60 | 46 | 31 | 74.097 | 4.48% |
| 标准拍照 | 56 | 46 | 34 | 37.049 | 6.27% |
| 录像准备取景器（真实双指输入） | 93 | 75 | 60 | 64.097 | 2.40% |
| 正在录像 | 54 | 47 | 35 | 37.049 | 4.14% |

门槛：中央至少 12 个实际变化，P95 ≤ 150 ms，最大相对步长 < 15%，
最后硬件倍率距目标 < 0.01。拍照和录制相对输入目标 2.5x，实际 2.4990847x；
取景器实际双指输入经过触摸 slop，目标约 2.10x，实际 2.0969284x，断言比较其实际手势目标。
每条路径的逐帧 CSV 和摘要保存在 `zoom-frames/`。

额外保留悬浮绝对倍率/倍率记忆、初始录制倍率、标准相机对焦/镜头重绑、
录制中移除 Preview 仍继续录制的回归。JVM 测试覆盖慢/缺失确认、32 ms 合并、
反向输入、绝对输入、精确收尾、相机重置与无效输入。

测试完善过程中遇到的两个问题：

- Compose 合成 pinch 只推进触摸事件时间，不推进真实相机时钟；改为按真实时间逐步发送双指移动，
  同步推进 Compose 测试时钟，并等待硬件最终收敛，保留帧连续性断言。
- 一次复跑在 Activity 已 RESUMED、Compose 根节点尚未注册时提前查询失败；原始记录保留为
  `instrumentation-startup-race.txt`。改为允许空根节点并在原 15 s 上限内继续等待，
  不跳过测试、不放宽变焦断言。最终全套重新通过。

## 正式 APK 烟雾验收

正式 arm64 APK 通过 `adb install --user 0 -r` 安装；版本及用户状态见 `device-package.txt`。
桌面 Alibi 图标目视及 Launcher UI 节点确认，force-stop 后点击图标冷启动成功。
Launcher 查询 MainActivity 的 icon=0x7f0d0000，非 0x0，见 `launcher.txt`。
Manifest 显式声明 activity icon/roundIcon，mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi 共 10 个普通/圆形 PNG 均存在。
此前“桌面无图标”原因是只安装在隐私 User 10，机主空间未安装；见 `../apk-0.6.8/launcher-20261029.md`。

已实际操作：

- 标准相机拍照，最近照片出现新 JPEG；录像开始、计时、停止保存出现成功提示。
- 录音开始、计时、保存回首页出现成功提示。
- 音频、视频、相机、存储与备份二级页面打开无崩溃；修改音频最大时长、视频目标 30 fps、
  拍摄策略后复查页面值，操作完成恢复原设置。
- 关于页面显示 v0.6.9 (26)，点击出现“已复制版本信息到剪贴板”；隐私承诺正常显示；
  GitHub 入口打开系统 GitHub 应用（`about-link.txt`）。
- 最终正式包悬浮拍照：后台/桌面显示快门，长按展开面板，点击 + 后 1.0× → 1.3×，点击 − 回到 1.0×；关闭面板、点击快门保存新 JPEG、长按后停止均成功。
  应用前台会主动隐藏悬浮窗；桌面截图因含个人应用信息不提交。实际相机变焦及倍率记忆另有上述真机测试覆盖。

应用页面、设置、关于、拍照、录音和录像截图为本目录 PNG。个人桌面、通知横幅、系统账户画面仅保存在
本机 .tmp，不提交。没有删除已有录音/照片或清空应用数据。

复查 PhotoCaptureService 的 ZSL、扩展回退、分析流、软件 JPEG 判定、flash、
内部存储/MediaStore 保存分支：本次仅改动变焦发送及 Debug 诊断接入，这些策略保留；
真机不支持夜景/HDR 扩展，标准管线正常绑定。本次未新增画质策略，未重新做完整 50MP 画质专项。

## 范围和限制

帧统计证明传感器/录制会话中的倍率连续推进；不是显示合成器逐帧呈现时延测试，也未逐帧解码成片做独立画质评估。
仅一台华为真机，未声称其他设备或横屏已验收。

测试后仅移除机主空间的临时 Debug/测试包，恢复原充电常亮设置与 256 KiB logcat 缓冲；正式版保留安装。
