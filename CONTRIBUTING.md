# 贡献指南

- 路由器配置快照只保留 IPTV 相关的 UCI 配置段，禁止提交完整的路由器备份。
- 禁止提交 IPTV 账号、机顶盒序列号、密码、VPN 私钥、令牌或抓包文件。
- 使用 `python3 -m py_compile` 验证 Python，使用 `docker compose config` 验证 Compose 配置。
- 修改频道映射后，应根据 `status.json` 报告节目单匹配覆盖率；不要使用可能导致节目串台的模糊匹配。
- HLS 媒体后端与 EPG 元数据服务必须保持独立部署能力。
- 修改路由器、Docker 服务或 Android 客户端时，必须记录对应的回滚步骤。
