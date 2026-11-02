"""Хранилище GSYNK-AI: настройки и проекты моделей на диске пользователя."""

from __future__ import annotations

import json
import os
import re
from pathlib import Path

from .dataset import Project
from .server import DEFAULT_REPO


def data_dir() -> Path:
    if os.name == "nt":
        base = Path(os.environ.get("APPDATA") or Path.home())
    else:
        base = Path.home() / ".config"
    path = base / "GSYNK-AI"
    path.mkdir(parents=True, exist_ok=True)
    (path / "projects").mkdir(exist_ok=True)
    return path


def projects_dir() -> Path:
    return data_dir() / "projects"


def slugify(name: str) -> str:
    table = str.maketrans({
        "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "e", "ж": "zh",
        "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m", "н": "n", "о": "o",
        "п": "p", "р": "r", "с": "s", "т": "t", "у": "u", "ф": "f", "х": "h", "ц": "c",
        "ч": "ch", "ш": "sh", "щ": "sch", "ъ": "", "ы": "y", "ь": "", "э": "e", "ю": "yu", "я": "ya",
    })
    slug = name.lower().translate(table)
    slug = re.sub(r"[^a-z0-9]+", "-", slug).strip("-")
    return slug or "model"


def project_dir(name: str) -> Path:
    path = projects_dir() / slugify(name)
    path.mkdir(parents=True, exist_ok=True)
    return path


def list_projects() -> list[Project]:
    out: list[Project] = []
    for proj_file in sorted(projects_dir().glob("*/project.json")):
        try:
            out.append(Project.load(proj_file))
        except Exception:
            continue
    return out


def save_project(project: Project) -> Path:
    folder = project_dir(project.name)
    project.save(folder / "project.json")
    return folder


def checkpoint_of(name: str) -> Path:
    return project_dir(name) / "checkpoint.pt"


def package_of(name: str) -> Path:
    return project_dir(name) / (slugify(name) + ".gsai")


class Settings:
    def __init__(self) -> None:
        self.repo: str = DEFAULT_REPO
        self.token: str = ""
        self.turbo: bool = True
        self.check_updates: bool = True
        self.path = data_dir() / "settings.json"
        self.load()

    def load(self) -> None:
        try:
            data = json.loads(self.path.read_text(encoding="utf-8"))
            self.repo = data.get("repo", self.repo)
            self.token = data.get("token", "")
            self.turbo = bool(data.get("turbo", True))
            self.check_updates = bool(data.get("check_updates", True))
        except Exception:
            pass

    def save(self) -> None:
        self.path.write_text(json.dumps({
            "repo": self.repo, "token": self.token,
            "turbo": self.turbo, "check_updates": self.check_updates,
        }, ensure_ascii=False, indent=1), encoding="utf-8")
