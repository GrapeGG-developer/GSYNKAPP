"""
Обучение GsaiGPT на железе пользователя.

Режим «ТУРБО 100% ПК»: все потоки процессора + видеокарта через DirectML
(AMD/NVIDIA/Intel под Windows) или CUDA, если доступна. Компьютер будет
шуметь вентиляторами — это нормально, но следите за температурами.
"""

from __future__ import annotations

import math
import os
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import numpy as np
import torch

from .model import GsaiConfig, GsaiGPT


@dataclass
class TrainState:
    step: int = 0
    loss: float = 0.0
    loss_ema: float = 0.0
    tok_per_sec: float = 0.0
    epoch: float = 0.0
    lr: float = 0.0
    device: str = "cpu"
    running: bool = False
    message: str = ""


def pick_device(turbo: bool = True) -> tuple[torch.device, str]:
    """DirectML (видеокарта под Windows) → CUDA → CPU всеми потоками."""
    if turbo:
        try:
            import torch_directml  # type: ignore

            return torch_directml.device(), "DirectML (видеокарта)"
        except Exception:
            pass
        if torch.cuda.is_available():
            return torch.device("cuda"), "CUDA (видеокарта NVIDIA)"
    threads = os.cpu_count() or 4
    torch.set_num_threads(threads)
    try:
        torch.set_num_interop_threads(max(1, threads // 2))
    except Exception:
        pass
    return torch.device("cpu"), f"CPU × {threads} потоков"


class Trainer:
    def __init__(
        self,
        model: GsaiGPT,
        stream: list[int],
        device: torch.device,
        device_name: str,
        lr: float = 3e-4,
        batch: int = 32,
        on_state: Callable[[TrainState], None] | None = None,
    ):
        if len(stream) < model.cfg.block_size + 2:
            raise ValueError("датасет слишком маленький: добавьте диалогов или текста")
        self.model = model.to(device)
        self.device = device
        self.state = TrainState(device=device_name)
        self.on_state = on_state
        self.lr0 = lr
        self.batch = batch
        self.data = np.asarray(stream, dtype=np.int64)
        self.opt = torch.optim.AdamW(model.parameters(), lr=lr, betas=(0.9, 0.95), weight_decay=0.05)
        self.stop_flag = threading.Event()
        self.rng = np.random.default_rng(1234)

    def _lr_at(self, step: int, total_hint: int) -> float:
        warmup = 40
        if step < warmup:
            return self.lr0 * (step + 1) / warmup
        horizon = max(total_hint, step + 400)
        progress = min(1.0, (step - warmup) / max(1, horizon - warmup))
        return self.lr0 * (0.1 + 0.9 * 0.5 * (1 + math.cos(math.pi * progress)))

    def _batch(self) -> tuple[torch.Tensor, torch.Tensor]:
        block = self.model.cfg.block_size
        starts = self.rng.integers(0, len(self.data) - block - 1, size=self.batch)
        xs = np.stack([self.data[s:s + block] for s in starts])
        ys = np.stack([self.data[s + 1:s + block + 1] for s in starts])
        return (
            torch.from_numpy(xs).to(self.device),
            torch.from_numpy(ys).to(self.device),
        )

    def train(self, max_steps: int, checkpoint_path: str | Path | None = None, save_every: int = 250) -> TrainState:
        self.model.train()
        self.stop_flag.clear()
        st = self.state
        st.running = True
        tokens_window = 0
        t_window = time.perf_counter()
        tokens_per_epoch = max(1, len(self.data))
        try:
            while st.step < max_steps and not self.stop_flag.is_set():
                lr = self._lr_at(st.step, max_steps)
                for group in self.opt.param_groups:
                    group["lr"] = lr
                x, y = self._batch()
                _, loss = self.model(x, y)
                self.opt.zero_grad(set_to_none=True)
                loss.backward()
                torch.nn.utils.clip_grad_norm_(self.model.parameters(), 1.0)
                self.opt.step()
                value = float(loss.item())
                st.step += 1
                st.loss = value
                st.loss_ema = value if st.loss_ema == 0 else 0.97 * st.loss_ema + 0.03 * value
                st.lr = lr
                tokens_window += int(x.numel())
                st.epoch = (st.step * self.batch * self.model.cfg.block_size) / tokens_per_epoch
                now = time.perf_counter()
                if now - t_window >= 0.5:
                    st.tok_per_sec = tokens_window / (now - t_window)
                    tokens_window = 0
                    t_window = now
                    if self.on_state:
                        self.on_state(st)
                if checkpoint_path and save_every > 0 and st.step % save_every == 0:
                    self.save_checkpoint(checkpoint_path)
        finally:
            st.running = False
            if checkpoint_path:
                self.save_checkpoint(checkpoint_path)
            if self.on_state:
                self.on_state(st)
        return st

    def stop(self) -> None:
        self.stop_flag.set()
        self.state.message = "останавливаю после текущего шага…"

    def save_checkpoint(self, path: str | Path) -> None:
        payload = {
            "config": self.model.cfg.to_dict(),
            "state_dict": {k: v.detach().cpu() for k, v in self.model.state_dict().items()},
            "step": self.state.step,
            "loss_ema": self.state.loss_ema,
        }
        torch.save(payload, str(path))

    @staticmethod
    def load_checkpoint(path: str | Path, device: torch.device) -> tuple[GsaiGPT, int, float]:
        payload = torch.load(str(path), map_location="cpu", weights_only=False)
        cfg = GsaiConfig(**payload["config"])
        model = GsaiGPT(cfg)
        model.load_state_dict(payload["state_dict"])
        model = model.to(device)
        return model, int(payload.get("step", 0)), float(payload.get("loss_ema", 0.0))
