#!/usr/bin/env python3
"""Сквозной тест GSYNK-AI 1.1 без GUI: знания → контекст → обучение → пакет."""
import os
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import numpy as np
import torch

from gsai.context import (
    ContextSession, KnowledgeIndex, build_context_prompt,
    generate_context_reply, score_candidate,
)
from gsai.dataset import (
    KnowledgeEntry, Project, build_training_data,
    parse_knowledge_document, parse_knowledge_plain,
)
from gsai.generate import chat_reply
from gsai.model import GsaiConfig, GsaiGPT
from gsai.package import GsaiPackage, export_package
from gsai.tokenizer import Tokenizer, split_tokens
from gsai.trainer import Trainer, auto_batch_size


def sample_project() -> Project:
    entries = [
        KnowledgeEntry("виноград", "виноград — сладкая ягода. виноград растёт гроздьями на лозе и любит солнце.", "definition", ["ягоды", "сад"], weight=3),
        KnowledgeEntry("фотосинтез", "фотосинтез — процесс, при котором растения превращают свет, воду и углекислый газ в кислород и энергию.", "definition", ["биология", "растения"], weight=3),
        KnowledgeEntry("Солнце", "Солнце — звезда в центре Солнечной системы. оно даёт Земле свет и тепло.", "definition", ["космос"], weight=2),
        KnowledgeEntry("правило сложения", "при сложении порядок чисел не меняет результат. например, 2 + 3 = 5 и 3 + 2 = 5.", "rule", ["математика"], weight=2),
    ]
    topics = ["кошка", "собака", "лиса", "сова", "река", "гора", "море", "лес", "луна", "Марс", "книга", "музыка"]
    facts = [
        "это интересная тема с собственными признаками и понятными примерами.",
        "это тема для проверки памяти модели и выбора нужного знания по контексту.",
        "важно помнить главные слова, связи и отличия от соседних тем.",
    ]
    for i, topic in enumerate(topics):
        entries.append(KnowledgeEntry(topic, f"{topic}: {facts[i % len(facts)]} тема номер {i + 1}.", "fact", ["проверка"]))
    project = Project(name="Тестик", size="mini", description="помнит знания и контекст")
    project.set_knowledge(entries)
    return project


def main() -> None:
    toks = split_tokens("Привет, мир! Сколько будет 2 + 2?")
    assert toks[:3] == ["привет", ",", "мир"], toks
    print("[ok] токенизатор режет русский текст")

    # Генератора в 1.1 нет: импорт строит карточки знаний, а не диалоги.
    import gsai.dataset as dataset_module
    assert not hasattr(dataset_module, "generate_russian_dataset")
    parsed = parse_knowledge_plain(
        "# Космос\n"
        "теги: астрономия, планеты\n"
        "Марс = красная планета с вулканом Олимп.\n\n"
        "старый вопрос => старый ответ как карточка"
    )
    assert len(parsed) == 2 and parsed[0].title.casefold() == "Марс".casefold() and parsed[1].kind == "qa", parsed
    js = parse_knowledge_document('[{"title":"Луна","body":"Луна — спутник Земли."}]', ".json")
    csv_data = parse_knowledge_document("title,body,tags\nМоре,Море связано с океаном,вода", ".csv")
    assert js[0].title == "Луна" and csv_data[0].tags == ["вода"]
    print("[ok] импорт знаний: текст, JSON и CSV")

    legacy = Project(dialogues=[["что это?", "это старое знание"]], texts=["старый свободный текст"])
    assert len(legacy.knowledge) == 2 and not legacy.dialogues and not legacy.texts
    print("[ok] старый проект мигрировал в карточки знаний")

    project = sample_project()
    stats = project.stats()
    assert stats["entries"] >= 16 and stats["words"] > 150, stats
    tokenizer = Tokenizer.build(project.all_texts(), max_vocab=4000, min_freq=1)
    data = build_training_data(project, tokenizer, block_size=128, validation_fraction=0.08)
    assert data.examples >= 50 and data.context_examples >= 20, data
    assert len(data.train_ids) == len(data.train_labels) and data.supervised_tokens > 500
    assert len(data.val_ids) > 128
    assert any(label == -100 for label in data.train_labels)
    print(
        f"[ok] знания→задания: {data.examples} примеров, контекстных {data.context_examples}, "
        f"train {len(data.train_ids)} токенов"
    )

    # Поиск и долговременная память без обучения модели.
    index = KnowledgeIndex(project.effective_knowledge())
    hits = index.search("что такое фотосинтез у растений", limit=3)
    assert hits and hits[0].entry.title == "фотосинтез", hits
    session = ContextSession()
    session.memory.observe("Меня зовут Семён. Я живу в Самаре. Я люблю виноград и космос.")
    assert session.memory.direct_answer("как меня зовут?") == "Тебя зовут Семён."
    assert "Самар" in (session.memory.direct_answer("где я живу?") or "")
    session.add_turn("расскажи про виноград", "виноград — сладкая ягода и растёт гроздьями.")
    plan = build_context_prompt(
        tokenizer, session.history, "а подробнее?", 128, reserve_tokens=24,
        memory_text=session.memory.summary(), retrieved=hits,
    )
    decoded_plan = " ".join(tokenizer.vocab[i] for i in plan.prompt_ids)
    assert plan.turns_used == 1 and "виноград" in decoded_plan and "фотосинтез" in decoded_plan
    good = score_candidate("Фотосинтез превращает свет и воду в кислород и энергию растений.", "что такое фотосинтез?", hits, session.memory, [])
    bad = score_candidate("да да да да да да", "что такое фотосинтез?", hits, session.memory, [])
    assert good.score > bad.score
    print("[ok] TF-IDF поиск, память пользователя и оценка кандидатов")

    cfg = GsaiConfig(vocab_size=tokenizer.vocab_size, block_size=128, n_layer=2, n_head=4, n_embd=128, dropout=0.0)
    model = GsaiGPT(cfg)
    device = torch.device("cpu")
    torch.set_num_threads(max(1, len(os.sched_getaffinity(0))))
    assert 2 <= auto_batch_size(model, device) <= 64
    trainer = Trainer(
        model, data.train_ids, device, "cpu-test", lr=1e-3, batch=8,
        labels=data.train_labels, val_stream=data.val_ids, val_labels=data.val_labels,
        eval_every=10,
    )
    state = trainer.train(max_steps=30)
    assert state.step == 30 and state.loss_ema > 0 and state.val_loss > 0, state
    print(
        f"[ok] маскированное обучение: 30 шагов, loss={state.loss_ema:.3f}, "
        f"val={state.val_loss:.3f}, {state.tok_per_sec:.0f} ток/с"
    )

    # Прямой ответ из памяти проходит через общий контекстный движок.
    reply = generate_context_reply(
        model, tokenizer, session, "как меня зовут?", project.effective_knowledge(),
        device=device, max_new=16, candidates=1,
    )
    assert reply.mode == "memory" and "Семён" in reply.text and reply.plan.prompt_tokens > 0, reply
    print("[ok] контекстный движок следит за диалогом и помнит имя")

    tmp = Path(tempfile.mkdtemp())
    gsai_path = tmp / "testik.gsai"
    meta = export_package(
        model, tokenizer, gsai_path, name="Тестик", steps=state.step, loss=state.loss_ema,
        knowledge=[e.__dict__ for e in project.effective_knowledge()],
    )
    package = GsaiPackage(gsai_path)
    assert package.meta["params"] == meta["params"]
    assert package.meta["context"]["engine"] == "greal-context-1"
    assert len(package.knowledge) == stats["entries"]
    print(f"[ok] пакет .gsai: {gsai_path.stat().st_size} байт, знаний {len(package.knowledge)}")

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

    ids = package.generate(prompt, max_new=20, temperature=0.0, top_k=0)
    np_text = tokenizer.decode(ids)
    torch_text = chat_reply(model, tokenizer, [], "привет", temperature=0.0, top_k=0, max_new=20)
    print(f"[ok] NumPy-пакет: {np_text!r}")
    print(f"[ok] PyTorch-чат: {torch_text!r}")
    assert np_text == torch_text
    print("ALL_CORE_TESTS_OK")


if __name__ == "__main__":
    main()
