"""
Датасеты GSYNK-AI: проект модели, генератор русских диалогов,
подхват текста из интернета и сборка обучающего потока токенов.
"""

from __future__ import annotations

import json
import random
import re
import urllib.parse
import urllib.request
from dataclasses import asdict, dataclass, field
from html.parser import HTMLParser
from pathlib import Path

from .tokenizer import Tokenizer

SIZES = {
    # имя: (d_model, слоёв, голов, контекст, макс. словарь, подпись)
    "mini": (192, 4, 4, 256, 4000, "Мини — быстрая, для первых опытов (~3 млн параметров)"),
    "lite": (256, 6, 4, 256, 8000, "Лёгкая — баланс скорости и ума (~8 млн)"),
    "mid": (384, 8, 6, 384, 12000, "Средняя — заметно умнее, учится дольше (~22 млн)"),
    "max": (512, 10, 8, 512, 16000, "Макс — самая умная, нужен запас терпения (~45 млн)"),
}

ICONS = ["🍇", "🤖", "🧠", "🦊", "🐱", "🐉", "🚀", "🎧", "📚", "⚡", "🌙", "🔥", "💎", "🦉", "🐼", "👾"]


@dataclass
class Project:
    name: str = "Моя модель"
    icon: str = "🍇"
    description: str = ""
    size: str = "lite"
    dialogues: list[list[str]] = field(default_factory=list)  # [[user, bot], ...]
    texts: list[str] = field(default_factory=list)
    lr: float = 3e-4
    batch: int = 32
    temperature: float = 0.8
    top_k: int = 40
    max_new_tokens: int = 90
    steps_done: int = 0
    best_loss: float = 0.0
    model_version: str = "1.0.0"

    def save(self, path: str | Path) -> None:
        Path(path).write_text(json.dumps(asdict(self), ensure_ascii=False, indent=1), encoding="utf-8")

    @classmethod
    def load(cls, path: str | Path) -> "Project":
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        known = {f for f in cls.__dataclass_fields__}
        return cls(**{k: v for k, v in data.items() if k in known})

    def all_texts(self) -> list[str]:
        out: list[str] = []
        for user, bot in self.dialogues:
            out.append(user)
            out.append(bot)
        out.extend(self.texts)
        return out

    def stats(self) -> dict:
        chars = sum(len(t) for t in self.all_texts())
        return {
            "dialogues": len(self.dialogues),
            "texts": len(self.texts),
            "chars": chars,
        }


def build_stream(project: Project, tokenizer: Tokenizer) -> list[int]:
    """Весь датасет одним потоком токенов: диалоги и свободные тексты."""
    stream: list[int] = []
    for user, bot in project.dialogues:
        stream.extend(tokenizer.encode_dialogue(user, bot))
    for text in project.texts:
        stream.extend(tokenizer.encode_text(text))
    return stream


def parse_dialogues_plain(text: str) -> list[list[str]]:
    """
    Разбирает вставленный пользователем текст в диалоги. Поддерживает:
      вопрос => ответ            (по одной паре на строку)
      Пользователь: ... / Модель: ...   (блоками)
    """
    pairs: list[list[str]] = []
    pending_user = ""
    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        if "=>" in line:
            q, a = line.split("=>", 1)
            q, a = q.strip(), a.strip()
            if q and a:
                pairs.append([q, a])
            continue
        low = line.lower()
        if low.startswith(("пользователь:", "user:", "вопрос:", "в:")):
            pending_user = line.split(":", 1)[1].strip()
        elif low.startswith(("модель:", "бот:", "ответ:", "о:", "ии:")) and pending_user:
            pairs.append([pending_user, line.split(":", 1)[1].strip()])
            pending_user = ""
    return pairs


# ---------------------------------------------------------------- генератор

_GREET_Q = ["привет", "здравствуй", "добрый день", "привет!", "хай", "ку", "добрый вечер", "салют"]
_GREET_A = [
    "привет! рад тебя видеть. как дела?",
    "здравствуй! чем могу помочь?",
    "привет! я на связи, спрашивай что хочешь.",
    "добрый день! расскажи, что у тебя нового?",
]
_HOW_Q = ["как дела?", "как ты?", "как настроение?", "что делаешь?", "как жизнь?"]
_HOW_A = [
    "у меня всё отлично, я только что обучился новому! а у тебя?",
    "прекрасно! сижу внутри телефона и жду твоих вопросов.",
    "всё хорошо, спасибо! а у тебя как дела?",
    "настроение боевое. готов болтать хоть весь день!",
]
_BYE_Q = ["пока", "до встречи", "бывай", "мне пора", "увидимся"]
_BYE_A = ["пока! заходи ещё поболтать.", "до встречи! буду ждать.", "бывай! хорошего дня!", "увидимся! я никуда не денусь."]
_THANKS_Q = ["спасибо", "благодарю", "спасибо большое", "ты помог"]
_THANKS_A = ["пожалуйста! всегда рад помочь.", "не за что! обращайся ещё.", "рад, что помог!"]
_YESNO = ["да", "конечно", "ага", "точно", "само собой"]
_ANIMALS = ["кошка", "собака", "лиса", "волк", "медведь", "заяц", "сова", "дельфин", "лошадь", "ёж", "панда", "тигр"]
_ANIMAL_FACTS = {
    "кошка": "кошки спят до 16 часов в сутки и отлично видят в темноте",
    "собака": "собаки чувствуют запахи в тысячи раз лучше человека",
    "лиса": "лисы очень хитрые и умеют запутывать следы",
    "волк": "волки живут стаями и общаются воем на большие расстояния",
    "медведь": "медведи зимой спят в берлоге несколько месяцев",
    "заяц": "зайцы меняют цвет шерсти зимой на белый, чтобы прятаться в снегу",
    "сова": "совы поворачивают голову на 270 градусов и летают почти бесшумно",
    "дельфин": "дельфины очень умные и общаются свистом, у каждого есть своё имя-свист",
    "лошадь": "лошади могут спать стоя и видят почти на 360 градусов вокруг",
    "ёж": "у ежа около пяти тысяч иголок, и он отлично слышит",
    "панда": "панды почти весь день едят бамбук — до 12 часов подряд",
    "тигр": "тигр — самая крупная дикая кошка, а полоски у каждого тигра уникальны",
}
_CITIES = ["Москва", "Санкт-Петербург", "Казань", "Новосибирск", "Екатеринбург", "Сочи", "Калининград", "Владивосток"]
_CITY_FACTS = {
    "Москва": "Москва — столица России, там находится Кремль и Красная площадь",
    "Санкт-Петербург": "Санкт-Петербург знаменит каналами, Эрмитажем и белыми ночами",
    "Казань": "Казань — столица Татарстана, городу больше тысячи лет",
    "Новосибирск": "Новосибирск — крупнейший город Сибири, стоит на реке Обь",
    "Екатеринбург": "Екатеринбург находится на границе Европы и Азии",
    "Сочи": "Сочи — курорт на Чёрном море, там проходила Олимпиада 2014 года",
    "Калининград": "Калининград — самый западный областной центр России, на Балтийском море",
    "Владивосток": "Владивосток — город у Тихого океана, конец Транссибирской магистрали",
}
_PLANETS = ["Меркурий", "Венера", "Земля", "Марс", "Юпитер", "Сатурн", "Уран", "Нептун"]
_PLANET_FACTS = {
    "Меркурий": "Меркурий — самая близкая к Солнцу планета, год там длится всего 88 дней",
    "Венера": "Венера — самая горячая планета, там около плюс 460 градусов",
    "Земля": "Земля — единственная известная планета с жизнью, ей около 4,5 миллиарда лет",
    "Марс": "Марс называют красной планетой из-за ржавой пыли, там есть самый большой вулкан Олимп",
    "Юпитер": "Юпитер — самая большая планета, в него поместилось бы больше тысячи Земель",
    "Сатурн": "Сатурн знаменит кольцами из льда и камней",
    "Уран": "Уран вращается лёжа на боку, как катящийся шар",
    "Нептун": "Нептун — самая далёкая планета, там дуют самые быстрые ветры",
}
_FOODS = ["пицца", "борщ", "пельмени", "мороженое", "шоколад", "яблоки", "виноград", "блины"]
_HOBBIES = ["футбол", "рисование", "музыка", "игры", "чтение", "программирование", "спорт", "танцы"]
_JOKES = [
    "почему программисты путают хэллоуин и рождество? потому что 31 октября равно 25 декабря в восьмеричной системе!",
    "что сказал ноль восьмёрке? классный у тебя пояс!",
    "почему книга по математике грустная? у неё слишком много проблем.",
    "как называется медведь без зубов? желейный медведь!",
    "почему компьютер пошёл к врачу? у него был вирус!",
]
_WEATHER_A = [
    "я сижу внутри устройства и в окно не вижу, но надеюсь, у тебя там солнце!",
    "погоду я не чувствую, зато всегда рад поболтать. у тебя тепло сегодня?",
    "мне погода нипочём — у меня всегда комнатная температура процессора.",
]


def generate_russian_dataset(model_name: str, count: int = 2000, seed: int = 42) -> tuple[list[list[str]], list[str]]:
    """Генерирует русский обучающий набор: диалоги и короткие тексты."""
    rnd = random.Random(seed)
    dialogues: list[list[str]] = []
    texts: list[str] = []
    name = model_name.strip() or "Модель"

    def add(q: str, a: str) -> None:
        dialogues.append([q, a])

    op_word = {"+": "плюс", "-": "минус", "*": "умножить на"}

    def math_q(a: int, op: str, b: int) -> str:
        return rnd.choice([
            f"сколько будет {a} {op} {b}?",
            f"{a}{op}{b}",
            f"{a} {op} {b}",
            f"посчитай {a}{op}{b}",
            f"реши пример: {a} {op} {b}",
            f"{a} {op_word[op]} {b} — сколько?",
        ])

    # обязательная мелкая арифметика «голыми» примерами: живые люди пишут «2+2», а не только «сколько будет»
    for a in range(1, 11):
        for b in range(1, 11):
            add(f"{a}+{b}", f"{a} + {b} = {a + b}.")
            add(f"{a} * {b}", f"{a} * {b} = {a * b}.")
    for a in range(2, 21):
        b = rnd.randint(1, a)
        add(f"{a}-{b}", f"{a} - {b} = {a - b}.")

    while len(dialogues) < count:
        kind = rnd.randrange(15)
        if kind == 0:
            add(rnd.choice(_GREET_Q), rnd.choice(_GREET_A))
        elif kind == 1:
            add(rnd.choice(_HOW_Q), rnd.choice(_HOW_A))
        elif kind == 2:
            add(rnd.choice(_BYE_Q), rnd.choice(_BYE_A))
        elif kind == 3:
            add(rnd.choice(_THANKS_Q), rnd.choice(_THANKS_A))
        elif kind == 4:
            q = rnd.choice(["как тебя зовут?", "ты кто?", "представься", "какое у тебя имя?"])
            a = rnd.choice([
                f"меня зовут {name}. я ии-модель, созданная в программе GSYNK-AI.",
                f"я — {name}, твоя собственная нейросеть из GSYNK-AI. приятно познакомиться!",
                f"моё имя {name}. меня обучили на компьютере моего создателя.",
            ])
            add(q, a)
        elif kind == 5:
            animal = rnd.choice(_ANIMALS)
            q = rnd.choice([f"расскажи про животное {animal}", f"что ты знаешь про {animal}?", f"факт про {animal}"])
            add(q, _ANIMAL_FACTS[animal] + ".")
        elif kind == 6:
            city = rnd.choice(_CITIES)
            q = rnd.choice([f"расскажи про город {city.lower()}", f"что за город {city.lower()}?", f"факт про {city.lower()}"])
            add(q, _CITY_FACTS[city] + ".")
        elif kind == 7:
            planet = rnd.choice(_PLANETS)
            q = rnd.choice([f"расскажи про планету {planet.lower()}", f"что такое {planet.lower()}?", f"факт про {planet.lower()}"])
            add(q, _PLANET_FACTS[planet] + ".")
        elif kind == 8:
            a, b = rnd.randint(2, 99), rnd.randint(2, 99)
            op = rnd.choice(["+", "-", "*"])
            if op == "+":
                ans = a + b
            elif op == "-":
                ans = a - b
            else:
                a, b = rnd.randint(2, 12), rnd.randint(2, 12)
                ans = a * b
            add(math_q(a, op, b), f"{a} {op} {b} = {ans}.")
        elif kind == 9:
            food = rnd.choice(_FOODS)
            add(f"ты любишь {food}?", rnd.choice([
                f"я не ем, но {food} звучит вкусно! а ты любишь?",
                f"если бы я мог есть, {food} был бы в моём меню каждый день.",
                f"про {food} я только читал в своём датасете. расскажи, вкусно?",
            ]))
        elif kind == 10:
            hobby = rnd.choice(_HOBBIES)
            add(f"что скажешь про {hobby}?", rnd.choice([
                f"{hobby} — отличное занятие! главное — заниматься регулярно и с удовольствием.",
                f"мне нравится тема «{hobby}». расскажи, как давно ты этим увлекаешься?",
                f"про {hobby} можно говорить долго. с чего ты начинал?",
            ]))
        elif kind == 11:
            add(rnd.choice(["расскажи шутку", "пошути", "расскажи анекдот", "развесели меня"]), rnd.choice(_JOKES))
        elif kind == 12:
            add(rnd.choice(["какая сегодня погода?", "что там с погодой?", "тепло сегодня?"]), rnd.choice(_WEATHER_A))
        elif kind == 13:
            a, b = rnd.randint(1, 20), rnd.randint(1, 20)
            op = rnd.choice(["+", "-", "*"])
            if op == "+":
                ans = a + b
            elif op == "-":
                ans = a - b
            else:
                a, b = rnd.randint(1, 10), rnd.randint(1, 10)
                ans = a * b
            add(math_q(a, op, b), f"{a} {op} {b} = {ans}.")
        else:
            q = rnd.choice(["что ты умеешь?", "кто тебя создал?", "ты настоящий ии?", "ты умный?"])
            a = {
                "что ты умеешь?": "я умею болтать, отвечать на простые вопросы, считать и рассказывать факты. чем больше меня обучают в GSYNK-AI, тем я умнее!",
                "кто тебя создал?": "меня создали и обучили в программе GSYNK-AI на обычном домашнем компьютере. я настоящая нейросеть-трансформер!",
                "ты настоящий ии?": "да, я настоящая нейронная сеть. маленькая, зато своя: меня обучили с нуля на датасете моего создателя.",
                "ты умный?": "я стараюсь! мой ум зависит от датасета и времени обучения. задай вопрос — проверим.",
            }[q]
            add(q, a)

    facts = list(_ANIMAL_FACTS.values()) + list(_CITY_FACTS.values()) + list(_PLANET_FACTS.values())
    for i in range(0, len(facts), 3):
        chunk = ". ".join(facts[i:i + 3]) + "."
        texts.append(chunk)
    texts.append(
        f"{name} — это ии-модель, созданная в программе GSYNK-AI. модель обучается на компьютере "
        "своего создателя, а потом работает прямо на телефоне в приложении GSYNK без интернета."
    )
    return dialogues[:count], texts


# ---------------------------------------------------------------- интернет

class _TextStripper(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.parts: list[str] = []
        self._skip = 0

    def handle_starttag(self, tag: str, attrs) -> None:
        if tag in ("script", "style", "noscript"):
            self._skip += 1
        if tag in ("p", "br", "h1", "h2", "h3", "li"):
            self.parts.append("\n")

    def handle_endtag(self, tag: str) -> None:
        if tag in ("script", "style", "noscript") and self._skip:
            self._skip -= 1

    def handle_data(self, data: str) -> None:
        if not self._skip:
            self.parts.append(data)


def _http_get(url: str, timeout: int = 30) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "GSYNK-AI/1.0 (dataset fetcher)"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


def fetch_url_text(url: str, max_chars: int = 200_000) -> str:
    """Скачивает страницу и вытаскивает читаемый текст абзацами."""
    raw = _http_get(url).decode("utf-8", "ignore")
    stripper = _TextStripper()
    stripper.feed(raw)
    text = "".join(stripper.parts)
    lines = [re.sub(r"\s+", " ", ln).strip() for ln in text.splitlines()]
    paras = [ln for ln in lines if len(ln) > 60]
    return "\n\n".join(paras)[:max_chars]


def fetch_wikipedia_ru(title: str, max_chars: int = 120_000) -> str:
    """Текст статьи русской Википедии по названию (официальный API)."""
    api = "https://ru.wikipedia.org/w/api.php?" + urllib.parse.urlencode({
        "action": "query", "prop": "extracts", "explaintext": "1",
        "titles": title, "format": "json", "redirects": "1",
    })
    data = json.loads(_http_get(api).decode("utf-8", "ignore"))
    pages = (data.get("query") or {}).get("pages") or {}
    for page in pages.values():
        extract = (page.get("extract") or "").strip()
        if extract:
            paras = [p.strip() for p in extract.split("\n") if len(p.strip()) > 60]
            return "\n\n".join(paras)[:max_chars]
    return ""


def split_paragraphs(text: str, max_len: int = 900) -> list[str]:
    out: list[str] = []
    for para in re.split(r"\n\s*\n", text):
        para = " ".join(para.split())
        while len(para) > max_len:
            cut = para.rfind(". ", 0, max_len)
            if cut < 200:
                cut = max_len
            out.append(para[:cut + 1].strip())
            para = para[cut + 1:].strip()
        if len(para) > 40:
            out.append(para)
    return out
