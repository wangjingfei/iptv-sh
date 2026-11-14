#!/usr/bin/env python3
import json
import mimetypes
import os
import re
import signal
import subprocess
import threading
import time
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit


CONFIG_PATH = Path(os.environ.get("IPTV_CONFIG_PATH", "/app/config/channels.json"))
RUNTIME_ROOT = Path(os.environ.get("IPTV_RUNTIME_ROOT", "/run/iptv-hls"))
BIND = os.environ.get("IPTV_BIND", "0.0.0.0")
PORT = int(os.environ.get("IPTV_PORT", "8090"))
IDLE_TIMEOUT = int(os.environ.get("IPTV_IDLE_TIMEOUT", "60"))
START_TIMEOUT = int(os.environ.get("IPTV_START_TIMEOUT", "20"))
HLS_TIME = float(os.environ.get("IPTV_HLS_TIME", "4"))
HLS_LIST_SIZE = int(os.environ.get("IPTV_HLS_LIST_SIZE", "6"))
FFMPEG_LOGLEVEL = os.environ.get("IPTV_FFMPEG_LOGLEVEL", "warning")
CHANNEL_ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
SEGMENT_RE = re.compile(r"^segment_[0-9]+\.ts$")


def log(message):
    print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {message}", flush=True)


class ConfigStore:
    def __init__(self, path):
        self.path = path
        self.lock = threading.Lock()
        self.mtime_ns = None
        self.channels = {}
        self.reload(force=True)

    def reload(self, force=False):
        try:
            stat = self.path.stat()
            if not force and stat.st_mtime_ns == self.mtime_ns:
                return
            raw = json.loads(self.path.read_text(encoding="utf-8"))
            channels = raw.get("channels")
            if not isinstance(channels, dict):
                raise ValueError("顶层 channels 必须是对象")

            validated = {}
            for channel_id, value in channels.items():
                if not CHANNEL_ID_RE.fullmatch(channel_id):
                    raise ValueError(f"无效频道 ID：{channel_id!r}")
                if not isinstance(value, dict):
                    raise ValueError(f"频道 {channel_id!r} 必须是对象")
                name = value.get("name")
                source = value.get("source")
                if not isinstance(name, str) or not name.strip():
                    raise ValueError(f"频道 {channel_id!r} 没有名称")
                if not isinstance(source, str) or not source.startswith(("http://", "https://")):
                    raise ValueError(f"频道 {channel_id!r} 的 HTTP 源无效")
                validated[channel_id] = {"name": name.strip(), "source": source}

            with self.lock:
                self.channels = validated
                self.mtime_ns = stat.st_mtime_ns
            log(f"已从 {self.path} 加载 {len(validated)} 个频道")
        except Exception as exc:
            if force:
                raise
            log(f"配置重载失败，继续使用旧配置：{exc}")

    def snapshot(self):
        self.reload()
        with self.lock:
            return dict(self.channels)


@dataclass
class ChannelState:
    process: subprocess.Popen
    source: str
    directory: Path
    last_access: float


class StreamManager:
    def __init__(self, config):
        self.config = config
        self.lock = threading.RLock()
        self.states = {}
        self.stopping = threading.Event()
        RUNTIME_ROOT.mkdir(parents=True, exist_ok=True)
        self.reaper = threading.Thread(target=self._reaper_loop, name="stream-reaper", daemon=True)
        self.reaper.start()

    def channel_config(self, channel_id):
        return self.config.snapshot().get(channel_id)

    def _command(self, source, directory):
        return [
            "ffmpeg",
            "-nostdin",
            "-hide_banner",
            "-loglevel",
            FFMPEG_LOGLEVEL,
            "-reconnect",
            "1",
            "-reconnect_streamed",
            "1",
            "-reconnect_on_network_error",
            "1",
            "-reconnect_delay_max",
            "5",
            "-i",
            source,
            "-map",
            "0:v:0?",
            "-map",
            "0:a:0?",
            "-c",
            "copy",
            "-f",
            "hls",
            "-hls_time",
            str(HLS_TIME),
            "-hls_list_size",
            str(HLS_LIST_SIZE),
            "-hls_delete_threshold",
            "2",
            "-hls_flags",
            "delete_segments+omit_endlist+program_date_time+temp_file",
            "-hls_segment_filename",
            str(directory / "segment_%06d.ts"),
            str(directory / "index.m3u8"),
        ]

    def _clean_directory(self, directory):
        directory.mkdir(parents=True, exist_ok=True)
        for path in directory.iterdir():
            if path.is_file():
                path.unlink(missing_ok=True)

    def ensure_running(self, channel_id):
        channel = self.channel_config(channel_id)
        if channel is None:
            return None, "未知频道"

        now = time.monotonic()
        with self.lock:
            state = self.states.get(channel_id)
            if state and state.process.poll() is None and state.source == channel["source"]:
                state.last_access = now
                return state, None
            if state:
                self._stop_locked(channel_id, "配置已变化或进程已退出")

            directory = RUNTIME_ROOT / channel_id
            self._clean_directory(directory)
            process = subprocess.Popen(
                self._command(channel["source"], directory),
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=None,
                start_new_session=True,
            )
            state = ChannelState(
                process=process,
                source=channel["source"],
                directory=directory,
                last_access=now,
            )
            self.states[channel_id] = state
            log(f"频道已启动 channel={channel_id} pid={process.pid}")
            return state, None

    def touch(self, channel_id):
        with self.lock:
            state = self.states.get(channel_id)
            if state and state.process.poll() is None:
                state.last_access = time.monotonic()

    def wait_for_playlist(self, state):
        deadline = time.monotonic() + START_TIMEOUT
        playlist = state.directory / "index.m3u8"
        while time.monotonic() < deadline:
            if state.process.poll() is not None:
                return False, f"FFmpeg 已退出，状态码为 {state.process.returncode}"
            try:
                if playlist.stat().st_size > 0:
                    return True, None
            except FileNotFoundError:
                pass
            time.sleep(0.2)
        return False, f"播放列表在 {START_TIMEOUT} 秒内未就绪"

    def _stop_locked(self, channel_id, reason):
        state = self.states.pop(channel_id, None)
        if state is None:
            return
        process = state.process
        if process.poll() is None:
            try:
                os.killpg(process.pid, signal.SIGTERM)
                process.wait(timeout=8)
            except (ProcessLookupError, subprocess.TimeoutExpired):
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
        self._clean_directory(state.directory)
        log(f"频道已停止 channel={channel_id} 原因={reason}")

    def _reaper_loop(self):
        while not self.stopping.wait(5):
            now = time.monotonic()
            with self.lock:
                for channel_id, state in list(self.states.items()):
                    if state.process.poll() is not None:
                        self._stop_locked(channel_id, "FFmpeg 已退出")
                    elif now - state.last_access >= IDLE_TIMEOUT:
                        self._stop_locked(channel_id, f"已空闲 {IDLE_TIMEOUT} 秒")

    def status(self):
        with self.lock:
            now = time.monotonic()
            return {
                channel_id: {
                    "pid": state.process.pid,
                    "running": state.process.poll() is None,
                    "idle_seconds": round(now - state.last_access, 1),
                }
                for channel_id, state in self.states.items()
            }

    def close(self):
        self.stopping.set()
        with self.lock:
            for channel_id in list(self.states):
                self._stop_locked(channel_id, "服务关闭")


class RequestHandler(BaseHTTPRequestHandler):
    server_version = "IPTV-HLS/1.0"

    def log_message(self, fmt, *args):
        log(f"http {self.client_address[0]} {fmt % args}")

    def _headers(self, status, content_type, content_length=None, cache_control="no-store"):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", cache_control)
        self.send_header("Access-Control-Allow-Origin", "*")
        if content_length is not None:
            self.send_header("Content-Length", str(content_length))
        self.end_headers()

    def _bytes(self, status, body, content_type="text/plain; charset=utf-8"):
        if isinstance(body, str):
            body = body.encode("utf-8")
        self._headers(status, content_type, len(body))
        if self.command != "HEAD":
            self.wfile.write(body)

    def _json(self, status, value):
        self._bytes(status, json.dumps(value, ensure_ascii=False) + "\n", "application/json; charset=utf-8")

    def _serve_file(self, path):
        try:
            file = path.open("rb")
        except FileNotFoundError:
            self._bytes(HTTPStatus.NOT_FOUND, "找不到媒体分片\n")
            return
        with file:
            size = os.fstat(file.fileno()).st_size
            content_type = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
            if path.suffix == ".m3u8":
                content_type = "application/vnd.apple.mpegurl"
                cache = "no-cache, no-store, must-revalidate"
            elif path.suffix == ".ts":
                content_type = "video/mp2t"
                cache = "public, max-age=30"
            else:
                cache = "no-store"
            self._headers(HTTPStatus.OK, content_type, size, cache)
            if self.command != "HEAD":
                while chunk := file.read(256 * 1024):
                    self.wfile.write(chunk)

    def _playlist(self):
        host = self.headers.get("Host", f"192.168.31.200:{PORT}").replace("\r", "").replace("\n", "")
        lines = ["#EXTM3U"]
        for channel_id, channel in self.server.config.snapshot().items():
            name = channel["name"].replace("\r", " ").replace("\n", " ")
            lines.append(f"#EXTINF:-1 tvg-id=\"{channel_id}\",{name}")
            lines.append(f"http://{host}/{channel_id}/index.m3u8")
        self._bytes(HTTPStatus.OK, "\n".join(lines) + "\n", "audio/x-mpegurl; charset=utf-8")

    def _handle(self):
        path = unquote(urlsplit(self.path).path)
        if path == "/":
            self.send_response(HTTPStatus.TEMPORARY_REDIRECT)
            self.send_header("Location", "/playlist.m3u")
            self.end_headers()
            return
        if path == "/healthz":
            self._json(HTTPStatus.OK, {"status": "ok"})
            return
        if path == "/api/status":
            self._json(
                HTTPStatus.OK,
                {
                    "configured_channels": len(self.server.config.snapshot()),
                    "active_channels": self.server.manager.status(),
                    "idle_timeout": IDLE_TIMEOUT,
                },
            )
            return
        if path == "/playlist.m3u":
            self._playlist()
            return

        parts = [part for part in path.split("/") if part]
        if len(parts) != 2 or not CHANNEL_ID_RE.fullmatch(parts[0]):
            self._bytes(HTTPStatus.NOT_FOUND, "not found\n")
            return
        channel_id, filename = parts
        if filename != "index.m3u8" and not SEGMENT_RE.fullmatch(filename):
            self._bytes(HTTPStatus.NOT_FOUND, "not found\n")
            return

        state, error = self.server.manager.ensure_running(channel_id)
        if error:
            self._bytes(HTTPStatus.NOT_FOUND, error + "\n")
            return
        self.server.manager.touch(channel_id)

        if filename == "index.m3u8":
            ready, error = self.server.manager.wait_for_playlist(state)
            if not ready:
                self._bytes(HTTPStatus.BAD_GATEWAY, error + "\n")
                return
        self._serve_file(state.directory / filename)

    def do_GET(self):
        self._handle()

    def do_HEAD(self):
        self._handle()

    def do_OPTIONS(self):
        self.send_response(HTTPStatus.NO_CONTENT)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        self.send_header("Content-Length", "0")
        self.end_headers()


class IPTVServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address, handler, config, manager):
        super().__init__(address, handler)
        self.config = config
        self.manager = manager


def main():
    config = ConfigStore(CONFIG_PATH)
    manager = StreamManager(config)
    server = IPTVServer((BIND, PORT), RequestHandler, config, manager)

    def shutdown(signum, _frame):
        log(f"收到信号 {signum}，正在关闭")
        threading.Thread(target=server.shutdown, daemon=True).start()

    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)
    log(f"正在监听 {BIND}:{PORT}，空闲超时为 {IDLE_TIMEOUT} 秒")
    try:
        server.serve_forever(poll_interval=0.5)
    finally:
        server.server_close()
        manager.close()


if __name__ == "__main__":
    main()
