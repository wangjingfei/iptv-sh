# Troubleshooting

| Symptom | First checks | Likely area |
| --- | --- | --- |
| STB says no network | Confirm untagged `192.168.32.2` lease and `br-stb` link; verify DHCP Offer/ACK includes Option 125 | LAN4 untagged DHCP / MAC reservation |
| STB has Internet but IPTV home is unavailable | Check the tagged VLAN 85 DHCP frames on LAN4 and IPTV gateway/EPG TCP responses | LAN4.85 to LAN5.85 bridge, modem IPTV binding |
| NAS playlist loads but channel is black | Check HLS `/api/status`, channel source URL, udpxy service, then multicast on `br-iptv` | Channel map / udpxy / IGMP membership |
| HLS startup takes long or returns 503 | Inspect container logs and confirm the source URL is valid MPEG-TS over HTTP | FFmpeg input and stream startup |
| HLS buffers under 4K multicast | Compare router UDP errors before/after, check per-stream bit rate, NAS/client LAN and CPU; avoid assuming HLS is transcoding | Loss on multicast or network path |
| EPG list works but no guide appears | Fetch `/status.json`; compare M3U `tvg-id` with XMLTV `channel id`; check player supports XMLTV | EPG source coverage or channel mapping |
| EPG container healthy but no fresh guide | Check update timestamp and logs; upstream may be unavailable; last known-good cache is retained | Public XMLTV upstream/network |
| Only 100/113 channels show a guide | Inspect `unmatched` in status JSON; do not map similarly named channels unless they are the same service | Missing source coverage / alias mapping |

Useful read-only checks:

```sh
curl -fsS http://192.168.31.200:8090/healthz
curl -fsS http://192.168.31.200:8090/api/status
curl -fsS http://192.168.31.200:8091/healthz
curl -fsS http://192.168.31.200:8091/status.json
ssh root@192.168.31.1 'ip route show default; ip route show dev br-iptv; netstat -su'
```

Capture only the needed interface and duration. DHCP captures may include device identifiers; IPTV HTTP captures can expose subscriber IDs or auth material. Keep packet captures outside Git and remove them after analysis.
