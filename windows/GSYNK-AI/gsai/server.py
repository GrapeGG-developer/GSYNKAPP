"""
Сервер моделей GSYNK-AI на GitHub.

Как устроено:
  • файлы .gsai лежат ассетами релиза с тегом AI-models в репозитории GSYNK;
  • каталог моделей — файл AI-models/index.json в том же репозитории;
  • телефон читает каталог по raw-ссылке без токена и качает .gsai по прямым ссылкам.

Для публикации нужен личный GitHub-токен с доступом к репозиторию —
он вводится в настройках GSYNK-AI и хранится только на вашем ПК.
"""

from __future__ import annotations

import base64
import hashlib
import json
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

DEFAULT_REPO = "GrapeGG-developer/GSYNKAPP"
MODELS_TAG = "AI-models"
INDEX_PATH = "AI-models/index.json"
API = "https://api.github.com"


def _headers(token: str = "") -> dict:
    h = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "GSYNK-AI",
    }
    if token:
        h["Authorization"] = f"Bearer {token}"
    return h


def _request(method: str, url: str, token: str = "", data: bytes | None = None,
             ctype: str | None = None, ok_404: bool = False):
    h = _headers(token)
    if ctype:
        h["Content-Type"] = ctype
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=600) as resp:
            body = resp.read()
            return resp.status, (json.loads(body) if body else {})
    except urllib.error.HTTPError as e:
        if e.code == 404 and ok_404:
            return 404, {}
        detail = e.read().decode("utf-8", "replace")[:400]
        raise RuntimeError(f"GitHub {method} → {e.code}: {detail}") from e


def index_url(repo: str = DEFAULT_REPO) -> str:
    return f"https://raw.githubusercontent.com/{repo}/main/{INDEX_PATH}"


def list_server_models(repo: str = DEFAULT_REPO) -> list[dict]:
    """Список опубликованных моделей (без токена)."""
    req = urllib.request.Request(index_url(repo), headers=_headers())
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return data.get("models", []) if isinstance(data, dict) else []
    except Exception:
        return []


def _ensure_models_release(repo: str, token: str) -> dict:
    # ВАЖНО: релиз моделей обязан быть prerelease, иначе GitHub сделает его "latest"
    # и автообновление самого GSYNK (releases/latest) начнёт видеть не APK-релиз.
    status, release = _request("GET", f"{API}/repos/{repo}/releases/tags/{MODELS_TAG}", token, ok_404=True)
    if status != 404:
        if not release.get("prerelease"):
            payload = json.dumps({"prerelease": True}).encode()
            _, release = _request("PATCH", f"{API}/repos/{repo}/releases/{release['id']}", token,
                                   data=payload, ctype="application/json")
        return release
    payload = json.dumps({
        "tag_name": MODELS_TAG,
        "name": "GSYNK-AI — модели",
        "body": "Файлы моделей GSYNK-AI (.gsai). Каталог: AI-models/index.json в репозитории. "
                "Приложение GSYNK само находит здесь модели и запускает их на телефоне.",
        "draft": False, "prerelease": True,
    }).encode()
    _, release = _request("POST", f"{API}/repos/{repo}/releases", token, data=payload, ctype="application/json")
    return release


def _get_index(repo: str, token: str) -> tuple[dict, str]:
    status, data = _request("GET", f"{API}/repos/{repo}/contents/{INDEX_PATH}", token, ok_404=True)
    if status == 404:
        return {"models": []}, ""
    raw = base64.b64decode(data.get("content", "")).decode("utf-8")
    try:
        index = json.loads(raw)
    except json.JSONDecodeError:
        index = {"models": []}
    return index, data.get("sha", "")


def _put_index(repo: str, token: str, index: dict, sha: str, message: str) -> None:
    payload = {
        "message": message,
        "content": base64.b64encode(json.dumps(index, ensure_ascii=False, indent=1).encode()).decode(),
    }
    if sha:
        payload["sha"] = sha
    _request("PUT", f"{API}/repos/{repo}/contents/{INDEX_PATH}", token,
             data=json.dumps(payload).encode(), ctype="application/json")


def publish_model(
    repo: str,
    token: str,
    gsai_path: str | Path,
    meta: dict,
    model_id: str,
    on_log=None,
) -> dict:
    """Загружает .gsai на сервер и обновляет каталог. Возвращает запись каталога."""
    log = on_log or (lambda msg: None)
    path = Path(gsai_path)
    if not token.strip():
        raise RuntimeError("нужен GitHub-токен: введите его в настройках GSYNK-AI")
    release = _ensure_models_release(repo, token)
    log("релиз сервера моделей готов")
    asset_name = f"{model_id}.gsai"
    for asset in release.get("assets", []):
        if asset.get("name") == asset_name:
            _request("DELETE", f"{API}/repos/{repo}/releases/assets/{asset['id']}", token)
            log("старая версия файла модели удалена")
    upload_url = release["upload_url"].split("{", 1)[0] + "?name=" + urllib.parse.quote(asset_name)
    _, asset = _request("POST", upload_url, token, data=path.read_bytes(),
                         ctype="application/octet-stream")
    log(f"файл модели загружен ({asset.get('size', 0)} байт)")
    entry = {
        "id": model_id,
        "name": meta.get("name", model_id),
        "icon": meta.get("icon", "🍇"),
        "description": meta.get("description", ""),
        "version": meta.get("version", "1.0.0"),
        "params": meta.get("params", 0),
        "size": asset.get("size", path.stat().st_size),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "url": asset.get("browser_download_url", ""),
        "architecture": meta.get("architecture", {}),
        "steps": meta.get("steps", 0),
        "loss": meta.get("loss", 0.0),
    }
    index, sha = _get_index(repo, token)
    models = [m for m in index.get("models", []) if m.get("id") != model_id]
    models.append(entry)
    models.sort(key=lambda m: m.get("name", ""))
    index["models"] = models
    _put_index(repo, token, index, sha, f"GSYNK-AI: модель {entry['name']} {entry['version']}")
    log("каталог моделей обновлён — телефон уже видит модель")
    return entry


def unpublish_model(repo: str, token: str, model_id: str, on_log=None) -> None:
    log = on_log or (lambda msg: None)
    release = _ensure_models_release(repo, token)
    for asset in release.get("assets", []):
        if asset.get("name") == f"{model_id}.gsai":
            _request("DELETE", f"{API}/repos/{repo}/releases/assets/{asset['id']}", token)
    index, sha = _get_index(repo, token)
    index["models"] = [m for m in index.get("models", []) if m.get("id") != model_id]
    _put_index(repo, token, index, sha, f"GSYNK-AI: модель {model_id} снята с сервера")
    log("модель убрана с сервера")
