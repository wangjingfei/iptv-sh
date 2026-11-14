# Home IPTV

上海电信 IPTV 家庭内网播放方案，覆盖路由器 VLAN 85 透传、udpxy 单播、NAS 上的按需 HLS、XMLTV 节目单，以及 Android TV 播放器的后续规划。

## Current Deployment

| Component | Location | Role |
| --- | --- | --- |
| GL-MT6000 / ImmortalWrt | `192.168.31.1` | IPTV VLAN 85 bridge, STB DHCP/Option 125, udpxy and multicast proxy |
| FnOS NAS | `192.168.31.200` | Docker host |
| `iptv-hls` | TCP `8090` | Converts selected udpxy MPEG-TS sources to on-demand HLS |
| `iptv-epg` | TCP `8091` | Generates an EPG-enriched M3U playlist and filtered XMLTV |
| IPTV STB | LAN4 | Untagged isolated network plus transparent VLAN 85 tagged path |

See [docs/current-state.md](docs/current-state.md) before changing the live network.

## Quick Start

```sh
make check
```

For a new installation, follow this order:

1. Read [topology](docs/topology.md) and cable the IPTV input/STB ports.
2. Back up the router, then review [router setup](docs/router-setup.md) and the guarded `router/apply-iptv.sh` script.
3. Copy `docker/iptv-hls` and `docker/iptv-epg` to sibling directories on the NAS and follow [NAS Docker deployment](docs/nas-docker.md).
4. Import `http://192.168.31.200:8091/playlist.m3u` in an XMLTV-capable player, or keep using port 8090 when no guide is needed.
5. Use [troubleshooting](docs/troubleshooting.md) for read-only checks before changing the network.

## Service Relationship

`iptv-hls` is the media backend. `iptv-epg` only publishes metadata and a playlist whose channel URLs point at `iptv-hls`. They are complementary, independently deployable services. The existing source list can continue to be used directly when EPG is not needed.

## Repository Layout

- `router/`: sanitized UCI snapshots, read-only checks and router setup/rollback notes.
- `docker/iptv-hls/`: deployed HLS app, Dockerfile, Compose and channel map.
- `docker/iptv-epg/`: EPG/M3U generator and Compose deployment.
- `docs/`: topology, procedures, troubleshooting, EPG caveats and Android TV app plan.
- `android-tv/`: product and technical design only; no APK is implemented yet.

The future TV client plan is in [docs/android-tv-player.md](docs/android-tv-player.md). The `android-tv/` directory is intentionally reserved for its eventual Gradle project.

## Security

This repository intentionally excludes Wi-Fi passwords, VPN private keys, IPTV account/SN, router backups, generated EPG caches and packet captures. Keep credentials in local untracked `.env` files or device-specific secret storage. Check `git status` and scan the staged diff before every commit.
