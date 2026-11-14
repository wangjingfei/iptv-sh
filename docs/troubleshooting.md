# 故障排查

| 现象 | 首要检查 | 可能范围 |
| --- | --- | --- |
| 机顶盒提示无网络 | 确认未打标签网络存在 `192.168.32.2` 租约，检查 `br-stb` 链路，并确认 DHCP Offer/ACK 包含 Option 125 | LAN4 未打标签 DHCP、MAC 固定地址 |
| 机顶盒可以上网，但 IPTV 首页不可用 | 检查 LAN4 上带 VLAN 85 标签的 DHCP 报文，以及 IPTV 网关和 EPG TCP 响应 | LAN4.85 到 LAN5.85 网桥、光猫 IPTV 端口绑定 |
| NAS 播放列表可以加载，但频道黑屏 | 检查 HLS `/api/status`、频道源地址和 udpxy 服务，然后检查 `br-iptv` 上的组播 | 频道表、udpxy、IGMP 入组 |
| HLS 启动很慢或返回 503 | 检查容器日志，确认源地址提供有效的 HTTP MPEG-TS | FFmpeg 输入和直播流启动 |
| 播放 4K 组播时卡顿 | 对比播放前后的路由器 UDP 错误，检查单路码率、NAS/客户端局域网和 CPU；不要直接假设 HLS 在转码 | 组播丢包或网络路径问题 |
| EPG 播放列表可用，但不显示节目单 | 请求 `/status.json`，比较 M3U `tvg-id` 与 XMLTV `channel id`，确认播放器支持 XMLTV | EPG 来源覆盖率或频道映射 |
| EPG 容器健康，但节目单没有更新 | 检查更新时间和日志；上游可能不可用，服务会保留上一份有效缓存 | 公共 XMLTV 上游或网络 |
| 只有 100/112 个频道显示节目单 | 查看状态 JSON 中的 `unmatched`；11 个 4K 同播频道会复用普通频道节目单，其他频道除非确认是同一频道，否则不要强行映射 | 上游缺少节目单或别名映射缺失 |
| 健康检查通过但个别频道无法播放 | 执行 `make live-check` 逐一读取全部源；健康页只代表进程存活 | 上游组播地址失效、临时停播或频道表过期 |

常用只读检查：

```sh
curl -fsS http://192.168.31.200:8090/healthz
curl -fsS http://192.168.31.200:8090/api/status
curl -fsS http://192.168.31.200:8091/healthz
curl -fsS http://192.168.31.200:8091/status.json
ssh root@192.168.31.1 'ip route show default; ip route show dev br-iptv; netstat -su'
make live-check-quick
```

抓包时只选择必要的接口和时长。DHCP 抓包可能包含设备标识符；IPTV HTTP 抓包可能暴露用户 ID 或认证信息。抓包文件必须保存在 Git 仓库外，分析结束后应及时移除。
