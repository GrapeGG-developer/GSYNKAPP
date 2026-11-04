"""
Контекстный движок GSYNK-AI 1.1.

Сама нейросеть остаётся совместимым с телефоном трансформером, а этот слой
делает то, чего маленькая модель одна делать не умеет:
  * выделяет факты о пользователе в долговременную память;
  * ищет релевантные карточки знаний TF-IDF-поиском;
  * собирает промпт из памяти, знаний и целых последних реплик, а не режет
    историю вслепую по хвосту;
  * генерирует несколько кандидатов, оценивает повторы, релевантность и
    противоречия и выбирает лучший ответ.
"""

from __future__ import annotations

import math
import random
import re
from collections import Counter
from dataclasses import asdict, dataclass, field
from typing import Iterable

import torch

from .dataset import KnowledgeEntry
from .model import GsaiGPT
from .tokenizer import BOS, BOT, END, EOS, USER, Tokenizer, split_tokens

_STOP = {
    "это", "как", "что", "где", "когда", "почему", "зачем", "какой", "какая", "какие", "для", "при", "или",
    "над", "под", "про", "без", "его", "её", "их", "был", "была", "быть", "есть", "один", "одна", "так",
    "уже", "все", "всё", "тоже", "также", "между", "через", "после", "меня", "тебя", "мне", "тебе", "нам",
    "расскажи", "скажи", "объясни", "подробнее", "пожалуйста", "знаешь", "думаешь",
}
_SUFFIXES = (
    "иями", "ями", "ами", "ого", "его", "ому", "ему", "ыми", "ими", "ать", "ять", "еть", "ить", "ает",
    "яет", "ует", "ают", "яют", "ость", "ости", "ание", "ение", "ов", "ев", "ам", "ям", "ах", "ях",
    "ой", "ый", "ий", "ая", "яя", "ое", "ее", "ые", "ие", "ом", "ем", "а", "я", "у", "ю", "е", "о", "ы", "и",
)


def stem(word: str) -> str:
    w = word.lower().strip()
    if len(w) <= 4:
        return w
    for suffix in _SUFFIXES:
        if len(w) - len(suffix) >= 4 and w.endswith(suffix):
            return w[: -len(suffix)]
    return w


def content_tokens(text: str) -> list[str]:
    out: list[str] = []
    for tok in split_tokens(text):
        # Цифры и операторы критичны для арифметики: без них запрос «2+2»
        # превращался в пустой поиск и модель не получала точную карточку.
        if tok in {"+", "-", "*", "/", "=", "%"}:
            out.append(tok)
        elif tok.isalnum() and tok not in _STOP and (len(tok) > 1 or tok.isdigit() or tok in {"x", "y"}):
            out.append(stem(tok))
    return out


def _clean(text: str) -> str:
    return re.sub(r"\s+", " ", str(text or "")).strip()


@dataclass
class MemoryFact:
    key: str
    label: str
    value: str
    turn: int = 0


class MemoryStore:
    """Небольшая, но честная долговременная память о пользователе."""

    _PATTERNS: list[tuple[str, str, re.Pattern[str]]] = [
        ("name", "имя пользователя", re.compile(r"(?:меня зовут|мо[её] имя|я\s*[-—])\s+([A-Za-zА-Яа-яЁё][A-Za-zА-Яа-яЁё-]{1,30})", re.I)),
        ("location", "место жительства", re.compile(r"(?:я живу|живу|я из|мой город)\s+(?:в\s+|во\s+)?([A-Za-zА-Яа-яЁё][A-Za-zА-Яа-яЁё -]{1,40}?)(?:[.!?,;]|$)", re.I)),
        ("age", "возраст", re.compile(r"(?:мне|мне сейчас)\s+(\d{1,3})\s*(?:года|лет|год)?", re.I)),
        ("occupation", "занятие", re.compile(r"(?:я работаю|я учусь|работаю|учусь|я занимаюсь)\s+([^.!?,;]{2,70})", re.I)),
        ("dislikes", "не любит", re.compile(r"(?:я не люблю|мне не нравится|ненавижу)\s+([^.!?,;]{2,70})", re.I)),
        ("likes", "любит", re.compile(r"(?:я люблю|я обожаю|мне нравится|я фанат)\s+([^.!?,;]{2,70})", re.I)),
    ]

    def __init__(self, facts: Iterable[MemoryFact] | None = None):
        self.facts: dict[str, MemoryFact] = {}
        self.custom: list[str] = []
        for fact in facts or []:
            self.facts[fact.key] = fact

    def observe(self, user_text: str, turn: int = 0) -> list[MemoryFact]:
        found: list[MemoryFact] = []
        text = _clean(user_text)
        explicit = re.search(r"запомни(?:те)?\s*[:,]?\s+(.+)", text, re.I)
        if explicit:
            value = _clean(explicit.group(1)).strip(" .")
            if value and value not in self.custom:
                self.custom.append(value)
                self.custom = self.custom[-12:]
        for key, label, pattern in self._PATTERNS:
            match = pattern.search(text)
            if not match:
                continue
            value = _clean(match.group(1)).strip(" .")
            value = re.sub(r"\s+(и|а|но)\s*$", "", value, flags=re.I)
            if key == "age" and value.isdigit():
                value = f"{value} лет"
            if value:
                fact = MemoryFact(key, label, value, turn)
                self.facts[key] = fact
                found.append(fact)
        return found

    def summary(self, limit: int = 10) -> str:
        parts = [f"{f.label} — {f.value}" for f in list(self.facts.values())[:limit]]
        parts.extend(self.custom[: max(0, limit - len(parts))])
        return "; ".join(parts)

    def direct_answer(self, user_text: str) -> str | None:
        q = _clean(user_text).lower()
        def get(key: str) -> str:
            fact = self.facts.get(key)
            return fact.value if fact else ""

        if any(p in q for p in ("что ты обо мне помнишь", "что ты помнишь обо мне", "что ты знаешь обо мне", "расскажи обо мне")):
            summary = self.summary()
            return f"Вот что я помню из нашего диалога: {summary}." if summary else None
        if ("как меня зовут" in q or "моё имя" in q or "мое имя" in q) and get("name"):
            return f"Тебя зовут {get('name')}."
        if ("где я живу" in q or "мой город" in q) and get("location"):
            return f"Ты живёшь: {get('location')}."
        if ("сколько мне лет" in q or "мой возраст" in q) and get("age"):
            return f"Тебе {get('age')}."
        if ("что я люблю" in q or "что мне нравится" in q) and get("likes"):
            return f"Ты говорил, что тебе нравится {get('likes')}."
        if ("что я не люблю" in q or "что мне не нравится" in q) and get("dislikes"):
            return f"Ты говорил, что тебе не нравится {get('dislikes')}."
        if ("чем я занимаюсь" in q or "где я работаю" in q or "где я учусь" in q) and get("occupation"):
            return f"Ты говорил о занятии: {get('occupation')}."
        return None

    def to_dict(self) -> dict:
        return {"facts": [asdict(f) for f in self.facts.values()], "custom": self.custom}

    @classmethod
    def from_dict(cls, data: dict | None) -> "MemoryStore":
        store = cls()
        if not data:
            return store
        for item in data.get("facts", []):
            try:
                fact = MemoryFact(**item)
                store.facts[fact.key] = fact
            except Exception:
                continue
        store.custom = [str(x) for x in data.get("custom", [])][:12]
        return store


@dataclass
class RetrievedKnowledge:
    entry: KnowledgeEntry
    score: float
    matched: list[str] = field(default_factory=list)


class KnowledgeIndex:
    """TF-IDF поиск по карточкам без внешних embedding-моделей."""

    def __init__(self, entries: list[KnowledgeEntry]):
        self.entries = list(entries)
        self._doc_tokens: list[Counter[str]] = []
        df: Counter[str] = Counter()
        for entry in self.entries:
            counts: Counter[str] = Counter()
            counts.update(content_tokens(entry.title) * 3)
            counts.update(t for tag in entry.tags for t in content_tokens(tag))
            counts.update(content_tokens(entry.tags and " ".join(entry.tags) or "") )
            counts.update(content_tokens(entry.body))
            self._doc_tokens.append(counts)
            df.update(counts.keys())
        total = max(1, len(self.entries))
        self._idf = {tok: math.log((total + 1) / (count + 1)) + 1.0 for tok, count in df.items()}

    def idf(self, token: str) -> float:
        return self._idf.get(token, 2.0)

    def search(self, query: str, limit: int = 4, min_score: float = 0.15) -> list[RetrievedKnowledge]:
        q_tokens = list(dict.fromkeys(content_tokens(query)))
        if not q_tokens or not self.entries:
            return []
        q_low = query.lower()
        scored: list[RetrievedKnowledge] = []
        for entry, counts in zip(self.entries, self._doc_tokens):
            matched: list[str] = []
            score = 0.0
            for tok in q_tokens:
                if tok in counts:
                    matched.append(tok)
                    score += (1.0 + math.log1p(counts[tok])) * self.idf(tok)
            if entry.title and entry.title.lower() in q_low:
                score += 8.0
                matched.append(entry.title)
            if score:
                score /= math.sqrt(max(6, sum(counts.values())))
                if score >= min_score:
                    scored.append(RetrievedKnowledge(entry, round(score, 4), matched))
        scored.sort(key=lambda item: item.score, reverse=True)
        return scored[: max(0, int(limit))]


@dataclass
class ContextPlan:
    prompt_ids: list[int]
    prefix_text: str
    turns_used: int
    prompt_tokens: int
    reserved_tokens: int
    memory_text: str
    retrieved: list[RetrievedKnowledge] = field(default_factory=list)


@dataclass
class ContextSession:
    history: list[tuple[str, str]] = field(default_factory=list)
    memory: MemoryStore = field(default_factory=MemoryStore)

    def topics(self, limit: int = 7) -> list[str]:
        counts: Counter[str] = Counter()
        for user, _bot in self.history:
            counts.update(t for t in content_tokens(user) if len(t) >= 4)
        return [tok for tok, _count in counts.most_common(limit)]

    def add_turn(self, user: str, bot: str) -> None:
        self.memory.observe(user, turn=len(self.history) + 1)
        self.history.append((user, bot))
        self.history = self.history[-80:]

    def to_dict(self) -> dict:
        return {"history": self.history, "memory": self.memory.to_dict()}

    @classmethod
    def from_dict(cls, data: dict | None) -> "ContextSession":
        if not data:
            return cls()
        return cls(
            history=[(str(a), str(b)) for a, b in data.get("history", []) if isinstance(a, str) and isinstance(b, str)],
            memory=MemoryStore.from_dict(data.get("memory")),
        )


def _encode_turn(tokenizer: Tokenizer, user: str, bot: str) -> list[int]:
    return [USER] + tokenizer.encode(user) + [BOT] + tokenizer.encode(bot) + [END]


def build_context_prompt(
    tokenizer: Tokenizer,
    history: list[tuple[str, str]],
    user_text: str,
    block_size: int,
    reserve_tokens: int = 96,
    identity_text: str = "",
    memory_text: str = "",
    retrieved: list[RetrievedKnowledge] | None = None,
    topics: list[str] | None = None,
) -> ContextPlan:
    retrieved = retrieved or []
    budget = max(32, int(block_size) - max(8, int(reserve_tokens)))
    current_ids = tokenizer.encode(user_text)
    current_ids = current_ids[-max(12, budget // 3):]
    current = [USER] + current_ids + [BOT]

    prefix_parts: list[str] = []
    if identity_text:
        prefix_parts.append(f"модель: {_clean(identity_text)}")
    if memory_text:
        prefix_parts.append(f"память диалога: {_clean(memory_text)}")
    if topics:
        prefix_parts.append("раньше обсуждали: " + ", ".join(topics[:7]))
    if retrieved:
        knowledge = " ".join(
            f"{r.entry.display_title()}. {_clean(r.entry.body)[:520]}" for r in retrieved
        )
        prefix_parts.append("знания: " + _clean(knowledge))
    prefix_text = " ".join(prefix_parts)
    prefix_ids: list[int] = []
    if prefix_text:
        raw = [BOS] + tokenizer.encode(prefix_text) + [EOS]
        prefix_ids = raw[: max(0, int(budget * 0.42))]
        if prefix_ids and prefix_ids[-1] != EOS:
            prefix_ids[-1] = EOS

    history_budget = max(0, budget - len(prefix_ids) - len(current))
    chosen: list[list[int]] = []
    used = 0
    for user, bot in reversed(history[-16:]):
        turn = _encode_turn(tokenizer, user, bot)
        if used + len(turn) > history_budget:
            # Один слишком большой последний ход полезнее пустой истории:
            # сохраняем его хвост, но не ломаем начало USER.
            if not chosen and history_budget >= 24:
                turn = turn[:1] + turn[-(history_budget - 1):]
                chosen.append(turn)
            break
        chosen.append(turn)
        used += len(turn)
    chosen.reverse()

    ids = prefix_ids + [tok for turn in chosen for tok in turn] + current
    ids = ids[-budget:]
    return ContextPlan(
        prompt_ids=ids,
        prefix_text=prefix_text,
        turns_used=len(chosen),
        prompt_tokens=len(ids),
        reserved_tokens=int(reserve_tokens),
        memory_text=memory_text,
        retrieved=retrieved,
    )


@dataclass
class CandidateResult:
    text: str
    score: float
    reasons: list[str] = field(default_factory=list)
    source: str = "model"


def _repeat_ratio(tokens: list[str]) -> float:
    if len(tokens) < 4:
        return 0.0
    grams = [tuple(tokens[i:i + 3]) for i in range(len(tokens) - 2)]
    return 1.0 - (len(set(grams)) / max(1, len(grams)))


def score_candidate(
    answer: str,
    user_text: str,
    retrieved: list[RetrievedKnowledge],
    memory: MemoryStore,
    history: list[tuple[str, str]],
) -> CandidateResult:
    text = _clean(answer)
    if not text or text == "…":
        return CandidateResult(text, -10.0, ["пустой ответ"])
    answer_tokens = content_tokens(text)
    if not answer_tokens:
        return CandidateResult(text, -5.0, ["нет содержательных слов"])
    answer_set = set(answer_tokens)
    score = 0.0
    reasons: list[str] = []

    query_set = set(content_tokens(user_text))
    # Follow-up без темы проверяем по двум последним репликам пользователя.
    for prev_user, _prev_bot in history[-2:]:
        query_set.update(content_tokens(prev_user))
    if query_set:
        overlap = len(answer_set & query_set) / max(1, len(query_set))
        score += overlap * 1.6
        if overlap:
            reasons.append(f"связь с вопросом {overlap:.0%}")

    if retrieved:
        knowledge_set = set()
        for item in retrieved:
            knowledge_set.update(content_tokens(item.entry.full_text()))
        if knowledge_set:
            overlap = len(answer_set & knowledge_set) / max(1, min(len(answer_set), len(knowledge_set)))
            score += overlap * 3.2
            reasons.append(f"опора на знания {overlap:.0%}")

    summary = memory.summary()
    if summary:
        memory_set = set(content_tokens(summary))
        overlap = len(answer_set & memory_set)
        if overlap:
            score += min(1.2, overlap * 0.25)
            reasons.append("учтена память")
        name = memory.facts.get("name")
        if name and "зовут" in text.lower() and stem(name.value.lower()) not in answer_set:
            score -= 2.5
            reasons.append("имя не совпало с памятью")

    repeats = _repeat_ratio(answer_tokens)
    if repeats:
        score -= repeats * 4.0
        reasons.append(f"повторы {repeats:.0%}")
    unique_ratio = len(answer_set) / max(1, len(answer_tokens))
    score += unique_ratio * 0.55

    # Копия прошлого ответа почти всегда означает зацикливание маленькой модели.
    for _u, prev_bot in history[-3:]:
        prev_set = set(content_tokens(prev_bot))
        if prev_set:
            jaccard = len(answer_set & prev_set) / max(1, len(answer_set | prev_set))
            if jaccard > 0.72:
                score -= 2.0
                reasons.append("похож на прошлый ответ")

    preferred = 18 if len(query_set) <= 5 else 34
    distance = abs(len(answer_tokens) - preferred)
    score -= min(1.0, distance / 80.0)
    if len(answer_tokens) < 3:
        score -= 1.5
        reasons.append("слишком коротко")
    if text.count("…") >= 2:
        score -= 1.0
        reasons.append(" много неизвестных слов")
    return CandidateResult(text, round(score, 3), reasons)


@dataclass
class ContextReply:
    text: str
    plan: ContextPlan
    candidates: list[CandidateResult]
    mode: str = "model"
    new_memory: list[MemoryFact] = field(default_factory=list)


def generate_context_reply(
    model: GsaiGPT,
    tokenizer: Tokenizer,
    session: ContextSession,
    user_text: str,
    entries: list[KnowledgeEntry],
    device: str | torch.device = "cpu",
    identity_text: str = "",
    temperature: float = 0.8,
    top_k: int = 40,
    top_p: float = 0.92,
    repetition_penalty: float = 1.08,
    max_new: int = 110,
    candidates: int = 3,
    use_memory: bool = True,
    use_retrieval: bool = True,
    seed: int | None = None,
) -> ContextReply:
    turn_no = len(session.history) + 1
    new_facts = session.memory.observe(user_text, turn=turn_no) if use_memory else []

    index = KnowledgeIndex(entries)
    query = user_text + " " + " ".join(u for u, _b in session.history[-2:])
    retrieved = index.search(query, limit=4) if use_retrieval else []
    memory_text = session.memory.summary() if use_memory else ""
    plan = build_context_prompt(
        tokenizer, session.history, user_text, model.cfg.block_size,
        reserve_tokens=max_new, identity_text=identity_text,
        memory_text=memory_text, retrieved=retrieved, topics=session.topics(),
    )

    direct = session.memory.direct_answer(user_text) if use_memory else None
    if direct:
        result = CandidateResult(direct, 10.0, ["точный факт из памяти"], source="memory")
        session.add_turn(user_text, direct)
        return ContextReply(direct, plan, [result], mode="memory", new_memory=new_facts)

    count = max(1, min(6, int(candidates or 1)))
    base_seed = random.SystemRandom().randint(1, 2_147_483_647) if seed is None else int(seed)
    results: list[CandidateResult] = []
    was_training = model.training
    model.eval()
    try:
        for i in range(count):
            temp_factor = (0.45, 1.0, 1.22, 0.75, 1.42, 0.6)[i % 6]
            out = model.generate_tokens(
                plan.prompt_ids, max_new=max_new,
                temperature=max(0.05, temperature * temp_factor),
                top_k=top_k, top_p=top_p,
                repetition_penalty=repetition_penalty,
                device=device, seed=base_seed + i * 7919,
            )
            answer = tokenizer.decode(out)
            results.append(score_candidate(answer, user_text, retrieved, session.memory, session.history))
    finally:
        if was_training:
            model.train()

    best = max(results, key=lambda item: item.score) if results else CandidateResult("…", -10.0)
    mode = "model"
    # Если знания нашлись очень точно, а модель ушла в сторону, честный RAG-ответ
    # из карточки лучше уверенной выдумки. В инспекторе GUI это будет видно.
    if retrieved and retrieved[0].score >= 0.55 and best.score < 0.75:
        entry = retrieved[0].entry
        fallback = CandidateResult(
            _clean(entry.body), max(best.score, 1.25),
            ["модель отклонилась, взят точный факт из базы знаний"], source="knowledge",
        )
        results.append(fallback)
        best = fallback
        mode = "knowledge"

    answer = best.text or "…"
    session.add_turn(user_text, answer)
    results.sort(key=lambda item: item.score, reverse=True)
    return ContextReply(answer, plan, results, mode=mode, new_memory=new_facts)
