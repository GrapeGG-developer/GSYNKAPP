#!/usr/bin/env python3
"""
GSYNK-AI — студия собственных ИИ-моделей.

Создавайте модель, собирайте базу знаний (вручную, файлами или из интернета),
обучайте на видеокарте ПК, проверяйте память и контекст в чате и выпускайте на сервер —
телефон с GSYNK сам подхватит модель и запустит её у себя.
"""

from __future__ import annotations

import json
import os
import sys
import threading
import traceback
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import torch
from PySide6.QtCore import Qt, QThread, QTimer, Signal
from dataclasses import asdict

from PySide6.QtGui import QBrush, QColor, QFont, QLinearGradient, QPainter, QPen, QRadialGradient
from PySide6.QtWidgets import (
    QApplication, QCheckBox, QComboBox, QDoubleSpinBox, QFileDialog, QFrame,
    QGraphicsDropShadowEffect, QGridLayout, QHBoxLayout, QLabel, QLineEdit,
    QListWidget, QMainWindow, QMessageBox, QPlainTextEdit, QProgressBar,
    QPushButton, QScrollArea, QSlider, QSpinBox, QSplitter, QStackedWidget,
    QTextBrowser, QVBoxLayout, QWidget,
)

from gsai import VERSION, server, updater
from gsai.context import ContextSession
from gsai.dataset import (
    ICONS, KNOWLEDGE_KINDS, SIZES, KnowledgeEntry, Project,
    build_training_data, dedupe_knowledge, entries_from_paragraphs,
    fetch_url_text, fetch_wikipedia_ru, parse_knowledge_document,
    parse_knowledge_plain, seed_identity_knowledge, split_paragraphs,
)
from gsai.generate import context_reply
from gsai.model import GsaiConfig, GsaiGPT
from gsai.package import export_package
from gsai.store import (
    Settings, checkpoint_of, context_file_of, list_projects, package_of,
    project_dir, save_project, slugify,
)
from gsai.tokenizer import Tokenizer
from gsai.trainer import Trainer, hardware_summary, pick_device

ACCENT = "#8B5CF6"
GREEN = "#7BD88F"

QSS = """
/* GREAL для ПК: живая аврора за стеклом, глубокие стеклянные панели,
   свет по краям, мягкие сферы и крупные радиусы. */
QMainWindow { background: #07040F; color: #F8F5FF; font-family: 'Segoe UI Variable Display', 'Segoe UI'; font-size: 13px; }
QWidget { color: #F8F5FF; font-family: 'Segoe UI Variable Display', 'Segoe UI'; font-size: 13px; }
QWidget#grealRoot { background: transparent; }
QFrame#card {
  background: qlineargradient(x1:0,y1:0,x2:1,y2:1,
    stop:0 rgba(255,255,255,0.16), stop:0.38 rgba(153,102,255,0.15),
    stop:0.72 rgba(50,220,255,0.09), stop:1 rgba(255,255,255,0.07));
  border: 1px solid rgba(255,255,255,0.30); border-radius: 22px;
}
QFrame#sidebar {
  background: qlineargradient(x1:0,y1:0,x2:1,y2:1,
    stop:0 rgba(91,45,180,0.58), stop:0.45 rgba(28,15,65,0.78), stop:1 rgba(8,80,100,0.52));
  border-right: 1px solid rgba(255,255,255,0.28);
}
QPushButton {
  background: qlineargradient(x1:0,y1:0,x2:0,y2:1,
    stop:0 rgba(255,255,255,0.30), stop:0.48 rgba(139,92,246,0.30), stop:1 rgba(72,35,155,0.36));
  border: 1px solid rgba(255,255,255,0.34); border-radius: 14px; padding: 8px 15px;
}
QPushButton:hover { background: rgba(167,124,255,0.52); border-color: rgba(255,255,255,0.62); }
QPushButton:pressed { background: rgba(98,55,210,0.62); padding-top: 9px; }
QPushButton:disabled { background: rgba(255,255,255,0.06); color: rgba(255,255,255,0.38); }
QPushButton#primary {
  background: qlineargradient(x1:0,y1:0,x2:1,y2:1,
    stop:0 #C4B5FD, stop:0.45 #8B5CF6, stop:1 #0EA5E9);
  color: white; font-weight: 700; border-color: rgba(255,255,255,0.66);
}
QPushButton#primary:hover { background: #A78BFA; }
QPushButton#danger { background: rgba(229,57,70,0.38); border-color: rgba(255,130,145,0.78); }
QPushButton#nav { background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08); text-align: left; padding: 11px 15px; font-size: 14px; border-radius: 16px; }
QPushButton#nav:hover { background: rgba(255,255,255,0.16); }
QPushButton#navActive {
  background: qlineargradient(x1:0,y1:0,x2:1,y2:0, stop:0 rgba(196,181,253,0.72), stop:1 rgba(56,189,248,0.48));
  border: 1px solid rgba(255,255,255,0.62); text-align: left; padding: 11px 15px; font-size: 14px; font-weight: 700; border-radius: 16px;
}
QLineEdit, QPlainTextEdit, QSpinBox, QDoubleSpinBox, QComboBox, QListWidget, QTextBrowser {
  background: rgba(9,5,24,0.48); border: 1px solid rgba(255,255,255,0.25);
  border-radius: 14px; padding: 7px; selection-background-color: #8B5CF6;
}
QLineEdit:focus, QPlainTextEdit:focus, QSpinBox:focus, QDoubleSpinBox:focus, QComboBox:focus {
  border: 1px solid rgba(125,211,252,0.90); background: rgba(20,12,44,0.66);
}
QComboBox::drop-down { border: none; width: 22px; }
QCheckBox { spacing: 7px; }
QCheckBox::indicator { width: 18px; height: 18px; border-radius: 6px; background: rgba(255,255,255,0.12); border: 1px solid rgba(255,255,255,0.4); }
QCheckBox::indicator:checked { background: #8B5CF6; }
QLabel#title { font-size: 27px; font-weight: 350; letter-spacing: 0.3px; }
QLabel#h2 { font-size: 16px; font-weight: 650; }
QLabel#dim { color: rgba(255,255,255,0.70); }
QLabel#metric { font-size: 15px; font-weight: 650; color: #E9D5FF; }
QProgressBar { border: 1px solid rgba(255,255,255,0.28); border-radius: 9px; height: 16px; text-align: center; background: rgba(0,0,0,0.22); }
QProgressBar::chunk { background: qlineargradient(x1:0,y1:0,x2:1,y2:0, stop:0 #8B5CF6, stop:1 #22D3EE); border-radius: 8px; }
QSlider::groove:horizontal { height: 8px; background: rgba(255,255,255,0.18); border-radius: 4px; }
QSlider::handle:horizontal { width: 20px; height: 20px; margin: -6px 0; border-radius: 10px; background: #C4B5FD; border: 2px solid white; }
QScrollArea { border: none; background: transparent; }
QScrollBar:vertical { width: 11px; background: transparent; margin: 2px; }
QScrollBar::handle:vertical { background: rgba(167,139,250,0.55); border-radius: 5px; min-height: 28px; }
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical { height: 0; }
QSplitter::handle { background: rgba(255,255,255,0.16); border-radius: 3px; }
"""


class GrealBackground(QWidget):
    """Анимированная аврора GREAL: использует GPU-рендер Qt и живёт за стеклом."""

    def __init__(self) -> None:
        super().__init__()
        self.setObjectName("grealRoot")
        self.phase = 0.0
        self._timer = QTimer(self)
        self._timer.timeout.connect(self._tick)
        self._timer.start(50)

    def _tick(self) -> None:
        self.phase = (self.phase + 0.006) % 1.0
        self.update()

    def paintEvent(self, event) -> None:  # noqa: N802
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        w, h = max(1, self.width()), max(1, self.height())
        base = QLinearGradient(0, 0, w, h)
        base.setColorAt(0.0, QColor(14, 6, 36))
        base.setColorAt(0.48, QColor(8, 10, 38))
        base.setColorAt(1.0, QColor(3, 35, 43))
        painter.fillRect(self.rect(), QBrush(base))
        import math

        blobs = [
            (0.18 + 0.05 * math.sin(self.phase * 6.28), 0.12, 0.52, QColor(147, 51, 234, 135)),
            (0.82, 0.18 + 0.06 * math.cos(self.phase * 6.28), 0.48, QColor(14, 165, 233, 112)),
            (0.68 + 0.05 * math.cos(self.phase * 4.1), 0.88, 0.54, QColor(34, 197, 94, 82)),
            (0.12, 0.82 + 0.04 * math.sin(self.phase * 5.2), 0.42, QColor(236, 72, 153, 76)),
        ]
        for cx, cy, radius, color in blobs:
            grad = QRadialGradient(w * cx, h * cy, max(w, h) * radius)
            grad.setColorAt(0.0, color)
            transparent = QColor(color)
            transparent.setAlpha(0)
            grad.setColorAt(1.0, transparent)
            painter.fillRect(self.rect(), QBrush(grad))
        highlight = QLinearGradient(0, 0, 0, h)
        highlight.setColorAt(0.0, QColor(255, 255, 255, 42))
        highlight.setColorAt(0.28, QColor(255, 255, 255, 0))
        highlight.setColorAt(1.0, QColor(0, 0, 0, 55))
        painter.fillRect(self.rect(), QBrush(highlight))


class GrealOrb(QWidget):
    """Объёмная сфера-логотип без внешних картинок."""

    def __init__(self, size: int = 64) -> None:
        super().__init__()
        self.setFixedSize(size, size)

    def paintEvent(self, event) -> None:  # noqa: N802
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        rect = self.rect().adjusted(5, 5, -5, -5)
        grad = QRadialGradient(rect.center().x() - rect.width() * 0.22, rect.center().y() - rect.height() * 0.28, rect.width() * 0.72)
        grad.setColorAt(0.0, QColor(255, 255, 255, 235))
        grad.setColorAt(0.18, QColor(196, 181, 253, 230))
        grad.setColorAt(0.55, QColor(124, 58, 237, 225))
        grad.setColorAt(0.82, QColor(14, 116, 233, 215))
        grad.setColorAt(1.0, QColor(8, 47, 73, 230))
        painter.setBrush(QBrush(grad))
        painter.setPen(QPen(QColor(255, 255, 255, 150), 1.4))
        painter.drawEllipse(rect)
        painter.setBrush(QColor(255, 255, 255, 95))
        painter.setPen(Qt.PenStyle.NoPen)
        painter.drawEllipse(int(rect.x() + rect.width() * 0.18), int(rect.y() + rect.height() * 0.10),
                            int(rect.width() * 0.30), int(rect.height() * 0.17))


def card(layout: QVBoxLayout | QHBoxLayout) -> QFrame:
    frame = QFrame()
    frame.setObjectName("card")
    layout.setContentsMargins(16, 14, 16, 14)
    frame.setLayout(layout)
    shadow = QGraphicsDropShadowEffect(frame)
    shadow.setBlurRadius(28)
    shadow.setOffset(0, 9)
    shadow.setColor(QColor(76, 29, 149, 105))
    frame.setGraphicsEffect(shadow)
    return frame


def label(text: str, obj: str = "") -> QLabel:
    w = QLabel(text)
    if obj:
        w.setObjectName(obj)
    w.setWordWrap(True)
    return w


class LossChart(QWidget):
    def __init__(self) -> None:
        super().__init__()
        self.points: list[float] = []
        self.setMinimumHeight(150)

    def push(self, value: float) -> None:
        self.points.append(value)
        self.points = self.points[-400:]
        self.update()

    def paintEvent(self, event) -> None:  # noqa: N802
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        painter.fillRect(self.rect(), QColor(255, 255, 255, 10))
        if len(self.points) < 2:
            painter.setPen(QColor(255, 255, 255, 120))
            painter.drawText(self.rect(), Qt.AlignmentFlag.AlignCenter, "график loss появится при обучении")
            return
        lo, hi = min(self.points), max(self.points)
        span = max(1e-6, hi - lo)
        w, h = self.width(), self.height()
        painter.setPen(QPen(QColor(255, 255, 255, 40), 1))
        for i in range(1, 4):
            y = h * i / 4
            painter.drawLine(0, int(y), w, int(y))
        painter.setPen(QPen(QColor(ACCENT), 2))
        prev = None
        for i, v in enumerate(self.points):
            x = w * i / max(1, len(self.points) - 1)
            y = h - 8 - (v - lo) / span * (h - 16)
            if prev is not None:
                painter.drawLine(int(prev[0]), int(prev[1]), int(x), int(y))
            prev = (x, y)
        painter.setPen(QColor(255, 255, 255, 200))
        painter.drawText(8, 16, f"loss сейчас: {self.points[-1]:.3f}   (меньше — умнее)")


class FuncWorker(QThread):
    done = Signal(object)
    failed = Signal(str)

    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def run(self) -> None:
        try:
            self.done.emit(self.fn())
        except Exception:
            self.failed.emit(traceback.format_exc(limit=4))


class TrainWorker(QThread):
    state = Signal(dict)
    finished_ok = Signal(dict)
    failed = Signal(str)

    def __init__(self, trainer: Trainer, max_steps: int, checkpoint: Path):
        super().__init__()
        self.trainer = trainer
        self.max_steps = max_steps
        self.checkpoint = checkpoint

    def run(self) -> None:
        try:
            def emit(st):
                self.state.emit({
                    "step": st.step, "loss": st.loss_ema, "val_loss": st.val_loss,
                    "perplexity": st.perplexity, "tok": st.tok_per_sec,
                    "epoch": st.epoch, "lr": st.lr, "device": st.device,
                    "precision": st.precision, "eta": st.eta_seconds,
                    "gpu_mb": st.gpu_memory_mb,
                    "batch": self.trainer.effective_batch, "msg": st.message,
                })

            self.trainer.on_state = emit
            st = self.trainer.train(self.max_steps, checkpoint_path=self.checkpoint, save_every=200)
            self.finished_ok.emit({"step": st.step, "loss": st.loss_ema})
        except Exception:
            self.failed.emit(traceback.format_exc(limit=5))


def friendly_error(text: str) -> str:
    """Переводим технические ошибки на человеческий русский."""
    low = text.lower()
    if "out of memory" in low or "could not allocate" in low or ("not enough" in low and "memory" in low):
        return ("Не хватило видеопамяти. Обычно обучение само уменьшает пакет и продолжается; "
                "если ошибка повторяется — выберите размер модели поменьше или отключите ТУРБО "
                "(обучение пойдёт на процессоре, ему памяти хватает всегда).")
    if "датасет слишком маленький" in low:
        return "База знаний слишком маленькая для обучения. Добавьте фактов, определений или текстов на вкладке «Знания»."
    if "updateerror:" in low or "обновлен" in low and ("github" in low or "скач" in low or "папк" in low):
        last = next((ln.strip() for ln in reversed(text.splitlines()) if ln.strip()), text)
        for prefix in ("UpdateError:", "RuntimeError:"):
            if last.startswith(prefix):
                last = last[len(prefix):].strip()
                break
        return last
    if "не хватает видеопамяти даже" in low:
        last = next((ln.strip() for ln in reversed(text.splitlines()) if ln.strip()), text)
        return last
    last = next((ln.strip() for ln in reversed(text.splitlines()) if ln.strip()), text)
    return f"Что-то пошло не так:\n{last}\n\nЕсли ошибка повторяется, сделайте скриншот и покажите его."


class MainWindow(QMainWindow):
    PAGES = [
        "🍇 1 · Модели", "✨ 2 · Создать", "🧠 3 · Знания", "🔥 4 · Обучение GPU",
        "💬 5 · Чат и контекст", "🚀 6 · Выпуск", "⚙ Настройки",
    ]

    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle(f"GSYNK-AI {VERSION} — GREAL-студия своих ИИ-моделей")
        self.resize(1420, 900)
        self.setMinimumSize(1040, 680)
        self.settings = Settings()
        self.project: Project | None = None
        self.trainer: Trainer | None = None
        self.train_worker: TrainWorker | None = None
        self.chat_model: GsaiGPT | None = None
        self.chat_tokenizer: Tokenizer | None = None
        self.chat_device = torch.device("cpu")
        self.chat_session = ContextSession()
        self.chat_busy = False
        self.visible_knowledge: list[KnowledgeEntry] = []
        self.editing_knowledge_id = ""
        self.workers: list[QThread] = []

        root = QHBoxLayout()
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        side = QFrame()
        side.setObjectName("sidebar")
        side.setFixedWidth(250)
        side_layout = QVBoxLayout(side)
        side_layout.setContentsMargins(14, 16, 14, 12)
        logo_row = QHBoxLayout()
        logo_row.addWidget(GrealOrb(58))
        logo_text = QVBoxLayout()
        logo_text.addWidget(label("GSYNK-AI", "title"))
        logo_text.addWidget(label("GREAL studio", "dim"))
        logo_row.addLayout(logo_text, 1)
        side_layout.addLayout(logo_row)
        side_layout.addWidget(label("знания → видеокарта → контекст → телефон", "dim"))
        side_layout.addSpacing(14)
        self.nav_buttons: list[QPushButton] = []
        for i, name in enumerate(self.PAGES):
            btn = QPushButton(name)
            btn.setObjectName("nav")
            btn.clicked.connect(lambda _=False, idx=i: self.goto(idx))
            self.nav_buttons.append(btn)
            side_layout.addWidget(btn)
        side_layout.addStretch(1)
        self.current_label = label("модель не выбрана", "dim")
        side_layout.addWidget(self.current_label)
        self.update_btn = QPushButton("")
        self.update_btn.setObjectName("primary")
        self.update_btn.hide()
        self.update_btn.clicked.connect(self.apply_update)
        side_layout.addWidget(self.update_btn)
        side_layout.addWidget(label(f"версия программы {VERSION}", "dim"))
        root.addWidget(side)

        self.stack = QStackedWidget()
        self.stack.addWidget(self.page_models())
        self.stack.addWidget(self.page_create())
        self.stack.addWidget(self.page_dataset())
        self.stack.addWidget(self.page_train())
        self.stack.addWidget(self.page_chat())
        self.stack.addWidget(self.page_release())
        self.stack.addWidget(self.page_settings())
        root.addWidget(self.stack, 1)

        container = GrealBackground()
        container.setLayout(root)
        self.setCentralWidget(container)
        self.goto(0)

        self.cpu_timer = QTimer(self)
        self.cpu_timer.timeout.connect(self.tick_cpu)
        self.cpu_timer.start(1000)

        if self.settings.check_updates:
            self.run_async(lambda: updater.check_update(self.settings.repo), self.on_update_checked, silent=True)
        self.refresh_models()

    def closeEvent(self, event) -> None:  # noqa: N802
        self.save_context_session()
        if self.trainer is not None:
            self.trainer.stop()
        if self.train_worker is not None:
            self.train_worker.wait(30000)
        for worker in list(self.workers):
            worker.wait(3000)
        super().closeEvent(event)

    # ------------------------------------------------------------ утилиты
    def goto(self, index: int) -> None:
        self.stack.setCurrentIndex(index)
        for i, btn in enumerate(self.nav_buttons):
            btn.setObjectName("navActive" if i == index else "nav")
            btn.setStyle(btn.style())
        if index == 4 and self.project is not None and self.chat_model is None:
            self.prepare_chat()

    def error(self, text: str) -> None:
        QMessageBox.critical(self, "GSYNK-AI", friendly_error(text))

    def info(self, text: str) -> None:
        QMessageBox.information(self, "GSYNK-AI", text)

    def run_async(self, fn, on_done, silent: bool = False, on_failed=None) -> None:
        worker = FuncWorker(fn)
        worker.done.connect(on_done)
        if silent:
            worker.failed.connect(lambda _: None)
        else:
            def failed(text):
                if on_failed is not None:
                    on_failed(text)
                self.error(text)

            worker.failed.connect(failed)
        worker.finished.connect(lambda: self.workers.remove(worker) if worker in self.workers else None)
        self.workers.append(worker)
        worker.start()

    def require_project(self) -> Project | None:
        if self.project is None:
            self.info("Сначала создайте модель на вкладке «Создать» или выберите её в «Модели».")
            return None
        return self.project

    def load_context_session(self) -> None:
        if self.project is None:
            self.chat_session = ContextSession()
            return
        try:
            path = context_file_of(self.project.name)
            data = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
            self.chat_session = ContextSession.from_dict(data)
        except Exception:
            self.chat_session = ContextSession()
        self.sync_context_inspector()
        self.render_saved_chat()

    def save_context_session(self) -> None:
        if self.project is None:
            return
        try:
            context_file_of(self.project.name).write_text(
                json.dumps(self.chat_session.to_dict(), ensure_ascii=False, indent=1), encoding="utf-8"
            )
        except Exception:
            pass

    def set_project(self, project: Project) -> None:
        project.materialize_knowledge()
        self.project = project
        self.chat_model = None
        self.chat_busy = False
        self.editing_knowledge_id = ""
        self.current_label.setText(f"модель: {project.icon} {project.name}")
        if hasattr(self, "chat_view"):
            self.chat_view.clear()
        self.load_context_session()
        self.sync_dataset_page()
        self.sync_release_page()
        self.refresh_train_labels()

    def tick_cpu(self) -> None:
        try:
            import psutil

            cpu = psutil.cpu_percent(interval=None)
            ram = psutil.virtual_memory().percent
            self.cpu_label.setText(f"CPU: {cpu:.0f}%   RAM: {ram:.0f}%")
            self.cpu_bar.setValue(int(cpu))
        except Exception:
            pass

    # ------------------------------------------------------------ страница: модели
    def page_models(self) -> QWidget:
        page = QWidget()
        lay = QVBoxLayout(page)
        lay.setContentsMargins(18, 16, 18, 16)
        lay.addWidget(label("Мои модели", "title"))
        lay.addWidget(label("Модели хранятся на этом ПК. Выпущенные на сервер модели телефон с GSYNK видит автоматически.", "dim"))
        actions = QHBoxLayout()
        new_btn = QPushButton("✨ Создать модель")
        new_btn.setObjectName("primary")
        new_btn.clicked.connect(lambda: self.goto(1))
        refresh_btn = QPushButton("Обновить список сервера")
        refresh_btn.clicked.connect(self.refresh_models)
        actions.addWidget(new_btn)
        actions.addWidget(refresh_btn)
        actions.addStretch(1)
        lay.addLayout(actions)

        self.models_area = QScrollArea()
        self.models_area.setWidgetResizable(True)
        self.models_host = QWidget()
        self.models_grid = QGridLayout(self.models_host)
        self.models_area.setWidget(self.models_host)
        lay.addWidget(self.models_area, 1)

        lay.addWidget(label("На сервере (их видит телефон):", "h2"))
        self.server_list = QListWidget()
        self.server_list.setMaximumHeight(130)
        lay.addWidget(self.server_list)
        return page

    def refresh_models(self) -> None:
        while self.models_grid.count():
            item = self.models_grid.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        projects = list_projects()
        if not projects:
            self.models_grid.addWidget(label("Пока нет ни одной модели. Нажмите «Создать модель» — это займёт минуту.", "dim"), 0, 0)
        for i, proj in enumerate(projects):
            lay = QVBoxLayout()
            lay.addWidget(label(f"{proj.icon} {proj.name}", "h2"))
            stats = proj.stats()
            context = SIZES.get(proj.size, SIZES["lite"])[3]
            lay.addWidget(label(
                f"размер: {proj.size} · контекст: {context}\n"
                f"знаний: {stats['entries']} · слов: {stats['words']} · тегов: {stats['tags']}\n"
                + (f"шагов обучения: {proj.steps_done} · loss: {proj.best_loss:.3f}" if proj.best_loss else "ещё не обучалась"), "dim"))
            btn = QPushButton("Открыть")
            btn.clicked.connect(lambda _=False, p=proj: (self.set_project(p), self.goto(2)))
            lay.addWidget(btn)
            self.models_grid.addWidget(card(lay), i // 3, i % 3)
        self.run_async(lambda: server.list_server_models(self.settings.repo), self.on_server_models, silent=True)

    def on_server_models(self, models) -> None:
        self.server_list.clear()
        if not models:
            self.server_list.addItem("на сервере пока пусто — выпустите первую модель!")
            return
        for m in models:
            self.server_list.addItem(
                f"{m.get('icon', '🍇')} {m.get('name')}  v{m.get('version')} · "
                f"{(m.get('params', 0) / 1e6):.1f} млн параметров · {round((m.get('size', 0)) / 1048576, 1)} МБ"
            )

    # ------------------------------------------------------------ страница: создать
    def page_create(self) -> QWidget:
        page = QWidget()
        lay = QVBoxLayout(page)
        lay.setContentsMargins(18, 16, 18, 16)
        lay.addWidget(label("Создать модель", "title"))
        lay.addWidget(label("Новая нейросеть-трансформер с нуля. Имя и иконку увидят в GSYNK на телефоне.", "dim"))

        form = QVBoxLayout()
        form.addWidget(label("Имя модели:", "h2"))
        self.create_name = QLineEdit()
        self.create_name.setPlaceholderText("например: Виноградик")
        form.addWidget(self.create_name)
        form.addWidget(label("Иконка:", "h2"))
        row = QHBoxLayout()
        self.create_icon = QComboBox()
        self.create_icon.addItems(ICONS)
        row.addWidget(self.create_icon)
        self.create_icon_file = QLineEdit()
        self.create_icon_file.setPlaceholderText("…или свой PNG (необязательно)")
        row.addWidget(self.create_icon_file, 1)
        pick = QPushButton("Выбрать PNG")
        pick.clicked.connect(self.pick_icon)
        row.addWidget(pick)
        form.addLayout(row)
        form.addWidget(label("Размер мозга:", "h2"))
        self.create_size = QComboBox()
        for key, spec in SIZES.items():
            self.create_size.addItem(f"{key} — {spec[5]}", key)
        self.create_size.setCurrentIndex(1)
        form.addWidget(self.create_size)
        form.addWidget(label("Описание (покажется в каталоге моделей):", "h2"))
        self.create_desc = QLineEdit()
        self.create_desc.setPlaceholderText("болтает, считает и рассказывает факты")
        form.addWidget(self.create_desc)
        form.addWidget(label("Характер и стиль ответов (станет знанием модели):", "h2"))
        self.create_persona = QPlainTextEdit()
        self.create_persona.setPlaceholderText("например: дружелюбная, отвечает коротко и честно говорит, если не знает")
        self.create_persona.setMaximumHeight(76)
        form.addWidget(self.create_persona)
        create_btn = QPushButton("Создать и перейти к знаниям")
        create_btn.setObjectName("primary")
        create_btn.clicked.connect(self.create_model)
        form.addWidget(create_btn)
        lay.addWidget(card(form))
        lay.addStretch(1)
        return page

    def pick_icon(self) -> None:
        path, _ = QFileDialog.getOpenFileName(self, "Иконка модели", "", "PNG (*.png)")
        if path:
            self.create_icon_file.setText(path)

    def create_model(self) -> None:
        name = self.create_name.text().strip()
        if not name:
            self.info("Введите имя модели.")
            return
        project = Project(
            name=name,
            icon=self.create_icon.currentText(),
            size=self.create_size.currentData(),
            description=self.create_desc.text().strip(),
            persona=self.create_persona.toPlainText().strip(),
        )
        seed_identity_knowledge(project)
        folder = save_project(project)
        icon_path = self.create_icon_file.text().strip()
        if icon_path and Path(icon_path).exists():
            (folder / "icon.png").write_bytes(Path(icon_path).read_bytes())
        self.set_project(project)
        self.info(f"Модель «{name}» создана. Теперь соберите базу знаний: факты, определения, слова, правила и тексты.")
        self.goto(2)

    # ------------------------------------------------------------ страница: датасет
    def page_dataset(self) -> QWidget:
        page = QWidget()
        lay = QVBoxLayout(page)
        lay.setContentsMargins(18, 16, 18, 16)
        lay.addWidget(label("База знаний", "title"))
        lay.addWidget(label(
            "Модель больше не учится на списке диалогов. Собирайте факты, слова, определения, "
            "правила и тексты — GREAL-конвейер сам сделает из них задания на память, контекст и follow-up.",
            "dim",
        ))
        self.dataset_stats = label("модель не выбрана", "metric")
        lay.addWidget(self.dataset_stats)

        splitter = QSplitter(Qt.Orientation.Horizontal)
        left = QWidget()
        left_lay = QVBoxLayout(left)

        editor = QVBoxLayout()
        editor.addWidget(label("✦ Карточка знания", "h2"))
        self.knowledge_title = QLineEdit()
        self.knowledge_title.setPlaceholderText("заголовок / термин, например: фотосинтез")
        editor.addWidget(self.knowledge_title)
        meta_row = QHBoxLayout()
        self.knowledge_kind = QComboBox()
        for key, title in KNOWLEDGE_KINDS.items():
            self.knowledge_kind.addItem(title, key)
        meta_row.addWidget(self.knowledge_kind)
        self.knowledge_tags = QLineEdit()
        self.knowledge_tags.setPlaceholderText("теги через запятую: биология, растения")
        meta_row.addWidget(self.knowledge_tags, 1)
        self.knowledge_weight = QSpinBox()
        self.knowledge_weight.setRange(1, 8)
        self.knowledge_weight.setPrefix("вес ")
        self.knowledge_weight.setToolTip("Важные знания повторяются в обучении чаще")
        meta_row.addWidget(self.knowledge_weight)
        editor.addLayout(meta_row)
        self.knowledge_body = QPlainTextEdit()
        self.knowledge_body.setPlaceholderText(
            "само знание обычным текстом: фотосинтез — процесс, при котором растения "
            "превращают свет, воду и углекислый газ в кислород и энергию."
        )
        self.knowledge_body.setMinimumHeight(118)
        editor.addWidget(self.knowledge_body)
        edit_row = QHBoxLayout()
        save_entry = QPushButton("＋ Сохранить знание")
        save_entry.setObjectName("primary")
        save_entry.clicked.connect(self.save_knowledge_editor)
        new_entry = QPushButton("Новое")
        new_entry.clicked.connect(self.clear_knowledge_editor)
        edit_row.addWidget(save_entry)
        edit_row.addWidget(new_entry)
        edit_row.addStretch(1)
        editor.addLayout(edit_row)
        left_lay.addWidget(card(editor))

        paste_lay = QVBoxLayout()
        paste_lay.addWidget(label("Массовый импорт текста", "h2"))
        paste_lay.addWidget(label(
            "Форматы: абзацы, `# заголовок`, `термин = значение`, старые `вопрос => ответ` "
            "превратятся в карточки вопрос—ответ.", "dim"))
        self.paste_edit = QPlainTextEdit()
        self.paste_edit.setPlaceholderText("виноград = сладкая ягода\n\n# Солнце\nСолнце — звезда в центре Солнечной системы.")
        self.paste_edit.setMinimumHeight(105)
        paste_lay.addWidget(self.paste_edit)
        row = QHBoxLayout()
        add_btn = QPushButton("Добавить знания")
        add_btn.setObjectName("primary")
        add_btn.clicked.connect(self.add_pasted)
        file_btn = QPushButton("Файл .txt/.md/.json/.csv…")
        file_btn.clicked.connect(self.add_file)
        row.addWidget(add_btn)
        row.addWidget(file_btn)
        row.addStretch(1)
        paste_lay.addLayout(row)
        left_lay.addWidget(card(paste_lay))
        left_lay.addStretch(1)

        right = QWidget()
        right_lay = QVBoxLayout(right)
        browser = QVBoxLayout()
        browser.addWidget(label("Что модель будет знать", "h2"))
        filter_row = QHBoxLayout()
        self.knowledge_search = QLineEdit()
        self.knowledge_search.setPlaceholderText("поиск по словам, заголовкам и тегам…")
        self.knowledge_search.textChanged.connect(lambda _text: self.sync_dataset_page())
        filter_row.addWidget(self.knowledge_search, 1)
        self.knowledge_filter = QComboBox()
        self.knowledge_filter.addItem("Все типы", "")
        for key, title in KNOWLEDGE_KINDS.items():
            self.knowledge_filter.addItem(title, key)
        self.knowledge_filter.currentIndexChanged.connect(lambda _i: self.sync_dataset_page())
        filter_row.addWidget(self.knowledge_filter)
        browser.addLayout(filter_row)
        self.knowledge_list = QListWidget()
        self.knowledge_list.setWordWrap(True)
        self.knowledge_list.currentRowChanged.connect(self.on_knowledge_selected)
        browser.addWidget(self.knowledge_list, 1)
        action_row = QHBoxLayout()
        dedupe_btn = QPushButton("🧹 Убрать дубли")
        dedupe_btn.clicked.connect(self.dedupe_dataset)
        del_sel = QPushButton("Удалить выбранное")
        del_sel.clicked.connect(self.delete_knowledge)
        clear_btn = QPushButton("Очистить знания")
        clear_btn.setObjectName("danger")
        clear_btn.clicked.connect(self.clear_dataset)
        action_row.addWidget(dedupe_btn)
        action_row.addWidget(del_sel)
        action_row.addWidget(clear_btn)
        action_row.addStretch(1)
        browser.addLayout(action_row)
        right_lay.addWidget(card(browser), 1)

        splitter.addWidget(left)
        splitter.addWidget(right)
        splitter.setSizes([560, 620])
        lay.addWidget(splitter, 1)

        web = QVBoxLayout()
        web.addWidget(label("Подхватить знания из интернета", "h2"))
        wrow = QHBoxLayout()
        self.wiki_edit = QLineEdit()
        self.wiki_edit.setPlaceholderText("тема в русской Википедии, например: космос")
        wrow.addWidget(self.wiki_edit, 1)
        wiki_btn = QPushButton("Скачать статью в знания")
        wiki_btn.clicked.connect(self.fetch_wiki)
        wrow.addWidget(wiki_btn)
        self.url_edit = QLineEdit()
        self.url_edit.setPlaceholderText("или любая ссылка https://…")
        wrow.addWidget(self.url_edit, 1)
        url_btn = QPushButton("Скачать текст в знания")
        url_btn.clicked.connect(self.fetch_url)
        wrow.addWidget(url_btn)
        web.addLayout(wrow)
        lay.addWidget(card(web))
        return page

    def sync_dataset_page(self) -> None:
        if self.project is None or not hasattr(self, "knowledge_list"):
            return
        self.project.materialize_knowledge()
        stats = self.project.stats()
        self.dataset_stats.setText(
            f"{self.project.icon} {self.project.name}: знаний {stats['entries']} · слов {stats['words']} · "
            f"уникальных {stats['unique_words']} · тегов {stats['tags']} · дублей {stats['duplicates']} · "
            f"≈{stats['estimated_tasks']} контекстных заданий"
        )
        query = self.knowledge_search.text().strip().lower() if hasattr(self, "knowledge_search") else ""
        kind_filter = self.knowledge_filter.currentData() if hasattr(self, "knowledge_filter") else ""
        entries = self.project.effective_knowledge()
        filtered: list[KnowledgeEntry] = []
        for entry in entries:
            if kind_filter and entry.kind != kind_filter:
                continue
            haystack = f"{entry.title} {entry.body} {' '.join(entry.tags)}".lower()
            if query and not all(part in haystack for part in query.split()):
                continue
            filtered.append(entry)
        self.visible_knowledge = filtered[-800:]
        self.knowledge_list.blockSignals(True)
        self.knowledge_list.clear()
        if len(filtered) > 800:
            self.knowledge_list.addItem(f"… показаны последние 800 из {len(filtered)} — уточните поиск")
        for entry in self.visible_knowledge:
            kind = KNOWLEDGE_KINDS.get(entry.kind, entry.kind)
            tags = f" · #{' #'.join(entry.tags)}" if entry.tags else ""
            preview = entry.body.replace("\n", " ")
            if len(preview) > 110:
                preview = preview[:110] + "…"
            self.knowledge_list.addItem(f"◆ {entry.display_title()}  ·  {kind} ×{entry.weight}{tags}\n{preview}")
        self.knowledge_list.blockSignals(False)

    def save_current(self) -> None:
        if self.project:
            self.project.materialize_knowledge()
            save_project(self.project)
            self.sync_dataset_page()

    def clear_knowledge_editor(self) -> None:
        self.editing_knowledge_id = ""
        self.knowledge_title.clear()
        self.knowledge_tags.clear()
        self.knowledge_body.clear()
        self.knowledge_kind.setCurrentIndex(0)
        self.knowledge_weight.setValue(1)
        self.knowledge_list.clearSelection()

    def on_knowledge_selected(self, row: int) -> None:
        entries = self.visible_knowledge
        actual_row = row
        # Служебная строка о лимите не является карточкой.
        if self.project and len(self.project.effective_knowledge()) > 800 and row == 0:
            return
        if self.knowledge_list.count() > len(entries):
            actual_row -= 1
        if not (0 <= actual_row < len(entries)):
            return
        entry = entries[actual_row]
        self.editing_knowledge_id = entry.id
        self.knowledge_title.setText(entry.title)
        self.knowledge_tags.setText(", ".join(entry.tags))
        self.knowledge_body.setPlainText(entry.body)
        idx = self.knowledge_kind.findData(entry.kind)
        self.knowledge_kind.setCurrentIndex(max(0, idx))
        self.knowledge_weight.setValue(entry.weight)

    def save_knowledge_editor(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        body = self.knowledge_body.toPlainText().strip()
        if not body:
            self.info("Введите само знание: факт, определение, правило или текст.")
            return
        entry = KnowledgeEntry(
            title=self.knowledge_title.text().strip(),
            body=body,
            kind=self.knowledge_kind.currentData(),
            tags=[t.strip() for t in self.knowledge_tags.text().split(",") if t.strip()],
            weight=self.knowledge_weight.value(),
            source="manual",
            id=self.editing_knowledge_id,
        )
        entries = proj.effective_knowledge()
        for i, old in enumerate(entries):
            if self.editing_knowledge_id and old.id == self.editing_knowledge_id:
                entries[i] = entry
                break
        else:
            entries.append(entry)
        proj.set_knowledge(dedupe_knowledge(entries))
        self.editing_knowledge_id = entry.id
        self.save_current()
        self.info("Знание сохранено. Оно попадёт в словарь, контекстные задания и поиск в чате.")

    def add_pasted(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        entries = parse_knowledge_plain(self.paste_edit.toPlainText(), source="paste")
        if not entries:
            self.info("Не нашёл знаний. Напишите факты абзацами или определения вида `термин = значение`.")
            return
        added = proj.add_knowledge(entries)
        self.paste_edit.clear()
        self.save_current()
        self.info(f"Добавлено карточек знаний: {added}. Дубли автоматически объединены.")

    def add_file(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        path, _ = QFileDialog.getOpenFileName(
            self, "Файл знаний", "", "Знания (*.txt *.md *.json *.csv);;Все файлы (*)"
        )
        if not path:
            return
        file_path = Path(path)
        try:
            text = file_path.read_text(encoding="utf-8-sig", errors="ignore")
            entries = parse_knowledge_document(text, suffix=file_path.suffix, source=f"file:{file_path.name}")
        except Exception as exc:
            self.error(str(exc))
            return
        if not entries:
            self.info("В файле не нашлось карточек знаний. Для JSON нужны поля title/body, для CSV — title и body.")
            return
        added = proj.add_knowledge(entries)
        self.save_current()
        self.info(f"Из файла «{file_path.name}» добавлено знаний: {added}.")

    def fetch_wiki(self) -> None:
        proj = self.require_project()
        title = self.wiki_edit.text().strip()
        if proj is None or not title:
            return

        def job():
            return entries_from_paragraphs(
                split_paragraphs(fetch_wikipedia_ru(title)), title=title, source="wikipedia"
            )

        def done(entries):
            if not entries:
                self.info("Статья не нашлась. Попробуйте другое название.")
                return
            added = proj.add_knowledge(entries)
            self.save_current()
            self.info(f"Википедия «{title}»: добавлено карточек знаний: {added}.")

        self.run_async(job, done)

    def fetch_url(self) -> None:
        proj = self.require_project()
        url = self.url_edit.text().strip()
        if proj is None or not url:
            return

        def job():
            host = url.split("/")[2] if "://" in url else "веб-страница"
            return entries_from_paragraphs(
                split_paragraphs(fetch_url_text(url)), title=host, source=f"url:{url}"
            )

        def done(entries):
            if not entries:
                self.info("Не удалось вытащить текст со страницы.")
                return
            added = proj.add_knowledge(entries)
            self.save_current()
            self.info(f"Со страницы добавлено карточек знаний: {added}.")

        self.run_async(job, done)

    def dedupe_dataset(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        before = len(proj.effective_knowledge())
        proj.set_knowledge(dedupe_knowledge(proj.effective_knowledge()))
        after = len(proj.knowledge)
        self.save_current()
        self.info(f"Проверка знаний завершена: было {before}, осталось {after}, дублей убрано: {before - after}.")

    def delete_knowledge(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        row = self.knowledge_list.currentRow()
        if self.knowledge_list.count() > len(self.visible_knowledge):
            row -= 1
        if not (0 <= row < len(self.visible_knowledge)):
            return
        entry_id = self.visible_knowledge[row].id
        proj.set_knowledge([e for e in proj.effective_knowledge() if e.id != entry_id])
        if self.editing_knowledge_id == entry_id:
            self.clear_knowledge_editor()
        self.save_current()

    def clear_dataset(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        if QMessageBox.question(self, "GSYNK-AI", "Полностью очистить базу знаний этой модели?") == QMessageBox.StandardButton.Yes:
            proj.set_knowledge([])
            self.clear_knowledge_editor()
            self.save_current()

    # ------------------------------------------------------------ словарь и загрузка
    def tokenizer_for(self, project: Project, rebuild: bool = False) -> Tokenizer:
        folder = project_dir(project.name)
        vocab_file = folder / "vocab.json"
        _, _, _, _, max_vocab, _ = SIZES[project.size]
        if vocab_file.exists() and not rebuild:
            return Tokenizer(json.loads(vocab_file.read_text(encoding="utf-8")))
        tokenizer = Tokenizer.build(project.all_texts(), max_vocab=max_vocab, min_freq=1)
        vocab_file.write_text(json.dumps(tokenizer.vocab, ensure_ascii=False), encoding="utf-8")
        return tokenizer

    def load_model_for(self, project: Project) -> tuple[GsaiGPT, Tokenizer, torch.device]:
        tokenizer = self.tokenizer_for(project)
        device, _ = pick_device(self.settings.turbo, self.settings.device_preference)
        ckpt = checkpoint_of(project.name)
        if ckpt.exists():
            model, _, _ = Trainer.load_checkpoint(ckpt, device)
        else:
            d, layers, heads, block, _, _ = SIZES[project.size]
            cfg = GsaiConfig(vocab_size=tokenizer.vocab_size, block_size=block, n_layer=layers, n_head=heads, n_embd=d)
            model = GsaiGPT(cfg).to(device)
        model.eval()
        return model, tokenizer, device

    # ------------------------------------------------------------ страница: обучение
    def page_train(self) -> QWidget:
        page = QWidget()
        lay = QVBoxLayout(page)
        lay.setContentsMargins(18, 16, 18, 16)
        lay.addWidget(label("Обучение на видеокарте", "title"))
        self.train_info = label("модель не выбрана", "metric")
        lay.addWidget(self.train_info)
        self.train_hint = label(
            "GPU-first: сначала CUDA, затем DirectML, и только потом CPU. Знания превратятся в задания "
            "на факты, выбор по контексту и follow-up; loss считается преимущественно по ответам, а не по промптам.",
            "dim",
        )
        lay.addWidget(self.train_hint)

        ctl = QHBoxLayout()
        ctl.addWidget(label("Шагов в этом забеге:"))
        self.steps_spin = QSpinBox()
        self.steps_spin.setRange(10, 1_000_000)
        self.steps_spin.setValue(3000)
        ctl.addWidget(self.steps_spin)
        self.turbo_box = QCheckBox("GPU ТУРБО: максимум ПК")
        self.turbo_box.setChecked(self.settings.turbo)
        ctl.addWidget(self.turbo_box)
        self.start_btn = QPushButton("🔥 Учить!")
        self.start_btn.setObjectName("primary")
        self.start_btn.clicked.connect(self.start_training)
        ctl.addWidget(self.start_btn)
        self.stop_btn = QPushButton("⏹ Хватит, стоп")
        self.stop_btn.setObjectName("danger")
        self.stop_btn.setEnabled(False)
        self.stop_btn.clicked.connect(self.stop_training)
        ctl.addWidget(self.stop_btn)
        self.reset_btn = QPushButton("↺ Сбросить обучение")
        self.reset_btn.setToolTip("Удалить контрольную точку и словарь: модель начнёт учиться с нуля по текущему датасету")
        self.reset_btn.clicked.connect(self.reset_training)
        ctl.addWidget(self.reset_btn)
        ctl.addStretch(1)
        lay.addWidget(card(ctl))

        adv = QHBoxLayout()
        adv.addWidget(label("Ускоритель:"))
        self.device_combo = QComboBox()
        self.device_combo.addItem("Авто: самая быстрая видеокарта", "auto")
        self.device_combo.addItem("CUDA (NVIDIA)", "cuda")
        self.device_combo.addItem("DirectML (AMD / Intel / NVIDIA в Windows)", "directml")
        self.device_combo.addItem("Только CPU", "cpu")
        dev_idx = self.device_combo.findData(self.settings.device_preference)
        self.device_combo.setCurrentIndex(max(0, dev_idx))
        adv.addWidget(self.device_combo, 1)
        self.auto_batch_box = QCheckBox("автобатч по VRAM")
        self.auto_batch_box.setChecked(self.settings.auto_batch)
        adv.addWidget(self.auto_batch_box)
        self.batch_spin = QSpinBox()
        self.batch_spin.setRange(1, 512)
        self.batch_spin.setValue(32)
        self.batch_spin.setPrefix("батч ")
        adv.addWidget(self.batch_spin)
        self.amp_box = QCheckBox("AMP (CUDA)")
        self.amp_box.setChecked(self.settings.mixed_precision)
        adv.addWidget(self.amp_box)
        self.compile_box = QCheckBox("torch.compile (CUDA)")
        self.compile_box.setChecked(self.settings.compile_model)
        adv.addWidget(self.compile_box)
        self.accum_spin = QSpinBox()
        self.accum_spin.setRange(1, 16)
        self.accum_spin.setPrefix("накопление ×")
        adv.addWidget(self.accum_spin)
        lay.addWidget(card(adv))

        self.chart = LossChart()
        chart_lay = QVBoxLayout()
        chart_lay.addWidget(self.chart)
        lay.addWidget(card(chart_lay))

        stats = QGridLayout()
        self.stat_step = label("шаг: —")
        self.stat_loss = label("loss: —")
        self.stat_tok = label("скорость: —")
        self.stat_epoch = label("эпох пройдено: —")
        self.stat_device = label("устройство: —")
        self.stat_batch = label("пакет (батч): —")
        self.stat_val = label("валидация: —")
        self.stat_precision = label("точность: —")
        self.stat_eta = label("осталось: —")
        self.cpu_label = label("CPU: —")
        stat_widgets = [
            self.stat_step, self.stat_loss, self.stat_val, self.stat_tok, self.stat_epoch,
            self.stat_device, self.stat_batch, self.stat_precision, self.stat_eta, self.cpu_label,
        ]
        for w in stat_widgets:
            w.setObjectName("metric")
        for i, w in enumerate(stat_widgets):
            stats.addWidget(w, i // 3, i % 3)
        stats_wrap = QVBoxLayout()
        stats_wrap.addLayout(stats)
        lay.addWidget(card(stats_wrap))
        lay.addWidget(label("Нагрузка процессора:"))
        self.cpu_bar = QProgressBar()
        self.cpu_bar.setRange(0, 100)
        lay.addWidget(self.cpu_bar)
        lay.addWidget(label(
            "В турбо-режиме вентиляторы будут шуметь на полную — это нормально. "
            "Следите за температурами (например, в HWMonitor). Контрольная точка пишется каждые 200 шагов: "
            "обучение можно останавливать и продолжать в любой момент.", "dim"))
        lay.addStretch(1)
        return page

    def refresh_train_labels(self) -> None:
        if self.project is None:
            return
        ckpt = checkpoint_of(self.project.name)
        stats = self.project.stats()
        context = SIZES.get(self.project.size, SIZES["lite"])[3]
        self.train_info.setText(
            f"{self.project.icon} {self.project.name} · контекст новых моделей {context} · "
            f"знаний {stats['entries']} · шагов {self.project.steps_done} · "
            + ("контрольная точка есть — продолжим с неё" if ckpt.exists() else "контрольной точки нет")
        )
        if hasattr(self, "batch_spin") and self.project.batch:
            self.batch_spin.setValue(self.project.batch)

    def start_training(self) -> None:
        proj = self.require_project()
        if proj is None or self.train_worker is not None:
            return
        if not proj.effective_knowledge():
            self.info("База знаний пуста. Добавьте факты, определения, слова, правила или тексты на вкладке «Знания».")
            self.goto(2)
            return
        self.settings.turbo = self.turbo_box.isChecked()
        self.settings.device_preference = self.device_combo.currentData()
        self.settings.auto_batch = self.auto_batch_box.isChecked()
        self.settings.mixed_precision = self.amp_box.isChecked()
        self.settings.compile_model = self.compile_box.isChecked()
        self.settings.save()
        try:
            ckpt = checkpoint_of(proj.name)
            tokenizer = self.tokenizer_for(proj, rebuild=not ckpt.exists())
            device, device_name = pick_device(self.settings.turbo, self.settings.device_preference)
            if ckpt.exists():
                model, _, _ = Trainer.load_checkpoint(ckpt, device)
                if model.cfg.vocab_size != tokenizer.vocab_size:
                    self.error("Словарь изменился и не совпадает с контрольной точкой. "
                               "Нажмите «Сбросить обучение», чтобы начать с чистого листа по новой базе знаний.")
                    return
            else:
                d, layers, heads, block, _, _ = SIZES[proj.size]
                cfg = GsaiConfig(vocab_size=tokenizer.vocab_size, block_size=block, n_layer=layers, n_head=heads, n_embd=d)
                model = GsaiGPT(cfg)
            data = build_training_data(proj, tokenizer, block_size=model.cfg.block_size, validation_fraction=0.06)
            batch = 0 if self.settings.auto_batch else self.batch_spin.value()
            proj.batch = batch
            save_project(proj)
            self.trainer = Trainer(
                model, data.train_ids, device, device_name, lr=proj.lr, batch=batch,
                labels=data.train_labels, val_stream=data.val_ids, val_labels=data.val_labels,
                compile_model=self.settings.compile_model,
                mixed_precision=self.settings.mixed_precision,
                grad_accum=self.accum_spin.value(), eval_every=250,
            )
            self.train_hint.setText(
                f"Собрано примеров: {data.examples}; контекстных: {data.context_examples}; "
                f"обучаемых токенов: {data.supervised_tokens}. Ускорение: {device_name} · {self.trainer.state.precision}."
            )
        except Exception:
            self.error(traceback.format_exc(limit=4))
            return
        self.chart.points.clear()
        self.trainer.state.step = proj.steps_done
        self.train_worker = TrainWorker(self.trainer, proj.steps_done + self.steps_spin.value(), checkpoint_of(proj.name))
        self.train_worker.state.connect(self.on_train_state)
        self.train_worker.finished_ok.connect(self.on_train_done)
        self.train_worker.failed.connect(self.on_train_failed)
        self.start_btn.setEnabled(False)
        self.stop_btn.setEnabled(True)
        self.reset_btn.setEnabled(False)
        self.train_worker.start()

    def on_train_state(self, st: dict) -> None:
        self.stat_step.setText(f"шаг: {st['step']}")
        self.stat_loss.setText(f"loss: {st['loss']:.3f} · PPL {st.get('perplexity', 0):.1f}")
        val = st.get("val_loss") or 0
        self.stat_val.setText(f"валидация: {val:.3f}" if val else "валидация: каждые 250 шагов")
        self.stat_tok.setText(f"скорость: {st['tok']:.0f} ток/с")
        self.stat_epoch.setText(f"эпох пройдено: {st['epoch']:.2f}")
        gpu_mb = st.get("gpu_mb") or 0
        self.stat_device.setText(f"устройство: {st['device']}" + (f" · VRAM {gpu_mb:.0f} МБ" if gpu_mb else ""))
        self.stat_batch.setText(f"эффективный батч: {st.get('batch', '—')}")
        self.stat_precision.setText(f"точность: {st.get('precision', 'FP32')} · LR {st.get('lr', 0):.2e}")
        eta = int(st.get("eta") or 0)
        self.stat_eta.setText(f"осталось: {eta // 3600}ч {(eta % 3600) // 60}м {eta % 60}с")
        if st.get("msg"):
            self.train_hint.setText("⚠ " + st["msg"])
        self.chart.push(st["loss"])

    def reset_training(self) -> None:
        proj = self.require_project()
        if proj is None or self.train_worker is not None:
            return
        if QMessageBox.question(self, "GSYNK-AI",
                                f"Сбросить обучение модели «{proj.name}»? Контрольная точка и словарь будут удалены, "
                                "следующее обучение начнётся с нуля по текущему датасету.") != QMessageBox.StandardButton.Yes:
            return
        for path in (checkpoint_of(proj.name), project_dir(proj.name) / "vocab.json"):
            try:
                path.unlink(missing_ok=True)
            except Exception:
                pass
        proj.steps_done = 0
        proj.best_loss = 0.0
        save_project(proj)
        self.refresh_train_labels()
        self.info("Обучение сброшено. Теперь «Учить!» начнёт с чистого листа.")

    def stop_training(self) -> None:
        if self.trainer:
            self.trainer.stop()

    def on_train_failed(self, text: str) -> None:
        self.train_worker = None
        self.start_btn.setEnabled(True)
        self.stop_btn.setEnabled(False)
        self.reset_btn.setEnabled(True)
        self.error(text)

    def on_train_done(self, result: dict) -> None:
        self.train_worker = None
        self.start_btn.setEnabled(True)
        self.stop_btn.setEnabled(False)
        self.reset_btn.setEnabled(True)
        if self.project:
            self.project.steps_done = result["step"]
            self.project.best_loss = result["loss"]
            save_project(self.project)
            self.refresh_train_labels()
        self.chat_model = None
        self.info(f"Обучение остановлено на шаге {result['step']} (loss {result['loss']:.3f}). "
                  "Сразу проверяем модель в чате!")
        self.goto(4)
        self.prepare_chat()

    # ------------------------------------------------------------ страница: чат
    def page_chat(self) -> QWidget:
        page = QWidget()
        lay = QVBoxLayout(page)
        lay.setContentsMargins(18, 16, 18, 16)
        lay.addWidget(label("Чат и контекст", "title"))
        lay.addWidget(label(
            "Модель видит память о вас, найденные карточки знаний и последние реплики целиком. "
            "В глубоком режиме она делает несколько вариантов и выбирает наименее противоречивый.",
            "dim",
        ))
        splitter = QSplitter(Qt.Orientation.Horizontal)
        chat_side = QWidget()
        chat_side.setMinimumWidth(520)
        chat_lay = QVBoxLayout(chat_side)
        self.chat_view = QTextBrowser()
        chat_lay.addWidget(self.chat_view, 1)
        row = QHBoxLayout()
        self.chat_input = QLineEdit()
        self.chat_input.setPlaceholderText("напишите модели… попробуйте: меня зовут …, а потом — как меня зовут?")
        self.chat_input.returnPressed.connect(self.send_chat)
        row.addWidget(self.chat_input, 1)
        self.chat_send_btn = QPushButton("Думать и ответить")
        self.chat_send_btn.setObjectName("primary")
        self.chat_send_btn.clicked.connect(self.send_chat)
        row.addWidget(self.chat_send_btn)
        chat_lay.addLayout(row)

        knobs = QVBoxLayout()
        temp_row = QHBoxLayout()
        temp_row.addWidget(label("Температура:"))
        self.temp_slider = QSlider(Qt.Orientation.Horizontal)
        self.temp_slider.setRange(5, 150)
        self.temp_slider.setValue(80)
        temp_row.addWidget(self.temp_slider, 1)
        self.temp_value = label("0.80")
        self.temp_slider.valueChanged.connect(lambda v: self.temp_value.setText(f"{v / 100:.2f}"))
        temp_row.addWidget(self.temp_value)
        temp_row.addWidget(label("top-p:"))
        self.top_p_spin = QDoubleSpinBox()
        self.top_p_spin.setRange(0.10, 1.00)
        self.top_p_spin.setSingleStep(0.01)
        self.top_p_spin.setValue(0.92)
        temp_row.addWidget(self.top_p_spin)
        temp_row.addWidget(label("кандидатов:"))
        self.candidates_spin = QSpinBox()
        self.candidates_spin.setRange(1, 6)
        self.candidates_spin.setValue(3)
        temp_row.addWidget(self.candidates_spin)
        knobs.addLayout(temp_row)
        toggles = QHBoxLayout()
        self.memory_box = QCheckBox("память о пользователе")
        self.memory_box.setChecked(True)
        self.retrieval_box = QCheckBox("искать в знаниях")
        self.retrieval_box.setChecked(True)
        clear_context = QPushButton("Сбросить контекст")
        clear_context.clicked.connect(self.clear_context)
        toggles.addWidget(self.memory_box)
        toggles.addWidget(self.retrieval_box)
        toggles.addWidget(clear_context)
        toggles.addStretch(1)
        knobs.addLayout(toggles)
        actions = QHBoxLayout()
        more = QPushButton("🔥 Учить ещё")
        more.clicked.connect(lambda: self.goto(3))
        fix = QPushButton("🧠 Править знания")
        fix.clicked.connect(lambda: self.goto(2))
        rel = QPushButton("🚀 Выпустить")
        rel.setObjectName("primary")
        rel.clicked.connect(lambda: self.goto(5))
        actions.addWidget(more)
        actions.addWidget(fix)
        actions.addWidget(rel)
        actions.addStretch(1)
        knobs.addLayout(actions)
        chat_lay.addWidget(card(knobs))

        inspector_side = QWidget()
        inspector_side.setMinimumWidth(300)
        inspector_outer = QVBoxLayout(inspector_side)
        inspector = QVBoxLayout()
        inspector.addWidget(label("◈ Инспектор контекста", "h2"))
        self.context_status = label("контекст ещё не собран", "metric")
        inspector.addWidget(self.context_status)
        inspector.addWidget(label("Память о вас:", "h2"))
        self.memory_view = QListWidget()
        self.memory_view.setWordWrap(True)
        inspector.addWidget(self.memory_view)
        inspector.addWidget(label("Найденные знания:", "h2"))
        self.retrieved_view = QListWidget()
        self.retrieved_view.setWordWrap(True)
        inspector.addWidget(self.retrieved_view)
        inspector.addWidget(label("Проверка кандидатов:", "h2"))
        self.candidates_view = QListWidget()
        self.candidates_view.setWordWrap(True)
        inspector.addWidget(self.candidates_view)
        inspector_outer.addWidget(card(inspector), 1)

        splitter.addWidget(chat_side)
        splitter.addWidget(inspector_side)
        splitter.setStretchFactor(0, 3)
        splitter.setStretchFactor(1, 2)
        splitter.setSizes([720, 430])
        lay.addWidget(splitter, 1)
        return page

    def chat_print(self, who: str, text: str, color: str) -> None:
        safe = text.replace("&", "&amp;").replace("<", "&lt;")
        self.chat_view.append(f'<p><b style="color:{color}">{who}:</b> {safe}</p>')

    def sync_context_inspector(self, reply=None) -> None:
        if not hasattr(self, "memory_view"):
            return
        session = self.chat_session
        self.memory_view.clear()
        facts = list(session.memory.facts.values())
        if facts or session.memory.custom:
            for fact in facts:
                self.memory_view.addItem(f"● {fact.label}: {fact.value}")
            for item in session.memory.custom:
                self.memory_view.addItem(f"● запомнить: {item}")
        else:
            self.memory_view.addItem("пока пусто — скажите «меня зовут …» или «запомни: …»")
        if reply is not None:
            plan = reply.plan
            self.context_status.setText(
                f"промпт {plan.prompt_tokens} токенов · ходов истории {plan.turns_used} · режим {reply.mode}"
            )
            self.retrieved_view.clear()
            if plan.retrieved:
                for item in plan.retrieved:
                    self.retrieved_view.addItem(
                        f"{item.score:.2f} · {item.entry.display_title()}\nсовпало: {', '.join(item.matched[:6])}"
                    )
            else:
                self.retrieved_view.addItem("подходящих знаний не найдено")
            self.candidates_view.clear()
            for i, cand in enumerate(reply.candidates, 1):
                mark = "✓ выбран" if i == 1 else "отклонён"
                reasons = ", ".join(cand.reasons) if cand.reasons else "без особых сигналов"
                self.candidates_view.addItem(f"#{i} {mark} · score {cand.score:.2f} · {cand.source}\n{reasons}")
        else:
            topics = session.topics()
            self.context_status.setText(
                f"сохранено ходов {len(session.history)}" + (f" · темы: {', '.join(topics)}" if topics else "")
            )

    def render_saved_chat(self) -> None:
        if not hasattr(self, "chat_view") or self.project is None:
            return
        for user, bot in self.chat_session.history[-30:]:
            self.chat_print("Вы", user, GREEN)
            self.chat_print(self.project.name, bot, "#C4B5FD")

    def clear_context(self) -> None:
        if QMessageBox.question(
            self, "GSYNK-AI", "Удалить историю чата и память о пользователе для этой модели?"
        ) != QMessageBox.StandardButton.Yes:
            return
        self.chat_session = ContextSession()
        self.chat_view.clear()
        self.retrieved_view.clear()
        self.candidates_view.clear()
        self.save_context_session()
        self.sync_context_inspector()

    def prepare_chat(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        if self.chat_model is not None:
            return
        self.chat_print("GSYNK-AI", "загружаю модель…", ACCENT)

        def done(bundle):
            self.chat_model, self.chat_tokenizer, self.chat_device = bundle
            self.temp_slider.setValue(int(proj.temperature * 100))
            self.top_p_spin.setValue(proj.top_p)
            self.candidates_spin.setValue(proj.think_candidates)
            self.memory_box.setChecked(proj.use_memory)
            self.retrieval_box.setChecked(proj.use_retrieval)
            self.sync_context_inspector()
            self.chat_print(
                "GSYNK-AI",
                f"модель {proj.icon} {proj.name} на связи. контекст {self.chat_model.cfg.block_size} токенов, "
                f"знаний {len(proj.effective_knowledge())}. Напишите ей что-нибудь!",
                ACCENT,
            )

        self.run_async(lambda: self.load_model_for(proj), done)

    def send_chat(self) -> None:
        proj = self.require_project()
        text = self.chat_input.text().strip()
        if proj is None or not text or self.chat_busy:
            return
        if self.chat_model is None:
            self.prepare_chat()
            self.info("Модель ещё загружается, секунду…")
            return
        self.chat_input.clear()
        self.chat_print("Вы", text, GREEN)
        self.chat_busy = True
        self.chat_send_btn.setEnabled(False)
        self.chat_send_btn.setText("Думаю…")
        model, tokenizer, device = self.chat_model, self.chat_tokenizer, self.chat_device
        session = self.chat_session
        entries = proj.identity_entries() + proj.effective_knowledge()
        identity = f"{proj.name}. {proj.description} {proj.persona}".strip()
        temp = self.temp_slider.value() / 100.0
        top_p = self.top_p_spin.value()
        candidates = self.candidates_spin.value()
        proj.temperature = temp
        proj.top_p = top_p
        proj.think_candidates = candidates
        proj.use_memory = self.memory_box.isChecked()
        proj.use_retrieval = self.retrieval_box.isChecked()
        save_project(proj)

        def job():
            return context_reply(
                model, tokenizer, session, text, entries, device=device,
                identity_text=identity, temperature=temp, top_k=proj.top_k,
                top_p=top_p, repetition_penalty=proj.repetition_penalty,
                max_new=proj.max_new_tokens, candidates=candidates,
                use_memory=proj.use_memory, use_retrieval=proj.use_retrieval,
            )

        def done(reply):
            self.chat_busy = False
            self.chat_send_btn.setEnabled(True)
            self.chat_send_btn.setText("Думать и ответить")
            self.chat_print(proj.name, reply.text, "#C4B5FD")
            self.save_context_session()
            self.sync_context_inspector(reply)

        def failed(_text):
            self.chat_busy = False
            self.chat_send_btn.setEnabled(True)
            self.chat_send_btn.setText("Думать и ответить")

        self.run_async(job, done, on_failed=failed)

    # ------------------------------------------------------------ страница: выпуск
    def page_release(self) -> QWidget:
        page = QWidget()
        lay = QVBoxLayout(page)
        lay.setContentsMargins(18, 16, 18, 16)
        lay.addWidget(label("Выпуск модели", "title"))
        self.release_info = label("модель не выбрана", "dim")
        lay.addWidget(self.release_info)
        row = QHBoxLayout()
        row.addWidget(label("Версия модели:"))
        self.release_version = QLineEdit("1.0.0")
        row.addWidget(self.release_version)
        row.addStretch(1)
        lay.addLayout(row)
        btns = QHBoxLayout()
        export_btn = QPushButton("💾 Собрать файл .gsai")
        export_btn.clicked.connect(self.export_local)
        publish_btn = QPushButton("🚀 Выпустить на сервер")
        publish_btn.setObjectName("primary")
        publish_btn.clicked.connect(self.publish)
        unpub_btn = QPushButton("Снять с сервера")
        unpub_btn.clicked.connect(self.unpublish)
        btns.addWidget(export_btn)
        btns.addWidget(publish_btn)
        btns.addWidget(unpub_btn)
        btns.addStretch(1)
        lay.addLayout(btns)
        lay.addWidget(label("Журнал выпуска:", "h2"))
        self.release_log = QPlainTextEdit()
        self.release_log.setReadOnly(True)
        lay.addWidget(self.release_log, 1)
        lay.addWidget(label("После выпуска откройте GSYNK на телефоне → плитка «GSYNK-AI модели» — модель уже там. "
                            "Для публикации нужен GitHub-токен из настроек.", "dim"))
        return page

    def sync_release_page(self) -> None:
        if self.project:
            stats = self.project.stats()
            context = SIZES.get(self.project.size, SIZES["lite"])[3]
            self.release_info.setText(
                f"{self.project.icon} {self.project.name} · шагов {self.project.steps_done} · "
                f"знаний {stats['entries']} · контекст новых моделей {context} · формат gsai-1"
            )
            self.release_version.setText(self.project.model_version)

    def rlog(self, text: str) -> None:
        self.release_log.appendPlainText(text)

    def build_package(self, project: Project):
        model, tokenizer, _ = self.load_model_for(project)
        folder = project_dir(project.name)
        icon_png = None
        if (folder / "icon.png").exists():
            icon_png = (folder / "icon.png").read_bytes()
        project.model_version = self.release_version.text().strip() or "1.0.0"
        out = package_of(project.name)
        meta = export_package(
            model, tokenizer, out,
            name=project.name, icon=project.icon, description=project.description,
            version=project.model_version, temperature=self.temp_slider.value() / 100.0,
            top_k=project.top_k, top_p=self.top_p_spin.value(),
            repetition_penalty=project.repetition_penalty,
            max_new_tokens=project.max_new_tokens,
            knowledge=[asdict(e) for e in project.effective_knowledge()],
            dataset_stats=project.stats(), steps=project.steps_done, loss=project.best_loss,
            icon_png=icon_png,
        )
        save_project(project)
        return out, meta

    def export_local(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        if not checkpoint_of(proj.name).exists():
            self.info("Модель ещё не обучалась — выпускать нечего.")
            return

        def done(result):
            out, meta = result
            self.rlog(
                f"собран {out.name}: {meta['file_size']} байт, параметров {meta['params']}, "
                f"контекст {meta['architecture']['block_size']}, знаний {meta['context']['knowledge_entries']}"
            )
            self.info(f"Файл модели готов:\n{out}")

        self.run_async(lambda: self.build_package(proj), done)

    def publish(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        if not self.settings.token.strip():
            self.info("Сначала введите GitHub-токен на вкладке «Настройки» (доступ к репозиторию GSYNK).")
            self.goto(6)
            return
        self.rlog("— выпуск на сервер —")

        def job():
            out, meta = self.build_package(proj)
            return server.publish_model(
                self.settings.repo, self.settings.token, out, meta, slugify(proj.name),
                on_log=lambda m: None,
            )

        def done(entry):
            self.rlog(f"опубликовано: {entry['name']} v{entry['version']} → {entry['url']}")
            self.info("Модель на сервере! Откройте GSYNK на телефоне — плитка «GSYNK-AI модели».")
            self.refresh_models()

        self.run_async(job, done)

    def unpublish(self) -> None:
        proj = self.require_project()
        if proj is None or not self.settings.token.strip():
            self.info("Нужна модель и GitHub-токен в настройках.")
            return

        def done(_):
            self.rlog("модель снята с сервера")
            self.refresh_models()

        self.run_async(lambda: server.unpublish_model(self.settings.repo, self.settings.token, slugify(proj.name)), done)

    # ------------------------------------------------------------ страница: настройки
    def page_settings(self) -> QWidget:
        page = QWidget()
        lay = QVBoxLayout(page)
        lay.setContentsMargins(18, 16, 18, 16)
        lay.addWidget(label("Настройки", "title"))

        form = QVBoxLayout()
        form.addWidget(label("Репозиторий сервера моделей (GitHub):", "h2"))
        self.repo_edit = QLineEdit(self.settings.repo)
        form.addWidget(self.repo_edit)
        form.addWidget(label("GitHub-токен (нужен только для выпуска моделей; хранится только на этом ПК):", "h2"))
        self.token_edit = QLineEdit(self.settings.token)
        self.token_edit.setEchoMode(QLineEdit.EchoMode.Password)
        form.addWidget(self.token_edit)
        self.turbo_setting = QCheckBox("ТУРБО по умолчанию: видеокарта (DirectML) / все потоки CPU")
        self.turbo_setting.setChecked(self.settings.turbo)
        form.addWidget(self.turbo_setting)
        self.updates_setting = QCheckBox("Проверять обновления GSYNK-AI при запуске")
        self.updates_setting.setChecked(self.settings.check_updates)
        form.addWidget(self.updates_setting)
        save_btn = QPushButton("Сохранить настройки")
        save_btn.setObjectName("primary")
        save_btn.clicked.connect(self.save_settings)
        form.addWidget(save_btn)
        lay.addWidget(card(form))

        upd = QVBoxLayout()
        upd.addWidget(label(f"Обновление программы (текущая версия {VERSION})", "h2"))
        check_btn = QPushButton("Проверить обновление GSYNK-AI")
        check_btn.clicked.connect(self.manual_update_check)
        upd.addWidget(check_btn)
        self.update_status = label("обновления проверяются с GitHub-релизов GSYNK", "dim")
        upd.addWidget(self.update_status)
        lay.addWidget(card(upd))

        hw = QVBoxLayout()
        hw.addWidget(label("Ваше железо", "h2"))
        hw.addWidget(label(self.hardware_text(), "dim"))
        lay.addWidget(card(hw))
        lay.addStretch(1)
        return page

    def hardware_text(self) -> str:
        lines = [f"потоков CPU: {os.cpu_count()}"]
        try:
            lines.append(hardware_summary())
            device, name = pick_device(self.settings.turbo, self.settings.device_preference)
            lines.append(f"приоритет обучения: {name} ({device})")
            if device.type == "cpu":
                lines.append("подсказка: torch-directml включает видеокарту AMD/Intel под Windows, а CUDA-сборка PyTorch — NVIDIA.")
        except Exception:
            pass
        return "\n".join(lines)

    def save_settings(self) -> None:
        self.settings.repo = self.repo_edit.text().strip() or server.DEFAULT_REPO
        self.settings.token = self.token_edit.text().strip()
        self.settings.turbo = self.turbo_setting.isChecked()
        self.settings.check_updates = self.updates_setting.isChecked()
        self.turbo_box.setChecked(self.settings.turbo)
        if hasattr(self, "device_combo"):
            self.settings.device_preference = self.device_combo.currentData()
            self.settings.auto_batch = self.auto_batch_box.isChecked()
            self.settings.mixed_precision = self.amp_box.isChecked()
            self.settings.compile_model = self.compile_box.isChecked()
        self.settings.save()
        self.info("Настройки сохранены.")

    # ------------------------------------------------------------ автообновление
    def on_update_checked(self, update) -> None:
        if update:
            self.pending_update = update
            self.update_btn.setText(f"⬆ Обновление GSYNK-AI {update['version']} — установить")
            self.update_btn.show()
            self.update_status.setText(f"доступна версия {update['version']} ({update['asset_name']})")

    def manual_update_check(self) -> None:
        self.update_status.setText("проверяю…")

        def done(update):
            if update:
                self.on_update_checked(update)
            else:
                self.update_status.setText("у вас последняя версия GSYNK-AI")

        def failed(_text):
            self.update_status.setText("не удалось проверить обновление — смотрите текст ошибки выше")

        self.run_async(lambda: updater.check_update(self.settings.repo), done, on_failed=failed)

    def apply_update(self) -> None:
        update = getattr(self, "pending_update", None)
        if not update:
            return
        self.update_btn.setEnabled(False)
        self.update_btn.setText("качаю обновление…")

        def job():
            try:
                updater.download_and_apply(update)
                return ""
            except Exception as exc:  # показываем человеку и разблокируем кнопку ниже
                return str(exc) or exc.__class__.__name__

        def done(error_text):
            if error_text:
                self.update_btn.setEnabled(True)
                self.update_btn.setText(f"⬆ Обновление GSYNK-AI {update['version']} — установить")
                self.update_status.setText("обновление не установилось — можно попробовать ещё раз")
                self.error(error_text)
                return
            self.info("Обновление скачано. GSYNK-AI сейчас закроется, обновится и запустится снова.")
            QApplication.quit()

        self.run_async(job, done)


def main() -> int:
    app = QApplication(sys.argv)
    app.setStyleSheet(QSS)
    app.setFont(QFont("Segoe UI", 10))
    window = MainWindow()
    window.show()
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
