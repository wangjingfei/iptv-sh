# IPTV EPG

Standalone XMLTV filter and M3U generator for the existing `iptv-hls` service.

Endpoints after startup:

- `http://192.168.31.200:8091/playlist.m3u`
- `http://192.168.31.200:8091/epg.xml`
- `http://192.168.31.200:8091/status.json`

The service refreshes every six hours. A failed refresh keeps the last valid
cache. It only reads the existing `../iptv-hls/config/channels.json` file and
does not modify or restart the live TV service.

Start it from the FnOS Docker Compose UI using this directory, or run:

```sh
docker compose up -d --build
```
