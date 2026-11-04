"""
GPU-first обучение GsaiGPT на железе пользователя.

GSYNK-AI 1.1 сначала ищет CUDA, затем DirectML (AMD/Intel/NVIDIA под Windows),
и только потом CPU. Размер пакета подбирается по объёму видеопамяти и длине
контекста, внимание считается fused SDPA-ядром, на CUDA включается mixed
precision и fused AdamW. Если видеопамяти всё же не хватит, пакет уменьшится
автоматически без падения обучения.
"""

from __future__ import annotations

import contextlib
import gc
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
    val_loss: float = 0.0
    perplexity: float = 0.0
    tok_per_sec: float = 0.0
    epoch: float = 0.0
    lr: float = 0.0
    device: str = "cpu"
    precision: str = "FP32"
    eta_seconds: float = 0.0
    gpu_memory_mb: float = 0.0
    running: bool = False
    message: str = ""


def _cpu_device() -> tuple[torch.device, str]:
    threads = os.cpu_count() or 4
    torch.set_num_threads(threads)
    try:
        torch.set_num_interop_threads(max(1, threads // 2))
    except Exception:
        pass
    return torch.device("cpu"), f"CPU × {threads} потоков"


def _directml_device() -> tuple[torch.device, str] | None:
    try:
        import torch_directml  # type: ignore

        device = torch_directml.device()
        name = "DirectML (видеокарта Windows)"
        try:
            detected = torch_directml.device_name(device)  # type: ignore[attr-defined]
            if detected:
                name = f"DirectML · {detected}"
        except Exception:
            pass
        return device, name
    except Exception:
        return None


def _cuda_device() -> tuple[torch.device, str] | None:
    try:
        if torch.cuda.is_available():
            idx = torch.cuda.current_device()
            name = torch.cuda.get_device_name(idx)
            total = torch.cuda.get_device_properties(idx).total_memory / (1024 ** 3)
            return torch.device("cuda", idx), f"CUDA · {name} · {total:.1f} ГБ VRAM"
    except Exception:
        pass
    return None


def configure_accelerator(device: torch.device) -> None:
    """Включает безопасные ускорения конкретного backend-а."""
    threads = os.cpu_count() or 4
    torch.set_num_threads(threads)
    if device.type == "cuda":
        torch.backends.cuda.matmul.allow_tf32 = True
        torch.backends.cudnn.allow_tf32 = True
        torch.backends.cudnn.benchmark = True
        try:
            torch.set_float32_matmul_precision("high")
        except Exception:
            pass
    elif device.type == "cpu":
        try:
            torch.set_num_interop_threads(max(1, threads // 2))
        except Exception:
            pass
        try:
            torch.set_float32_matmul_precision("high")
        except Exception:
            pass


def pick_device(turbo: bool = True, preference: str = "auto") -> tuple[torch.device, str]:
    """Выбирает ускоритель: auto = CUDA → DirectML → CPU."""
    pref = str(preference or "auto").lower()
    if not turbo or pref == "cpu":
        device, name = _cpu_device()
        configure_accelerator(device)
        return device, name

    candidates: list[tuple[torch.device, str]] = []
    if pref == "directml":
        dml = _directml_device()
        cuda = _cuda_device()
        candidates = [x for x in (dml, cuda) if x is not None]
    elif pref == "cuda":
        cuda = _cuda_device()
        dml = _directml_device()
        candidates = [x for x in (cuda, dml) if x is not None]
    else:
        cuda = _cuda_device()
        dml = _directml_device()
        candidates = [x for x in (cuda, dml) if x is not None]
    if candidates:
        device, name = candidates[0]
        configure_accelerator(device)
        return device, name
    device, name = _cpu_device()
    configure_accelerator(device)
    return device, name


def available_accelerators() -> list[str]:
    out: list[str] = []
    cuda = _cuda_device()
    if cuda:
        out.append(cuda[1])
    dml = _directml_device()
    if dml:
        out.append(dml[1])
    out.append(_cpu_device()[1])
    return out


def hardware_summary() -> str:
    lines = [f"PyTorch {torch.__version__}"]
    lines.extend("• " + item for item in available_accelerators())
    try:
        import psutil

        ram = psutil.virtual_memory().total / (1024 ** 3)
        lines.append(f"• RAM: {ram:.1f} ГБ")
    except Exception:
        pass
    return "\n".join(lines)


def auto_batch_size(model: GsaiGPT, device: torch.device) -> int:
    """Подбирает пакет по целевому числу токенов и известной VRAM."""
    cfg = model.cfg
    complexity = max(1.0, (cfg.n_embd / 256.0) * (cfg.n_layer / 6.0))
    if device.type == "cuda":
        try:
            vram_gb = torch.cuda.get_device_properties(device).total_memory / (1024 ** 3)
        except Exception:
            vram_gb = 6.0
        token_budget = 131_072 if vram_gb >= 20 else 98_304 if vram_gb >= 12 else 65_536 if vram_gb >= 8 else 49_152 if vram_gb >= 5 else 32_768
        token_budget = int(token_budget / complexity)
        return max(4, min(256, token_budget // max(32, cfg.block_size)))
    if device.type == "privateuseone":
        # DirectML не отдаёт надёжный объём VRAM. Бюджет рассчитан на типичные
        # 6 ГБ (например RX 5600 XT), а OOM-обработчик подстрахует слабые карты.
        token_budget = int(49_152 / complexity)
        return max(4, min(192, token_budget // max(32, cfg.block_size)))
    token_budget = int(16_384 / complexity)
    return max(2, min(64, token_budget // max(32, cfg.block_size)))


class Trainer:
    def __init__(
        self,
        model: GsaiGPT,
        stream: list[int],
        device: torch.device,
        device_name: str,
        lr: float = 3e-4,
        batch: int = 0,
        on_state: Callable[[TrainState], None] | None = None,
        labels: list[int] | None = None,
        val_stream: list[int] | None = None,
        val_labels: list[int] | None = None,
        compile_model: bool = False,
        mixed_precision: bool = True,
        grad_accum: int = 1,
        eval_every: int = 250,
        seed: int = 1234,
    ):
        if len(stream) < model.cfg.block_size + 2:
            raise ValueError("датасет слишком маленький: добавьте знаний")
        configure_accelerator(device)
        self.raw_model = model.to(device)
        self.model: GsaiGPT | torch.nn.Module = self.raw_model
        self.device = device
        self.state = TrainState(device=device_name)
        self.on_state = on_state
        self.lr0 = lr
        self.batch = int(batch) if batch else auto_batch_size(self.raw_model, device)
        self.batch = max(1, self.batch)
        self.grad_accum = max(1, int(grad_accum or 1))
        self.eval_every = max(0, int(eval_every or 0))
        self.data = np.asarray(stream, dtype=np.int64)
        self.labels = np.asarray(labels, dtype=np.int64) if labels is not None else self.data.copy()
        if len(self.labels) != len(self.data):
            raise ValueError("метки обучения не совпали по длине с потоком токенов")
        self.val_data = np.asarray(val_stream, dtype=np.int64) if val_stream else None
        self.val_labels = (
            np.asarray(val_labels, dtype=np.int64)
            if val_labels is not None and val_stream
            else (self.val_data.copy() if self.val_data is not None else None)
        )
        self._offsets = np.arange(self.raw_model.cfg.block_size, dtype=np.int64)

        fused = device.type == "cuda"
        try:
            self.opt = torch.optim.AdamW(
                self.raw_model.parameters(), lr=lr, betas=(0.9, 0.95),
                weight_decay=0.05, fused=fused,
            )
        except Exception:
            self.opt = torch.optim.AdamW(
                self.raw_model.parameters(), lr=lr, betas=(0.9, 0.95), weight_decay=0.05
            )

        self.amp_dtype: torch.dtype | None = None
        self.scaler = None
        if mixed_precision and device.type == "cuda":
            if torch.cuda.is_bf16_supported():
                self.amp_dtype = torch.bfloat16
                self.state.precision = "BF16 AMP"
            else:
                self.amp_dtype = torch.float16
                self.state.precision = "FP16 AMP"
                self.scaler = torch.amp.GradScaler("cuda") if hasattr(torch, "amp") else torch.cuda.amp.GradScaler()
        else:
            self.state.precision = "FP32"

        if compile_model and device.type == "cuda":
            try:
                self.model = torch.compile(self.raw_model)  # type: ignore[assignment]
                self.state.message = "torch.compile включён: первые шаги уйдут на сборку быстрых ядер"
            except Exception:
                self.model = self.raw_model

        self.stop_flag = threading.Event()
        self.rng = np.random.default_rng(seed)

    @property
    def effective_batch(self) -> int:
        return self.batch * self.grad_accum

    def _autocast(self):
        if self.amp_dtype is not None and self.device.type == "cuda":
            return torch.autocast(device_type="cuda", dtype=self.amp_dtype)
        return contextlib.nullcontext()

    def _lr_at(self, step: int, total_hint: int) -> float:
        warmup = 40
        if step < warmup:
            return self.lr0 * (step + 1) / warmup
        horizon = max(total_hint, step + 400)
        progress = min(1.0, (step - warmup) / max(1, horizon - warmup))
        return self.lr0 * (0.1 + 0.9 * 0.5 * (1 + math.cos(math.pi * progress)))

    def _make_batch(self, data: np.ndarray, labels: np.ndarray, batch: int | None = None) -> tuple[torch.Tensor, torch.Tensor]:
        block = self.raw_model.cfg.block_size
        count = batch or self.batch
        starts = self.rng.integers(0, len(data) - block - 1, size=count, dtype=np.int64)
        idx = starts[:, None] + self._offsets[None, :]
        xs = data[idx]
        ys = labels[idx + 1]
        non_blocking = self.device.type == "cuda"
        return (
            torch.from_numpy(np.ascontiguousarray(xs)).to(self.device, non_blocking=non_blocking),
            torch.from_numpy(np.ascontiguousarray(ys)).to(self.device, non_blocking=non_blocking),
        )

    def _batch(self) -> tuple[torch.Tensor, torch.Tensor]:
        return self._make_batch(self.data, self.labels)

    @torch.no_grad()
    def evaluate(self, batches: int = 8) -> float:
        if self.val_data is None or self.val_labels is None:
            return 0.0
        if len(self.val_data) < self.raw_model.cfg.block_size + 2:
            return 0.0
        was_training = self.raw_model.training
        self.model.eval()
        total = 0.0
        seen = 0
        try:
            for _ in range(max(1, batches)):
                x, y = self._make_batch(self.val_data, self.val_labels, batch=min(16, self.batch))
                with self._autocast():
                    _logits, loss = self.model(x, y)
                value = float(loss.detach().float().cpu())
                if math.isfinite(value):
                    total += value
                    seen += 1
        finally:
            if was_training:
                self.model.train()
        return total / seen if seen else 0.0

    def _gpu_memory_mb(self) -> float:
        if self.device.type != "cuda":
            return 0.0
        try:
            return torch.cuda.max_memory_allocated(self.device) / (1024 * 1024)
        except Exception:
            return 0.0

    def _oom_shrink(self, st: TrainState) -> bool:
        self.opt.zero_grad(set_to_none=True)
        gc.collect()
        try:
            if self.device.type == "cuda":
                torch.cuda.empty_cache()
        except Exception:
            pass
        if self.batch <= 2:
            return False
        self.batch = max(2, self.batch // 2)
        st.message = f"памяти не хватило — уменьшил пакет до {self.batch} и продолжил обучение"
        if self.on_state:
            self.on_state(st)
        return True

    def train(self, max_steps: int, checkpoint_path: str | Path | None = None, save_every: int = 250) -> TrainState:
        self.model.train()
        self.stop_flag.clear()
        st = self.state
        st.running = True
        run_start_step = st.step
        tokens_window = 0
        tokens_done = 0
        t_window = time.perf_counter()
        started = t_window
        tokens_per_epoch = max(1, len(self.data))
        try:
            while st.step < max_steps and not self.stop_flag.is_set():
                lr = self._lr_at(st.step, max_steps)
                for group in self.opt.param_groups:
                    group["lr"] = lr
                try:
                    self.opt.zero_grad(set_to_none=True)
                    values: list[float] = []
                    step_tokens = 0
                    for micro in range(self.grad_accum):
                        x, y = self._batch()
                        with self._autocast():
                            _logits, loss = self.model(x, y)
                            scaled_loss = loss / self.grad_accum
                        if self.scaler is not None:
                            self.scaler.scale(scaled_loss).backward()
                        else:
                            scaled_loss.backward()
                        values.append(float(loss.detach().float().cpu()))
                        step_tokens += int(x.numel())
                        del x, y, loss, scaled_loss
                    value = float(np.mean(values)) if values else 0.0
                    if not math.isfinite(value):
                        self.opt.zero_grad(set_to_none=True)
                        st.message = "пропустил нестабильный шаг: loss стал нечисловым"
                        continue
                    if self.scaler is not None:
                        self.scaler.unscale_(self.opt)
                    torch.nn.utils.clip_grad_norm_(self.raw_model.parameters(), 1.0)
                    if self.scaler is not None:
                        self.scaler.step(self.opt)
                        self.scaler.update()
                    else:
                        self.opt.step()
                except RuntimeError as exc:
                    text = str(exc).lower()
                    if "out of memory" in text or "could not allocate" in text or ("not enough" in text and "memory" in text):
                        if self._oom_shrink(st):
                            continue
                        raise RuntimeError(
                            "Не хватает видеопамяти даже для самого маленького пакета. "
                            "Выберите модель меньшего размера или режим CPU."
                        ) from exc
                    raise

                st.step += 1
                st.loss = value
                st.loss_ema = value if st.loss_ema == 0 else 0.97 * st.loss_ema + 0.03 * value
                st.perplexity = math.exp(min(20.0, st.loss_ema)) if st.loss_ema else 0.0
                st.lr = lr
                tokens_window += step_tokens
                tokens_done += step_tokens
                st.epoch = tokens_done / tokens_per_epoch
                st.gpu_memory_mb = self._gpu_memory_mb()

                if self.eval_every and st.step % self.eval_every == 0:
                    val = self.evaluate()
                    if val:
                        st.val_loss = val
                        st.perplexity = math.exp(min(20.0, val))

                now = time.perf_counter()
                elapsed = max(0.001, now - started)
                completed = max(1, st.step - run_start_step)
                st.eta_seconds = max(0.0, (max_steps - st.step) * (elapsed / completed))
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
            "config": self.raw_model.cfg.to_dict(),
            "state_dict": {k: v.detach().cpu() for k, v in self.raw_model.state_dict().items()},
            "step": self.state.step,
            "loss_ema": self.state.loss_ema,
            "val_loss": self.state.val_loss,
            "precision": self.state.precision,
            "trainer": "gsynk-AI-1.1-gpu-first",
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
