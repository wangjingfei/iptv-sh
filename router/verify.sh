#!/bin/sh
set -u

fail=0
check() {
	description="$1"
	shift
	if "$@" >/dev/null 2>&1; then
		echo "通过 $description"
	else
		echo "失败 $description"
		fail=1
	fi
}

main_bridge_clean() {
	for section in $(uci show network | sed -n 's/^network\.\([^=]*\)=device$/\1/p'); do
		[ "$(uci -q get "network.$section.name")" = 'br-lan' ] || continue
		! uci -q get "network.$section.ports" | grep -Eq '(^| )(lan4|lan5)( |$)'
		return
	done
	return 1
}

check "设备型号为 GL-MT6000" sh -c '[ "$(ubus call system board | jsonfilter -e "@.board_name")" = "glinet,gl-mt6000" ]'
check "主默认路由仍然走 WAN" sh -c 'ip route show default | grep -q " dev eth1"'
check "主网桥不再包含 LAN4/LAN5" main_bridge_clean
check "LAN5 物理链路已连接" sh -c 'ip link show lan5 | grep -q "LOWER_UP"'
check "IPTV 网桥链路已启用" sh -c 'ip link show br-iptv | grep -q "state UP"'
check "机顶盒网桥地址正确" sh -c 'ip -4 addr show br-stb | grep -q "192.168.32.1/24"'
check "IPTV DHCP 探测接口已启用" sh -c 'ubus call network.interface.iptv_probe status | jsonfilter -e "@.up" | grep -qx true'
check "udpxy 正在运行" /etc/init.d/udpxy status
check "igmpproxy 正在运行" /etc/init.d/igmpproxy status
check "udpxy 状态页可访问" curl -fsS --connect-timeout 3 http://192.168.31.1:4022/status/
check "udpxy 能收到实际频道数据" sh -c '[ "$(curl -sS --connect-timeout 3 --max-time 8 http://192.168.31.1:4022/rtp/233.18.204.57:5140/ | head -c 188 | wc -c)" -eq 188 ]'
check "自定义 udpxy 文件会被固件升级备份" sh -c 'sysupgrade -l | grep -qx /etc/init.d/udpxy && sysupgrade -l | grep -qx /etc/sysctl.d/90-udpxy-buffer.conf'

exit "$fail"
