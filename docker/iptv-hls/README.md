# IPTV HLS Docker

This project converts an udpxy HTTP MPEG-TS stream to on-demand HLS. FFmpeg starts when a player requests a channel and stops after the configured idle timeout.

## 1. Configure channels

Edit `config/channels.json`. Replace the example multicast address and port with a real channel:

```json
{
  "channels": {
    "cctv1": {
      "name": "CCTV-1",
      "source": "http://192.168.31.1:4022/udp/239.1.2.3:1234/"
    }
  }
}
```

Channel IDs may contain letters, numbers, `_`, and `-`. Changes are reloaded automatically on the next request. If a source URL changes, the old FFmpeg process is replaced on the next request.

## 2. Build and start

```sh
docker compose config
docker compose up -d --build
docker compose ps
docker compose logs -f
```

## 3. Test

```sh
curl http://127.0.0.1:8090/healthz
curl http://127.0.0.1:8090/api/status
curl http://127.0.0.1:8090/playlist.m3u
```

LAN playlist URL:

```text
http://192.168.31.200:8090/playlist.m3u
```

Individual channel URL:

```text
http://192.168.31.200:8090/cctv1/index.m3u8
```

The first request waits for FFmpeg to create the initial playlist. Later segment requests keep the channel active. The default idle timeout is 60 seconds.

## Storage and networking

- HLS playlists and segments are stored in the container tmpfs at `/run/iptv-hls`.
- The tmpfs limit is 512 MiB.
- The container uses Docker bridge networking and publishes only TCP port 8090.
- No host networking, privileged mode, or GPU device is required because FFmpeg uses stream copy rather than transcoding.
- The project directory stores only configuration and source files; it does not contain live video segments.

## Stop and remove

```sh
docker compose down
```

This stops all FFmpeg processes and discards all temporary HLS files.
