# Contributing

- Keep router snapshots limited to IPTV-related UCI sections; never add a full router backup.
- Never commit account IDs, STB serial numbers, passwords, VPN private keys, tokens or packet captures.
- Validate Python with `python3 -m py_compile` and Compose with `docker compose config`.
- For a channel-map change, report guide-match coverage from `status.json`; don't create fuzzy mappings that can attach the wrong programme.
- Keep the HLS backend and EPG metadata service independently deployable.
- Record a rollback step for router, Docker, and Android client changes.
