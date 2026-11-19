# Android TV 客户端

`android-tv/` 是“家庭电视”原生 Android TV / Google TV 客户端。当前版本 `0.6.0` 已于 2026-11-19 发布，包含三栏首页、统一遥控器焦点视觉、可滚动设置与诊断区域和具名 TV banner；正式安装包见 [GitHub Release](https://github.com/wangjingfei/iptv-sh/releases/tag/v0.6.0)。正式版自更新流程、长期 release keystore、GitHub Secrets、云端签名和固定更新地址均已验证，GitHub Actions 使用 API 34 Android TV 镜像执行设备测试。用户于 2026-11-16 决定暂时跳过真实设备验证；物理电视遥控器、4K 解码、长时间播放、手机实网扫码和同签名升级/回滚均保留为延期项，不视为已经通过。

## 已实现

- 从可修改的 M3U 和 XMLTV 地址同步频道与节目单。
- 首次安装先单独测试播放列表、XMLTV 与样例 HLS；确认后才保存来源并导入，测试失败不会覆盖已有设置。
- 首次设置页和设置页可临时显示二维码，在同一局域网的手机网页中填写来源；手机提交后仍由电视测试，并需在电视端确认应用。
- 使用 ETag/Last-Modified 条件请求，未变化的数据不会重复解析和写库。
- Room 缓存频道/节目，DataStore 保存来源配置和上次频道。
- 数据库显式迁移到 v3；节目缺少结束时间时仍可保存，消失频道的收藏保留 30 天。
- 全部、收藏、最近和确定性频道分组，支持名称或频道号搜索。
- 首页使用分组、频道、节目单三栏结构；频道采用适合十英尺距离扫视的单列大行，节目单可滚动、聚焦并查看节目详情。
- 首页显示当前、下一及后续节目；收藏操作集中在节目栏，避免频道行内部出现第二个隐蔽焦点；EPG 不可用时仍保留频道缓存。
- Media3 HLS 全屏播放、上下换台、350 ms 换台防抖；首页数字键聚焦频道，播放器数字键直接换台。
- 支持遥控器媒体播放/暂停键和浮层播放图标；暂停后恢复时回到直播默认位置。
- 主要按钮和图标按钮统一使用颜色、边框和缩放三重焦点提示；首页当前频道、分组、节目栏、设置入口、播放器浮层和设置来源输入框具有确定性的五向键焦点路径。
- 快捷频道栏兼容不足 5 个频道的列表，不会因循环展示产生重复 key。
- 返回首页或进入后台时停止播放器，仍处于播放页时回到前台自动恢复当前频道。
- 音轨切换、字幕开关、播放错误码、1/2/4 秒自动重试和手动重试。
- M3U、XMLTV、EPG 健康、HLS 健康和样例频道 HLS 的诊断。
- 解码错误启用 Media3 decoder fallback；诊断页保留最近播放错误和导入警告。
- 支持清空节目单、最近观看和收藏，并对破坏性操作二次确认。
- 设置页可保存默认字幕状态，并把播放中选择的音轨偏好恢复为自动。
- Leanback 启动入口、横屏全屏、无触摸屏依赖和带“家庭电视”名称的电视 banner。
- 正式版启动两秒后异步检查 GitHub Release，每 24 小时最多自动检查一次；设置页可手动检查。
- 下载 APK 后校验清单大小、SHA-256、包名、版本号和签名证书，再交给 Android 系统确认安装。
- 开发版包名为 `com.wangjingfei.hometv.debug`，不会尝试安装正式版更新，避免形成两个包或破坏签名升级链。

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

存在多个 ADB 设备时用 `ANDROID_TV_SERIAL=<序列号>` 指定目标。需要同时执行当前 9 项设备测试时运行：

```sh
./scripts/verify-android-device.sh --with-tests
```

脚本通过只证明安装和启动链路可用；首次设置以外的完整遥控器路径、4K 解码和长时间播放仍需人工观察。

关键遥控器用例还覆盖首页三栏焦点路径：数字键跳台、频道栏进入节目栏并返回、播放器两级返回，以及设置页双向焦点导航。软件模拟器没有 KVM 时可能需要提高测试等待时间；这不改变生产界面的数字输入时限或浮层自动隐藏时间。

本次界面调整不改变数据库、播放源配置、网络协议或签名链。需要回滚时安装上一正式版本，或在开发环境恢复对应提交后重新构建；收藏、节目单缓存和来源设置不需要迁移。

卸载开发版即可回滚，不会影响路由器或 NAS：

```sh
adb uninstall com.wangjingfei.hometv.debug
```

## 默认来源

- M3U：`http://192.168.31.200:8091/playlist.m3u`
- XMLTV：`http://192.168.31.200:8091/epg.xml`

地址只是开发版预填值，可在首次连接页或设置页修改。明文 HTTP 只接受 RFC 1918、`.home.arpa`、`.local` 和 localhost；公网来源必须使用 HTTPS。

## 手机扫码设置

在首次设置页或“来源与同步”中选择“手机设置”。电视会启动一个临时局域网页并显示二维码：

- 手机必须与电视处于同一 IPv4 子网；访客 Wi-Fi、AP 隔离或跨 VLAN 防火墙可能阻止访问。
- 会话使用 128 位随机令牌，十分钟后过期；关闭弹窗、切换到后台或应用来源时立即停止服务。
- 服务只接受固定表单路由，请求体上限为 8 KiB，单个 URL 上限为 2048 个字符。
- 手机提交不会直接覆盖设置。电视会复用来源测试，测试通过后仍需在电视上确认“应用已测试来源”。
- 页面和二维码都在电视本地生成，不依赖公网二维码或配置服务。

当前应用以 SDK 36 为目标，已有 `INTERNET` 权限可访问局域网。将来升级到 target SDK 37 或更高时，必须按 Android 17 规则声明并运行时申请 `ACCESS_LOCAL_NETWORK`，否则播放与扫码服务都会被系统阻止。

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

### GitHub Release 自动更新

应用固定读取以下 HTTPS 地址：

```text
https://github.com/wangjingfei/iptv-sh/releases/latest/download/update.json
```

`.github/workflows/android-tv-release.yml` 在推送 `v*` 标签时构建同一次签名的 APK，生成 `update.json`，再把两者发布到 GitHub Release。仓库需要预先配置四个 Actions Secrets：

- `HOME_TV_KEYSTORE_BASE64`：长期 release keystore 的 Base64 内容。
- `HOME_TV_STORE_PASSWORD`：keystore 密码。
- `HOME_TV_KEY_ALIAS`：签名别名。
- `HOME_TV_KEY_PASSWORD`：签名私钥密码。

当前长期签名证书 SHA-256 指纹为：

```text
2759ad17a978eda42ee4d1821088114c03785e1beb538c7e2995e66cd3d376a6
```

首次正式发布前先手动运行 dry-run，验证 GitHub Secrets、签名 APK 和更新清单。该操作使用只读仓库权限，只上传保留 7 天的 Actions artifact，不创建标签或 GitHub Release；只有标签事件的独立发布 job 拥有 Release 写权限。2026-11-16 的首次云端 dry-run `43816612953` 已通过，且复核确认当时未创建 Release：

```sh
gh workflow run android-tv-release.yml --ref main --repo wangjingfei/iptv-sh
gh run watch --repo wangjingfei/iptv-sh
```

release keystore 是应用的长期身份。丢失后无法给已安装版本做原位升级；泄露后必须视为发布身份失陷。原始 JKS、密码和恢复说明必须保存在独立的加密备份中，不得提交到 Git。`v0.5.0` 于 2026-11-16 首次发布，`v0.6.0` 于 2026-11-19 使用同一签名链发布；后续版本必须先更新 `versionCode` 和 `versionName`，测试通过后再推送与版本名一致的标签：

```sh
git tag v0.5.1
git push origin v0.5.1
```

标签必须与 `versionName` 完全一致。工作流会拒绝版本不一致、缺少密钥、未签名 APK 或无法读取证书的发布。首次安装前还必须先把当前电视上的 Debug 包手动替换为正式包；Debug 与 Release 的包名和签名不同，Android 无法把两者视为同一个应用。以后只要一直使用同一 release keystore，应用内更新即可保留设置和数据。

构建和发布是两个独立 job：构建 job 只有仓库只读权限，发布 job 才有 Release 写权限。发布 job 不 checkout 源码，因此 `gh release create` 必须显式传入 `--repo "$GITHUB_REPOSITORY"`；否则 `--verify-tag` 会错误地尝试从当前目录读取本地 Git 仓库。如果签名构建与 artifact 上传已经成功、但发布命令失败，不得移动或重建版本标签，应先修复发布命令，再从原运行下载同一签名 artifact 创建 Release：

```sh
gh run download <运行编号> \
  --repo wangjingfei/iptv-sh \
  --name android-tv-release-<标签对应提交SHA> \
  --dir /tmp/home-tv-release

gh release create v0.5.0 \
  --repo wangjingfei/iptv-sh \
  /tmp/home-tv-release/home-tv-0.5.0.apk \
  /tmp/home-tv-release/update.json \
  --verify-tag --generate-notes --title '家庭电视 0.5.0'
```

本地也可从已签名 APK 生成相同格式的清单：

```sh
python3 ../scripts/generate-android-update-manifest.py \
  --apk app/build/outputs/apk/release/app-release.apk \
  --apk-url https://github.com/wangjingfei/iptv-sh/releases/download/v0.5.0/home-tv-0.5.0.apk \
  --output /tmp/update.json \
  --aapt2 "$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  --apksigner "$ANDROID_HOME/build-tools/36.0.0/apksigner" \
  --release-notes '新增应用内自动更新'
```

首次安装或首次应用内更新时，电视可能要求为“家庭电视”开启“安装未知应用”。授权完成并返回应用后会自动继续打开系统安装器；未授权返回时保留待安装状态，不会反复跳转。该授权只允许应用调用系统安装器，不会绕过系统的安装确认。Google Play Core 的应用内更新不支持 Android TV，因此本项目使用 GitHub Release 分发和 Android 系统安装界面。

可使用一次性临时 keystore 验证 Gradle 签名配置与 APK 完整性；脚本结束后会删除临时密钥和测试签名 APK，不能把它当成正式发布包：

```sh
make android-signing-check
```

检查脚本从 Release 构建生成的 `BuildConfig.java` 读取当前版本，并核对 APK 实际元数据与 `update.json`，版本升级时无需在脚本中维护第二份版本常量。若 Release 输出目录已存在同名签名 APK，脚本会在运行期间暂存它，并在退出时恢复原文件。
