"""
Словесный токенизатор GSYNK-AI.

Один и тот же алгоритм реализован здесь (Python) и в GSYNK на телефоне (Kotlin),
поэтому модель, обученная на ПК, одинаково читает текст на устройстве.

Специальные токены:
  0 <pad>   — добивка
  1 <unk>   — незнакомое слово
  2 [USER]  — начало реплики пользователя
  3 [BOT]   — начало реплики модели
  4 [END]   — конец реплики модели
  5 [BOS]   — начало свободного текста
  6 [EOS]   — конец свободного текста
"""

from __future__ import annotations

import re
from collections import Counter

PAD, UNK, USER, BOT, END, BOS, EOS = 0, 1, 2, 3, 4, 5, 6
SPECIALS = ["<pad>", "<unk>", "[USER]", "[BOT]", "[END]", "[BOS]", "[EOS]"]
SPECIAL_SET = set(SPECIALS)

_TOKEN_RE = re.compile(r"[a-zа-яё0-9]+|[^\sa-zа-яё0-9]", re.IGNORECASE)
_NO_SPACE_BEFORE = set(".,!?;:…%)»")
_NO_SPACE_AFTER = set("«(")


def split_tokens(text: str) -> list[str]:
    """Режет текст на слова и знаки. Текст приводится к нижнему регистру."""
    if not text:
        return []
    return _TOKEN_RE.findall(text.lower())


class Tokenizer:
    def __init__(self, vocab: list[str]):
        if vocab[: len(SPECIALS)] != SPECIALS:
            raise ValueError("словарь должен начинаться со специальных токенов GSYNK-AI")
        self.vocab = list(vocab)
        self.stoi = {tok: i for i, tok in enumerate(self.vocab)}

    @property
    def vocab_size(self) -> int:
        return len(self.vocab)

    @classmethod
    def build(cls, texts: list[str], max_vocab: int = 8000, min_freq: int = 2) -> "Tokenizer":
        counter: Counter[str] = Counter()
        for text in texts:
            counter.update(split_tokens(text))
        words = [w for w, c in counter.most_common() if c >= min_freq and w not in SPECIAL_SET]
        words = words[: max(0, max_vocab - len(SPECIALS))]
        return cls(SPECIALS + words)

    def encode(self, text: str) -> list[int]:
        stoi = self.stoi
        return [stoi.get(tok, UNK) for tok in split_tokens(text)]

    def encode_dialogue(self, user: str, bot: str) -> list[int]:
        return [USER] + self.encode(user) + [BOT] + self.encode(bot) + [END]

    def encode_text(self, text: str) -> list[int]:
        return [BOS] + self.encode(text) + [EOS]

    def decode(self, ids: list[int], stop_at_end: bool = True) -> str:
        out: list[str] = []
        capitalize_next = True
        prev = ""
        for idx in ids:
            if idx in (PAD, USER, BOT, BOS):
                continue
            if idx in (END, EOS):
                if stop_at_end:
                    break
                continue
            tok = self.vocab[idx] if 0 <= idx < len(self.vocab) else ""
            if tok == "<unk>":
                tok = "…"
            if not tok:
                continue
            if capitalize_next and tok[0].isalpha():
                tok = tok[0].upper() + tok[1:]
                capitalize_next = False
            if out and tok not in _NO_SPACE_BEFORE and prev not in _NO_SPACE_AFTER:
                out.append(" ")
            out.append(tok)
            prev = tok
            if tok in (".", "!", "?"):
                capitalize_next = True
        return "".join(out).strip()
