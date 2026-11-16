#!/usr/bin/env python3
"""从已签名 APK 生成 Android TV 自动更新清单。"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
from urllib.parse import urlparse


def command_output(*args: str) -> str:
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--apk-url", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--aapt2", required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--release-notes", default="")
    return parser.parse_args()


def extract(pattern: str, value: str, field: str) -> str:
    match = re.search(pattern, value, re.MULTILINE)
    if not match:
        raise SystemExit(f"无法从 APK 读取 {field}")
    return match.group(1)


def main() -> None:
    args = parse_args()
    if not args.apk.is_file():
        raise SystemExit(f"APK 不存在：{args.apk}")
    parsed_url = urlparse(args.apk_url)
    if parsed_url.scheme != "https" or not parsed_url.netloc:
        raise SystemExit("APK 下载地址必须使用 HTTPS")

    badging = command_output(args.aapt2, "dump", "badging", str(args.apk))
    package_name = extract(r"^package: name='([^']+)'", badging, "包名")
    version_code = int(extract(r"^package: .*versionCode='([0-9]+)'", badging, "versionCode"))
    version_name = extract(r"^package: .*versionName='([^']+)'", badging, "versionName")
    min_sdk = int(extract(r"^(?:minSdkVersion|sdkVersion):'([0-9]+)'", badging, "minSdk"))

    signer = command_output(args.apksigner, "verify", "--print-certs", str(args.apk))
    certificate = extract(
        r"^Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)$",
        signer,
        "签名证书 SHA-256",
    ).lower()
    digest = hashlib.sha256()
    with args.apk.open("rb") as apk_file:
        for chunk in iter(lambda: apk_file.read(1024 * 1024), b""):
            digest.update(chunk)
    manifest = {
        "schemaVersion": 1,
        "versionCode": version_code,
        "versionName": version_name,
        "packageName": package_name,
        "minSdk": min_sdk,
        "apkUrl": args.apk_url,
        "apkSha256": digest.hexdigest(),
        "apkSize": args.apk.stat().st_size,
        "certificateSha256": certificate,
        "releaseNotes": args.release_notes.strip(),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
