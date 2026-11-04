#!/usr/bin/env python3
"""Offscreen smoke-тест GREAL GUI GSYNK-AI 1.1."""
import os
import sys
import tempfile
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
os.environ["HOME"] = tempfile.mkdtemp(prefix="gsynk-AI-gui-")
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from PySide6.QtWidgets import QApplication

import app as gui
from gsai.dataset import KnowledgeEntry, Project, parse_knowledge_plain

gui.updater.check_update = lambda repo: None
gui.server.list_server_models = lambda repo: []


def main() -> None:
    qt = QApplication(sys.argv)
    qt.setStyleSheet(gui.QSS)
    window = gui.MainWindow()
    qt.processEvents()

    assert window.stack.count() == 7
    assert "Знания" in window.PAGES[2] and "Обучение GPU" in window.PAGES[3]
    assert "Чат и контекст" in window.PAGES[4]
    # Генератор и расширение фиктивных диалогов удалены из программы.
    assert not hasattr(window, "gen_count")
    assert not hasattr(window, "generate_dataset")
    assert not hasattr(window, "expand_with_model")
    # Новая студия знаний и GPU-страница реально собраны.
    for name in (
        "knowledge_title", "knowledge_body", "knowledge_list", "knowledge_search",
        "device_combo", "auto_batch_box", "amp_box", "compile_box",
        "memory_view", "retrieved_view", "candidates_view",
    ):
        assert hasattr(window, name), name

    project = Project(name="GUI Знания", persona="дружелюбная и внимательная")
    project.set_knowledge(parse_knowledge_plain(
        "виноград = сладкая ягода, которая растёт гроздьями"
    ) + [KnowledgeEntry("память", "модель помнит имя и предыдущие реплики", tags=["контекст"])])
    window.set_project(project)
    qt.processEvents()
    assert window.knowledge_list.count() == 2
    assert "знаний 2" in window.dataset_stats.text()
    assert "контекст" in window.train_info.text()
    window.goto(2)
    window.goto(3)
    window.goto(5)
    window.goto(6)
    qt.processEvents()
    print("PAGES_OK 7")
    print("KNOWLEDGE_GUI_OK")
    print("GPU_GUI_OK")
    print("GUI_SMOKE_OK")
    window.close()
    qt.processEvents()


if __name__ == "__main__":
    main()
