#!/bin/sh
set -eu

backup_dir="${1:-}"
[ -n "$backup_dir" ] && [ -d "$backup_dir" ] || {
	echo "用法：$0 /root/rollback/YYYYMMDD-HHMMSS-home-iptv" >&2
	exit 1
}
[ "${ROLLBACK:-0}" = "1" ] || {
	echo "当前仅为预演。请检查 $backup_dir，然后使用 ROLLBACK=1 重新执行。" >&2
	exit 0
}

for name in network dhcp firewall udpxy igmpproxy; do
	[ -f "$backup_dir/$name" ] || { echo "缺少备份文件：$name" >&2; exit 1; }
done

for name in network dhcp firewall udpxy igmpproxy; do
	cp "$backup_dir/$name" "/etc/config/$name"
done
for spec in /etc/init.d/udpxy:udpxy.init /etc/sysctl.d/90-udpxy-buffer.conf:90-udpxy-buffer.conf /etc/sysupgrade.conf:sysupgrade.conf; do
	target_path="${spec%%:*}"
	backup_name="${spec##*:}"
	if [ -f "$backup_dir/$backup_name" ]; then
		cp "$backup_dir/$backup_name" "$target_path"
	elif [ -f "$backup_dir/$backup_name.absent" ]; then
		rm -f "$target_path"
	else
		echo "警告：旧备份缺少文件状态记录，保持当前文件不变：$target_path" >&2
	fi
done

for service in udpxy igmpproxy; do
	if [ -f "$backup_dir/$service.enabled" ]; then
		/etc/init.d/$service enable
	elif [ -f "$backup_dir/$service.disabled" ]; then
		/etc/init.d/$service disable
	else
		echo "警告：旧备份缺少服务启用状态，保持当前状态不变：$service" >&2
	fi
done

echo "备份已恢复。请从具备恢复能力的连接重载服务或重启设备。"
