#!/bin/sh
set -u

fail=0
check() {
	description="$1"
	shift
	if "$@" >/dev/null 2>&1; then
		echo "OK   $description"
	else
		echo "FAIL $description"
		fail=1
	fi
}

check "GL-MT6000 board" sh -c '[ "$(ubus call system board | jsonfilter -e "@.board_name")" = "glinet,gl-mt6000" ]'
check "main default route remains on WAN" sh -c 'ip route show default | grep -q " dev eth1"'
check "IPTV bridge link is up" sh -c 'ip link show br-iptv | grep -q "state UP"'
check "STB bridge address" sh -c 'ip -4 addr show br-stb | grep -q "192.168.32.1/24"'
check "IPTV DHCP probe is up" sh -c 'ubus call network.interface.iptv_probe status | jsonfilter -e "@.up" | grep -qx true'
check "udpxy is running" /etc/init.d/udpxy status
check "igmpproxy is running" /etc/init.d/igmpproxy status
check "udpxy status page" wget -q -O /dev/null http://192.168.31.1:4022/status/

exit "$fail"
