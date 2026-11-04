"""
GsaiGPT — маленький, но настоящий трансформер (decoder-only, как GPT).

Архитектура специально зеркалится один-в-один в Kotlin-движке GSYNK на телефоне:
без dropout в инференсе, ReLU в MLP, LayerNorm eps=1e-5, fused QKV.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

import torch
import torch.nn as nn
import torch.nn.functional as F


@dataclass
class GsaiConfig:
    vocab_size: int
    block_size: int = 256
    n_layer: int = 6
    n_head: int = 4
    n_embd: int = 256
    dropout: float = 0.1

    def to_dict(self) -> dict:
        return {
            "vocab_size": self.vocab_size,
            "block_size": self.block_size,
            "n_layer": self.n_layer,
            "n_head": self.n_head,
            "n_embd": self.n_embd,
        }


class Block(nn.Module):
    def __init__(self, cfg: GsaiConfig):
        super().__init__()
        d = cfg.n_embd
        self.n_head = cfg.n_head
        self.head_dim = d // cfg.n_head
        self.ln1 = nn.LayerNorm(d)
        self.qkv = nn.Linear(d, 3 * d)
        self.proj = nn.Linear(d, d)
        self.ln2 = nn.LayerNorm(d)
        self.fc = nn.Linear(d, 4 * d)
        self.mlp_proj = nn.Linear(4 * d, d)
        self.drop = nn.Dropout(cfg.dropout)
        self.dropout_p = float(cfg.dropout)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        b, t, d = x.shape
        h = self.ln1(x)
        qkv = self.qkv(h)
        q, k, v = qkv.chunk(3, dim=-1)
        q = q.view(b, t, self.n_head, self.head_dim).transpose(1, 2)
        k = k.view(b, t, self.n_head, self.head_dim).transpose(1, 2)
        v = v.view(b, t, self.n_head, self.head_dim).transpose(1, 2)
        if x.device.type == "privateuseone":
            # torch-directml 2.3 не гарантирует SDPA. Для AMD/Intel под Windows
            # оставляем проверенный ручной causal attention с теми же весами.
            att = (q @ k.transpose(-2, -1)) / math.sqrt(self.head_dim)
            mask = torch.triu(
                torch.full((t, t), float("-inf"), device=x.device, dtype=att.dtype), diagonal=1
            )
            att = F.softmax(att + mask, dim=-1)
            att = self.drop(att)
            y = att @ v
        else:
            # SDPA равен ручному causal attention по математике, но использует fused
            # ядра ускорителя и не строит огромную матрицу внимания в памяти.
            y = F.scaled_dot_product_attention(
                q, k, v,
                dropout_p=self.dropout_p if self.training else 0.0,
                is_causal=True,
            )
        y = y.transpose(1, 2).contiguous().view(b, t, d)
        x = x + self.proj(y)
        h2 = self.ln2(x)
        x = x + self.mlp_proj(F.relu(self.fc(h2)))
        return x


class GsaiGPT(nn.Module):
    def __init__(self, cfg: GsaiConfig):
        super().__init__()
        self.cfg = cfg
        self.tok_emb = nn.Embedding(cfg.vocab_size, cfg.n_embd)
        self.pos_emb = nn.Embedding(cfg.block_size, cfg.n_embd)
        self.blocks = nn.ModuleList([Block(cfg) for _ in range(cfg.n_layer)])
        self.ln_f = nn.LayerNorm(cfg.n_embd)
        self.head = nn.Linear(cfg.n_embd, cfg.vocab_size)
        self.drop = nn.Dropout(cfg.dropout)
        self.apply(self._init)

    @staticmethod
    def _init(module: nn.Module) -> None:
        if isinstance(module, nn.Linear):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)
            if module.bias is not None:
                nn.init.zeros_(module.bias)
        elif isinstance(module, nn.Embedding):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)

    def forward(self, idx: torch.Tensor, targets: torch.Tensor | None = None):
        b, t = idx.shape
        pos = torch.arange(t, device=idx.device)
        x = self.drop(self.tok_emb(idx) + self.pos_emb(pos))
        for block in self.blocks:
            x = block(x)
        logits = self.head(self.ln_f(x))
        loss = None
        if targets is not None:
            if bool((targets != -100).any().detach().cpu()):
                loss = F.cross_entropy(
                    logits.view(-1, logits.size(-1)), targets.view(-1), ignore_index=-100
                )
            else:
                loss = logits.sum() * 0.0
        return logits, loss

    def num_params(self) -> int:
        return sum(p.numel() for p in self.parameters())

    @torch.no_grad()
    def generate_tokens(
        self,
        prompt: list[int],
        max_new: int = 90,
        temperature: float = 0.8,
        top_k: int = 40,
        top_p: float = 1.0,
        repetition_penalty: float = 1.0,
        stop_ids: tuple[int, ...] = (4, 6),
        device: str | torch.device = "cpu",
        seed: int | None = None,
    ) -> list[int]:
        self.eval()
        gen = None
        if seed is not None:
            gen = torch.Generator(device="cpu").manual_seed(seed)
        ids = list(prompt)[-self.cfg.block_size:]
        out: list[int] = []
        for _ in range(max_new):
            window = ids[-self.cfg.block_size:]
            x = torch.tensor([window], dtype=torch.long, device=device)
            logits, _ = self(x)
            last = logits[0, -1].float().cpu()
            if repetition_penalty and repetition_penalty > 1.0 and out:
                seen: dict[int, int] = {}
                for tok in out[-96:]:
                    seen[tok] = min(4, seen.get(tok, 0) + 1)
                for tok, count in seen.items():
                    penalty = float(repetition_penalty) ** count
                    last[tok] = last[tok] / penalty if last[tok] > 0 else last[tok] * penalty
            if temperature <= 0.01:
                nxt = int(torch.argmax(last).item())
            else:
                last = last / temperature
                if top_k and top_k > 0:
                    vals, _ = torch.topk(last, min(top_k, last.numel()))
                    last[last < vals[-1]] = float("-inf")
                probs = F.softmax(last, dim=-1)
                if top_p and 0.0 < top_p < 1.0:
                    sorted_probs, sorted_idx = torch.sort(probs, descending=True)
                    cumulative = torch.cumsum(sorted_probs, dim=-1)
                    keep_sorted = cumulative - sorted_probs < top_p
                    filtered = torch.zeros_like(probs)
                    filtered[sorted_idx[keep_sorted]] = probs[sorted_idx[keep_sorted]]
                    probs = filtered / filtered.sum().clamp_min(1e-12)
                nxt = int(torch.multinomial(probs, 1, generator=gen).item())
            if nxt in stop_ids:
                break
            out.append(nxt)
            ids.append(nxt)
        return out


# Порядок тензоров в файле weights.bin пакета .gsai — общий контракт с телефоном.
def tensor_order(cfg: GsaiConfig) -> list[tuple[str, tuple[int, ...]]]:
    d, v, t = cfg.n_embd, cfg.vocab_size, cfg.block_size
    order: list[tuple[str, tuple[int, ...]]] = [
        ("tok_emb.weight", (v, d)),
        ("pos_emb.weight", (t, d)),
    ]
    for i in range(cfg.n_layer):
        p = f"blocks.{i}."
        order += [
            (p + "ln1.weight", (d,)),
            (p + "ln1.bias", (d,)),
            (p + "qkv.weight", (3 * d, d)),
            (p + "qkv.bias", (3 * d,)),
            (p + "proj.weight", (d, d)),
            (p + "proj.bias", (d,)),
            (p + "ln2.weight", (d,)),
            (p + "ln2.bias", (d,)),
            (p + "fc.weight", (4 * d, d)),
            (p + "fc.bias", (4 * d,)),
            (p + "mlp_proj.weight", (d, 4 * d)),
            (p + "mlp_proj.bias", (d,)),
        ]
    order += [
        ("ln_f.weight", (d,)),
        ("ln_f.bias", (d,)),
        ("head.weight", (v, d)),
        ("head.bias", (v,)),
    ]
    return order
