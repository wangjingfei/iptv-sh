#!/usr/bin/env python3
import argparse
import json
import re
import time
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CHANNELS_PATH = ROOT / "docker/iptv-hls/config/channels.json"
HLS_BASE = "http://192.168.31.200:8090"
EPG_BASE = "http://192.168.31.200:8091"
UDPXY_STATUS = "http://192.168.31.1:4022/status/"


def fetch(url, timeout=10, limit=None):
    with urllib.request.urlopen(url, timeout=timeout) as response:
        return response.read() if limit is None else response.read(limit)


def probe_source(item):
    channel_id, channel = item
    started = time.monotonic()
    try:
        body = fetch(channel["source"], timeout=7, limit=1316)
        if len(body) < 188:
            raise RuntimeError(f"只收到 {len(body)} 字节")
        return channel_id, channel["name"], None, time.monotonic() - started
    except Exception as exc:
        return channel_id, channel["name"], str(exc), time.monotonic() - started


def active_udpxy_clients():
    try:
        body = fetch(UDPXY_STATUS, timeout=3).decode("utf-8", "replace")
        match = re.search(
            r"<th>Active clients</th>.*?<tr>.*?<td>.*?</td>.*?<td>.*?</td>"
            r".*?<td>.*?</td>.*?<td>(\d+)</td>",
            body,
            re.S,
        )
        return int(match.group(1)) if match else None
    except Exception:
        return None


def wait_for_udpxy_cleanup(timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        active = active_udpxy_clients()
        if active in (None, 0):
            return
        time.sleep(2)


def check_all_sources(channels):
    failures = []
    items = list(channels.items())
    for start in range(0, len(items), 8):
        batch = items[start : start + 8]
        with ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(probe_source, batch))
        for channel_id, name, error, elapsed in results:
            if error:
                failures.append(f"{name} ({channel_id})：{error}")
        print(f"已检查组播源 {min(start + 8, len(items))}/{len(items)}")
        if start + 8 < len(items):
            wait_for_udpxy_cleanup()
    if failures:
        raise RuntimeError("组播源失败：\n" + "\n".join(failures))


def check_hls(sample_id):
    playlist = fetch(f"{HLS_BASE}/{sample_id}/index.m3u8", timeout=30).decode("utf-8")
    if "#EXTM3U" not in playlist or ".ts" not in playlist:
        raise RuntimeError("HLS 播放列表没有媒体分片")


def check_epg(channel_count):
    status = json.loads(fetch(f"{EPG_BASE}/status.json").decode("utf-8"))
    if status.get("total_channels") != channel_count:
        raise RuntimeError(
            f"EPG 频道数为 {status.get('total_channels')}，频道表为 {channel_count}；请刷新 EPG 缓存"
        )
    playlist = fetch(f"{EPG_BASE}/playlist.m3u").decode("utf-8")
    xml_root = ET.fromstring(fetch(f"{EPG_BASE}/epg.xml", timeout=20))
    playlist_ids = re.findall(r'tvg-id="([^"]+)"', playlist)
    xml_ids = {node.get("id") for node in xml_root.findall("channel")}
    missing = sorted(set(playlist_ids) - xml_ids)
    if len(playlist_ids) != channel_count or missing:
        raise RuntimeError(f"EPG 播放列表或 XMLTV 不完整，缺失 ID：{missing}")
    return status


def main():
    parser = argparse.ArgumentParser(description="检查家庭 IPTV 的真实媒体与 EPG 状态")
    parser.add_argument("--skip-all-sources", action="store_true", help="只抽测一条媒体源")
    args = parser.parse_args()

    channels = json.loads(CHANNELS_PATH.read_text(encoding="utf-8"))["channels"]
    sample_id = next(iter(channels))

    if args.skip_all_sources:
        channel_id, name, error, _elapsed = probe_source((sample_id, channels[sample_id]))
        if error:
            raise RuntimeError(f"抽测频道 {name} ({channel_id}) 失败：{error}")
        source_summary = f"抽测 1/{len(channels)} 个组播源"
    else:
        check_all_sources(channels)
        source_summary = f"检查 {len(channels)} 个组播源"

    check_hls(sample_id)
    status = check_epg(len(channels))
    print(
        f"通过：{source_summary}、HLS 实际播放、EPG "
        f"{status['matched_channels']}/{status['total_channels']} 个频道匹配"
    )


if __name__ == "__main__":
    main()
