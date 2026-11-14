# 路由器配置与恢复

本文记录当前正常运行的 GL-MT6000/ImmortalWrt 配置。端口名称和 UCI 设备定义与具体型号有关。修改路由器前，请先阅读[当前状态](current-state.md)，并在本地安全保存一份 `sysupgrade -k -b` 备份。

## 接线方式

1. 将光猫的 IPTV 输出口连接到路由器 LAN5；LAN5 是路由器侧的 IPTV 输入口。
2. 路由器 LAN4 连接中兴机顶盒。
3. 保持普通 WAN 连接，用于正常互联网访问。

## 必要配置行为

1. 在 `lan5` 上创建 `lan5.85`，VID 为 85。
2. 在 `lan4` 上创建 `lan4.85`，VID 为 85。
3. 在 `br-iptv` 中桥接 `lan5.85` 和 `lan4.85`。
4. 将未打标签的 `lan4` 保留在独立的 `br-stb` 网桥中，地址为 `192.168.32.1/24`。
5. 运行本地机顶盒 DHCP 地址池，并使用 `router/snapshots/iptv-dhcp-firewall.uci` 中的完整强制 DHCP Option 125。
6. 为已知机顶盒 MAC 固定分配 `192.168.32.2`。
7. `br-iptv` 上的 DHCP 不得安装默认路由或上游 DNS。已观察到的租约地址为 `30.171.109.55/16`，网关为 `30.171.0.1`。
8. IPTV 防火墙区域的输入和转发默认保持拒绝，只在本机放行必要的组播和 IGMP。不要配置机顶盒到主 LAN 的转发。
9. 只有当前设计确实需要路由机顶盒组播时才运行 IGMP 代理；当前中兴机顶盒使用的 IPTV VLAN 本身是二层透明桥接。
10. `udpxy` 必须以 `br-iptv` 为源接口，并绑定主 LAN 地址。

当前在线配置中与 IPTV 有关的 UCI 段保存在 `router/snapshots/`。DHCP Option 125 不是账号密码，但仍然与运营商和设备相关；部署前需要确认目标机顶盒能够接受该值。

## 只读验证

```sh
ssh root@192.168.31.1 'ubus call network.interface.iptv_probe status'
ssh root@192.168.31.1 'ip route show default; ip route show dev br-iptv'
ssh root@192.168.31.1 'ip -4 addr show br-iptv; ip -4 addr show br-stb'
ssh root@192.168.31.1 'grep -i d4:ad:2d:a5:76:18 /tmp/dhcp.leases'
ssh root@192.168.31.1 '/etc/init.d/udpxy status; /etc/init.d/igmpproxy status'
curl -fsS http://192.168.31.1:4022/status/ | head
```

预期结果：默认路由仍然走 WAN；`br-iptv` 已取得 IPTV DHCP 租约；机顶盒同时具有本地 DHCP 和运营商 VLAN 连通性；udpxy 和组播路由处于活动状态。

## 变更规范

- 先备份当前配置：执行 `sysupgrade -k -b /tmp/router-backup.tar.gz`，然后将备份安全复制到路由器外，并使用 `gzip -t` 验证。
- 将 `/etc/init.d/udpxy` 和 `/etc/sysctl.d/90-udpxy-buffer.conf` 加入备份保留清单，具体见路由器备份说明。
- 每次只修改一个接口；除非已经准备好恢复方式，否则不要通过远程 SSH 一次性重载全部网络配置。
- 每次修改后都要验证 WAN 默认路由、主 LAN 连通性、NAS `:8090`、机顶盒本地 DHCP 和至少一个 udpxy 流。
- 回滚时恢复已保存的备份或配置片段，或者只撤销本次修改的 UCI 段，然后重载受影响的服务。
- 未确认备份归档、设备型号、固件版本并安排好重启前，禁止在当前活动路由器上执行 `sysupgrade -r`。

## 已知范围说明

`igmpproxy` 当前上游声明了 `altnet 0.0.0.0/0`，这是现有正常运行配置的一部分。只有在收集到真实源网段并完成组播测试后，才可以缩小该范围；不要通过扩大防火墙转发范围来规避问题。
