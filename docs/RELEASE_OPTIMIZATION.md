# DraARL Android Release 优化基线

更新时间：2026-08-10

## 构建配置

Release 使用 Android Gradle Plugin 9.3 的 `optimization.enable`，由 R8 同时执行代码压缩、混淆、优化和资源收缩。构建仅包含 `arm64-v8a`，未配置发布签名时生成可供静态检查的未签名 APK。

项目规则位于 `app/src/main/keepRules/release.keep`：

- 高德地图与搜索 SDK 以不带 consumer rules 的 JAR 发布，保留 `com.amap` 与 `com.autonavi` 的反射和 JNI 边界。
- `RnnoiseNative` 的二进制类名与 native 方法保持稳定，以匹配 `rnnoise_jni.cpp` 导出的 JNI 符号。
- 业务 DTO 使用手写 `JSONObject` 映射，不依赖反射序列化规则。

## 体积结果

对照组在提交 `49c87e6` 上关闭优化并执行 `clean assembleRelease`；实验组只启用 Release 优化与上述 keep rules 后执行 `assembleRelease`。两组使用相同源码、依赖、ABI 与本地地图 Key 配置。

| 指标 | 未压缩 | R8 + 资源收缩 | 变化 |
| --- | ---: | ---: | ---: |
| APK | 43,948,633 B | 32,028,495 B | -11,920,138 B（-27.12%） |
| DEX（APK 内压缩大小） | 14,341,077 B | 2,789,803 B | -80.55% |
| 资源（APK 内压缩大小） | 537,629 B | 188,662 B | -64.91% |
| Assets（APK 内压缩大小） | 2,895,373 B | 2,893,243 B | -0.07% |
| Native（APK 内大小） | 26,009,960 B | 26,009,960 B | 不变 |

优化后 APK 为 30.54 MiB，SHA-256 为 `0284A507FEC9FC26B5994413341B17FCA6BA23352D3107FDBEBCB4B33131D3D2`。Native 库占主要剩余体积，其中高德地图约 19.07 MiB，RNNoise 约 5.72 MiB。

生产源码基线 `9a68895` 的静态回归构建同样通过 `assembleRelease`：APK 为 32,062,557 B（30.58 MiB），SHA-256 为
`4369F0AAAD784F93E323DDA8FBCE751ACD1DB91D88DF366640E2385A504874A4`。与历史优化基线的体积差异来自后续业务与边界测试代码变化，未改变 R8/资源收缩配置。

## 静态验收

- `assembleRelease` 与 Release `lintVital` 通过，生成 `mapping.txt`、`seeds.txt`、`usage.txt` 和 `resources.txt`。
- 最终 APK 只包含一个 `classes.dex`，并保留 `libAMapSDK_MAP_v10_0_600.so`、`libdraarl_rnnoise.so` 与 AndroidX path native 库。
- R8 映射确认 `RnnoiseNative`、`MapView` 和 `com.autonavi` 内部类未重命名；seeds 确认四个 RNNoise native 方法均被保留。
- 最终 Manifest 保留 `DraarlApplication`、`MainActivity`、`RadioConnectionService`、`AprsService` 和 `FileProvider` 入口。

复现命令：

```powershell
.\gradlew.bat assembleRelease
```

APK 位于 `app/build/outputs/apk/release/app-release.apk`，R8 报告位于 `app/build/outputs/mapping/release`。

## 尚未覆盖

当前没有连接 Android 设备，因此尚未执行 RNNoise、地图、应用内更新、前台服务、冷启动、首帧和运行内存的 Release 真机回归。项目也尚未生成覆盖 DraARL 关键流程的自定义 Baseline Profile；这两部分继续由 `TODO.md` 跟踪。
