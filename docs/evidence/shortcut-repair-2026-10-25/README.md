# 桌面快捷启动修复验收证据

对应报告：[桌面快捷启动修复验收报告](../../reviews/shortcut-repair-2026-10-25.md)。

环境为 `Pixel9_API36` / Android 16 / API 36 / x86_64，设备序列号固定为 `emulator-5580`。脚本先检查 `ro.kernel.qemu=1`，不会自动选择或操作物理手机。

## 复现

```powershell
python docs/evidence/shortcut-repair-2026-10-25/run-repair-audit.py
```

脚本会重装当前 Debug 和 Release x86_64 APK，修改模拟器内测试应用权限和 DataStore 设置，并在异常目录测试后恢复最小有效设置。设备命令均明确指定 `emulator-5580`。

## 结果文件

| 文件 | 内容 |
| --- | --- |
| `repair-results.json` | 逐项设备回归结果 |
| `repair-summary.txt` | 通过和失败计数 |
| `instrumented-tests.txt` | 3 项 Android 设备测试结果 |
| `debug-test-checks.log` | 最终 Debug 构建、16 项单元测试和 lint |
| `release-build-final.log` | 当前源码的最终 Release 构建 |
| `debug-main-launch.txt` / `release-main-launch.txt` | 两种构建的首页启动 |
| `*-crash.txt` | 各回归场景 crash buffer，应为空 |
| `audio-cold-services.txt` / `audio-cold-files.txt` | 后台录音服务和 AAC 文件 |
| `audio-running-ui.png` / `.xml` | 运行绿点及无障碍语义 |
| `audio-stopped-ui.png` / `.xml` | 服务停止后绿点隐藏 |
| `black-photo-switch-windows.txt` | 黑屏会话切换为悬浮窗口 |
| `photo-cold-screen.png` | 后台悬浮拍照界面 |
| `photo-tap-files.txt` / `photo-tap-logcat.txt` | JPEG 保存及耗时 |
| `invalid-folder-crash.txt` | 异常目录恢复后无 crash |
| `final-release-main-launch.txt` / `final-release-crash.txt` | 最终签名 Release 冷启动复验 |

`*-crash.txt` 文件为空表示没有崩溃记录；脚本同时检查 `FATAL EXCEPTION` 和主界面恢复结果，不能只凭启动命令返回 `Status: ok` 判定通过。
