# 家庭 IPTV

上海电信 IPTV 家庭内网播放方案，覆盖路由器 VLAN 85 透传、udpxy 单播、NAS 上的按需 HLS、XMLTV 节目单，以及原生 Android TV 播放器。

## 当前部署

| 组件 | 位置 | 作用 |
| --- | --- | --- |
| GL-MT6000 / ImmortalWrt | `192.168.31.1` | 桥接 IPTV VLAN 85、提供机顶盒 DHCP/Option 125、运行 udpxy 和组播代理 |
| 飞牛 NAS | `192.168.31.200` | Docker 宿主机 |
| `iptv-hls` | TCP `8090` | 将指定的 udpxy MPEG-TS 源按需转换为 HLS |
| `iptv-epg` | TCP `8091` | 生成带 EPG 的 M3U 播放列表和经过筛选的 XMLTV |
| IPTV 机顶盒 | LAN4 | 同时使用未打标签的隔离网络和透明透传的 VLAN 85 标签网络 |

修改当前网络前，请先阅读[当前状态](docs/current-state.md)。

## 快速开始

```sh
make check
```

在当前家庭网络中执行完整在线检查：

```sh
make live-check
```

完整检查会逐一读取全部组播源，再验证 HLS 和 EPG；`make live-check-quick` 只抽测一个频道，适合日常快速确认。

全新安装时，按以下顺序操作：

1. 阅读[网络拓扑](docs/topology.md)，连接 IPTV 输入端口和机顶盒端口。
2. 备份路由器，然后阅读[路由器配置](docs/router-setup.md)并检查带型号、资源和回滚状态保护的 `router/apply-iptv.sh` 脚本。
3. 将 `docker/iptv-hls` 和 `docker/iptv-epg` 复制到 NAS 上同级的两个目录，并按照 [NAS Docker 部署](docs/nas-docker.md)操作。
4. 在支持 XMLTV 的播放器中导入 `http://192.168.31.200:8091/playlist.m3u`；不需要节目单时，也可以继续使用 8090 端口的播放列表。
5. 修改网络前，先参考[故障排查](docs/troubleshooting.md)中的只读检查方法。
6. 需要电视端应用时，按照 [Android TV 客户端](android-tv/README.md)构建并侧载开发版 APK。

## 服务关系

`iptv-hls` 是媒体后端。`iptv-epg` 只提供元数据，以及一份频道地址指向 `iptv-hls` 的播放列表。两个服务互相配合，但可以独立部署。不需要 EPG 时，可以继续直接使用原有 HLS 播放列表。

## 仓库结构

- `router/`：脱敏后的 UCI 快照、只读检查脚本，以及路由器安装和回滚说明。
- `docker/iptv-hls/`：当前部署的 HLS 程序、Dockerfile、Compose 配置和频道表。
- `docker/iptv-epg/`：EPG/M3U 生成程序和 Compose 配置。
- `docs/`：网络拓扑、操作流程、故障排查、EPG 限制和 Android TV 应用规划。
- `android-tv/`：Kotlin/Compose、Media3、Room 和 Hilt 实现的原生电视客户端。

电视客户端的产品、交互、架构、测试和验收基线见 [Android TV 应用详细设计](docs/android-tv-player.md)。当前 `0.3.0` 开发版已经通过本地单元测试和 APK 构建，但物理电视、4K、长时间播放与正式签名升级尚未验证，不能标记为最终发布版。

## 安全要求

本仓库不会保存 Wi-Fi 密码、VPN 私钥、IPTV 账号或 SN、路由器备份、生成的 EPG 缓存和抓包文件。凭据应保存在未被 Git 跟踪的本地 `.env` 文件或设备专用的秘密存储中。每次提交前都应检查 `git status` 和暂存区差异。
