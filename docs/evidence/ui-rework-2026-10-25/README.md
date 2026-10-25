# 修复与 UI 重构证据

对应报告：[修复与 UI 重构验收](../../reviews/repair-ui-implementation-2026-10-25.md)。

环境：Android 16 / API 36，`Pixel9_API36`，固定序列号 `emulator-5580`。所有 ADB 命令显式使用该序列号；未连接或操作物理设备。

## 关键证据

- `home-final.png` / `.xml`：App 数据初始化后的最终首页和 Compose 语义树。
- `photo-setup.png` / `.xml`：模式选择与单一主动作布局。
- `black-capture.png`：黑屏会话；画面保持纯黑，无应用侧拍照文字。
- `burst-capture-log.txt`：120 ms 间隔的 5 次点击均曝光和保存。
- `long-press-return.png`：黑屏长按后返回首页。
- `instrumented-tests.xml`：4 项 Android instrumentation 全部通过。
- `TEST-*.xml`：16 项 JVM 单元测试，全部通过。
- `lint-results-debug.txt`：完整 lint 输出。
- `apk-sha256.json`：最终 x86_64 Debug / Release APK 哈希。
- `release-signature.txt`：最终 Release 包签名证书。

快捷入口的 13 项回归位于 `docs/evidence/shortcut-repair-2026-10-25/repair-results.json`，本次使用当前源码构建包重新执行。

模拟器未测试真实相机画质、麦克风效果或震动幅度；这些结果不能代替真机验收。
