#!/usr/bin/env python3
"""
GSYNK-AI — студия собственных ИИ-моделей.

Создавайте модель, собирайте датасет (вручную, генератором или из интернета),
обучайте на своём ПК на 100% мощности, проверяйте в чате и выпускайте на сервер —
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
from PySide6.QtGui import QColor, QFont, QPainter, QPen
from PySide6.QtWidgets import (
    QApplication, QCheckBox, QComboBox, QFileDialog, QFrame, QGridLayout,
    QHBoxLayout, QLabel, QLineEdit, QListWidget, QMainWindow, QMessageBox,
    QPlainTextEdit, QProgressBar, QPushButton, QScrollArea, QSlider, QSpinBox,
    QStackedWidget, QTextBrowser, QVBoxLayout, QWidget,
)

from gsai import VERSION, server, updater
from gsai.dataset import (
    ICONS, SIZES, Project, build_stream, fetch_url_text, fetch_wikipedia_ru,
    generate_russian_dataset, parse_dialogues_plain, split_paragraphs,
)
from gsai.generate import chat_reply
from gsai.model import GsaiConfig, GsaiGPT
from gsai.package import export_package
from gsai.store import Settings, list_projects, package_of, project_dir, save_project, slugify, checkpoint_of
from gsai.tokenizer import Tokenizer
from gsai.trainer import Trainer, pick_device

ACCENT = "#8B5CF6"
GREEN = "#7BD88F"

QSS = """
QMainWindow, QWidget { background: #0B0616; color: #F2EEFF; font-family: 'Segoe UI'; font-size: 13px; }
QFrame#card { background: rgba(139, 92, 246, 0.10); border: 1px solid rgba(255,255,255,0.16); border-radius: 10px; }
QFrame#sidebar { background: qlineargradient(x1:0,y1:0,x2:0,y2:1, stop:0 #150B2E, stop:1 #0B0616); border-right: 1px solid rgba(255,255,255,0.12); }
QPushButton { background: rgba(139,92,246,0.25); border: 1px solid rgba(255,255,255,0.22); border-radius: 7px; padding: 7px 14px; }
QPushButton:hover { background: rgba(139,92,246,0.45); }
QPushButton:disabled { background: rgba(255,255,255,0.06); color: rgba(255,255,255,0.4); }
QPushButton#primary { background: #8B5CF6; color: white; font-weight: 600; }
QPushButton#primary:hover { background: #9D72FF; }
QPushButton#danger { background: rgba(229,57,70,0.35); border-color: rgba(229,57,70,0.7); }
QPushButton#nav { background: transparent; border: none; text-align: left; padding: 10px 14px; font-size: 14px; border-radius: 7px; }
QPushButton#nav:hover { background: rgba(139,92,246,0.20); }
QPushButton#navActive { background: rgba(139,92,246,0.40); border: none; text-align: left; padding: 10px 14px; font-size: 14px; border-radius: 7px; }
QLineEdit, QPlainTextEdit, QSpinBox, QComboBox, QListWidget, QTextBrowser {
  background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.18); border-radius: 7px; padding: 6px; selection-background-color: #8B5CF6;
}
QComboBox::drop-down { border: none; }
QLabel#title { font-size: 24px; font-weight: 300; }
QLabel#h2 { font-size: 16px; font-weight: 600; }
QLabel#dim { color: rgba(255,255,255,0.62); }
QProgressBar { border: 1px solid rgba(255,255,255,0.2); border-radius: 6px; height: 14px; text-align: center; }
QProgressBar::chunk { background: #8B5CF6; border-radius: 5px; }
QSlider::groove:horizontal { height: 6px; background: rgba(255,255,255,0.15); border-radius: 3px; }
QSlider::handle:horizontal { width: 16px; height: 16px; margin: -5px 0; border-radius: 8px; background: #8B5CF6; }
QScrollBar:vertical { width: 10px; background: transparent; }
QScrollBar::handle:vertical { background: rgba(139,92,246,0.5); border-radius: 5px; }
"""


def card(layout: QVBoxLayout | QHBoxLayout) -> QFrame:
    frame = QFrame()
    frame.setObjectName("card")
    layout.setContentsMargins(14, 12, 14, 12)
    frame.setLayout(layout)
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
                    "step": st.step, "loss": st.loss_ema, "tok": st.tok_per_sec,
                    "epoch": st.epoch, "lr": st.lr, "device": st.device,
                    "batch": self.trainer.batch, "msg": st.message,
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
        return "Датасет слишком маленький для обучения. Добавьте диалогов на вкладке «Датасет»."
    if "не хватает видеопамяти даже" in low:
        last = next((ln.strip() for ln in reversed(text.splitlines()) if ln.strip()), text)
        return last
    last = next((ln.strip() for ln in reversed(text.splitlines()) if ln.strip()), text)
    return f"Что-то пошло не так:\n{last}\n\nЕсли ошибка повторяется, сделайте скриншот и покажите его."


class MainWindow(QMainWindow):
    PAGES = ["🍇 1 · Модели", "✨ 2 · Создать", "🗂 3 · Датасет", "🔥 4 · Обучение", "💬 5 · Проверка в чате", "🚀 6 · Выпуск", "⚙ Настройки"]

    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle(f"GSYNK-AI {VERSION} — студия своих ИИ-моделей")
        self.resize(1240, 800)
        self.settings = Settings()
        self.project: Project | None = None
        self.trainer: Trainer | None = None
        self.train_worker: TrainWorker | None = None
        self.chat_model: GsaiGPT | None = None
        self.chat_tokenizer: Tokenizer | None = None
        self.chat_device = torch.device("cpu")
        self.chat_history: list[tuple[str, str]] = []
        self.workers: list[QThread] = []

        root = QHBoxLayout()
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        side = QFrame()
        side.setObjectName("sidebar")
        side.setFixedWidth(230)
        side_layout = QVBoxLayout(side)
        side_layout.setContentsMargins(12, 16, 12, 12)
        logo = label("🍇 GSYNK-AI", "title")
        side_layout.addWidget(logo)
        side_layout.addWidget(label("свои нейросети:\nсоздай → обучи → выпусти в GSYNK", "dim"))
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

        container = QWidget()
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

    def error(self, text: str) -> None:
        QMessageBox.critical(self, "GSYNK-AI", friendly_error(text))

    def info(self, text: str) -> None:
        QMessageBox.information(self, "GSYNK-AI", text)

    def run_async(self, fn, on_done, silent: bool = False) -> None:
        worker = FuncWorker(fn)
        worker.done.connect(on_done)
        if silent:
            worker.failed.connect(lambda _: None)
        else:
            worker.failed.connect(self.error)
        worker.finished.connect(lambda: self.workers.remove(worker) if worker in self.workers else None)
        self.workers.append(worker)
        worker.start()

    def require_project(self) -> Project | None:
        if self.project is None:
            self.info("Сначала создайте модель на вкладке «Создать» или выберите её в «Модели».")
            return None
        return self.project

    def set_project(self, project: Project) -> None:
        self.project = project
        self.chat_model = None
        self.chat_history = []
        self.current_label.setText(f"модель: {project.icon} {project.name}")
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
            lay.addWidget(label(
                f"размер: {proj.size} · диалогов: {stats['dialogues']} · текстов: {stats['texts']}\n"
                f"шагов обучения: {proj.steps_done} · loss: {proj.best_loss:.3f}" if proj.best_loss else
                f"размер: {proj.size} · диалогов: {stats['dialogues']} · текстов: {stats['texts']}\nещё не обучалась", "dim"))
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
        create_btn = QPushButton("Создать и перейти к датасету")
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
        )
        folder = save_project(project)
        icon_path = self.create_icon_file.text().strip()
        if icon_path and Path(icon_path).exists():
            (folder / "icon.png").write_bytes(Path(icon_path).read_bytes())
        self.set_project(project)
        self.info(f"Модель «{name}» создана. Теперь соберите датасет — можно одной кнопкой сгенерировать русский.")
        self.goto(2)

    # ------------------------------------------------------------ страница: датасет
    def page_dataset(self) -> QWidget:
        page = QWidget()
        lay = QVBoxLayout(page)
        lay.setContentsMargins(18, 16, 18, 16)
        lay.addWidget(label("Датасет", "title"))
        self.dataset_stats = label("модель не выбрана", "dim")
        lay.addWidget(self.dataset_stats)

        gen = QHBoxLayout()
        gen.addWidget(label("Сгенерировать русский датасет:", "h2"))
        self.gen_count = QSpinBox()
        self.gen_count.setRange(100, 50000)
        self.gen_count.setValue(3000)
        gen.addWidget(self.gen_count)
        gen_btn = QPushButton("🎲 Сгенерировать")
        gen_btn.setObjectName("primary")
        gen_btn.clicked.connect(self.generate_dataset)
        gen.addWidget(gen_btn)
        gen.addStretch(1)
        lay.addWidget(card(gen))

        paste_lay = QVBoxLayout()
        paste_lay.addWidget(label("Свои диалоги — по одному на строку:  вопрос => ответ   (или блоками «Пользователь: … / Модель: …»)", "h2"))
        self.paste_edit = QPlainTextEdit()
        self.paste_edit.setPlaceholderText("привет => привет! как дела?\nкак тебя зовут? => меня зовут …")
        self.paste_edit.setMaximumHeight(110)
        paste_lay.addWidget(self.paste_edit)
        row = QHBoxLayout()
        add_btn = QPushButton("Добавить диалоги")
        add_btn.clicked.connect(self.add_pasted)
        file_btn = QPushButton("Загрузить .txt файл…")
        file_btn.clicked.connect(self.add_file)
        row.addWidget(add_btn)
        row.addWidget(file_btn)
        row.addStretch(1)
        paste_lay.addLayout(row)
        lay.addWidget(card(paste_lay))

        web = QVBoxLayout()
        web.addWidget(label("Подхватить знания из интернета:", "h2"))
        wrow = QHBoxLayout()
        self.wiki_edit = QLineEdit()
        self.wiki_edit.setPlaceholderText("тема в русской Википедии, например: космос")
        wrow.addWidget(self.wiki_edit, 1)
        wiki_btn = QPushButton("Скачать статью")
        wiki_btn.clicked.connect(self.fetch_wiki)
        wrow.addWidget(wiki_btn)
        web.addLayout(wrow)
        urow = QHBoxLayout()
        self.url_edit = QLineEdit()
        self.url_edit.setPlaceholderText("или любая ссылка https://…")
        urow.addWidget(self.url_edit, 1)
        url_btn = QPushButton("Скачать текст")
        url_btn.clicked.connect(self.fetch_url)
        urow.addWidget(url_btn)
        web.addLayout(urow)
        lay.addWidget(card(web))

        edit = QHBoxLayout()
        self.expand_btn = QPushButton("🧠 Расширить датасет самой моделью")
        self.expand_btn.clicked.connect(self.expand_with_model)
        edit.addWidget(self.expand_btn)
        del_sel = QPushButton("Удалить выбранный диалог")
        del_sel.clicked.connect(self.delete_dialogue)
        edit.addWidget(del_sel)
        clear_btn = QPushButton("Очистить датасет")
        clear_btn.setObjectName("danger")
        clear_btn.clicked.connect(self.clear_dataset)
        edit.addWidget(clear_btn)
        edit.addStretch(1)
        lay.addLayout(edit)

        self.dialogue_list = QListWidget()
        lay.addWidget(self.dialogue_list, 1)
        return page

    def sync_dataset_page(self) -> None:
        if self.project is None:
            return
        stats = self.project.stats()
        self.dataset_stats.setText(
            f"{self.project.icon} {self.project.name}: диалогов {stats['dialogues']}, текстов {stats['texts']}, "
            f"символов {stats['chars']}. Чем больше и разнообразнее датасет — тем умнее модель."
        )
        self.dialogue_list.clear()
        for user, bot in self.project.dialogues[-400:]:
            self.dialogue_list.addItem(f"👤 {user}  →  🤖 {bot}")
        if len(self.project.dialogues) > 400:
            self.dialogue_list.insertItem(0, f"… показаны последние 400 из {len(self.project.dialogues)}")

    def save_current(self) -> None:
        if self.project:
            save_project(self.project)
            self.sync_dataset_page()

    def generate_dataset(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        count = self.gen_count.value()
        dialogues, texts = generate_russian_dataset(proj.name, count=count)
        proj.dialogues.extend(dialogues)
        proj.texts.extend(texts)
        self.save_current()
        self.info(f"Добавлено диалогов: {len(dialogues)}, текстов: {len(texts)}.")

    def add_pasted(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        pairs = parse_dialogues_plain(self.paste_edit.toPlainText())
        if not pairs:
            self.info("Не нашёл диалогов. Формат:  вопрос => ответ  по одному на строку.")
            return
        proj.dialogues.extend(pairs)
        self.paste_edit.clear()
        self.save_current()
        self.info(f"Добавлено диалогов: {len(pairs)}.")

    def add_file(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        path, _ = QFileDialog.getOpenFileName(self, "Файл датасета", "", "Текст (*.txt *.md)")
        if not path:
            return
        text = Path(path).read_text(encoding="utf-8", errors="ignore")
        pairs = parse_dialogues_plain(text)
        if pairs:
            proj.dialogues.extend(pairs)
            self.info(f"Из файла добавлено диалогов: {len(pairs)}.")
        else:
            chunks = split_paragraphs(text)
            proj.texts.extend(chunks)
            self.info(f"Из файла добавлено текстовых кусков: {len(chunks)}.")
        self.save_current()

    def fetch_wiki(self) -> None:
        proj = self.require_project()
        title = self.wiki_edit.text().strip()
        if proj is None or not title:
            return

        def job():
            return split_paragraphs(fetch_wikipedia_ru(title))

        def done(chunks):
            if not chunks:
                self.info("Статья не нашлась. Попробуйте другое название.")
                return
            proj.texts.extend(chunks)
            self.save_current()
            self.info(f"Википедия «{title}»: добавлено кусков текста: {len(chunks)}.")

        self.run_async(job, done)

    def fetch_url(self) -> None:
        proj = self.require_project()
        url = self.url_edit.text().strip()
        if proj is None or not url:
            return

        def job():
            return split_paragraphs(fetch_url_text(url))

        def done(chunks):
            if not chunks:
                self.info("Не удалось вытащить текст со страницы.")
                return
            proj.texts.extend(chunks)
            self.save_current()
            self.info(f"Со страницы добавлено кусков текста: {len(chunks)}.")

        self.run_async(job, done)

    def expand_with_model(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        ckpt = checkpoint_of(proj.name)
        if not ckpt.exists():
            self.info("Сначала обучите модель хоть немного — потом она сможет сама дописывать датасет.")
            return

        def job():
            model, tokenizer, device = self.load_model_for(proj)
            seeds = [u for u, _ in proj.dialogues[:: max(1, len(proj.dialogues) // 60 or 1)]][:60]
            if not seeds:
                seeds = ["привет", "расскажи что-нибудь", "как дела?"]
            new_pairs = []
            for q in seeds:
                a = chat_reply(model, tokenizer, [], q, device=device, temperature=0.9, top_k=50, max_new=60)
                if a and a != "…" and len(a) > 3:
                    new_pairs.append([q, a[0].lower() + a[1:] if len(a) > 1 else a])
            return new_pairs

        def done(pairs):
            proj.dialogues.extend(pairs)
            self.save_current()
            self.info(f"Модель сама дописала {len(pairs)} диалогов. Загляните в список и удалите чушь перед обучением!")

        self.run_async(job, done)

    def delete_dialogue(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        row = self.dialogue_list.currentRow()
        total = len(proj.dialogues)
        shown_from = max(0, total - 400)
        idx = shown_from + row
        if len(proj.dialogues) > 400 and row == 0:
            return
        if 0 <= idx < total:
            proj.dialogues.pop(idx)
            self.save_current()

    def clear_dataset(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        if QMessageBox.question(self, "GSYNK-AI", "Полностью очистить датасет этой модели?") == QMessageBox.StandardButton.Yes:
            proj.dialogues.clear()
            proj.texts.clear()
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
        device, _ = pick_device(self.settings.turbo)
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
        lay.addWidget(label("Обучение", "title"))
        self.train_info = label("модель не выбрана", "dim")
        lay.addWidget(self.train_info)
        self.train_hint = label("Как учить: 1) выберите число шагов, 2) «Учить!», 3) жмите «Стоп», когда loss перестанет заметно падать. "
                                "Если видеокарте не хватит памяти, пакет (батч) уменьшится сам — обучение не упадёт.", "dim")
        lay.addWidget(self.train_hint)

        ctl = QHBoxLayout()
        ctl.addWidget(label("Шагов в этом забеге:"))
        self.steps_spin = QSpinBox()
        self.steps_spin.setRange(10, 1_000_000)
        self.steps_spin.setValue(3000)
        ctl.addWidget(self.steps_spin)
        self.turbo_box = QCheckBox("ТУРБО: 100% мощности ПК")
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
        self.cpu_label = label("CPU: —")
        for i, w in enumerate([self.stat_step, self.stat_loss, self.stat_tok, self.stat_epoch, self.stat_device, self.stat_batch, self.cpu_label]):
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
        self.train_info.setText(
            f"{self.project.icon} {self.project.name} · шагов уже сделано: {self.project.steps_done} · "
            + ("контрольная точка есть — обучение продолжится с неё" if ckpt.exists() else "модель ещё не обучалась")
        )

    def start_training(self) -> None:
        proj = self.require_project()
        if proj is None or self.train_worker is not None:
            return
        if not proj.dialogues and not proj.texts:
            self.info("Датасет пуст. Сгенерируйте русский набор или добавьте свои диалоги.")
            self.goto(2)
            return
        self.settings.turbo = self.turbo_box.isChecked()
        self.settings.save()
        try:
            ckpt = checkpoint_of(proj.name)
            tokenizer = self.tokenizer_for(proj, rebuild=not ckpt.exists())
            stream = build_stream(proj, tokenizer)
            device, device_name = pick_device(self.settings.turbo)
            if ckpt.exists():
                model, _, _ = Trainer.load_checkpoint(ckpt, device)
                if model.cfg.vocab_size != tokenizer.vocab_size:
                    self.error("Словарь изменился и не совпадает с контрольной точкой. "
                               "Нажмите «Сбросить обучение» на этой странице, чтобы начать с чистого листа по новому датасету.")
                    return
            else:
                d, layers, heads, block, _, _ = SIZES[proj.size]
                cfg = GsaiConfig(vocab_size=tokenizer.vocab_size, block_size=block, n_layer=layers, n_head=heads, n_embd=d)
                model = GsaiGPT(cfg)
            if device.type == "cpu":
                batch = min(proj.batch, 32)
            else:
                batch = {"mini": 48, "lite": 32, "mid": 16, "max": 8}.get(proj.size, 16)
            self.trainer = Trainer(model, stream, device, device_name, lr=proj.lr, batch=batch)
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
        self.stat_loss.setText(f"loss: {st['loss']:.3f}")
        self.stat_tok.setText(f"скорость: {st['tok']:.0f} ток/с")
        self.stat_epoch.setText(f"эпох пройдено: {st['epoch']:.2f}")
        self.stat_device.setText(f"устройство: {st['device']}")
        self.stat_batch.setText(f"пакет (батч): {st.get('batch', '—')}")
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
        lay.addWidget(label("Проверка в чате", "title"))
        lay.addWidget(label("Поговорите с моделью. Не нравится — правьте датасет или учите ещё. Нравится — жмите «Выпуск».", "dim"))
        self.chat_view = QTextBrowser()
        lay.addWidget(self.chat_view, 1)
        row = QHBoxLayout()
        self.chat_input = QLineEdit()
        self.chat_input.setPlaceholderText("напишите что-нибудь модели…")
        self.chat_input.returnPressed.connect(self.send_chat)
        row.addWidget(self.chat_input, 1)
        send = QPushButton("Отправить")
        send.setObjectName("primary")
        send.clicked.connect(self.send_chat)
        row.addWidget(send)
        lay.addLayout(row)
        knobs = QHBoxLayout()
        knobs.addWidget(label("Креативность (температура):"))
        self.temp_slider = QSlider(Qt.Orientation.Horizontal)
        self.temp_slider.setRange(10, 150)
        self.temp_slider.setValue(80)
        knobs.addWidget(self.temp_slider)
        self.temp_value = label("0.80")
        self.temp_slider.valueChanged.connect(lambda v: self.temp_value.setText(f"{v / 100:.2f}"))
        knobs.addWidget(self.temp_value)
        more = QPushButton("🔥 Учить ещё")
        more.clicked.connect(lambda: self.goto(3))
        fix = QPushButton("🗂 Править датасет")
        fix.clicked.connect(lambda: self.goto(2))
        rel = QPushButton("🚀 Всё нравится — выпустить!")
        rel.setObjectName("primary")
        rel.clicked.connect(lambda: self.goto(5))
        knobs.addWidget(more)
        knobs.addWidget(fix)
        knobs.addWidget(rel)
        lay.addLayout(knobs)
        return page

    def chat_print(self, who: str, text: str, color: str) -> None:
        safe = text.replace("&", "&amp;").replace("<", "&lt;")
        self.chat_view.append(f'<p><b style="color:{color}">{who}:</b> {safe}</p>')

    def prepare_chat(self) -> None:
        proj = self.require_project()
        if proj is None:
            return
        if self.chat_model is not None:
            return
        self.chat_print("GSYNK-AI", "загружаю модель…", ACCENT)

        def done(bundle):
            self.chat_model, self.chat_tokenizer, self.chat_device = bundle
            self.chat_print("GSYNK-AI", f"модель {proj.icon} {proj.name} на связи. напишите ей что-нибудь!", ACCENT)

        self.run_async(lambda: self.load_model_for(proj), done)

    def send_chat(self) -> None:
        proj = self.require_project()
        text = self.chat_input.text().strip()
        if proj is None or not text:
            return
        if self.chat_model is None:
            self.prepare_chat()
            self.info("Модель ещё загружается, секунду…")
            return
        self.chat_input.clear()
        self.chat_print("Вы", text, GREEN)
        model, tokenizer, device = self.chat_model, self.chat_tokenizer, self.chat_device
        history = list(self.chat_history)
        temp = self.temp_slider.value() / 100.0

        def job():
            return chat_reply(model, tokenizer, history, text, device=device,
                              temperature=temp, top_k=proj.top_k, max_new=proj.max_new_tokens)

        def done(answer):
            self.chat_history.append((text, answer))
            self.chat_print(proj.name, answer, "#C4B5FD")

        self.run_async(job, done)

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
            self.release_info.setText(f"{self.project.icon} {self.project.name} · шагов: {self.project.steps_done}")
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
            top_k=project.top_k, max_new_tokens=project.max_new_tokens,
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
            self.rlog(f"собран {out.name}: {meta['file_size']} байт, параметров {meta['params']}")
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
            import psutil

            lines.append(f"оперативной памяти: {psutil.virtual_memory().total / 1073741824:.0f} ГБ")
        except Exception:
            pass
        try:
            device, name = pick_device(self.settings.turbo)
            lines.append(f"обучение идёт на: {name} ({device})")
            if device.type == "cpu":
                lines.append("подсказка: пакет torch-directml включит видеокарту AMD/Intel под Windows — "
                             "setup.bat ставит его автоматически, если получится.")
        except Exception:
            pass
        return "\n".join(lines)

    def save_settings(self) -> None:
        self.settings.repo = self.repo_edit.text().strip() or server.DEFAULT_REPO
        self.settings.token = self.token_edit.text().strip()
        self.settings.turbo = self.turbo_setting.isChecked()
        self.settings.check_updates = self.updates_setting.isChecked()
        self.turbo_box.setChecked(self.settings.turbo)
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

        self.run_async(lambda: updater.check_update(self.settings.repo), done)

    def apply_update(self) -> None:
        update = getattr(self, "pending_update", None)
        if not update:
            return
        self.update_btn.setEnabled(False)
        self.update_btn.setText("качаю обновление…")

        def job():
            updater.download_and_apply(update)
            return True

        def done(_):
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
