"""Генерация ответов PyTorch-модели: обычный и контекстный режимы."""

from __future__ import annotations

import torch

from .context import ContextReply, ContextSession, build_context_prompt, generate_context_reply
from .dataset import KnowledgeEntry
from .model import GsaiGPT
from .tokenizer import BOT, END, USER, Tokenizer

__all__ = [
    "ContextReply", "ContextSession", "build_context_prompt", "build_prompt",
    "chat_reply", "context_reply",
]


def build_prompt(tokenizer: Tokenizer, history: list[tuple[str, str]], user_text: str, block_size: int) -> list[int]:
    """Совместимый промпт без знаний/памяти, но уже без слепого среза по хвосту."""
    return build_context_prompt(
        tokenizer, history, user_text, block_size, reserve_tokens=min(90, max(16, block_size // 4))
    ).prompt_ids


def chat_reply(
    model: GsaiGPT,
    tokenizer: Tokenizer,
    history: list[tuple[str, str]],
    user_text: str,
    device: str | torch.device = "cpu",
    temperature: float = 0.8,
    top_k: int = 40,
    top_p: float = 0.92,
    repetition_penalty: float = 1.08,
    max_new: int = 90,
    seed: int | None = None,
) -> str:
    prompt = build_prompt(tokenizer, history, user_text, model.cfg.block_size)
    out = model.generate_tokens(
        prompt, max_new=max_new, temperature=temperature, top_k=top_k, top_p=top_p,
        repetition_penalty=repetition_penalty, device=device, seed=seed,
    )
    text = tokenizer.decode(out)
    return text or "…"


def context_reply(
    model: GsaiGPT,
    tokenizer: Tokenizer,
    session: ContextSession,
    user_text: str,
    entries: list[KnowledgeEntry],
    device: str | torch.device = "cpu",
    **kwargs,
) -> ContextReply:
    return generate_context_reply(model, tokenizer, session, user_text, entries, device=device, **kwargs)
