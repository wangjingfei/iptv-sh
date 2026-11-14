# IPTV HLS Docker 服务

本项目将 udpxy 提供的 HTTP MPEG-TS 直播流按需转换为 HLS。播放器请求某个频道时，服务启动对应的 FFmpeg 进程；超过配置的空闲时间后，进程自动停止。

## 1. 配置频道

编辑 `config/channels.json`，将示例组播地址和端口替换为真实频道：

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

频道 ID 可以包含字母、数字、`_` 和 `-`。配置变化会在下一次请求时自动加载。如果频道源地址发生变化，下一次请求会停止旧 FFmpeg 进程并使用新地址启动。

## 2. 构建并启动

```sh
docker compose config
docker compose up -d --build
docker compose ps
docker compose logs -f
```

## 3. 验证

```sh
curl http://127.0.0.1:8090/healthz
curl http://127.0.0.1:8090/api/status
curl http://127.0.0.1:8090/playlist.m3u
```

局域网播放列表地址：

```text
http://192.168.31.200:8090/playlist.m3u
```

单个频道地址：

```text
http://192.168.31.200:8090/cctv1/index.m3u8
```

首次请求会等待 FFmpeg 生成初始播放列表。后续分片请求会保持频道处于活动状态。默认空闲超时时间为 60 秒。

## 存储和网络

- HLS 播放列表与分片存放在容器的 `/run/iptv-hls` tmpfs 中。
- tmpfs 上限为 512 MiB。
- 容器使用 Docker 桥接网络，只发布 TCP 8090 端口。
- FFmpeg 使用流复制而非转码，因此不需要宿主机网络、特权模式或 GPU 设备。
- 项目目录只存放配置和源代码，不保存直播视频分片。

## 停止并移除

```sh
docker compose down
```

该命令会停止所有 FFmpeg 进程，并丢弃全部临时 HLS 文件。
