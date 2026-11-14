#!/bin/sh
set -eu

EXPECTED_BOARD="glinet,gl-mt6000"
SCRIPT_DIR="$(CDPATH= cd "$(dirname "$0")" && pwd)"
STB_MAC="${STB_MAC:-D4:AD:2D:A5:76:18}"
STB_IP="${STB_IP:-192.168.32.2}"
LAN_IP="${LAN_IP:-192.168.31.1}"
DHCP_OPTION_125="${DHCP_OPTION_125:-00:00:00:00:1d:02:06:48:47:57:2d:43:54:03:07:48:47:32:32:30:47:53:0a:02:20:00:0b:02:00:55:0d:02:00:2e}"

board_name="$(ubus call system board | jsonfilter -e '@.board_name')"
[ "$board_name" = "$EXPECTED_BOARD" ] || {
	echo "拒绝执行：预期设备为 $EXPECTED_BOARD，实际为 $board_name" >&2
	exit 1
}

[ "${APPLY:-0}" = "1" ] || {
	cat <<EOF
当前仅为预演。该脚本会在 GL-MT6000 上将 LAN5 配置为 IPTV VLAN 85 输入，
并将 LAN4 配置为中兴机顶盒混合输出。请检查脚本和当前 UCI 配置后，
使用 APPLY=1 重新执行。主 LAN 的 LAN1-LAN3 和无关配置会保留。
EOF
	exit 0
}

for command in uci ubus jsonfilter apk; do
	command -v "$command" >/dev/null || { echo "缺少命令：$command" >&2; exit 1; }
done
for package in udpxy igmpproxy; do
	apk list --installed "$package" 2>/dev/null | grep -q "^$package-" || {
		echo "缺少软件包：$package。请先安装再应用配置。" >&2
		exit 1
	}
done
for port in lan4 lan5; do
	[ -d "/sys/class/net/$port" ] || { echo "缺少物理端口：$port" >&2; exit 1; }
done
for asset in files/etc/init.d/udpxy files/etc/sysctl.d/90-udpxy-buffer.conf; do
	[ -f "$SCRIPT_DIR/$asset" ] || { echo "缺少路由器资源：$asset" >&2; exit 1; }
done

main_bridge_section=''
for section in $(uci show network | sed -n 's/^network\.\([^=]*\)=device$/\1/p'); do
	if [ "$(uci -q get "network.$section.name" || true)" = 'br-lan' ]; then
		main_bridge_section="$section"
		break
	fi
done
[ -n "$main_bridge_section" ] || { echo "找不到主网桥 br-lan 的 UCI 设备段" >&2; exit 1; }

stamp="$(date +%Y%m%d-%H%M%S)"
backup_dir="/root/rollback/${stamp}-home-iptv"
mkdir -p "$backup_dir"
for name in network dhcp firewall udpxy igmpproxy; do
	cp "/etc/config/$name" "$backup_dir/$name"
done
for spec in /etc/init.d/udpxy:udpxy.init /etc/sysctl.d/90-udpxy-buffer.conf:90-udpxy-buffer.conf /etc/sysupgrade.conf:sysupgrade.conf; do
	source_path="${spec%%:*}"
	backup_name="${spec##*:}"
	if [ -f "$source_path" ]; then
		cp "$source_path" "$backup_dir/$backup_name"
	else
		: > "$backup_dir/$backup_name.absent"
	fi
done
for service in udpxy igmpproxy; do
	/etc/init.d/$service enabled >/dev/null 2>&1 && : > "$backup_dir/$service.enabled" || : > "$backup_dir/$service.disabled"
done
echo "回滚备份：$backup_dir"

# GL-MT6000 出厂配置将 LAN4、LAN5 放在 br-lan 中；一个端口不能同时属于两个网桥。
uci -q del_list "network.$main_bridge_section.ports=lan4" || true
uci -q del_list "network.$main_bridge_section.ports=lan5" || true

uci -q delete network.br_iptv || true
uci set network.br_iptv=device
uci set network.br_iptv.name='br-iptv'
uci set network.br_iptv.type='bridge'
uci add_list network.br_iptv.ports='lan5.85'
uci add_list network.br_iptv.ports='lan4.85'
uci set network.br_iptv.stp='0'
uci set network.br_iptv.igmp_snooping='0'

uci -q delete network.iptv_vlan85 || true
uci set network.iptv_vlan85=device
uci set network.iptv_vlan85.type='8021q'
uci set network.iptv_vlan85.ifname='lan5'
uci set network.iptv_vlan85.vid='85'
uci set network.iptv_vlan85.name='lan5.85'

uci -q delete network.stb_vlan85 || true
uci set network.stb_vlan85=device
uci set network.stb_vlan85.type='8021q'
uci set network.stb_vlan85.ifname='lan4'
uci set network.stb_vlan85.vid='85'
uci set network.stb_vlan85.name='lan4.85'

uci -q delete network.iptv_bridge || true
uci set network.iptv_bridge=interface
uci set network.iptv_bridge.device='br-iptv'
uci set network.iptv_bridge.proto='static'
uci set network.iptv_bridge.auto='1'
uci set network.iptv_bridge.ipaddr='169.254.250.1'
uci set network.iptv_bridge.netmask='255.255.255.255'
uci set network.iptv_bridge.defaultroute='0'
uci set network.iptv_bridge.peerdns='0'

uci -q delete network.iptv_probe || true
uci set network.iptv_probe=interface
uci set network.iptv_probe.device='br-iptv'
uci set network.iptv_probe.proto='dhcp'
uci set network.iptv_probe.defaultroute='0'
uci set network.iptv_probe.peerdns='0'
uci set network.iptv_probe.delegate='0'
uci set network.iptv_probe.metric='900'

uci -q delete network.br_stb || true
uci set network.br_stb=device
uci set network.br_stb.name='br-stb'
uci set network.br_stb.type='bridge'
uci add_list network.br_stb.ports='lan4'
uci set network.br_stb.stp='0'

uci -q delete network.stb || true
uci set network.stb=interface
uci set network.stb.device='br-stb'
uci set network.stb.proto='static'
uci set network.stb.ipaddr='192.168.32.1'
uci set network.stb.netmask='255.255.255.0'
uci set network.stb.defaultroute='0'
uci set network.stb.peerdns='0'

uci -q delete dhcp.stb || true
uci set dhcp.stb=dhcp
uci set dhcp.stb.interface='stb'
uci set dhcp.stb.start='2'
uci set dhcp.stb.limit='20'
uci set dhcp.stb.leasetime='24h'
uci set dhcp.stb.dhcpv4='server'
uci set dhcp.stb.dhcpv6='disabled'
uci set dhcp.stb.dhcp_option_force="125,$DHCP_OPTION_125"

uci -q delete dhcp.iptv_bridge || true
uci set dhcp.iptv_bridge=dhcp
uci set dhcp.iptv_bridge.interface='iptv_bridge'
uci set dhcp.iptv_bridge.ignore='1'

uci -q delete dhcp.stb_box || true
uci set dhcp.stb_box=host
uci set dhcp.stb_box.name='zte-iptv'
uci set dhcp.stb_box.mac="$STB_MAC"
uci set dhcp.stb_box.ip="$STB_IP"

for section in iptv iptv_multicast_udp iptv_igmp stb stb_to_wan; do
	uci -q delete "firewall.$section" || true
done
uci set firewall.iptv=zone
uci set firewall.iptv.name='iptv'
uci add_list firewall.iptv.network='iptv_bridge'
uci add_list firewall.iptv.network='iptv_probe'
uci set firewall.iptv.input='REJECT'
uci set firewall.iptv.output='ACCEPT'
uci set firewall.iptv.forward='REJECT'
uci set firewall.iptv.family='ipv4'
uci set firewall.iptv_multicast_udp=rule
uci set firewall.iptv_multicast_udp.name='Allow-IPTV-Multicast-to-udpxy'
uci set firewall.iptv_multicast_udp.src='iptv'
uci set firewall.iptv_multicast_udp.family='ipv4'
uci set firewall.iptv_multicast_udp.proto='udp'
uci set firewall.iptv_multicast_udp.dest_ip='224.0.0.0/4'
uci set firewall.iptv_multicast_udp.target='ACCEPT'
uci set firewall.iptv_igmp=rule
uci set firewall.iptv_igmp.name='Allow-IPTV-IGMP'
uci set firewall.iptv_igmp.src='iptv'
uci set firewall.iptv_igmp.family='ipv4'
uci set firewall.iptv_igmp.proto='igmp'
uci set firewall.iptv_igmp.target='ACCEPT'
uci set firewall.stb=zone
uci set firewall.stb.name='stb'
uci add_list firewall.stb.network='stb'
uci set firewall.stb.input='ACCEPT'
uci set firewall.stb.output='ACCEPT'
uci set firewall.stb.forward='REJECT'
uci set firewall.stb.family='ipv4'
uci set firewall.stb_to_wan=forwarding
uci set firewall.stb_to_wan.src='stb'
uci set firewall.stb_to_wan.dest='wan'

while uci -q delete 'udpxy.@udpxy[0]'; do :; done
udpxy_section="$(uci add udpxy udpxy)"
uci set "udpxy.$udpxy_section.disabled=0"
uci set "udpxy.$udpxy_section.respawn=1"
uci set "udpxy.$udpxy_section.verbose=0"
uci set "udpxy.$udpxy_section.status=1"
uci set "udpxy.$udpxy_section.port=4022"
uci set "udpxy.$udpxy_section.bind=$LAN_IP"
uci set "udpxy.$udpxy_section.source=br-iptv"
uci set "udpxy.$udpxy_section.max_clients=20"
uci set "udpxy.$udpxy_section.buffer_size=1048576"
uci set "udpxy.$udpxy_section.buffer_messages=-1"
uci set "udpxy.$udpxy_section.buffer_time=1"
uci set "udpxy.$udpxy_section.socket_buffer=4194304"

while uci -q delete 'igmpproxy.@phyint[0]'; do :; done
while uci -q delete 'igmpproxy.@igmpproxy[0]'; do :; done
igmp_section="$(uci add igmpproxy igmpproxy)"
uci set "igmpproxy.$igmp_section.quickleave=1"
upstream="$(uci add igmpproxy phyint)"
uci set "igmpproxy.$upstream.network=iptv_bridge"
uci set "igmpproxy.$upstream.zone=iptv"
uci set "igmpproxy.$upstream.direction=upstream"
uci set "igmpproxy.$upstream.altnet=0.0.0.0/0"
downstream="$(uci add igmpproxy phyint)"
uci set "igmpproxy.$downstream.network=stb"
uci set "igmpproxy.$downstream.zone=stb"
uci set "igmpproxy.$downstream.direction=downstream"

uci commit network
uci commit dhcp
uci commit firewall
uci commit udpxy
uci commit igmpproxy

cp "$SCRIPT_DIR/files/etc/init.d/udpxy" /etc/init.d/udpxy
chmod 755 /etc/init.d/udpxy
cp "$SCRIPT_DIR/files/etc/sysctl.d/90-udpxy-buffer.conf" /etc/sysctl.d/90-udpxy-buffer.conf

for path in /etc/init.d/udpxy /etc/sysctl.d/90-udpxy-buffer.conf; do
	grep -qxF "$path" /etc/sysupgrade.conf || echo "$path" >> /etc/sysupgrade.conf
done
/etc/init.d/udpxy enable
/etc/init.d/igmpproxy enable
sysctl -p /etc/sysctl.d/90-udpxy-buffer.conf >/dev/null

echo "配置已提交，自定义文件和开机启动已安装。请在维护窗口重载网络、防火墙和服务。"
echo "回滚来源：$backup_dir"
