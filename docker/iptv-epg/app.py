#!/usr/bin/env python3
import gzip
import json
import os
import re
import threading
import time
import unicodedata
import urllib.request
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


CHANNELS_PATH = Path(os.environ.get("CHANNELS_PATH", "/app/config/channels.json"))
DATA_DIR = Path(os.environ.get("DATA_DIR", "/app/data"))
SOURCE_URL = os.environ.get("EPG_SOURCE_URL", "https://epg.pw/xmltv/epg_CN.xml.gz")
HLS_BASE_URL = os.environ.get("IPTV_HLS_BASE_URL", "http://192.168.31.200:8090").rstrip("/")
BIND = os.environ.get("EPG_BIND", "0.0.0.0")
PORT = int(os.environ.get("EPG_PORT", "8091"))
REFRESH_SECONDS = max(900, int(os.environ.get("EPG_REFRESH_SECONDS", "21600")))
REQUEST_TIMEOUT = int(os.environ.get("EPG_REQUEST_TIMEOUT", "90"))

EPG_PATH = DATA_DIR / "epg.xml"
PLAYLIST_PATH = DATA_DIR / "playlist.m3u"
STATUS_PATH = DATA_DIR / "status.json"


ALIASES = {
    "上海新闻综合": ["新闻综合", "上海新闻综合频道"],
    "上海都市": ["都市频道", "上海都市频道"],
    "东方影视": ["上视东方影视"],
    "上海第一财经": ["第一财经", "第一财经频道"],
    "东方财经": ["东方财经浦东", "东方财经频道"],
    "上海教育": ["上海教育台"],
    "CETV早期教育": ["早期教育"],
    "CCTV5+": ["CCTV5+体育赛事", "CCTV5PLUS"],
    "CCTV16": ["CCTV16奥林匹克"],
    "CCTV16「4K」": ["CCTV16奥林匹克4K", "CCTV16 4K"],
    "CCTV4K「4K」": ["CCTV4K", "CCTV 4K"],
    "东方卫视「4K」": ["东方卫视4K"],
    "北京卫视「4K」": ["北京卫视4K"],
    "广东卫视「4K」": ["广东卫视4K"],
    "湖南卫视「4K」": ["湖南卫视4K"],
    "江苏卫视「4K」": ["江苏卫视4K"],
    "山东卫视「4K」": ["山东卫视4K"],
    "深圳卫视「4K」": ["深圳卫视4K"],
    "四川卫视「4K」": ["四川卫视4K"],
    "浙江卫视「4K」": ["浙江卫视4K"],
}


def log(message):
    print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {message}", flush=True)


def normalize(name):
    value = unicodedata.normalize("NFKC", name or "").upper().strip()
    value = value.replace("中央电视台", "CCTV").replace("央视", "CCTV")
    value = value.replace("PLUS", "+")
    value = re.sub(r"[（(](?:高清|超高清|标清|电信|IPTV)[）)]", "", value)
    value = re.sub(r"(?:高清|超高清|标清|频道|电视台)$", "", value)
    value = re.sub(r"[\s·•_\-—–:：,，.。/\\\[\]【】()（）<>《》\"'「」]+", "", value)
    return value


def candidate_keys(name):
    values = [name, *ALIASES.get(name, [])]
    normalized = []
    for value in values:
        key = normalize(value)
        if key and key not in normalized:
            normalized.append(key)
        if key.endswith("4K"):
            base = key[:-2]
            if base and base not in normalized:
                normalized.append(base)
    return normalized


def load_local_channels():
    payload = json.loads(CHANNELS_PATH.read_text(encoding="utf-8"))
    channels = payload.get("channels")
    if not isinstance(channels, dict) or not channels:
        raise ValueError("channels.json 中没有频道")
    result = []
    for channel_id, item in channels.items():
        name = item.get("name") if isinstance(item, dict) else None
        if isinstance(name, str) and name.strip():
            result.append((channel_id, name.strip()))
    return result


def download_source():
    request = urllib.request.Request(
        SOURCE_URL,
        headers={"User-Agent": "iptv-epg-home/1.0", "Accept-Encoding": "identity"},
    )
    with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT) as response:
        body = response.read()
    if SOURCE_URL.lower().endswith(".gz") or body[:2] == b"\x1f\x8b":
        body = gzip.decompress(body)
    return body


def source_index(root):
    programmes_by_id = defaultdict(list)
    counts = Counter()
    for programme in root.findall("programme"):
        source_id = programme.get("channel")
        if source_id:
            programmes_by_id[source_id].append(programme)
            counts[source_id] += 1

    by_name = defaultdict(list)
    source_names = {}
    for channel in root.findall("channel"):
        source_id = channel.get("id")
        names = [node.text.strip() for node in channel.findall("display-name") if node.text and node.text.strip()]
        if not source_id or not names:
            continue
        source_names[source_id] = names[0]
        for name in names:
            key = normalize(name)
            if key and source_id not in by_name[key]:
                by_name[key].append(source_id)
    for key in by_name:
        by_name[key].sort(key=lambda item: counts[item], reverse=True)
    return by_name, source_names, programmes_by_id


def find_source_id(local_name, by_name):
    for key in candidate_keys(local_name):
        matches = by_name.get(key)
        if matches:
            return matches[0]
    return None


def clone_element(element):
    return ET.fromstring(ET.tostring(element, encoding="utf-8"))


def atomic_write(path, data, binary=False):
    temp = path.with_suffix(path.suffix + ".tmp")
    if binary:
        temp.write_bytes(data)
    else:
        temp.write_text(data, encoding="utf-8")
    os.replace(temp, path)


def build_outputs(source_body):
    local_channels = load_local_channels()
    source_root = ET.fromstring(source_body)
    by_name, source_names, programmes = source_index(source_root)

    output = ET.Element(
        "tv",
        {
            "generator-info-name": "iptv-epg-home",
            "source-info-url": SOURCE_URL,
        },
    )
    matched = []
    unmatched = []
    programme_count = 0

    for channel_id, local_name in local_channels:
        source_id = find_source_id(local_name, by_name)
        channel = ET.SubElement(output, "channel", {"id": channel_id})
        ET.SubElement(channel, "display-name", {"lang": "zh"}).text = local_name

        if source_id:
            matched.append({"id": channel_id, "name": local_name, "source": source_names.get(source_id, source_id)})
            for programme in programmes.get(source_id, []):
                copy = clone_element(programme)
                copy.set("channel", channel_id)
                output.append(copy)
                programme_count += 1
        else:
            unmatched.append({"id": channel_id, "name": local_name})

    ET.indent(output, space="  ")
    xml_body = b"<?xml version='1.0' encoding='UTF-8'?>\n" + ET.tostring(output, encoding="utf-8") + b"\n"

    lines = [f'#EXTM3U x-tvg-url="http://192.168.31.200:{PORT}/epg.xml"']
    matched_ids = {item["id"] for item in matched}
    for channel_id, name in local_channels:
        escaped_name = name.replace('"', "'")
        lines.append(f'#EXTINF:-1 tvg-id="{channel_id}" tvg-name="{escaped_name}",{name}')
        lines.append(f"{HLS_BASE_URL}/{channel_id}/index.m3u8")
    playlist = "\n".join(lines) + "\n"

    now = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    status = {
        "updated_at": now,
        "source_url": SOURCE_URL,
        "total_channels": len(local_channels),
        "matched_channels": len(matched_ids),
        "unmatched_channels": len(unmatched),
        "programme_count": programme_count,
        "matched": matched,
        "unmatched": unmatched,
    }
    return xml_body, playlist, status


refresh_lock = threading.Lock()


def refresh():
    if not refresh_lock.acquire(blocking=False):
        return
    try:
        log(f"正在从 {SOURCE_URL} 刷新 EPG")
        source_body = download_source()
        xml_body, playlist, status = build_outputs(source_body)
        DATA_DIR.mkdir(parents=True, exist_ok=True)
        atomic_write(EPG_PATH, xml_body, binary=True)
        atomic_write(PLAYLIST_PATH, playlist)
        atomic_write(STATUS_PATH, json.dumps(status, ensure_ascii=False, indent=2) + "\n")
        log(
            f"EPG 已就绪：{status['matched_channels']}/{status['total_channels']} 个频道，"
            f"{status['programme_count']} 条节目"
        )
    except Exception as exc:
        log(f"EPG 刷新失败，保留旧缓存：{exc}")
        if not STATUS_PATH.exists():
            DATA_DIR.mkdir(parents=True, exist_ok=True)
            atomic_write(
                STATUS_PATH,
                json.dumps({"error": str(exc), "updated_at": None}, ensure_ascii=False, indent=2) + "\n",
            )
    finally:
        refresh_lock.release()


def refresh_loop():
    while True:
        refresh()
        time.sleep(REFRESH_SECONDS)


class Handler(BaseHTTPRequestHandler):
    server_version = "iptv-epg-home/1.0"

    def send_file(self, path, content_type):
        if not path.exists():
            self.send_error(503, "EPG 缓存尚未就绪")
            return
        body = path.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path in ("/", "/playlist.m3u"):
            self.send_file(PLAYLIST_PATH, "audio/x-mpegurl; charset=utf-8")
        elif path == "/epg.xml":
            self.send_file(EPG_PATH, "application/xml; charset=utf-8")
        elif path == "/status.json":
            self.send_file(STATUS_PATH, "application/json; charset=utf-8")
        elif path == "/healthz":
            if EPG_PATH.exists() and PLAYLIST_PATH.exists():
                body = b"ok\n"
                self.send_response(200)
                self.send_header("Content-Type", "text/plain")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            else:
                self.send_error(503, "EPG 缓存尚未就绪")
        else:
            self.send_error(404)

    def log_message(self, fmt, *args):
        log(f"{self.client_address[0]} {fmt % args}")


def main():
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    if os.environ.get("EPG_ONESHOT") == "1":
        refresh()
        status = json.loads(STATUS_PATH.read_text(encoding="utf-8"))
        print(json.dumps(status, ensure_ascii=False, indent=2))
        raise SystemExit(0 if status.get("matched_channels", 0) else 1)

    thread = threading.Thread(target=refresh_loop, name="epg-refresh", daemon=True)
    thread.start()
    server = ThreadingHTTPServer((BIND, PORT), Handler)
    log(f"正在监听 http://{BIND}:{PORT}")
    server.serve_forever()


if __name__ == "__main__":
    main()
