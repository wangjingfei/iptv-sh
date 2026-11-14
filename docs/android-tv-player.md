# Android TV 播放器方案

本仓库目前尚未包含 APK。现有服务已经提供原生电视客户端可以使用的后端和稳定接口。

## 产品范围

- 作为 Android TV / Google TV 应用安装和启动，优先支持遥控器方向键操作。
- 导入播放列表 URL，首个目标为家庭 EPG 播放列表。
- 支持频道分组、搜索、收藏、最近观看，以及精简的当前/下一节目指南。
- 支持全屏播放、频道上下切换、音轨/字幕选择和重连状态反馈。
- 支持配置 XMLTV URL，并将 `tvg-id` 与 `channel id` 精确匹配。
- 播放过程保持在局域网内：客户端从 NAS 获取 HLS，路由器和 NAS 负责接收组播。
- 应用不保存 IPTV 凭据，也不尝试模拟中兴机顶盒认证过程。

## 后端接口约定

- 播放列表：从 EPG 服务请求 `GET /playlist.m3u`。列表声明 `x-tvg-url`，并为每个直播流提供 `tvg-id`。
- 节目单：请求 `GET /epg.xml`，XMLTV 中的频道 ID 与播放列表中的 `tvg-id` 一致。
- 直播流：M3U 中的每个地址都指向 `iptv-hls` 的 `/{channel-id}/index.m3u8` 及其 HLS 分片。
- 健康和诊断：使用 `/healthz`、`/status.json` 和 HLS 的 `/api/status`。

## 建议实现方式

- 使用 Kotlin 开发原生 Android 应用，以 Compose for TV 构建界面，以 AndroidX Media3 ExoPlayer 播放视频。Android 当前的电视应用指南推荐使用 Compose for TV 构建现代电视界面；Media3 官方文档确认支持包含 MPEG-TS 分片的 HLS 直播。
- 使用仓库层处理 M3U 解析、XMLTV 解析、本地缓存和刷新策略。
- 播放器使用独立的全屏页面；节目单和频道列表使用适合电视的双栏布局，并保证方向键切换焦点时不会丢失焦点。
- 使用本地数据库或缓存保存节目单条目和用户偏好。除正常播放缓冲外，不下载或持久化 MPEG-TS 视频数据。
- 增加网络超时与重试、HLS 错误分类、无障碍标签、遥控器测试和低内存设备测试。

开始实现前，需要重新核对最新的 Android TV 和 Media3 官方文档，并在目标电视硬件上测试飞牛 NAS 提供的实际 HLS 流。不要硬编码局域网 IP；应用应允许编辑播放列表 URL，并可配置备用 EPG URL。

官方参考资料：

- https://developer.android.com/training/tv/get-started
- https://developer.android.com/training/tv/get-started/create
- https://developer.android.com/media/media3/exoplayer/hls

## 开发里程碑

1. 架构验证：证明 NAS HLS 地址能够在目标 Android TV 硬件上播放。
2. 实现播放列表导入和支持方向键的频道列表。
3. 实现 XMLTV 解析、缓存和当前/下一节目指南。
4. 实现收藏、分组、重连和诊断功能。
5. 构建签名 APK/AAB，验证安装、升级流程和客厅使用体验。

## 验收标准

- 冷启动后无需触摸操作即可进入频道列表。
- 方向键可以在频道列表、节目单和播放控制之间移动，且不会丢失焦点。
- 可以播放和切换频道，且无需重建后端容器。
- 当前和下一节目与本地时区下的 XMLTV 时间一致。
- 缺少 EPG 时仍能播放；XMLTV 过期时，应明确显示当前使用的是缓存元数据。
