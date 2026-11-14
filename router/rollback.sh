#!/bin/sh
set -eu

backup_dir="${1:-}"
[ -n "$backup_dir" ] && [ -d "$backup_dir" ] || {
	echo "Usage: $0 /root/rollback/YYYYMMDD-HHMMSS-home-iptv" >&2
	exit 1
}
[ "${ROLLBACK:-0}" = "1" ] || {
	echo "Dry run only. Review $backup_dir, then rerun with ROLLBACK=1." >&2
	exit 0
}

for name in network dhcp firewall udpxy igmpproxy; do
	[ -f "$backup_dir/$name" ] || { echo "Missing backup file: $name" >&2; exit 1; }
done

for name in network dhcp firewall udpxy igmpproxy; do
	cp "$backup_dir/$name" "/etc/config/$name"
done
[ ! -f "$backup_dir/udpxy.init" ] || cp "$backup_dir/udpxy.init" /etc/init.d/udpxy
[ ! -f "$backup_dir/90-udpxy-buffer.conf" ] || cp "$backup_dir/90-udpxy-buffer.conf" /etc/sysctl.d/90-udpxy-buffer.conf

echo "Backup restored. Reload services or reboot from a recovery-capable connection."
