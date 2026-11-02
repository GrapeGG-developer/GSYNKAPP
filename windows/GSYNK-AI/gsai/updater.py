"""
Автообновление GSYNK-AI прямо из программы.

Программа смотрит релизы GitHub с тегами вида ``gsynk-AI-vX.Y.Z``.

Важное правило совместимости форматов:

* запущена из PyInstaller-exe — нужен полный ``GSYNK-AI-win64.zip``
  (папка ``GSYNK-AI`` с ``GSYNK-AI.exe`` и ``_internal``);
* отдельный ``GSYNK-AI.exe`` допустим только как «спасательный» мост для
  старых версий 1.0.3/1.0.4, чей апдейтер не знал про win64.zip;
* запущена из исходников — нужен только ``GSYNK-AI.zip`` с исходниками.

Исходный zip никогда не накладывается на exe-сборку: именно такой fallback
в версиях до 1.0.4 ломал обновление и пытался стартовать ``run.bat`` поверх
PyInstaller-папки.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

from .server import DEFAULT_REPO
from .version import VERSION

TAG_PREFIX = "gsynk-AI-v"
WIN64_ASSET = "GSYNK-AI-win64.zip"
EXE_ASSET = "GSYNK-AI.exe"
SOURCE_ASSET = "GSYNK-AI.zip"


class UpdateError(RuntimeError):
    """Понятная пользователю ошибка обновления."""


def _parse(version: str) -> tuple[int, ...]:
    out = []
    for part in version.strip().lstrip("v").split("."):
        digits = "".join(ch for ch in part if ch.isdigit())
        out.append(int(digits) if digits else 0)
    return tuple(out)


def is_frozen() -> bool:
    return bool(getattr(sys, "frozen", False))


def app_dir() -> Path:
    if is_frozen():
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent.parent


def _asset_kind(asset_name: str) -> str:
    low = asset_name.lower()
    if low == WIN64_ASSET.lower():
        return "win64-zip"
    if low == EXE_ASSET.lower():
        return "exe"
    if low == SOURCE_ASSET.lower():
        return "source-zip"
    return "unknown"


def select_asset(assets: list[dict], frozen: bool | None = None) -> dict | None:
    """Выбирает только формат, совместимый с текущим способом запуска."""
    if frozen is None:
        frozen = is_frozen()
    by_name: dict[str, dict] = {}
    for asset in assets or []:
        name = str(asset.get("name") or "")
        if name and asset.get("browser_download_url"):
            by_name.setdefault(name.lower(), asset)

    priorities = (WIN64_ASSET, EXE_ASSET) if frozen else (SOURCE_ASSET,)
    for wanted in priorities:
        asset = by_name.get(wanted.lower())
        if asset is not None:
            return asset
    return None


def find_update(
    releases: list[dict],
    current_version: str = VERSION,
    frozen: bool | None = None,
) -> dict | None:
    """Ищет самое новое совместимое обновление в уже загруженном списке релизов."""
    if frozen is None:
        frozen = is_frozen()

    best: dict | None = None
    for release in releases or []:
        tag = str(release.get("tag_name") or "")
        if not tag.startswith(TAG_PREFIX) or release.get("draft"):
            continue
        version = tag[len(TAG_PREFIX):]
        if _parse(version) <= _parse(current_version):
            continue

        asset = select_asset(release.get("assets") or [], frozen=frozen)
        if asset is None:
            continue

        asset_name = str(asset["name"])
        candidate = {
            "version": version,
            "tag": tag,
            "asset_name": asset_name,
            "kind": _asset_kind(asset_name),
            "url": asset["browser_download_url"],
            "size": int(asset.get("size") or 0),
            "digest": str(asset.get("digest") or ""),
            "notes": (release.get("body") or "")[:2000],
        }
        if best is None or _parse(version) > _parse(best["version"]):
            best = candidate
    return best


def _fetch_releases(repo: str) -> list[dict]:
    releases: list[dict] = []
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": f"GSYNK-AI/{VERSION}",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    for page in range(1, 4):
        url = f"https://api.github.com/repos/{repo}/releases?per_page=100&page={page}"
        req = urllib.request.Request(url, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                batch = json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            if exc.code in (403, 429):
                raise UpdateError(
                    "GitHub временно ограничил проверку обновлений (лимит запросов). "
                    "Подождите несколько минут и попробуйте ещё раз."
                ) from exc
            raise UpdateError(f"GitHub вернул ошибку HTTP {exc.code} при проверке обновления.") from exc
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            raise UpdateError(f"Не удалось связаться с GitHub для проверки обновления: {exc}") from exc

        if not isinstance(batch, list):
            raise UpdateError("GitHub вернул неожиданный ответ при проверке обновления.")
        releases.extend(batch)
        if len(batch) < 100:
            break
    return releases


def check_update(repo: str = DEFAULT_REPO) -> dict | None:
    """Возвращает описание обновления или None, если установлена последняя версия."""
    return find_update(_fetch_releases(repo))


def _download_file(url: str, target: Path, expected_size: int = 0, on_progress=None) -> Path:
    part = target.with_name(target.name + ".part")
    req = urllib.request.Request(url, headers={"User-Agent": f"GSYNK-AI/{VERSION}"})
    done = 0
    last_progress = 0.0
    try:
        with urllib.request.urlopen(req, timeout=120) as resp, open(part, "wb") as fh:
            total = int(resp.headers.get("Content-Length") or expected_size or 0)
            while True:
                chunk = resp.read(1024 * 1024)
                if not chunk:
                    break
                fh.write(chunk)
                done += len(chunk)
                if on_progress and total > 0:
                    now = time.monotonic()
                    if now - last_progress >= 0.2 or done >= total:
                        last_progress = now
                        try:
                            on_progress(done / total)
                        except Exception:
                            pass
    except urllib.error.HTTPError as exc:
        part.unlink(missing_ok=True)
        raise UpdateError(f"Не удалось скачать обновление: GitHub вернул HTTP {exc.code}.") from exc
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        part.unlink(missing_ok=True)
        raise UpdateError(f"Не удалось скачать обновление: {exc}") from exc

    if expected_size and done != expected_size:
        part.unlink(missing_ok=True)
        raise UpdateError(
            f"Обновление скачалось не полностью: получено {done} байт вместо {expected_size}."
        )
    if done <= 0:
        part.unlink(missing_ok=True)
        raise UpdateError("GitHub прислал пустой файл обновления.")
    part.replace(target)
    return target


def _ensure_writable(folder: Path) -> None:
    probe = folder / ".gsynk-AI-update-write-test"
    try:
        probe.write_bytes(b"ok")
        probe.unlink(missing_ok=True)
    except OSError as exc:
        raise UpdateError(
            "Папка GSYNK-AI недоступна для записи, поэтому заменить файлы нельзя. "
            "Перенесите папку программы, например, на рабочий стол или диск D: — "
            f"не в Program Files. Папка: {folder}"
        ) from exc


def _find_zip_root(names: list[str], marker: str) -> str | None:
    normalized = [name.replace("\\", "/").lstrip("/") for name in names]
    candidates = [name for name in normalized if name == marker or name.endswith("/" + marker)]
    if not candidates:
        return None
    best = sorted(candidates, key=lambda item: (item.count("/"), len(item)))[0]
    return best[: -len(marker)].rstrip("/")


def _extract_and_validate(target: Path, extract_dir: Path, *, frozen: bool, asset_name: str) -> Path:
    try:
        zf = zipfile.ZipFile(target)
    except zipfile.BadZipFile as exc:
        raise UpdateError("Скачанный файл обновления повреждён: это не корректный ZIP-архив.") from exc

    with zf:
        names = [name.replace("\\", "/") for name in zf.namelist() if not name.endswith("/")]
        if frozen:
            root_name = _find_zip_root(names, EXE_ASSET)
            if root_name is None:
                raise UpdateError("В пакете обновления для exe-версии нет GSYNK-AI.exe.")
            if asset_name.lower() == WIN64_ASSET.lower():
                internal_prefix = f"{root_name}/_internal/" if root_name else "_internal/"
                if not any(name.startswith(internal_prefix) for name in names):
                    raise UpdateError("В полном win64-пакете обновления нет папки _internal.")
        else:
            root_name = _find_zip_root(names, "app.py")
            if root_name is None:
                raise UpdateError("В исходном пакете обновления нет app.py.")
            run_bat = f"{root_name}/run.bat" if root_name else "run.bat"
            if run_bat not in names:
                raise UpdateError("В исходном пакете обновления нет run.bat.")

        try:
            zf.extractall(extract_dir)
        except (OSError, RuntimeError, zipfile.BadZipFile) as exc:
            raise UpdateError(f"Не удалось распаковать обновление: {exc}") from exc

    root = extract_dir / Path(root_name) if root_name else extract_dir
    if frozen:
        if not (root / EXE_ASSET).exists():
            raise UpdateError("После распаковки обновления не найден GSYNK-AI.exe.")
    elif not (root / "app.py").exists() or not (root / "run.bat").exists():
        raise UpdateError("После распаковки исходного обновления не найден app.py или run.bat.")
    return root


def _bat_value(path: Path | str) -> str:
    return str(path).replace("%", "%%").replace('"', "")


def _wait_for_pid_lines(pid: int) -> list[str]:
    return [
        ":waitloop",
        f'tasklist /FI "PID eq {pid}" /NH 2>nul | findstr /R /C:" {pid} " >nul',
        "if not errorlevel 1 (",
        "  ping -n 2 127.0.0.1 >nul",
        "  goto waitloop",
        ")",
        "ping -n 2 127.0.0.1 >nul",
    ]


def _make_frozen_bat(*, pid: int, src: Path, home: Path, has_internal: bool) -> str:
    lines = [
        "@echo off",
        "setlocal EnableExtensions",
        "title GSYNK-AI update",
        f'set "APPDIR={_bat_value(home)}"',
        f'set "SRC={_bat_value(src)}"',
        'set "LOG=%TEMP%\\GSYNK-AI-update.log"',
        'set "BACKUP=%APPDIR%\\.gsynk-AI-backup-%RANDOM%%RANDOM%"',
        "echo Updating GSYNK-AI, please wait...",
        '> "%LOG%" echo GSYNK-AI update started',
        '>> "%LOG%" echo Source: %SRC%',
        '>> "%LOG%" echo Target: %APPDIR%',
        *_wait_for_pid_lines(pid),
        'mkdir "%BACKUP%" >> "%LOG%" 2>&1',
        "if errorlevel 1 goto fail",
    ]
    if has_internal:
        lines += [
            'if exist "%APPDIR%\\_internal" (',
            '  move /y "%APPDIR%\\_internal" "%BACKUP%\\_internal" >> "%LOG%" 2>&1',
            "  if errorlevel 1 goto fail",
            ")",
        ]
    lines += [
        'if exist "%APPDIR%\\GSYNK-AI.exe" (',
        '  move /y "%APPDIR%\\GSYNK-AI.exe" "%BACKUP%\\GSYNK-AI.exe" >> "%LOG%" 2>&1',
        "  if errorlevel 1 goto rollback",
        ")",
    ]
    if has_internal:
        lines += [
            'robocopy "%SRC%\\_internal" "%APPDIR%\\_internal" /E /R:8 /W:2 /NFL /NDL /NJH /NJS >> "%LOG%" 2>&1',
            "if errorlevel 8 goto rollback",
        ]
    lines += [
        'robocopy "%SRC%" "%APPDIR%" /E /XD "%SRC%\\_internal" /R:8 /W:2 /NFL /NDL /NJH /NJS >> "%LOG%" 2>&1',
        "if errorlevel 8 goto rollback",
        'if not exist "%APPDIR%\\GSYNK-AI.exe" goto rollback',
        '>> "%LOG%" echo Update installed. Backup: %BACKUP%',
        'start "" /D "%APPDIR%" "%APPDIR%\\GSYNK-AI.exe"',
        "exit /b 0",
        ":rollback",
        '>> "%LOG%" echo Install failed, rolling back',
    ]
    if has_internal:
        lines += [
            'if exist "%APPDIR%\\_internal" rmdir /s /q "%APPDIR%\\_internal" >> "%LOG%" 2>&1',
            'if exist "%BACKUP%\\_internal" move /y "%BACKUP%\\_internal" "%APPDIR%\\_internal" >> "%LOG%" 2>&1',
        ]
    lines += [
        'if exist "%APPDIR%\\GSYNK-AI.exe" del /q "%APPDIR%\\GSYNK-AI.exe" >> "%LOG%" 2>&1',
        'if exist "%BACKUP%\\GSYNK-AI.exe" move /y "%BACKUP%\\GSYNK-AI.exe" "%APPDIR%\\GSYNK-AI.exe" >> "%LOG%" 2>&1',
        ":fail",
        "echo.",
        "echo GSYNK-AI update failed. The previous version was restored when possible.",
        "echo Log: %LOG%",
        "pause",
        "exit /b 1",
        "",
    ]
    return "\r\n".join(lines)


def _make_single_exe_bat(*, pid: int, downloaded_exe: Path, home: Path) -> str:
    lines = [
        "@echo off",
        "setlocal EnableExtensions",
        "title GSYNK-AI update",
        f'set "APPDIR={_bat_value(home)}"',
        f'set "NEWEXE={_bat_value(downloaded_exe)}"',
        'set "LOG=%TEMP%\\GSYNK-AI-update.log"',
        'set "BACKUP=%APPDIR%\\GSYNK-AI.exe.bak"',
        "echo Updating GSYNK-AI, please wait...",
        '> "%LOG%" echo GSYNK-AI single-exe update started',
        *_wait_for_pid_lines(pid),
        'if exist "%BACKUP%" del /q "%BACKUP%" >> "%LOG%" 2>&1',
        'if exist "%APPDIR%\\GSYNK-AI.exe" move /y "%APPDIR%\\GSYNK-AI.exe" "%BACKUP%" >> "%LOG%" 2>&1',
        "if errorlevel 1 goto fail",
        'copy /y "%NEWEXE%" "%APPDIR%\\GSYNK-AI.exe" >> "%LOG%" 2>&1',
        "if errorlevel 1 goto rollback",
        'start "" /D "%APPDIR%" "%APPDIR%\\GSYNK-AI.exe"',
        "exit /b 0",
        ":rollback",
        'if exist "%APPDIR%\\GSYNK-AI.exe" del /q "%APPDIR%\\GSYNK-AI.exe" >> "%LOG%" 2>&1',
        'if exist "%BACKUP%" move /y "%BACKUP%" "%APPDIR%\\GSYNK-AI.exe" >> "%LOG%" 2>&1',
        ":fail",
        "echo.",
        "echo GSYNK-AI update failed. The previous exe was restored when possible.",
        "echo Log: %LOG%",
        "pause",
        "exit /b 1",
        "",
    ]
    return "\r\n".join(lines)


def _make_source_bat(*, pid: int, src: Path, home: Path) -> str:
    lines = [
        "@echo off",
        "setlocal EnableExtensions",
        "title GSYNK-AI update",
        f'set "APPDIR={_bat_value(home)}"',
        f'set "SRC={_bat_value(src)}"',
        'set "LOG=%TEMP%\\GSYNK-AI-update.log"',
        "echo Updating GSYNK-AI, please wait...",
        '> "%LOG%" echo GSYNK-AI source update started',
        *_wait_for_pid_lines(pid),
        'robocopy "%SRC%" "%APPDIR%" /E /XD "%SRC%\\.venv" __pycache__ /XF *.pyc /R:8 /W:2 /NFL /NDL /NJH /NJS >> "%LOG%" 2>&1',
        "if errorlevel 8 goto fail",
        'if not exist "%APPDIR%\\run.bat" goto fail',
        'start "GSYNK-AI" /D "%APPDIR%" "%APPDIR%\\run.bat"',
        "exit /b 0",
        ":fail",
        "echo.",
        "echo GSYNK-AI update failed.",
        "echo Log: %LOG%",
        "pause",
        "exit /b 1",
        "",
    ]
    return "\r\n".join(lines)


def _spawn_update_bat(tmp: Path, bat_text: str) -> None:
    bat_path = tmp / "update.bat"
    try:
        bat_path.write_bytes(bat_text.encode("cp866"))
    except UnicodeEncodeError as exc:
        raise UpdateError(
            "Путь к GSYNK-AI содержит символы, которые старый формат update.bat не поддерживает. "
            "Перенесите папку программы в путь из английских или русских букв без специальных символов."
        ) from exc
    creationflags = getattr(subprocess, "CREATE_NEW_CONSOLE", 0)
    try:
        subprocess.Popen(
            ["cmd.exe", "/c", str(bat_path)],
            cwd=str(tmp),
            creationflags=creationflags,
            close_fds=True,
        )
    except OSError as exc:
        raise UpdateError(f"Не удалось запустить помощник обновления Windows: {exc}") from exc


def download_and_apply(update: dict, on_progress=None) -> None:
    """
    Качает обновление и запускает замену. После вызова программа должна закрыться:
    update.bat дождётся её выхода, заменит файлы и стартует GSYNK-AI снова.
    """
    if not update or not update.get("url") or not update.get("asset_name"):
        raise UpdateError("Нет данных обновления для установки.")

    asset_name = Path(str(update["asset_name"])).name
    kind = str(update.get("kind") or _asset_kind(asset_name))
    frozen = is_frozen()
    if frozen and kind == "source-zip":
        raise UpdateError(
            "Для exe-версии найден только исходный GSYNK-AI.zip. Его нельзя ставить поверх "
            "GSYNK-AI.exe. Дождитесь полного GSYNK-AI-win64.zip или скачайте его вручную с GitHub."
        )
    if not frozen and kind != "source-zip":
        raise UpdateError("Для версии из исходников подходит только GSYNK-AI.zip с исходным кодом.")
    if kind == "unknown":
        raise UpdateError(f"Неизвестный формат файла обновления: {asset_name}")

    home = app_dir()
    _ensure_writable(home)
    tmp = Path(tempfile.mkdtemp(prefix="gsynk-AI-update-"))
    target = _download_file(str(update["url"]), tmp / asset_name, int(update.get("size") or 0), on_progress)
    pid = os.getpid()

    if kind == "exe":
        with open(target, "rb") as fh:
            if fh.read(2) != b"MZ":
                raise UpdateError("Скачанный GSYNK-AI.exe повреждён: у файла нет заголовка Windows EXE.")
        bat = _make_single_exe_bat(pid=pid, downloaded_exe=target, home=home)
    else:
        src = _extract_and_validate(target, tmp / "unzipped", frozen=frozen, asset_name=asset_name)
        if frozen:
            bat = _make_frozen_bat(
                pid=pid,
                src=src,
                home=home,
                has_internal=(src / "_internal").is_dir(),
            )
        else:
            bat = _make_source_bat(pid=pid, src=src, home=home)

    _spawn_update_bat(tmp, bat)
