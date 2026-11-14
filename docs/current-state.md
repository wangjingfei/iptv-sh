# Current State

Last live check: 2026-11-14 Asia/Shanghai. This is an operational snapshot, not a claim that every upstream IPTV endpoint will remain stable.

## Hardware and Firmware

- Router: GL-MT6000 / Flint 2, ImmortalWrt 25.12.2, `mediatek/filogic`.
- Router LAN: `192.168.31.1/24`; main WAN currently gets `192.168.100.x` from the upstream gateway. Main default route remains on WAN.
- FnOS NAS: `192.168.31.200`.
- STB: ZTE, MAC `D4:AD:2D:A5:76:18`, untagged DHCP reservation `192.168.32.2`.

## Physical Ports

| Port | Cable | Router treatment |
| --- | --- | --- |
| LAN5 | Optical modem IPTV port | `lan5.85`, IPTV VLAN 85 tagged |
| LAN4 | ZTE STB | Untagged frames join isolated `br-stb`; VLAN 85 tagged frames use `lan4.85` and bridge transparently to LAN5 |

The ZTE box uses both networks: the untagged local DHCP service supplies Option 125 and normal Internet access; VLAN 85 provides the operator IPTV network. Do not remove either side of the hybrid LAN4 configuration.

## Router Services

- `br-iptv`: bridge of `lan5.85` and `lan4.85`.
- `iptv_probe`: DHCP on `br-iptv`, no default route and no peer DNS; observed address `30.171.109.55/16`, DHCP gateway `30.171.0.1`.
- IPTV route `30.171.0.0/16` is connected on `br-iptv`; main default route is still the regular WAN.
- `br-stb`: untagged LAN4 only, `192.168.32.1/24`, DHCP range `.2-.21`.
- DHCP Option 125 uses the Shanghai Telecom ZTE value from the owner's prior configuration record.
- Firewall keeps the STB subnet in its own zone and permits STB-to-WAN; no STB-to-main-LAN forwarding is configured.
- `igmpproxy` handles native multicast for the set-top-box path.
- `udpxy` listens on `192.168.31.1:4022`, source `br-iptv`, max clients 20, buffer 1 MiB, socket buffer 4 MiB.
- Custom `/etc/init.d/udpxy` supports the socket buffer parameter; sysctl `rmem_max=8388608`.

Sanitized relevant UCI sections are in `router/snapshots/`. They are a reference and must not be blindly replayed on a different model or firmware.

## NAS Services

- HLS: `http://192.168.31.200:8090`, Compose directory `/vol1/1000/dockers/iptv-hls`.
- EPG: `http://192.168.31.200:8091`, Compose directory `/vol1/1000/dockers/iptv-epg`.
- Both returned healthy in the last check.
- HLS reads 113 channel entries from `channels.json`; each source is an HTTP udpxy URL through the router.
- EPG builds a second M3U that points at the HLS service and an XMLTV file. The last cache had 113 channels, 100 matches and 13,802 programme records.

## EPG Limitation

Current EPG source is the public `https://epg.pw/xmltv/epg_CN.xml.gz`, filtered and name-matched locally. It is not the ZTE box's native Shanghai Telecom EPG. Thirteen channels currently have no mapping; see the live EPG `status.json` endpoint. Channel names, coverage and upstream availability can change.

An earlier explored project can fetch Shanghai Telecom's native list/EPG but requires IPTV user ID, STB SN and MAC and access to the operator private network. The SN has not been recovered/verified, no native fetcher is deployed, and the NAS container is not currently routed through IPTV VLAN. Do not add account/SN/token values to Git.
