# Android TV 开发验证记录

## 当前结论

`0.3.0` 开发版已经具备可重复构建的 Gradle 工程、单元测试、显式数据库迁移和可侧载 Debug APK。当前结论仅覆盖本地构建与家庭网络在线接口，不等同于物理电视验收完成。

## 2026-11-15 开发验证

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| Kotlin/资源/Hilt/Room 编译 | 通过 | AGP 8.13.2、Gradle 8.13、Android 36 |
| JVM 单元测试 | 通过 | 27 项，覆盖 112 频道导入、14,000 条 XMLTV 性能、来源探测、解码/首帧超时错误策略、错误浮层保持、实际播放状态常亮规则、URL 脱敏、条件请求、损坏 XML、来源限制、分组和首页筛选 |
| Room 迁移/事务测试 | 通过 | GitHub API 35 模拟器实际执行 5 项；覆盖 v1→v3 迁移、收藏归档恢复和清空 EPG 后强制重新下载 |
| 首次设置 DPAD 焦点 | 通过 | GitHub API 35 模拟器从完整 Activity 发送方向键，验证播放列表 → XMLTV → 测试连接；测试在 Activity 启动前临时重置首次设置状态并在结束后恢复，可在已配置过的设备上重复运行且不改变原有来源配置；测试按钮使用 TV Material 焦点实现 |
| Debug APK | 通过 | `com.wangjingfei.hometv.debug`，版本 `0.3.0-debug`，最低 API 23 |
| Release 构建、R8 与签名链路 | 通过 | 已生成未签名 release APK；一次性 JKS 的 v1/v2 签名和证书读取通过，真实 release keystore 升级仍待验证 |
| 仓库静态检查 | 通过 | Python、Compose、秘密特征和 Android TV 工程结构 |
| 当前后端快速在线检查 | 通过 | 112 个频道，抽测组播/HLS 实际播放，EPG 匹配 100/112 |
| 本机 Android TV 模拟器 | 通过 | Android TV API 36 / Android 16 ARM64 镜像；Debug APK 安装及 TV 启动器启动通过，6 项 `connectedDebugAndroidTest` 全部通过；连接 NAS 实际导入 112 个频道和 11,304 条节目，并从上海新闻综合 HLS 解码出视频画面 |
| 物理电视安装/启动器展示 | 未验证 | 当前 ADB 没有连接设备 |
| 遥控器全路径 | 部分通过 | 首次设置三步 DPAD 路径已在 API 35 模拟器通过；首页、播放浮层和设置页全路径仍需物理遥控器验证 |
| 普通频道解码 | 通过 | 本机 Android TV API 36 模拟器可从上海新闻综合 HLS 解码并显示动态画面 |
| 音频焦点与播放常亮 | 模拟器通过，实体电视未验证 | API 36 TV 模拟器实际播放上海新闻综合时，系统音频焦点栈显示应用持有 `USAGE_MEDIA / CONTENT_TYPE_MOVIE` 焦点，MediaSession 为 `PLAYING`，窗口成为 `mHoldScreenWindow`；按 Home 后焦点栈为空、MediaSession 变为 `NONE`、`mHoldScreenWindow=null` |
| 4K 解码（模拟器） | 未通过，不能代表实体电视 | BesTV动画「4K」识别为 HEVC 3840×2160；模拟器创建了 `c2.goldfish.hevc.decoder`，但持续处于缓冲状态且未渲染首帧。日志出现 `PesReader: Unexpected start code prefix`；同一模拟器切回上海新闻综合后可正常播放。宿主机读取 NAS HLS 分片返回 HTTP 200（约 5.8 MB），因此当前证据更指向模拟器 4K/HEVC 解码路径，不足以判断频道流在实体电视上的兼容性。 |
| 首帧超时保护 | 通过 | API 36 TV 模拟器播放上述 4K 流时，30 秒无首帧后停止播放器并显示“电视不支持编码或直播流不完整”的提示及重试/诊断操作；普通频道首帧到达后取消计时。实体电视仍需单独验收。 |
| 4K 解码（实体电视） | 未验证 | 尚未在目标电视确认 4K 首帧、稳定性、缓冲和画面流畅度 |
| 连续播放 1 小时/连续换台 | 未验证 | 需要目标电视与 NAS 联合观察 |
| Home/返回后的 HLS 释放与恢复 | 模拟器通过，实体电视未验证 | API 36 TV 模拟器按 Home 后播放器变为 `NONE`，音频焦点和有效亮屏占用均已释放；回到仍打开的播放页恢复当前频道的实体电视行为仍待验证 |
| 初次来源测试流程 | 自动化部分通过，未实机验证 | M3U、XMLTV 和真实 HLS 样例探测通过后才允许应用来源；修改输入会使旧测试失效；三步焦点路径已自动化 |
| GitHub Actions 自动构建 | 通过 | 运行 `43696932921` 的 build 与 instrumentation 作业均通过；仓库检查、Android 构建、Release 签名路径和 API 35 模拟器 6 项设备测试通过 |
| release 签名、升级和回滚 | 部分通过 | 一次性 JKS 的签名构建和 APK 完整性已验证；尚未配置用户持有的 release keystore，也未在同一电视验证升级/回滚 |

本地构建命令：

```sh
cd android-tv
./gradlew --no-parallel testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug assembleRelease
./gradlew --no-parallel lintDebug
```

家庭网络在线检查：

```sh
make live-check-quick
```

## 版本锁定原因

工程使用 Android 36 和 AGP 8.13.2。2026 年最新一批 Compose/Lifecycle/Navigation/Hilt 依赖已经要求 API 37 与 AGP 9.1，因此本开发版锁定到兼容 Android 36 的版本：Compose BOM `2025.06.01`、Lifecycle `2.9.4`、Hilt `2.57.2`、Room `2.7.2`、OkHttp `4.12.0`。这不是功能降级，而是避免把未完成迁移的新构建工具混入首版。

## 实机验收步骤

1. 在电视开发者设置中开启网络调试，记录电视 IP。
2. 使用 `adb connect <电视IP>:5555` 并确认授权。
3. 安装 Debug APK，确认“家庭电视”出现在 TV 启动器中。
4. 仅用五向键完成首次同步、分组、搜索、收藏、播放、换台、字幕和返回。
5. 抽测一个普通频道和一个 4K 频道，记录首帧时间、缓冲与错误提示。
6. 连续播放 1 小时，再连续换台 20 次，同时观察 NAS 的活动 FFmpeg 数量。
7. 分别停止 EPG、HLS 和路由器 udpxy，核对诊断结论；测试后恢复服务。

连接设备后执行数据库迁移和收藏归档仪器测试：

```sh
./gradlew connectedDebugAndroidTest
```

在仓库根目录安装并验证 TV 启动器启动链路：

```sh
make android-device-check
```

需要在同一设备上先跑仪器测试再安装启动时：

```sh
./scripts/verify-android-device.sh --with-tests
```

GitHub Actions 工作流位于 `.github/workflows/android-tv.yml`。2026-11-15 已核验运行 `43696932921`：完整构建、临时 release 签名校验和 API 35 模拟器的 6 项设备测试均通过，并成功上传 APK 与测试报告；6 项包括 5 项 Room/数据行为测试和 1 项完整 Activity 的首次设置 DPAD 焦点测试。本机 Android TV API 36 模拟器也已通过相同 6 项设备测试和启动冒烟检查。后续提交仍以各自运行结果为准。构建与 Lint 使用两个串行的 `--no-parallel` Gradle 调用，避免 Lint 在 Hilt release 源码重建期间读取到已替换的中间文件。
