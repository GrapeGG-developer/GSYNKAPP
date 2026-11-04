"""
Знания GSYNK-AI: проект модели, карточки знаний, импорт из файлов и интернета,
а также сборка контекстных обучающих примеров из этих знаний.

В 1.1 датасет больше не является списком диалогов. Пользователь собирает
факты, определения, правила, примеры и тексты, а обучающий конвейер сам
превращает их в задания на запоминание, поиск по контексту и продолжение
разговора. Встроенного генератора фиктивных диалогов больше нет.
"""

from __future__ import annotations

import csv
import hashlib
import io
import json
import random
import re
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from dataclasses import asdict, dataclass, field
from html.parser import HTMLParser
from pathlib import Path
from typing import Any

from .tokenizer import BOS, BOT, END, EOS, USER, Tokenizer, split_tokens


def _tokens(text: str) -> list[str]:
    return split_tokens(text)


def _join_tokens(tokens: list[str]) -> str:
    out = ""
    for tok in tokens:
        if out and tok not in ".,!?;:…%)»" and not out.endswith(("«", "(")):
            out += " "
        out += tok
    return out

SIZES = {
    # имя: (d_model, слоёв, голов, контекст, макс. словарь, подпись)
    "mini": (192, 4, 4, 384, 5000, "Мини — быстрая, контекст 384 токена (~3 млн параметров)"),
    "lite": (256, 6, 4, 512, 10000, "Лёгкая — баланс скорости и ума, контекст 512 (~8 млн)"),
    "mid": (384, 8, 6, 768, 16000, "Средняя — контекст 768, заметно умнее (~22 млн)"),
    "max": (512, 10, 8, 1024, 24000, "Макс — контекст 1024, самая внимательная (~45 млн)"),
}

ICONS = ["🍇", "🤖", "🧠", "🦊", "🐱", "🐉", "🚀", "🎧", "📚", "⚡", "🌙", "🔥", "💎", "🦉", "🐼", "👾"]

KNOWLEDGE_KINDS = {
    "fact": "Факт",
    "definition": "Определение",
    "word": "Слово / термин",
    "rule": "Правило",
    "example": "Пример",
    "instruction": "Инструкция",
    "qa": "Вопрос — ответ",
    "text": "Текст",
}

_IGNORE = -100


def clean_text(text: str) -> str:
    """Мягкая нормализация пользовательского знания без потери смысла."""
    text = str(text or "").replace("\u00a0", " ")
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def _entry_id(title: str, body: str, kind: str) -> str:
    raw = f"{kind}\n{title.lower().strip()}\n{body.lower().strip()}"
    return hashlib.sha1(raw.encode("utf-8")).hexdigest()[:14]


@dataclass
class KnowledgeEntry:
    """Одна карточка базы знаний модели."""

    title: str = ""
    body: str = ""
    kind: str = "fact"
    tags: list[str] = field(default_factory=list)
    source: str = "manual"
    weight: int = 1
    id: str = ""

    def __post_init__(self) -> None:
        self.title = clean_text(self.title)
        self.body = clean_text(self.body)
        self.kind = self.kind if self.kind in KNOWLEDGE_KINDS else "fact"
        if isinstance(self.tags, str):
            self.tags = [t.strip() for t in re.split(r"[,;#]", self.tags) if t.strip()]
        self.tags = [clean_text(str(t)).lower() for t in (self.tags or []) if clean_text(str(t))]
        self.weight = max(1, min(8, int(self.weight or 1)))
        if not self.id:
            self.id = _entry_id(self.title, self.body, self.kind)

    @classmethod
    def from_any(cls, value: Any) -> "KnowledgeEntry":
        if isinstance(value, cls):
            return value
        if isinstance(value, dict):
            known = {f for f in cls.__dataclass_fields__}
            data = {k: v for k, v in value.items() if k in known}
            if "body" not in data:
                data["body"] = value.get("text") or value.get("content") or value.get("answer") or ""
            if "title" not in data:
                data["title"] = value.get("name") or value.get("question") or value.get("term") or ""
            return cls(**data)
        return cls(body=str(value))

    def display_title(self) -> str:
        if self.title:
            return self.title
        words = Tokenizer.tokenize(self.body)
        return " ".join(words[:5]) or "без названия"

    def full_text(self) -> str:
        parts = []
        if self.title:
            parts.append(self.title)
        if self.body:
            parts.append(self.body)
        if self.tags:
            parts.append("теги: " + ", ".join(self.tags))
        return ". ".join(parts)

    def key(self) -> str:
        return f"{self.title.lower().strip()}|{self.body.lower().strip()}"


def dedupe_knowledge(entries: list[KnowledgeEntry]) -> list[KnowledgeEntry]:
    """Убирает точные дубли, объединяя теги и сохраняя максимальный вес."""
    by_key: dict[str, KnowledgeEntry] = {}
    out: list[KnowledgeEntry] = []
    for raw in entries:
        entry = KnowledgeEntry.from_any(raw)
        if not entry.body and not entry.title:
            continue
        key = entry.key()
        if key in by_key:
            old = by_key[key]
            old.tags = sorted(set(old.tags) | set(entry.tags))
            old.weight = max(old.weight, entry.weight)
            continue
        by_key[key] = entry
        out.append(entry)
    return out


@dataclass
class Project:
    name: str = "Моя модель"
    icon: str = "🍇"
    description: str = ""
    size: str = "lite"
    # Старые поля оставлены только как вход миграции проектов GSYNK-AI 1.0.x.
    # После первого сохранения они пусты, а все данные живут в knowledge.
    dialogues: list[list[str]] = field(default_factory=list)
    texts: list[str] = field(default_factory=list)
    knowledge: list[KnowledgeEntry] = field(default_factory=list)
    persona: str = ""
    lr: float = 3e-4
    batch: int = 0  # 0 = автоматически под видеокарту/процессор
    temperature: float = 0.8
    top_k: int = 40
    top_p: float = 0.92
    repetition_penalty: float = 1.08
    max_new_tokens: int = 110
    use_memory: bool = True
    use_retrieval: bool = True
    think_candidates: int = 3
    steps_done: int = 0
    best_loss: float = 0.0
    model_version: str = "1.0.0"
    dataset_version: int = 2

    def __post_init__(self) -> None:
        self.knowledge = [KnowledgeEntry.from_any(item) for item in (self.knowledge or [])]
        if not self.knowledge and (self.dialogues or self.texts):
            migrated: list[KnowledgeEntry] = []
            for pair in self.dialogues or []:
                if len(pair) >= 2 and clean_text(str(pair[1])):
                    migrated.append(KnowledgeEntry(
                        title=clean_text(str(pair[0])), body=clean_text(str(pair[1])),
                        kind="qa", source="legacy-dialogue",
                    ))
            for text in self.texts or []:
                if clean_text(str(text)):
                    migrated.append(KnowledgeEntry(body=clean_text(str(text)), kind="text", source="legacy-text"))
            self.knowledge = migrated
            self.dialogues = []
            self.texts = []
        self.think_candidates = max(1, min(6, int(self.think_candidates or 3)))
        self.batch = max(0, int(self.batch or 0))

    def save(self, path: str | Path) -> None:
        Path(path).write_text(json.dumps(asdict(self), ensure_ascii=False, indent=1), encoding="utf-8")

    @classmethod
    def load(cls, path: str | Path) -> "Project":
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        known = {f for f in cls.__dataclass_fields__}
        return cls(**{k: v for k, v in data.items() if k in known})

    def effective_knowledge(self) -> list[KnowledgeEntry]:
        entries = [KnowledgeEntry.from_any(item) for item in self.knowledge]
        # Если проект был собран программно в старом стиле, не теряем данные.
        for pair in self.dialogues or []:
            if len(pair) >= 2:
                entries.append(KnowledgeEntry(title=str(pair[0]), body=str(pair[1]), kind="qa", source="legacy-dialogue"))
        for text in self.texts or []:
            entries.append(KnowledgeEntry(body=str(text), kind="text", source="legacy-text"))
        return [e for e in entries if e.body or e.title]

    def materialize_knowledge(self) -> None:
        self.knowledge = self.effective_knowledge()
        self.dialogues = []
        self.texts = []
        self.dataset_version = 2

    def set_knowledge(self, entries: list[KnowledgeEntry]) -> None:
        self.knowledge = [KnowledgeEntry.from_any(e) for e in entries if KnowledgeEntry.from_any(e).body]
        self.dialogues = []
        self.texts = []
        self.dataset_version = 2

    def add_knowledge(self, entries: list[KnowledgeEntry], dedupe: bool = True) -> int:
        before = len(self.effective_knowledge())
        combined = self.effective_knowledge() + [KnowledgeEntry.from_any(e) for e in entries]
        self.set_knowledge(dedupe_knowledge(combined) if dedupe else combined)
        return max(0, len(self.knowledge) - before)

    def identity_entries(self) -> list[KnowledgeEntry]:
        name = clean_text(self.name) or "модель"
        entries = [KnowledgeEntry(
            title="кто я",
            body=f"меня зовут {name}. я языковая модель, созданная в GSYNK-AI и обученная на пользовательской базе знаний.",
            kind="fact", tags=["личность", "имя"], source="identity", weight=3,
        )]
        if self.description:
            entries.append(KnowledgeEntry(
                title="что я умею", body=clean_text(self.description),
                kind="fact", tags=["личность", "возможности"], source="identity", weight=2,
            ))
        if self.persona:
            entries.append(KnowledgeEntry(
                title="характер и стиль", body=clean_text(self.persona),
                kind="instruction", tags=["личность", "стиль"], source="identity", weight=2,
            ))
        return entries

    def all_texts(self) -> list[str]:
        out = [entry.full_text() for entry in self.identity_entries() + self.effective_knowledge()]
        out.extend(context_scaffold_texts())
        return [t for t in out if t]

    def stats(self) -> dict:
        entries = self.effective_knowledge()
        words: list[str] = []
        tags: set[str] = set()
        kinds: Counter[str] = Counter()
        chars = 0
        for entry in entries:
            text = entry.full_text()
            chars += len(text)
            words.extend(Tokenizer.tokenize(text))
            tags.update(entry.tags)
            kinds[entry.kind] += 1
        unique = set(words)
        duplicate_keys = len(entries) - len({e.key() for e in entries})
        return {
            "entries": len(entries),
            "knowledge": len(entries),
            "chars": chars,
            "words": len(words),
            "unique_words": len(unique),
            "tags": len(tags),
            "duplicates": duplicate_keys,
            "kinds": dict(kinds),
            "avg_words": (len(words) / len(entries)) if entries else 0.0,
            "estimated_tasks": sum(max(3, e.weight * 3) for e in entries),
            # Совместимые ключи для старого кода и тестов.
            "dialogues": 0,
            "texts": len(entries),
        }


def seed_identity_knowledge(project: Project) -> None:
    """Для новой модели сразу создаёт видимые карточки личности в базе знаний."""
    existing_ids = {e.key() for e in project.effective_knowledge()}
    add = [e for e in project.identity_entries() if e.key() not in existing_ids]
    if add:
        project.set_knowledge(project.effective_knowledge() + add)


def context_scaffold_texts() -> list[str]:
    """Слова служебных шаблонов контекста: они обязаны попадать в словарь."""
    return [
        "контекст знания память диалога пользователь сказал модель ответила важно помнить",
        "расскажи подробнее объясни что такое что сказано про это он она оно они этот эта",
        "а подробнее а почему а что дальше продолжи повтори своими словами сравни главное",
        "меня зовут как меня зовут где я живу что я люблю запомни пожалуйста не забывай",
        "используй знания из контекста отвечай по фактам если не знаешь честно скажи",
    ]


# ---------------------------------------------------------------- импорт знаний

_QA_PREFIXES = ("пользователь:", "user:", "вопрос:", "в:")
_ANSWER_PREFIXES = ("модель:", "бот:", "ответ:", "о:", "ии:")


def _split_tags(text: str) -> list[str]:
    return [t.strip().lower() for t in re.split(r"[,;#]", text) if t.strip()]


def _definition_parts(line: str) -> tuple[str, str, str] | None:
    for sep, kind in (("=>", "qa"), ("=", "definition"), (" — ", "definition"), (" – ", "definition")):
        if sep in line:
            left, right = line.split(sep, 1)
            left, right = left.strip(" -•\t"), right.strip()
            if left and right and len(left.split()) <= 9:
                return left, right, kind
    if ":" in line and not line.lower().startswith(("http:", "https:")):
        left, right = line.split(":", 1)
        left, right = left.strip(" -•\t"), right.strip()
        if left and right and len(left.split()) <= 7 and len(right) >= 3:
            return left, right, "definition"
    return None


def parse_knowledge_plain(text: str, source: str = "paste") -> list[KnowledgeEntry]:
    """
    Разбирает обычный текст в карточки знаний.

    Поддерживает `# заголовок`, строки `теги: ...`, определения
    `термин = значение`, абзацы и старый импорт `вопрос => ответ`
    (он становится карточкой типа qa, а не отдельным диалоговым датасетом).
    """
    entries: list[KnowledgeEntry] = []
    current_title = ""
    current_tags: list[str] = []
    paragraph: list[str] = []
    pending_question = ""

    def flush_paragraph() -> None:
        nonlocal paragraph
        if paragraph:
            body = clean_text(" ".join(paragraph))
            if body:
                entries.append(KnowledgeEntry(
                    title=current_title, body=body, kind="text" if len(body) > 260 else "fact",
                    tags=list(current_tags), source=source,
                ))
        paragraph = []

    for raw in str(text or "").splitlines():
        line = raw.strip()
        if not line:
            flush_paragraph()
            continue
        if line.startswith("#"):
            flush_paragraph()
            current_title = clean_text(line.lstrip("#"))
            continue
        low = line.lower()
        if low.startswith(("теги:", "tags:")):
            current_tags = _split_tags(line.split(":", 1)[1])
            continue
        if low.startswith(_QA_PREFIXES):
            flush_paragraph()
            pending_question = line.split(":", 1)[1].strip()
            continue
        if low.startswith(_ANSWER_PREFIXES) and pending_question:
            entries.append(KnowledgeEntry(
                title=pending_question, body=line.split(":", 1)[1].strip(),
                kind="qa", tags=list(current_tags), source=source,
            ))
            pending_question = ""
            continue
        parts = _definition_parts(line)
        if parts:
            flush_paragraph()
            title, body, kind = parts
            entries.append(KnowledgeEntry(title=title, body=body, kind=kind, tags=list(current_tags), source=source))
            continue
        paragraph.append(line.lstrip("-•* ").strip())
    flush_paragraph()
    return dedupe_knowledge(entries)


def _entries_from_objects(items: list[Any], source: str) -> list[KnowledgeEntry]:
    out: list[KnowledgeEntry] = []
    for item in items:
        if isinstance(item, str):
            out.extend(parse_knowledge_plain(item, source=source))
        elif isinstance(item, dict):
            title = item.get("title") or item.get("name") or item.get("term") or item.get("question") or ""
            body = item.get("body") or item.get("text") or item.get("content") or item.get("answer") or item.get("value") or ""
            if body:
                out.append(KnowledgeEntry(
                    title=str(title), body=str(body), kind=str(item.get("kind") or "fact"),
                    tags=item.get("tags") or [], source=source,
                    weight=int(item.get("weight") or 1),
                ))
    return dedupe_knowledge(out)


def parse_knowledge_document(text: str, suffix: str = ".txt", source: str = "file") -> list[KnowledgeEntry]:
    suffix = suffix.lower()
    if suffix == ".json":
        data = json.loads(text)
        if isinstance(data, dict):
            if isinstance(data.get("knowledge"), list):
                return _entries_from_objects(data["knowledge"], source)
            if isinstance(data.get("entries"), list):
                return _entries_from_objects(data["entries"], source)
            return _entries_from_objects([data], source)
        if isinstance(data, list):
            return _entries_from_objects(data, source)
        return []
    if suffix == ".csv":
        rows = list(csv.reader(io.StringIO(text)))
        if not rows:
            return []
        header = [h.strip().lower() for h in rows[0]]
        out: list[KnowledgeEntry] = []
        has_header = any(h in ("title", "body", "text", "заголовок", "текст", "термин") for h in header)
        start = 1 if has_header else 0
        for row in rows[start:]:
            if not row or not any(c.strip() for c in row):
                continue
            if has_header:
                item = {header[i]: row[i] for i in range(min(len(header), len(row)))}
                title = item.get("title") or item.get("заголовок") or item.get("термин") or ""
                body = item.get("body") or item.get("text") or item.get("текст") or ""
                tags = _split_tags(item.get("tags") or item.get("теги") or "")
            else:
                title = row[0] if len(row) > 1 else ""
                body = row[1] if len(row) > 1 else row[0]
                tags = _split_tags(row[2]) if len(row) > 2 else []
            if body.strip():
                out.append(KnowledgeEntry(title=title.strip(), body=body.strip(), tags=tags, source=source))
        return dedupe_knowledge(out)
    return parse_knowledge_plain(text, source=source)


def entries_from_paragraphs(paragraphs: list[str], title: str = "", source: str = "web") -> list[KnowledgeEntry]:
    out: list[KnowledgeEntry] = []
    for i, para in enumerate(paragraphs):
        body = clean_text(para)
        if len(body) < 20:
            continue
        entry_title = title if i == 0 else (f"{title} · часть {i + 1}" if title else "")
        out.append(KnowledgeEntry(title=entry_title, body=body, kind="text", source=source))
    return dedupe_knowledge(out)


# ---------------------------------------------------------------- обучающие примеры

@dataclass
class TrainingData:
    train_ids: list[int]
    train_labels: list[int]
    val_ids: list[int] = field(default_factory=list)
    val_labels: list[int] = field(default_factory=list)
    examples: int = 0
    supervised_tokens: int = 0
    context_examples: int = 0


_STOP_WORDS = {
    "это", "как", "что", "для", "при", "или", "над", "под", "про", "без", "его", "её", "их", "был", "была",
    "быть", "есть", "один", "одна", "так", "уже", "все", "всё", "тоже", "также", "между", "через", "после",
}


def keyword_subject(entry: KnowledgeEntry) -> str:
    if entry.title:
        return entry.title
    words = [w for w in _tokens(entry.body) if w.isalnum() and w not in _STOP_WORDS]
    return " ".join(words[:4]) or "это"


def _sentences(text: str) -> list[str]:
    parts = re.split(r"(?<=[.!?…])\s+", clean_text(text))
    return [p.strip() for p in parts if p.strip()]


def _clip_words(text: str, words: int) -> str:
    tokens = Tokenizer.tokenize(text)
    return Tokenizer.detokenize(tokens[:words]) if tokens else text


class _ExampleBuilder:
    def __init__(self, tokenizer: Tokenizer, block_size: int):
        self.tokenizer = tokenizer
        self.block_size = max(32, int(block_size))
        self.examples: list[tuple[list[int], list[bool], bool]] = []

    def _enc(self, text: str) -> list[int]:
        return self.tokenizer.encode(text)

    def add(self, segments: list[tuple[Any, bool]], context: bool = False) -> None:
        ids: list[int] = []
        mask: list[bool] = []
        for value, train in segments:
            if isinstance(value, int):
                part = [int(value)]
            elif isinstance(value, (list, tuple)):
                part = [int(v) for v in value]
            else:
                part = self._enc(str(value))
            room = self.block_size - len(ids)
            if room <= 0:
                break
            part = part[:room]
            ids.extend(part)
            mask.extend([bool(train)] * len(part))
        if len(ids) >= 4 and any(mask):
            self.examples.append((ids, mask, context))

    def add_task(self, prefix: list[tuple[Any, bool]], answer: str, context: bool = False) -> None:
        ids: list[int] = []
        mask: list[bool] = []
        for value, train in prefix:
            if isinstance(value, int):
                part = [int(value)]
            elif isinstance(value, (list, tuple)):
                part = [int(v) for v in value]
            else:
                part = self._enc(str(value))
            ids.extend(part)
            mask.extend([bool(train)] * len(part))
        room = self.block_size - len(ids) - 1
        if room < 3:
            return
        answer_ids = self._enc(answer)[:room]
        ids.extend(answer_ids)
        mask.extend([True] * len(answer_ids))
        ids.append(END)
        mask.append(True)
        self.examples.append((ids, mask, context))


def knowledge_examples(
    project: Project,
    tokenizer: Tokenizer,
    block_size: int,
    seed: int = 67,
) -> list[tuple[list[int], list[bool], bool]]:
    """Собирает из знаний примеры: факты, определения, выбор по контексту и follow-up."""
    entries = dedupe_knowledge(project.identity_entries() + project.effective_knowledge())
    builder = _ExampleBuilder(tokenizer, block_size)
    rnd = random.Random(seed + len(entries) * 131 + sum(len(e.body) for e in entries))

    question_templates = [
        "что такое {s}?", "расскажи про {s}", "что ты знаешь про {s}?",
        "объясни: {s}", "дай главное про {s}", "{s} — что важно помнить?",
    ]
    context_questions = [
        "используй знания из контекста. что сказано про {s}?",
        "посмотри в контекст и ответь про {s}",
        "что важно помнить про {s}?",
        "по контексту расскажи про {s}",
    ]
    followups = ["а подробнее?", "и что это значит?", "а главное?", "расскажи ещё про это", "почему это важно?"]

    for entry in entries:
        subject = keyword_subject(entry)
        body = entry.body or entry.title
        reps = max(1, entry.weight)
        for rep in range(reps):
            # 1) Чистое знание как текст: модель учит формулировки и связи слов.
            # Длинные тексты режутся на окна, иначе хвост карточки потерялся бы.
            full_ids = tokenizer.encode(entry.full_text())
            chunk_size = max(32, block_size - 2)
            for start in range(0, max(1, len(full_ids)), chunk_size):
                builder.add([(BOS, False), (full_ids[start:start + chunk_size], True), (EOS, True)])
            # 2) Вопрос из заголовка/ключевых слов: пользовательский датасет остаётся
            # знаниями, а диалоговая форма появляется только внутри обучающих заданий.
            q = question_templates[(rep + len(subject)) % len(question_templates)].format(s=subject)
            builder.add_task([(USER, False), (q, False), (BOT, False)], body)
            # 3) Ответ строго по поданному контексту: основа retrieval-поведения.
            cq = context_questions[(rep + len(body)) % len(context_questions)].format(s=subject)
            builder.add_task([
                (BOS, False), (f"знания: {entry.full_text()}", False), (EOS, False),
                (USER, False), (cq, False), (BOT, False),
            ], body, context=True)
            # 4) Продолжение начала факта.
            words = Tokenizer.tokenize(body)
            if len(words) >= 8:
                cut = max(3, len(words) // 3)
                builder.add_task(
                    [(USER, False), (f"продолжи: {Tokenizer.detokenize(words[:cut])}", False), (BOT, False)],
                    Tokenizer.detokenize(words[cut:]),
                )
            # 5) Follow-up: второй вопрос не повторяет тему и заставляет смотреть назад.
            first = _sentences(body)[0] if _sentences(body) else _clip_words(body, 18)
            builder.add_task([
                (USER, False), (f"расскажи про {subject}", False), (BOT, False),
                (_clip_words(first, 24), False), (END, False),
                (USER, False), (followups[rep % len(followups)], False), (BOT, False),
            ], body, context=True)

    # 6) Выбор нужного знания среди соседних: учит не хватать первое попавшееся.
    groups: dict[str, list[KnowledgeEntry]] = defaultdict(list)
    for entry in entries:
        groups[(entry.tags[0] if entry.tags else entry.kind)].append(entry)
    for group in groups.values():
        if len(group) < 2:
            continue
        for entry in group:
            distractors = [e for e in group if e.id != entry.id]
            rnd.shuffle(distractors)
            pack = [entry] + distractors[:2]
            rnd.shuffle(pack)
            context = "знания: " + " ".join(f"{e.display_title()}. {_clip_words(e.body, 42)}" for e in pack)
            builder.add_task([
                (BOS, False), (context, False), (EOS, False),
                (USER, False), (f"из контекста выбери главное про {keyword_subject(entry)}", False), (BOT, False),
            ], entry.body, context=True)

    rnd.shuffle(builder.examples)
    return builder.examples


def _concat_examples(examples: list[tuple[list[int], list[bool], bool]]) -> tuple[list[int], list[int], int]:
    ids: list[int] = []
    labels: list[int] = []
    supervised = 0
    for ex_ids, mask, _context in examples:
        ids.extend(ex_ids)
        labels.extend(token if train else _IGNORE for token, train in zip(ex_ids, mask))
        supervised += sum(1 for train in mask if train)
        ids.append(EOS)
        labels.append(_IGNORE)
    return ids, labels, supervised


def build_training_data(
    project: Project,
    tokenizer: Tokenizer,
    block_size: int | None = None,
    validation_fraction: float = 0.06,
    seed: int = 67,
) -> TrainingData:
    if block_size is None:
        block_size = SIZES.get(project.size, SIZES["lite"])[3]
    examples = knowledge_examples(project, tokenizer, int(block_size), seed=seed)
    if not examples:
        return TrainingData([], [])
    validation_fraction = max(0.0, min(0.3, float(validation_fraction)))
    val_every = 0
    if validation_fraction > 0 and len(examples) >= 24:
        val_every = max(3, round(1.0 / validation_fraction))
    train_examples: list[tuple[list[int], list[bool], bool]] = []
    val_examples: list[tuple[list[int], list[bool], bool]] = []
    for i, example in enumerate(examples):
        if val_every and (i % val_every == val_every - 1):
            val_examples.append(example)
        else:
            train_examples.append(example)
    train_ids, train_labels, supervised = _concat_examples(train_examples)
    val_ids, val_labels, _ = _concat_examples(val_examples) if val_examples else ([], [], 0)
    return TrainingData(
        train_ids=train_ids,
        train_labels=train_labels,
        val_ids=val_ids,
        val_labels=val_labels,
        examples=len(examples),
        supervised_tokens=supervised,
        context_examples=sum(1 for _ids, _mask, ctx in examples if ctx),
    )


def build_stream(project: Project, tokenizer: Tokenizer, block_size: int | None = None) -> list[int]:
    """Совместимая обёртка: полный обучающий поток без валидации."""
    return build_training_data(project, tokenizer, block_size=block_size, validation_fraction=0.0).train_ids


# ---------------------------------------------------------------- интернет

class _TextStripper(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.parts: list[str] = []
        self._skip = 0

    def handle_starttag(self, tag: str, attrs) -> None:
        if tag in ("script", "style", "noscript"):
            self._skip += 1
        if tag in ("p", "br", "h1", "h2", "h3", "li"):
            self.parts.append("\n")

    def handle_endtag(self, tag: str) -> None:
        if tag in ("script", "style", "noscript") and self._skip:
            self._skip -= 1

    def handle_data(self, data: str) -> None:
        if not self._skip:
            self.parts.append(data)


def _http_get(url: str, timeout: int = 30) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "GSYNK-AI/1.1 (knowledge fetcher)"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


def fetch_url_text(url: str, max_chars: int = 200_000) -> str:
    """Скачивает страницу и вытаскивает читаемый текст абзацами."""
    raw = _http_get(url).decode("utf-8", "ignore")
    stripper = _TextStripper()
    stripper.feed(raw)
    text = "".join(stripper.parts)
    lines = [re.sub(r"\s+", " ", ln).strip() for ln in text.splitlines()]
    paras = [ln for ln in lines if len(ln) > 60]
    return "\n\n".join(paras)[:max_chars]


def fetch_wikipedia_ru(title: str, max_chars: int = 120_000) -> str:
    """Текст статьи русской Википедии по названию (официальный API)."""
    api = "https://ru.wikipedia.org/w/api.php?" + urllib.parse.urlencode({
        "action": "query", "prop": "extracts", "explaintext": "1",
        "titles": title, "format": "json", "redirects": "1",
    })
    data = json.loads(_http_get(api).decode("utf-8", "ignore"))
    pages = (data.get("query") or {}).get("pages") or {}
    for page in pages.values():
        extract = (page.get("extract") or "").strip()
        if extract:
            paras = [p.strip() for p in extract.split("\n") if len(p.strip()) > 60]
            return "\n\n".join(paras)[:max_chars]
    return ""


def split_paragraphs(text: str, max_len: int = 900) -> list[str]:
    out: list[str] = []
    for para in re.split(r"\n\s*\n", text):
        para = " ".join(para.split())
        while len(para) > max_len:
            cut = para.rfind(". ", 0, max_len)
            if cut < 200:
                cut = max_len
            out.append(para[:cut + 1].strip())
            para = para[cut + 1:].strip()
        if len(para) > 40:
            out.append(para)
    return out
