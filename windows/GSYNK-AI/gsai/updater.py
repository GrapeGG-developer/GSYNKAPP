"""
Автообновление GSYNK-AI прямо из программы.

Программа смотрит релизы GitHub с тегами вида gsynk-AI-vX.Y.Z.
Если версия новее текущей — качает GSYNK-AI.exe (когда запущена из exe)
или GSYNK-AI.zip с исходниками (когда запущена из папки), пишет update.bat,
который дожидается закрытия программы, заменяет файлы и запускает её снова.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

from .server import DEFAULT_REPO
from .version import VERSION

TAG_PREFIX = "gsynk-AI-v"


def _parse(version: str) -> tuple[int, ...]:
    out = []
    for part in version.strip().lstrip("v").split("."):
        digits = "".join(ch for ch in part if ch.isdigit())
        out.append(int(digits) if digits else 0)
    return tuple(out)


def is_frozen() -> bool:
    return getattr(sys, "frozen", False)


def app_dir() -> Path:
    if is_frozen():
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent.parent


def check_update(repo: str = DEFAULT_REPO) -> dict | None:
    """Возвращает {version, tag, asset_name, url, size} или None, если обновлений нет."""
    url = f"https://api.github.com/repos/{repo}/releases?per_page=30"
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json", "User-Agent": "GSYNK-AI"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        releases = json.loads(resp.read().decode("utf-8"))
    best: dict | None = None
    for release in releases:
        tag = release.get("tag_name", "")
        if not tag.startswith(TAG_PREFIX) or release.get("draft"):
            continue
        version = tag[len(TAG_PREFIX):]
        if _parse(version) <= _parse(VERSION):
            continue
        wanted = "GSYNK-AI.exe" if is_frozen() else "GSYNK-AI.zip"
        fallback = "GSYNK-AI.zip"
        asset = None
        for a in release.get("assets", []):
            if a.get("name") == wanted:
                asset = a
                break
        if asset is None:
            for a in release.get("assets", []):
                if a.get("name") == fallback:
                    asset = a
                    break
        if asset is None:
            continue
        candidate = {
            "version": version,
            "tag": tag,
            "asset_name": asset["name"],
            "url": asset["browser_download_url"],
            "size": asset.get("size", 0),
            "notes": (release.get("body") or "")[:2000],
        }
        if best is None or _parse(version) > _parse(best["version"]):
            best = candidate
    return best


def download_and_apply(update: dict, on_progress=None) -> None:
    """
    Качает обновление и запускает замену. После вызова программа должна закрыться:
    update.bat дождётся её выхода, заменит файлы и стартует GSYNK-AI снова.
    """
    tmp = Path(tempfile.mkdtemp(prefix="gsai-update-"))
    target = tmp / update["asset_name"]
    req = urllib.request.Request(update["url"], headers={"User-Agent": "GSYNK-AI"})
    with urllib.request.urlopen(req, timeout=900) as resp, open(target, "wb") as fh:
        total = int(resp.headers.get("Content-Length") or update.get("size") or 0)
        done = 0
        while True:
            chunk = resp.read(1024 * 256)
            if not chunk:
                break
            fh.write(chunk)
            done += len(chunk)
            if on_progress and total > 0:
                on_progress(done / total)
    home = app_dir()
    pid = os.getpid()
    if update["asset_name"].endswith(".exe"):
        exe_name = Path(sys.executable).name if is_frozen() else "GSYNK-AI.exe"
        bat = f"""@echo off
:waitloop
tasklist /FI "PID eq {pid}" | find "{pid}" >nul && (timeout /t 1 /nobreak >nul & goto waitloop)
copy /y "{target}" "{home / exe_name}" >nul
start "" "{home / exe_name}"
"""
    else:
        extract_dir = tmp / "unzipped"
        with zipfile.ZipFile(target) as zf:
            zf.extractall(extract_dir)
        inner = extract_dir / "GSYNK-AI"
        src = inner if inner.exists() else extract_dir
        runner = home / "run.bat"
        bat = f"""@echo off
:waitloop
tasklist /FI "PID eq {pid}" | find "{pid}" >nul && (timeout /t 1 /nobreak >nul & goto waitloop)
xcopy /e /y /i "{src}\\*" "{home}\\" >nul
start "" "{runner}"
"""
    bat_path = tmp / "update.bat"
    bat_path.write_text(bat, encoding="cp866", errors="ignore")
    subprocess.Popen(["cmd", "/c", str(bat_path)], creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
