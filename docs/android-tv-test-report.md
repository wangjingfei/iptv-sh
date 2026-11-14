# Android TV 开发验证记录

## 当前结论

`0.3.0` 开发版已经具备可重复构建的 Gradle 工程、单元测试、显式数据库迁移和可侧载 Debug APK。当前结论仅覆盖本地构建与家庭网络在线接口，不等同于物理电视验收完成。

## 2026-11-15 本地验证

| 项目 | 结果 | 说明 |
| --- | --- | --- |
| Kotlin/资源/Hilt/Room 编译 | 通过 | AGP 8.13.2、Gradle 8.13、Android 36 |
| JVM 单元测试 | 通过 | 24 项，覆盖 112 频道导入、14,000 条 XMLTV 性能、来源探测、解码错误策略、URL 脱敏、条件请求、损坏 XML、来源限制、分组和首页筛选 |
| Room 迁移/事务测试 | 模拟器执行发现并修复 1 个缺陷，待复验 | 恢复收藏后未删除归档会导致未来错误恢复；现已在事务中恢复后删除当前频道归档 |
| Debug APK | 通过 | `com.wangjingfei.hometv.debug`，版本 `0.3.0-debug`，最低 API 23 |
| Release 构建与 R8 压缩 | 通过 | 已生成未签名 release APK；已支持环境变量注入独立签名，真实升级仍待验证 |
| 仓库静态检查 | 通过 | Python、Compose、秘密特征和 Android TV 工程结构 |
| 当前后端快速在线检查 | 通过 | 112 个频道，抽测组播/HLS 实际播放，EPG 匹配 100/112 |
| Android TV 模拟器 | 未执行 | 本机只剩约 1 GiB 空间，Google TV 系统镜像无法安装 |
| 物理电视安装/启动器展示 | 未验证 | 当前 ADB 没有连接设备 |
| 遥控器全路径 | 未验证 | 需要物理遥控器或 TV 模拟器 |
| 普通频道与 4K 解码 | 未验证 | 依赖目标电视硬件 |
| 连续播放 1 小时/连续换台 | 未验证 | 需要目标电视与 NAS 联合观察 |
| Home/返回后的 HLS 释放与恢复 | 代码路径已实现，未实机验证 | 返回首页和进入后台会停止播放器；回到仍打开的播放页会恢复当前频道 |
| 初次来源测试流程 | 单元与服务测试通过，未实机验证 | 测试 M3U、XMLTV 和真实 HLS 样例后才允许应用来源；修改输入会使旧测试失效 |
| GitHub Actions 自动构建 | build job 已通过，仪器测试修复待复验 | 远端已生成并上传 APK；API 35 模拟器实际执行 4 项测试并发现收藏归档缺陷 |
| release 签名、升级和回滚 | 未验证 | 尚未配置独立 release keystore |

本地构建命令：

```sh
cd android-tv
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug assembleRelease
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

GitHub Actions 工作流位于 `.github/workflows/android-tv.yml`。首次推送后应检查该工作流的真实运行结果；工作流文件存在本身不等于远端门禁已通过。
