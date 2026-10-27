# 变焦延迟优化与悬浮快门变焦证据

对应报告：[变焦延迟优化与悬浮快门变焦验收](../../reviews/zoom-overlay-2026-10-27.md)。

环境：Android 16 / API 36，模拟器 `Pixel9_API36`，固定序列号 `emulator-5580`；1080×2424 px，density 420（2.625×）；系统语言中文；应用包 `com.zgybeep.alibi.debug`；版本 0.6.1 (18)；CameraX 1.3.4。模拟器相机报告 `zoomRatioRange = [1.0, 10.0]`。

## 关键证据

| 文件 | 说明 |
| --- | --- |
| `floating-overlay.png` | 桌面上的悬浮快门（READY 状态，56 dp，窗口 `(891,739) 147×147`）。 |
| `floating-panel.png` | 长按展开后的动作面板：顶部 − / 1.0× / ＋ 与倍率滑杆，下面是打开 Alibi、停止、关闭；窗口 `(618,739) 462×581`，高度按内容自适应。 |
| `floating-panel-zoomed.png` | 连续点击 ＋ 后 2.4×；滑杆同步移动。 |
| `floating-panel-max.png` | 到设备上限 10.0×，＋ 按钮禁用（alpha 0.45），滑杆在右端。 |
| `floating-panel-reset.png` | 点击中间倍率文字重置回 1.0×，− 按钮禁用。 |
| `floating-collapsed.png` | 点击“关闭”后面板收起，窗口恢复 56 dp（`147×147`）。 |
| `overlay-zoom-logcat.txt` | 每次悬浮窗变焦的 `setZoomRatio` future 完成日志与耗时（模拟器相机 213–270 ms/次）。 |
| `zoom-test-logcat.txt` | 自动化测试 telemetry：黑屏双指捏合与高频变焦合并、rememberZoom 持久化结果。 |
| `verification-summary.md` | 独立验证者（zoom-verifier）的验收摘要：真实测试数字、telemetry、悬浮窗手动验证与代码审查。 |
| `verify-*.png` / `verify-*.txt` | 独立验证者采集的悬浮面板、拖动/停止/重启、日志与报告。 |

## 自动化结果

| 检查 | 结果 |
| --- | --- |
| `:app:assembleDebug` | 通过 |
| JVM 单元测试 | 21 / 21 通过（原有 16 + `ZoomRequestQueueTest` 5） |
| Android instrumentation | 7 / 7 通过（原有 4 + 新增 `PhotoZoomInstrumentedTest` 3） |
| lint | 0 error / 0 fatal（188 warning，均为既有工程告警） |
| 双指捏合 | Compose 测试在真实黑屏页面注入捏合：`before=1.0 after=3.18`，24 次手势采样只产生 2 次 CameraX 请求。 |
| 高频变焦合并 | 60 次 60 Hz 变焦输入 → 6–7 次 CameraX 请求（59 次被最新值替换），最终倍率 5.89×，最新请求距手势输入 33–183 ms（随相机管线时序波动，量级一致）。独立复跑见 `verification-summary.md`。 |
| rememberZoom | 悬浮窗会话开启“记住变焦”后变焦到 1.8×，DataStore 中 `lastZoomRatio=1.8`。 |

`zoom-test-logcat.txt` 关键行：

```text
PhotoZoomTest: pinch before=1.0 after=3.1818178 inputs=24 applies=2 stalenessMs=236
PhotoZoomTest: floating overlay rememberZoom stored=1.8
PhotoZoomTest: coalescing inputs=60 applies=7 coalesced=59 finalRatio=5.8915944 stalenessMs=183
# 独立复跑：inputs=60 applies=6 coalesced=59 stalenessMs=33
```

`overlay-zoom-logcat.txt` 关键行（悬浮面板 −/＋ 逐次操作）：

```text
PhotoCaptureService: zoom applied target=1.25 latencyMs=270
PhotoCaptureService: zoom applied target=1.5625 latencyMs=220
...
PhotoCaptureService: zoom applied target=10.0 latencyMs=220
PhotoCaptureService: zoom applied target=1.0 latencyMs=244
```

## 延迟优化说明

旧实现在每个指针事件中直接调用一次 `CameraControl.setZoomRatio()`，并 `publish()` 全量会话状态。CameraX 1.3.4 的 `ZoomControl.setZoomRatio()` 每次都会向相机控制执行器提交任务，并触发 `updateSessionConfigSynchronous()`，future 要等相机结果回传才完成；连续双指手势会堆积大量请求，预览因此落后手指。

现在服务端使用 `ZoomRequestQueue` 做 latest-wins 合并：同一时刻最多一个 CameraX 缩放请求在途，完成时只应用最新目标；UI 侧的缩放倍率独立为 `PhotoCaptureModel.zoomRatio`，不再为每次缩放重发全量状态或重启页面效果。日志中的“60 次输入 → 7 次相机请求”即合并效果；单次相机请求耗时受模拟器相机管线限制（213–283 ms），真机延迟与手感仍需物理设备验证。

## 复现方法

```bash
# 构建与安装
./gradlew :app:assembleDebug --console=plain
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-x86_64-debug.apk

# 悬浮拍照（需相机与悬浮窗权限）
adb -s emulator-5580 shell pm grant com.zgybeep.alibi.debug android.permission.CAMERA
adb -s emulator-5580 shell appops set com.zgybeep.alibi.debug SYSTEM_ALERT_WINDOW allow
adb -s emulator-5580 shell am start -a com.zgybeep.alibi.debug.shortcut.TAKE_PHOTO \
  -n com.zgybeep.alibi.debug/app.myzel394.alibi.ShortcutActivity
# 长按悬浮按钮（初始位置约 964,812）展开面板，再点 ＋ / − / 倍率文字 / 关闭

# 自动化
./gradlew :app:testDebugUnitTest --console=plain        # 21 tests
./gradlew :app:connectedDebugAndroidTest --console=plain # 7 tests
./gradlew :app:lintDebug --console=plain                 # 0 error / 188 warning
```

## 边界

- 截图与延迟数字均为模拟器结果；模拟器相机每次缩放请求完成耗时 213–283 ms，不能代表物理手机的手感或帧率。真机上的双指跟手度、90/120 Hz 表现仍按计划 U05 验收。
- 悬浮面板截图背景为系统桌面（深色壁纸）；未覆盖大字体、TalkBack 朗读、横屏或厂商后台限制场景。
- `:app:connectedDebugAndroidTest` 会卸载 debug APK 并清空应用数据；重装后需要重新授权。
