# Android TV 客户端

`android-tv/` 是“家庭电视”原生 Android TV / Google TV 客户端。当前版本为 `0.1.0` 开发版，已经能够生成带调试签名、可侧载的 APK；物理电视的遥控器、4K 解码、长时间播放和正式签名升级仍待实机验收。

## 已实现

- 从可修改的 M3U 和 XMLTV 地址同步频道与节目单。
- Room 缓存频道/节目，DataStore 保存来源配置和上次频道。
- 全部、收藏、最近和确定性频道分组，支持名称或频道号搜索。
- 首页显示当前、下一及后续节目；EPG 不可用时仍保留频道缓存。
- Media3 HLS 全屏播放、上下换台、350 ms 换台防抖、数字键选台。
- 音轨切换、字幕开关、播放错误码、1/2/4 秒自动重试和手动重试。
- M3U、XMLTV、EPG 健康、HLS 健康和样例频道 HLS 的诊断。
- Leanback 启动入口、横屏全屏、无触摸屏依赖和电视 banner。

详细产品与验收基线见[《Android TV 应用详细设计》](../docs/android-tv-player.md)，当前验证结果见[《Android TV 开发验证记录》](../docs/android-tv-test-report.md)。

## 构建

需要 JDK 17 或更高版本，以及包含 Android 36 平台的 Android SDK。首次构建前创建不提交 Git 的 `local.properties`：

```properties
sdk.dir=/你的/Android/Sdk
```

执行：

```sh
./gradlew testDebugUnitTest assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。安装到已开启 ADB 的电视或盒子：

```sh
adb connect <电视IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.wangjingfei.hometv.debug/com.wangjingfei.hometv.MainActivity
```

卸载开发版即可回滚，不会影响路由器或 NAS：

```sh
adb uninstall com.wangjingfei.hometv.debug
```

## 默认来源

- M3U：`http://192.168.31.200:8091/playlist.m3u`
- XMLTV：`http://192.168.31.200:8091/epg.xml`

地址只是开发版预填值，可在首次连接页或设置页修改。明文 HTTP 只接受 RFC 1918、`.home.arpa`、`.local` 和 localhost；公网来源必须使用 HTTPS。

## 正式发布

仓库不保存 keystore 或密码。当前 Debug APK 使用 Android 调试签名，可以直接侧载，但不能视为正式发布包。正式发布前需要准备独立 keystore、配置 release 签名、生成 SHA-256，并在同一台物理电视上验证安装、升级和回滚。
