"""Чат с моделью: форматирование истории и генерация ответов (PyTorch)."""

from __future__ import annotations

import torch

from .model import GsaiGPT
from .tokenizer import BOT, END, USER, Tokenizer


def build_prompt(tokenizer: Tokenizer, history: list[tuple[str, str]], user_text: str, block_size: int) -> list[int]:
    ids: list[int] = []
    for user, bot in history:
        ids += [USER] + tokenizer.encode(user) + [BOT] + tokenizer.encode(bot) + [END]
    ids += [USER] + tokenizer.encode(user_text) + [BOT]
    return ids[-block_size:]


def chat_reply(
    model: GsaiGPT,
    tokenizer: Tokenizer,
    history: list[tuple[str, str]],
    user_text: str,
    device: str | torch.device = "cpu",
    temperature: float = 0.8,
    top_k: int = 40,
    max_new: int = 90,
    seed: int | None = None,
) -> str:
    prompt = build_prompt(tokenizer, history, user_text, model.cfg.block_size)
    out = model.generate_tokens(prompt, max_new=max_new, temperature=temperature, top_k=top_k,
                                device=device, seed=seed)
    text = tokenizer.decode(out)
    return text or "…"
