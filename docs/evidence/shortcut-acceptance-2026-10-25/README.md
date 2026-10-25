# 桌面快捷启动验收证据 · 2026-10-25

对应报告：[桌面快捷启动与运行状态验收报告](../../reviews/shortcut-acceptance-2026-10-25.md)。

验收环境为 `Pixel9_API36` / Android 16 / API 36 / x86_64，设备序列号明确指定为 `emulator-5580`，`ro.kernel.qemu=1`。未操作物理手机。

## 结果

- 当前工作区 Debug、Release 构建成功；16 项 JVM 测试强制重跑通过；lint 为 0 errors、189 warnings。
- 系统注册、Launcher 长按菜单、已授权后台录音、悬浮拍照文件保存和未知 Action 退出均有设备证据。
- Debug 与 Release 首页均因静态/动态快捷方式 ID 冲突而崩溃；权限缺失回退同样失败。
- 非 tree URI 保存目录会使快捷录音的服务连接回调崩溃；探针已恢复模拟器原设置。
- 应用内标题栏绿点与页面切换验收被首页崩溃阻塞，不能计为通过。

## 复现脚本

```powershell
# 先启动指定 AVD，并安装当前工作区的 Debug APK。
# 脚本会改变模拟器内的测试 App 权限，并在各用例间停止测试 App。
python docs/evidence/shortcut-acceptance-2026-10-25/run-emulator-audit.py

# 负向目录配置测试；finally 中恢复原模拟器 settings.json。
python docs/evidence/shortcut-acceptance-2026-10-25/probe-invalid-folder.py
```

脚本中的 ADB 路径为本机 `C:\Android\Sdk\platform-tools\adb.exe`。脚本在开始操作前检查模拟器属性，不会自动选择其他设备。

`run-emulator-audit.py` 会记录实际观察，不会把程序输出中的 `crashed=true` 转成非零脚本退出码。**脚本运行完成不等于功能验收通过**，应读取 `emulator-results.json` 和各场景的 crash / services / files 日志。

服务存在、没有 MainActivity、没有崩溃分别记录；这些字段不能单独证明相机已经 READY 或音频内容有效。拍照实际保存另由 `photo-tap-files.txt` 和 `photo-tap-logcat.txt` 证明。

## 主要文件

| 文件 | 内容 |
| --- | --- |
| `gradle-checks.log` / `unit-tests-rerun.log` | 构建与强制重跑单元测试 |
| `TEST-*.xml` / `lint-results-debug.txt` | 测试明细和完整 lint 结果 |
| `device-environment.txt` | 模拟器身份、Android 版本与主机 UTC 时间 |
| `main-launch-crash.txt` / `release-main-crash.txt` | 两种构建首页的 ID 冲突崩溃 |
| `missing-*-crash.txt` | 缺少权限后的回退崩溃 |
| `invalid-folder-crash.txt` | 异常目录连接回调崩溃 |
| `audio-*-services.txt` / `audio-*-files.txt` | 后台音频服务和文件列表 |
| `photo-tap-files.txt` / `photo-tap-logcat.txt` | 点击悬浮按钮后 JPEG 与索引保存 |
| `launcher-shortcut-menu.png` / `.xml` | Launcher 实际长按菜单 |
| `photo-cold-screen.png` | 桌面悬浮按钮及系统顶部相机标识 |
| `*-shortcuts-xmltree.txt` | Debug / Release 快捷入口目标资源 |
| `release-badging.txt` / `release-signature.txt` | Release 标识和签名验证 |
| `source-sha256.json` / `apk-sha256.json` | 被验收源码和 APK 指纹 |

Release 资源经过路径压缩，快捷方式 XML 在本次 APK 内的路径为 `res/Eq.xml`，实际资源名仍为 `xml/shortcuts`。

日志保留模拟器自身时间，环境文件额外记录主机 UTC 时间；报告日期采用 Asia/Shanghai。照片中的计时、壁纸和模拟相机内容不代表物理设备采集。
