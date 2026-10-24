# 本轮验收证据

审查基线：HEAD c7f46514a71bc8a8b2d662572c5745c7cadbe463 加未提交工作区；源文件见 source-sha256.json。

| 文件 | 含义 |
| --- | --- |
| checks.log | 本轮强制重跑 JVM 单元测试及执行 lintDebug 的日志；无 APK 打包任务 |
| TEST-*.xml | 7 个测试通过，其中 1 个示例测试；不能证明设备性能或服务时序正确 |
| lint-results-debug.txt | 0 errors、162 warnings；本轮使用有效缓存，包含部分 lint 自定义规则跳过的提示 |
| material3-dependencies.log | 实际解析 Material 3 1.3.0 的依赖证据 |
| source-sha256.json | 210 个应用源码、资源和构建文件的指纹，用于识别审查对象 |

本轮没有设备帧时间、ANR trace、崩溃栈或性能录屏，不将缺失的设备测量记为通过。

报告见 [验收报告](../../reviews/current-version-acceptance-2026-10-24.md)，后续步骤见 [实施计划](../../plans/performance-m3-expressive-2026-10-24.md)。
