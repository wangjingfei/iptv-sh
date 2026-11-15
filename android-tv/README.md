# Android TV 客户端

`android-tv/` 是“家庭电视”原生 Android TV / Google TV 客户端。当前版本为 `0.3.0` 开发版，已经能够生成带调试签名、可侧载的 APK，并通过 API 35 模拟器的首次设置 DPAD 焦点和数据层设备测试，以及 Android TV API 36 模拟器上的安装、启动冒烟检查和 6 项设备测试；物理电视的完整遥控器路径、4K 解码、长时间播放和正式签名升级仍待实机验收。

## 已实现

- 从可修改的 M3U 和 XMLTV 地址同步频道与节目单。
- 首次安装先单独测试播放列表、XMLTV 与样例 HLS；确认后才保存来源并导入，测试失败不会覆盖已有设置。
- 使用 ETag/Last-Modified 条件请求，未变化的数据不会重复解析和写库。
- Room 缓存频道/节目，DataStore 保存来源配置和上次频道。
- 数据库显式迁移到 v3；节目缺少结束时间时仍可保存，消失频道的收藏保留 30 天。
- 全部、收藏、最近和确定性频道分组，支持名称或频道号搜索。
- 首页显示当前、下一及后续节目；EPG 不可用时仍保留频道缓存。
- Media3 HLS 全屏播放、上下换台、350 ms 换台防抖、数字键选台。
- 返回首页或进入后台时停止播放器，仍处于播放页时回到前台自动恢复当前频道。
- 音轨切换、字幕开关、播放错误码、1/2/4 秒自动重试和手动重试。
- M3U、XMLTV、EPG 健康、HLS 健康和样例频道 HLS 的诊断。
- 解码错误启用 Media3 decoder fallback；诊断页保留最近播放错误和导入警告。
- 支持清空节目单、最近观看和收藏，并对破坏性操作二次确认。
- 设置页可保存默认字幕状态，并把播放中选择的音轨偏好恢复为自动。
- Leanback 启动入口、横屏全屏、无触摸屏依赖和电视 banner。

详细产品与验收基线见[《Android TV 应用详细设计》](../docs/android-tv-player.md)，当前验证结果见[《Android TV 开发验证记录》](../docs/android-tv-test-report.md)。

## 构建

需要 JDK 17 或更高版本，以及包含 Android 36 平台的 Android SDK。首次构建前创建不提交 Git 的 `local.properties`：

```properties
sdk.dir=/你的/Android/Sdk
```

执行：

```sh
./gradlew --no-parallel testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug
./gradlew --no-parallel lintDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。安装到已开启 ADB 的电视或盒子：

```sh
adb connect <电视IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.wangjingfei.hometv.debug/com.wangjingfei.hometv.MainActivity
```

也可以在仓库根目录运行可重复的设备冒烟检查。脚本会自动选择唯一已授权设备、安装 APK、从 TV 启动器启动并确认应用进入前台；前台 Activity 检测兼容新版 Android 输出的 `topResumedActivity`，也兼容旧版字段：

```sh
make android-device-check
```

存在多个 ADB 设备时用 `ANDROID_TV_SERIAL=<序列号>` 指定目标。需要同时执行 6 项设备测试时运行：

```sh
./scripts/verify-android-device.sh --with-tests
```

脚本通过只证明安装和启动链路可用；首次设置以外的完整遥控器路径、4K 解码和长时间播放仍需人工观察。

卸载开发版即可回滚，不会影响路由器或 NAS：

```sh
adb uninstall com.wangjingfei.hometv.debug
```

## 默认来源

- M3U：`http://192.168.31.200:8091/playlist.m3u`
- XMLTV：`http://192.168.31.200:8091/epg.xml`

地址只是开发版预填值，可在首次连接页或设置页修改。明文 HTTP 只接受 RFC 1918、`.home.arpa`、`.local` 和 localhost；公网来源必须使用 HTTPS。

## 正式发布

仓库不保存 keystore 或密码。当前 Debug APK 使用 Android 调试签名，可以直接侧载，但不能视为正式发布包。正式发布时通过环境变量提供签名材料：

```sh
export HOME_TV_KEYSTORE=/安全位置/home-tv-release.jks
export HOME_TV_STORE_PASSWORD='...'
export HOME_TV_KEY_ALIAS='home-tv'
export HOME_TV_KEY_PASSWORD='...'
./gradlew assembleRelease
```

四项变量不齐全时仍可验证 R8 构建，但输出文件是 `app-release-unsigned.apk`。正式签名后需要生成 SHA-256，并在同一台物理电视上验证安装、升级和回滚。

可使用一次性临时 keystore 验证 Gradle 签名配置与 APK 完整性；脚本结束后会删除临时密钥和测试签名 APK，不能把它当成正式发布包：

```sh
make android-signing-check
```

若 Release 输出目录已存在同名签名 APK，检查脚本会在运行期间暂存它，并在退出时恢复原文件。
