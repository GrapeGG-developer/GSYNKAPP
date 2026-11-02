#!/usr/bin/env python3
"""Сквозной тест ядра GSYNK-AI без GUI: датасет → обучение → пакет → паритет."""
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import numpy as np
import torch

from gsai.dataset import Project, build_stream, generate_russian_dataset
from gsai.generate import chat_reply
from gsai.model import GsaiConfig, GsaiGPT
from gsai.package import GsaiPackage, export_package
from gsai.tokenizer import Tokenizer, split_tokens
from gsai.trainer import Trainer


def main() -> None:
    # 1) токенизатор
    toks = split_tokens("Привет, мир! Сколько будет 2 + 2?")
    assert toks[:3] == ["привет", ",", "мир"], toks
    print("[ok] токенизатор режет русский текст")

    # 2) генератор датасета
    dialogues, texts = generate_russian_dataset("Тестик", count=800, seed=1)
    assert len(dialogues) == 800 and texts
    assert any("Тестик" in " ".join(pair) for pair in dialogues)
    project = Project(name="Тестик", size="mini", dialogues=dialogues, texts=texts)
    print(f"[ok] генератор: {len(dialogues)} диалогов, {len(texts)} текстов")

    tokenizer = Tokenizer.build(project.all_texts(), max_vocab=4000, min_freq=1)
    stream = build_stream(project, tokenizer)
    assert len(stream) > 8000, len(stream)
    print(f"[ok] словарь {tokenizer.vocab_size}, поток {len(stream)} токенов")

    # 3) обучение (короткое)
    cfg = GsaiConfig(vocab_size=tokenizer.vocab_size, block_size=128, n_layer=2, n_head=4, n_embd=128, dropout=0.0)
    model = GsaiGPT(cfg)
    device = torch.device("cpu")
    torch.set_num_threads(max(1, len(__import__('os').sched_getaffinity(0))))
    trainer = Trainer(model, stream, device, "cpu-test", lr=1e-3, batch=16)
    state = trainer.train(max_steps=50)
    assert state.step == 50 and state.loss_ema > 0
    print(f"[ok] обучение: 50 шагов, loss={state.loss_ema:.3f}, {state.tok_per_sec:.0f} ток/с")

    # 4) экспорт пакета и загрузка
    tmp = Path(tempfile.mkdtemp())
    gsai_path = tmp / "testik.gsai"
    meta = export_package(model, tokenizer, gsai_path, name="Тестик", steps=state.step, loss=state.loss_ema)
    package = GsaiPackage(gsai_path)
    assert package.meta["params"] == meta["params"]
    print(f"[ok] пакет .gsai: {gsai_path.stat().st_size} байт, параметров {meta['params']}")

    # 5) паритет: PyTorch против NumPy из пакета
    model.eval()
    prompt = tokenizer.encode_dialogue("привет", "")[:-1]
    with torch.no_grad():
        torch_logits, _ = model(torch.tensor([prompt]))
    torch_last = torch_logits[0, -1].numpy()
    np_last = package.logits(prompt)[-1]
    diff = float(np.abs(torch_last - np_last).max())
    agree = int(np.argmax(torch_last)) == int(np.argmax(np_last))
    print(f"[ok] паритет логитов: max diff={diff:.4f}, argmax совпал={agree}")
    assert diff < 0.05 and agree

    # 6) генерация из пакета (NumPy) и из torch-модели
    ids = package.generate(prompt, max_new=20, temperature=0.0, top_k=0)
    np_text = tokenizer.decode(ids)
    torch_text = chat_reply(model, tokenizer, [], "привет", temperature=0.0, top_k=0, max_new=20)
    print(f"[ok] NumPy-пакет: {np_text!r}")
    print(f"[ok] PyTorch-чат: {torch_text!r}")
    assert np_text == torch_text

    print("ALL_CORE_TESTS_OK")


if __name__ == "__main__":
    main()
