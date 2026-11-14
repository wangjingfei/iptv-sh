# 路由器配置与恢复

本文记录当前正常运行的 GL-MT6000/ImmortalWrt 配置。端口名称和 UCI 设备定义与具体型号有关。修改路由器前，请先阅读[当前状态](current-state.md)，并在本地安全保存一份 `sysupgrade -k -b` 备份。

## 接线方式

1. 将光猫的 IPTV 输出口连接到路由器 LAN5；LAN5 是路由器侧的 IPTV 输入口。
2. 路由器 LAN4 连接中兴机顶盒。
3. 保持普通 WAN 连接，用于正常互联网访问。

## 必要配置行为

1. 先从主网桥 `br-lan` 移除 LAN4 和 LAN5，主局域网只保留 LAN1-LAN3。
2. 在 `lan5` 上创建 `lan5.85`，VID 为 85。
3. 在 `lan4` 上创建 `lan4.85`，VID 为 85。
4. 在 `br-iptv` 中桥接 `lan5.85` 和 `lan4.85`。
5. 将未打标签的 `lan4` 保留在独立的 `br-stb` 网桥中，地址为 `192.168.32.1/24`。
6. 运行本地机顶盒 DHCP 地址池，并使用 `router/snapshots/iptv-dhcp-firewall.uci` 中的完整强制 DHCP Option 125。
7. 为已知机顶盒 MAC 固定分配 `192.168.32.2`。
8. `br-iptv` 上的 DHCP 不得安装默认路由或上游 DNS。已观察到的租约地址为 `30.171.109.55/16`，网关为 `30.171.0.1`。
9. IPTV 防火墙区域的输入和转发默认保持拒绝，只在本机放行必要的组播和 IGMP。不要配置机顶盒到主 LAN 的转发。
10. 当前配置同时保留 VLAN 85 二层透明桥接和 `igmpproxy` 到 `br-stb` 的组播路由。未完成机顶盒停机对照测试前，不删除任何一条路径。
11. `udpxy` 必须以 `br-iptv` 为源接口，并绑定主 LAN 地址。

当前在线配置中与 IPTV 有关的 UCI 段保存在 `router/snapshots/`。DHCP Option 125 不是账号密码，但仍然与运营商和设备相关；部署前需要确认目标机顶盒能够接受该值。

`router/apply-iptv.sh` 会检查型号、软件包、端口和自定义文件，备份原配置，移出 LAN4/LAN5，提交 UCI，并启用 udpxy 与 igmpproxy 的开机启动。为避免远程断网，脚本不会自动重载主网络；应在具备恢复手段的维护窗口手工重载或重启。

## 只读验证

```sh
ssh root@192.168.31.1 'ubus call network.interface.iptv_probe status'
ssh root@192.168.31.1 'ip route show default; ip route show dev br-iptv'
ssh root@192.168.31.1 'ip -4 addr show br-iptv; ip -4 addr show br-stb'
ssh root@192.168.31.1 'grep -i d4:ad:2d:a5:76:18 /tmp/dhcp.leases'
ssh root@192.168.31.1 '/etc/init.d/udpxy status; /etc/init.d/igmpproxy status'
curl -fsS http://192.168.31.1:4022/status/ | head
```

也可以把仓库复制到路由器后直接运行 `router/verify.sh`。该脚本还会检查 LAN4/LAN5 是否已从主网桥移除、udpxy 是否能收到真实频道数据，以及自定义文件是否进入固件升级备份清单。

预期结果：默认路由仍然走 WAN；`br-iptv` 已取得 IPTV DHCP 租约；udpxy 和组播路由处于活动状态。只有 LAN4 物理链路为 UP 且机顶盒实际播放成功，才能确认机顶盒两条路径完整可用。

## 变更规范

- 先备份当前配置：执行 `sysupgrade -k -b /tmp/router-backup.tar.gz`，然后将备份安全复制到路由器外，并使用 `gzip -t` 验证。
- 将 `/etc/init.d/udpxy` 和 `/etc/sysctl.d/90-udpxy-buffer.conf` 加入备份保留清单，具体见路由器备份说明。
- 每次只修改一个接口；除非已经准备好恢复方式，否则不要通过远程 SSH 一次性重载全部网络配置。
- 每次修改后都要验证 WAN 默认路由、主 LAN 连通性、NAS `:8090`、机顶盒本地 DHCP 和至少一个 udpxy 流。
- 回滚脚本会完整恢复由新版安装脚本记录的 UCI、自定义文件、`sysupgrade.conf` 和服务启用状态；旧格式备份缺少状态记录时会发出警告并保留对应现状。恢复后仍需重载服务或重启。
- `sysupgrade -r <备份文件>` 只恢复文件，不会自动重启。未确认备份归档、设备型号、固件版本和后续重载方式前，禁止在当前活动路由器上执行。

## 已知范围说明

`igmpproxy` 当前上游声明了 `altnet 0.0.0.0/0`，这是现有正常运行配置的一部分。只有在收集到真实源网段并完成组播测试后，才可以缩小该范围；不要通过扩大防火墙转发范围来规避问题。
