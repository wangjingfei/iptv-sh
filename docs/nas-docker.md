# NAS Docker Deployment

The production folders currently live side by side on FnOS:

```text
/vol1/1000/dockers/
  iptv-hls/
  iptv-epg/
```

The repository preserves the same relative relationship under `docker/`, so the EPG Compose bind mount `../iptv-hls/config/channels.json` resolves correctly when launched from `docker/iptv-epg`.

## HLS Service

```sh
cd /vol1/1000/dockers/iptv-hls
docker compose config
docker compose up -d --build
docker compose ps
curl -fsS http://127.0.0.1:8090/healthz
curl -fsS http://127.0.0.1:8090/playlist.m3u
```

HLS conversion uses FFmpeg stream-copy, not 4K transcoding. It starts on demand. Output is in a 512 MiB tmpfs and disappears when the container stops. Edit `config/channels.json` to add/change source channels; use actual router udpxy URLs.

## EPG Service

```sh
cd /vol1/1000/dockers/iptv-epg
docker compose config
docker compose up -d --build
docker compose ps
curl -fsS http://127.0.0.1:8091/healthz
curl -fsS http://127.0.0.1:8091/status.json
```

The service reads the HLS channel map read-only, fetches the configured public XMLTV source every six hours, writes atomic cache files under `data/`, and retains the last cache if an update fails.

Player URLs:

- EPG-enabled playlist: `http://192.168.31.200:8091/playlist.m3u`
- XMLTV directly: `http://192.168.31.200:8091/epg.xml`
- Existing HLS playlist: `http://192.168.31.200:8090/playlist.m3u`

Use the EPG playlist only in players that support XMLTV. Existing HLS URLs remain valid and still serve the video backend.

## FnOS UI

If using the FnOS Docker app, create/import each Compose project separately from its directory. Do not combine the services into a single container. HLS and EPG can be independently restarted; they use ports 8090 and 8091 respectively.

## Update and Recovery

1. Back up the relevant compose directory and `iptv-hls/config/channels.json`.
2. Review `git diff` and `docker compose config`.
3. Update files in a staging directory or maintenance window.
4. Recreate only the changed service with `docker compose up -d --build` from that service's directory.
5. Check health, playlist and logs.
6. Restore the previous directory/files and recreate that one service to roll back.

Do not commit `docker/iptv-epg/data/*.xml`, generated playlists or runtime status; they are ignored.
