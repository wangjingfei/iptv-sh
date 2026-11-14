# Router Setup and Recovery

This documents the working GL-MT6000/ImmortalWrt layout. Port names and UCI device definitions are model-specific. Read [current-state.md](current-state.md) and make a private `sysupgrade -k -b` backup before changing the router.

## Cable Layout

1. Optical modem IPTV port -> router LAN5.
2. Router LAN4 -> ZTE set-top box.
3. Keep normal WAN connected for regular Internet service.

## Required Behavior

1. Create `lan5.85` on `lan5`, VID 85.
2. Create `lan4.85` on `lan4`, VID 85.
3. Bridge `lan5.85` and `lan4.85` on `br-iptv`.
4. Keep untagged `lan4` in a separate `br-stb` bridge, `192.168.32.1/24`.
5. Run a local STB DHCP pool and exact forced DHCP Option 125 from `router/snapshots/iptv-dhcp-firewall.uci`.
6. Reserve the known STB MAC at `192.168.32.2`.
7. DHCP on `br-iptv` must not install a default route or peer DNS. The observed lease is `30.171.109.55/16`, gateway `30.171.0.1`.
8. Keep IPTV firewall input/forward default-reject; allow only required multicast and IGMP locally. Keep STB-to-main-LAN forwarding absent.
9. Run IGMP proxy for routed STB multicast only if needed by the active design; the current ZTE's IPTV VLAN itself is transparent L2.
10. `udpxy` must use `br-iptv` as its source and bind to the main LAN address.

The exact live relevant UCI sections are in `router/snapshots/`. DHCP Option 125 is not an account password but is still operator/device-specific; verify that the target STB model accepts it before deployment.

## Read-Only Verification

```sh
ssh root@192.168.31.1 'ubus call network.interface.iptv_probe status'
ssh root@192.168.31.1 'ip route show default; ip route show dev br-iptv'
ssh root@192.168.31.1 'ip -4 addr show br-iptv; ip -4 addr show br-stb'
ssh root@192.168.31.1 'grep -i d4:ad:2d:a5:76:18 /tmp/dhcp.leases'
ssh root@192.168.31.1 '/etc/init.d/udpxy status; /etc/init.d/igmpproxy status'
curl -fsS http://192.168.31.1:4022/status/ | head
```

Expected: main default route remains via WAN; `br-iptv` has an IPTV DHCP lease; STB has both the local DHCP lease and operator VLAN connectivity; udpxy and multicast route remain active.

## Change Discipline

- Back up current configuration first: `sysupgrade -k -b /tmp/router-backup.tar.gz`, then securely copy it off-router and verify `gzip -t`.
- Include `/etc/init.d/udpxy` and `/etc/sysctl.d/90-udpxy-buffer.conf` in the backup allow-list; see router backup notes.
- Apply interface changes one at a time; do not reload all networking over remote SSH unless a recovery path is available.
- After every change, verify WAN default route, main LAN reachability, NAS `:8090`, STB local DHCP, and an udpxy stream.
- Roll back by restoring the saved backup/config snippets or reverting only the changed UCI section, then reload the affected service.
- Do not run `sysupgrade -r` on the active router without confirming the archive, model and firmware match and planning for its reboot.

## Known-Scope Note

`igmpproxy` currently declares upstream `altnet 0.0.0.0/0`. This was part of the working setup. Narrow it only after collecting real source networks and testing multicast; do not broaden firewall forwarding as a workaround.
