# NAS Docker 部署

当前生产目录在飞牛 NAS 上并列存放：

```text
/vol1/1000/dockers/
  iptv-hls/
  iptv-epg/
```

仓库在 `docker/` 下保持相同的相对目录关系，因此从 `docker/iptv-epg` 启动时，EPG Compose 中的绑定挂载 `../iptv-hls/config/channels.json` 可以正确解析。

## HLS 服务

```sh
cd /vol1/1000/dockers/iptv-hls
docker compose config
docker compose up -d --build
docker compose ps
curl -fsS http://127.0.0.1:8090/healthz
curl -fsS http://127.0.0.1:8090/playlist.m3u
```

HLS 转换使用 FFmpeg 流复制，不进行 4K 转码。服务按需启动，输出存放在 512 MiB 的 tmpfs 中，容器停止后自动消失。新增或修改频道时编辑 `config/channels.json`，并填写真实的路由器 udpxy 地址。

## EPG 服务

```sh
cd /vol1/1000/dockers/iptv-epg
docker compose config
docker compose up -d --build
docker compose ps
curl -fsS http://127.0.0.1:8091/healthz
curl -fsS http://127.0.0.1:8091/status.json
```

该服务以只读方式加载 HLS 频道表，每 6 小时获取配置的公共 XMLTV 源，在 `data/` 下以原子写入方式更新缓存；更新失败时保留上一份有效缓存。

播放器地址：

- 带 EPG 的播放列表：`http://192.168.31.200:8091/playlist.m3u`
- 直接访问 XMLTV：`http://192.168.31.200:8091/epg.xml`
- 原有 HLS 播放列表：`http://192.168.31.200:8090/playlist.m3u`

只有支持 XMLTV 的播放器才需要使用 EPG 播放列表。原有 HLS 地址仍然有效，并继续作为视频后端。

## 飞牛管理界面

使用飞牛 Docker 应用时，应分别从各自目录创建或导入两个 Compose 项目，不要将两个服务合并进同一个容器。HLS 和 EPG 可以独立重启，分别使用 8090 和 8091 端口。

## 更新与恢复

1. 备份需要修改的 Compose 目录和 `iptv-hls/config/channels.json`。
2. 检查 `git diff` 和 `docker compose config`。
3. 在暂存目录或维护窗口中更新文件。
4. 进入发生变化的服务目录，执行 `docker compose up -d --build`，只重建该服务。
5. 检查健康状态、播放列表和日志。
6. 回滚时恢复旧目录或文件，并只重建对应服务。

禁止提交 `docker/iptv-epg/data/*.xml`、生成的播放列表或运行状态文件；这些文件已被忽略。
