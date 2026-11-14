#!/usr/bin/env python3
import ast
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
errors = []


def fail(message):
    errors.append(message)


for source in (ROOT / "docker/iptv-hls/app.py", ROOT / "docker/iptv-epg/app.py", ROOT / "scripts/live_check.py"):
    try:
        ast.parse(source.read_text(encoding="utf-8"), filename=str(source))
    except Exception as exc:
        fail(f"{source.relative_to(ROOT)}: {exc}")

channels_path = ROOT / "docker/iptv-hls/config/channels.json"
try:
    payload = json.loads(channels_path.read_text(encoding="utf-8"))
    channels = payload["channels"]
    if not channels:
        fail("channels.json 中没有频道")
    channel_id = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
    source_url = re.compile(r"^http://192\.168\.31\.1:4022/(?:udp|rtp)/\d+(?:\.\d+){3}:\d+/$")
    for key, value in channels.items():
        if not channel_id.fullmatch(key):
            fail(f"无效频道 ID：{key}")
        if not isinstance(value, dict) or not str(value.get("name", "")).strip():
            fail(f"无效频道记录：{key}")
        if not source_url.fullmatch(str(value.get("source", ""))):
            fail(f"频道 {key} 的源地址不符合预期")
except Exception as exc:
    fail(f"channels.json: {exc}")

for compose in (ROOT / "docker/iptv-hls/docker-compose.yml", ROOT / "docker/iptv-epg/docker-compose.yml"):
    text = compose.read_text(encoding="utf-8")
    if "services:" not in text or "restart: unless-stopped" not in text:
        fail(f"Compose 文件不完整：{compose.relative_to(ROOT)}")

android_required = (
    "android-tv/settings.gradle.kts",
    "android-tv/gradlew",
    "android-tv/app/build.gradle.kts",
    "android-tv/app/src/main/AndroidManifest.xml",
    "android-tv/app/src/main/java/com/wangjingfei/hometv/MainActivity.kt",
    "android-tv/app/src/main/java/com/wangjingfei/hometv/ui/HomeTvApp.kt",
    "android-tv/app/src/main/java/com/wangjingfei/hometv/data/M3uParser.kt",
    "android-tv/app/src/main/java/com/wangjingfei/hometv/data/XmlTvParser.kt",
)
for relative in android_required:
    if not (ROOT / relative).is_file():
        fail(f"Android TV 工程缺少：{relative}")

manifest = (ROOT / "android-tv/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for marker in ("LEANBACK_LAUNCHER", "android.software.leanback", "android.hardware.touchscreen"):
    if marker not in manifest:
        fail(f"Android TV Manifest 缺少：{marker}")

if errors:
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    raise SystemExit(1)

print(f"通过：Python 语法、Compose 结构、Android TV 工程和 {len(channels)} 条频道记录")
