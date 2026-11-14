# IPTV EPG 服务

为现有 `iptv-hls` 服务提供独立的 XMLTV 筛选和 M3U 生成功能。

启动后可访问以下接口：

- `http://192.168.31.200:8091/playlist.m3u`
- `http://192.168.31.200:8091/epg.xml`
- `http://192.168.31.200:8091/status.json`

服务每 6 小时更新一次节目单。更新失败时会继续使用上一份有效缓存。服务只读访问现有的 `../iptv-hls/config/channels.json`，不会修改或重启直播服务。

可以在飞牛 Docker Compose 管理界面中使用本目录启动服务，也可以执行：

```sh
docker compose up -d --build
```
