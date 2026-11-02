#!/usr/bin/env python3
"""Регрессионные тесты автообновления GSYNK-AI без сети и без GUI."""

import sys
import tempfile
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from gsai import updater


def release(version: str, *asset_names: str) -> dict:
    return {
        "tag_name": f"gsynk-AI-v{version}",
        "draft": False,
        "prerelease": True,
        "body": "notes",
        "assets": [
            {
                "name": name,
                "size": 123,
                "browser_download_url": f"https://example.invalid/{name}",
            }
            for name in asset_names
        ],
    }


def main() -> None:
    releases = [
        release("1.0.4", "GSYNK-AI.zip"),
        release("1.0.5", "GSYNK-AI.zip", "GSYNK-AI.exe", "GSYNK-AI-win64.zip"),
    ]

    frozen = updater.find_update(releases, current_version="1.0.4", frozen=True)
    assert frozen is not None
    assert frozen["asset_name"] == "GSYNK-AI-win64.zip", frozen
    assert frozen["kind"] == "win64-zip"
    print("[ok] exe-версия выбирает полный GSYNK-AI-win64.zip")

    # GSYNK-AI.exe остаётся спасательным мостом, если полного пакета вдруг нет.
    rescue = updater.find_update(
        [release("1.0.5", "GSYNK-AI.zip", "GSYNK-AI.exe")],
        current_version="1.0.4",
        frozen=True,
    )
    assert rescue is not None and rescue["asset_name"] == "GSYNK-AI.exe", rescue
    print("[ok] exe-версия умеет использовать спасательный GSYNK-AI.exe")

    # Ключевая регрессия 1.0.3/1.0.4: исходники нельзя ставить поверх exe.
    source_only = updater.find_update(
        [release("1.0.5", "GSYNK-AI.zip")],
        current_version="1.0.4",
        frozen=True,
    )
    assert source_only is None, source_only
    print("[ok] exe-версия больше не скачивает исходный GSYNK-AI.zip")

    source = updater.find_update(releases, current_version="1.0.4", frozen=False)
    assert source is not None and source["asset_name"] == "GSYNK-AI.zip", source
    print("[ok] версия из исходников выбирает GSYNK-AI.zip")

    with tempfile.TemporaryDirectory() as td:
        root = Path(td)

        payload = root / "payload.bin"
        payload.write_bytes(b"hello-update" * 1000)
        downloaded = root / "download.bin"
        updater._download_file(payload.as_uri(), downloaded, payload.stat().st_size)
        assert downloaded.read_bytes() == payload.read_bytes()
        print("[ok] загрузка проверяет размер и пишет файл атомарно через .part")

        win64 = root / "GSYNK-AI-win64.zip"
        with zipfile.ZipFile(win64, "w") as zf:
            zf.writestr("GSYNK-AI/GSYNK-AI.exe", b"MZ" + b"x" * 100)
            zf.writestr("GSYNK-AI/_internal/python311.dll", b"dll")
        extracted = updater._extract_and_validate(
            win64,
            root / "unzipped-win64",
            frozen=True,
            asset_name="GSYNK-AI-win64.zip",
        )
        assert (extracted / "GSYNK-AI.exe").exists()
        assert (extracted / "_internal" / "python311.dll").exists()
        print("[ok] win64-пакет проверяется по GSYNK-AI.exe и _internal")

        source_zip = root / "GSYNK-AI.zip"
        with zipfile.ZipFile(source_zip, "w") as zf:
            zf.writestr("GSYNK-AI/app.py", "# app")
            zf.writestr("GSYNK-AI/run.bat", "@echo off")
        extracted_source = updater._extract_and_validate(
            source_zip,
            root / "unzipped-source",
            frozen=False,
            asset_name="GSYNK-AI.zip",
        )
        assert (extracted_source / "app.py").exists()
        print("[ok] исходный пакет проверяется по app.py и run.bat")

        bat = updater._make_frozen_bat(
            pid=4242,
            src=extracted,
            home=root / "installed",
            has_internal=True,
        )
        assert "robocopy" in bat and "GSYNK-AI.exe" in bat and "run.bat" not in bat
        assert "rollback" in bat and "BACKUP" in bat
        print("[ok] Windows-скрипт exe-обновления имеет backup/rollback и не запускает run.bat")

    print("ALL_UPDATER_TESTS_OK")


if __name__ == "__main__":
    main()
