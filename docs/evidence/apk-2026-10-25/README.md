# APK 构建记录

日期：2026-10-25

| 产物 | 包名 | 版本 | 大小 | SHA-256 |
| --- | --- | --- | ---: | --- |
| Debug universal | `app.myzel394.alibi.debug` | 0.5.3 (16) | 145,511,957 B / 138.77 MiB | `0735E93068C23E492D9F32F786DAFEAB011F4897AD7D1975B44637F9C70BF4C4` |
| Release universal | `app.myzel394.alibi` | 0.5.3 (16) | 125,085,001 B / 119.29 MiB | `822EBB436D039ABEE43811267050729A2C2E68A4FFBD5165F0E83FD4D76C2DC4` |

- Debug 使用 Android Debug 证书，APK Signature Scheme v2 验证通过。
- Release 已完成 R8 混淆和资源压缩，但仓库缺少 `key.properties`，因此是 **未签名** Release APK。
- 两个 APK 均为 universal，包含可用 ABI；`minSdk 24`、`targetSdk 34`、`compileSdk 36`。

签名验证和完整 Gradle 日志位于当前目录。Release 未使用 Debug 证书签名，避免冒充正式发布身份或破坏后续升级密钥。
