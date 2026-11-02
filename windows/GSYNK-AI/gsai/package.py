"""
Пакет модели .gsai — то, что улетает на сервер и запускается на телефоне.

Состав (zip):
  model.json  — имя, иконка, архитектура, настройки генерации, статистика
  vocab.json  — словарь токенизатора
  weights.bin — веса float16 little-endian в порядке model.tensor_order()
  icon.png    — необязательная иконка

Здесь же — эталонный NumPy-инференс: телефонный Kotlin-движок обязан
давать те же ответы, что и он (и что PyTorch-модель).
"""

from __future__ import annotations

import hashlib
import json
import math
import zipfile
from pathlib import Path

import numpy as np

from .model import GsaiConfig, GsaiGPT, tensor_order
from .tokenizer import Tokenizer

FORMAT = "gsai-1"


def export_package(
    model: GsaiGPT,
    tokenizer: Tokenizer,
    out_path: str | Path,
    name: str,
    icon: str = "🍇",
    description: str = "",
    version: str = "1.0.0",
    temperature: float = 0.8,
    top_k: int = 40,
    max_new_tokens: int = 90,
    dataset_stats: dict | None = None,
    steps: int = 0,
    loss: float = 0.0,
    icon_png: bytes | None = None,
) -> dict:
    cfg = model.cfg
    state = model.state_dict()
    chunks: list[np.ndarray] = []
    for key, shape in tensor_order(cfg):
        tensor = state[key].detach().cpu().numpy()
        assert tuple(tensor.shape) == tuple(shape), f"{key}: {tensor.shape} != {shape}"
        chunks.append(tensor.astype(np.float16).ravel())
    weights = np.concatenate(chunks).astype("<f2").tobytes()
    meta = {
        "format": FORMAT,
        "name": name,
        "icon": icon,
        "description": description,
        "version": version,
        "architecture": cfg.to_dict(),
        "params": int(model.num_params()),
        "temperature": temperature,
        "top_k": top_k,
        "max_new_tokens": max_new_tokens,
        "dataset": dataset_stats or {},
        "steps": steps,
        "loss": loss,
        "weights_sha256": hashlib.sha256(weights).hexdigest(),
    }
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("model.json", json.dumps(meta, ensure_ascii=False, indent=1))
        zf.writestr("vocab.json", json.dumps(tokenizer.vocab, ensure_ascii=False))
        zf.writestr("weights.bin", weights)
        if icon_png:
            zf.writestr("icon.png", icon_png)
    meta["file_size"] = Path(out_path).stat().st_size
    meta["file_sha256"] = hashlib.sha256(Path(out_path).read_bytes()).hexdigest()
    return meta


class GsaiPackage:
    """Загруженный .gsai: метаданные + NumPy-веса + инференс."""

    def __init__(self, path: str | Path):
        with zipfile.ZipFile(path) as zf:
            self.meta = json.loads(zf.read("model.json").decode("utf-8"))
            vocab = json.loads(zf.read("vocab.json").decode("utf-8"))
            raw = zf.read("weights.bin")
        if self.meta.get("format") != FORMAT:
            raise ValueError("неизвестный формат пакета: нужен gsai-1")
        if hashlib.sha256(raw).hexdigest() != self.meta["weights_sha256"]:
            raise ValueError("weights.bin повреждён: контрольная сумма не сошлась")
        self.tokenizer = Tokenizer(vocab)
        arch = self.meta["architecture"]
        self.cfg = GsaiConfig(**arch)
        flat = np.frombuffer(raw, dtype="<f2").astype(np.float32)
        self.weights: dict[str, np.ndarray] = {}
        offset = 0
        for key, shape in tensor_order(self.cfg):
            count = int(np.prod(shape))
            self.weights[key] = flat[offset:offset + count].reshape(shape)
            offset += count
        if offset != flat.size:
            raise ValueError(f"размер весов не совпал: {offset} != {flat.size}")

    # --- NumPy forward: зеркало Kotlin-движка телефона ---
    def _ln(self, x: np.ndarray, w: np.ndarray, b: np.ndarray) -> np.ndarray:
        mean = x.mean(axis=-1, keepdims=True)
        var = x.var(axis=-1, keepdims=True)
        return (x - mean) / np.sqrt(var + 1e-5) * w + b

    @staticmethod
    def _lin(x: np.ndarray, w: np.ndarray, b: np.ndarray) -> np.ndarray:
        return x @ w.T + b

    def logits(self, ids: list[int]) -> np.ndarray:
        cfg, w = self.cfg, self.weights
        t = len(ids)
        x = w["tok_emb.weight"][ids] + w["pos_emb.weight"][:t]
        d = cfg.n_embd
        hd = d // cfg.n_head
        for i in range(cfg.n_layer):
            p = f"blocks.{i}."
            h = self._ln(x, w[p + "ln1.weight"], w[p + "ln1.bias"])
            qkv = self._lin(h, w[p + "qkv.weight"], w[p + "qkv.bias"])
            q, k, v = qkv[:, :d], qkv[:, d:2 * d], qkv[:, 2 * d:]
            out = np.zeros_like(x)
            for head in range(cfg.n_head):
                s = slice(head * hd, (head + 1) * hd)
                att = q[:, s] @ k[:, s].T / math.sqrt(hd)
                mask = np.triu(np.ones((t, t), dtype=bool), k=1)
                att = np.where(mask, -1e30, att)
                att = att - att.max(axis=-1, keepdims=True)
                exp = np.exp(att)
                att = exp / exp.sum(axis=-1, keepdims=True)
                out[:, s] = att @ v[:, s]
            x = x + self._lin(out, w[p + "proj.weight"], w[p + "proj.bias"])
            h2 = self._ln(x, w[p + "ln2.weight"], w[p + "ln2.bias"])
            x = x + self._lin(np.maximum(self._lin(h2, w[p + "fc.weight"], w[p + "fc.bias"]), 0),
                              w[p + "mlp_proj.weight"], w[p + "mlp_proj.bias"])
        x = self._ln(x, w["ln_f.weight"], w["ln_f.bias"])
        return self._lin(x, w["head.weight"], w["head.bias"])

    def generate(self, prompt: list[int], max_new: int, temperature: float, top_k: int, seed: int = 7) -> list[int]:
        rng = np.random.default_rng(seed)
        ids = list(prompt)[-self.cfg.block_size:]
        out: list[int] = []
        for _ in range(max_new):
            last = self.logits(ids[-self.cfg.block_size:])[-1]
            if temperature <= 0.01:
                nxt = int(np.argmax(last))
            else:
                last = last / temperature
                if top_k and top_k > 0 and top_k < last.size:
                    thresh = np.partition(last, -top_k)[-top_k]
                    last = np.where(last < thresh, -1e30, last)
                probs = np.exp(last - last.max())
                probs /= probs.sum()
                nxt = int(rng.choice(len(probs), p=probs))
            if nxt in (4, 6):
                break
            out.append(nxt)
            ids.append(nxt)
        return out
