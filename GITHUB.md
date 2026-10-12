# Как выложить GSYNK на GitHub

Репозиторий уже инициализирован локально (`git init` + первый коммит). Осталось
создать пустой репозиторий на GitHub и отправить туда код.

## Вариант 1. Через веб-интерфейс (самый простой)

1. Откройте https://github.com/new
2. Имя: `GSYNK` (или любое), видимость — **Public** (нужно для бесплатных релизов
   с APK) → **Create repository**. Не добавляйте README/LICENSE — они уже есть.
3. Скопируйте адрес вида `https://github.com/ВАШ_ЛОГИН/GSYNK.git` и выполните:

```bash
cd GSYNK
git remote add origin https://github.com/ВАШ_ЛОГИН/GSYNK.git
git branch -M main
git push -u origin main
```

Git попросит логин и **токен** вместо пароля:
GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) →
Generate new token → выберите галочку `repo` → скопируйте токен и вставьте его вместо пароля.

## Вариант 2. Через GitHub CLI (gh)

```bash
gh auth login
cd GSYNK
gh repo create GSYNK --public --source=. --push
```

## Вариант 3. Через встроенный скрипт

```bash
cd GSYNK
bash push_to_github.sh ВАШ_ЛОГИН/GSYNK
```

Скрипт сам добавит remote `origin` и запушит ветку `main`.

---

## Как выпускать релиз (для OTA-обновления)

Обновление внутри приложения работает через **Releases** репозитория:

1. Поднимите версию в `app/build.gradle.kts` (`versionCode` +1, `versionName`).
2. Соберите APK: `bash check.sh` (или `bash gradlew assembleDebug`).
3. GitHub → Releases → **Draft a new release**:
   - Tag: `v2.0.1` (тег обязан начинаться с `v` или с цифры);
   - прикрепите файл `app/build/outputs/apk/debug/app-debug.apk`
     (можно переименовать в `GSYNK-v2.0.1.apk`);
   - текст релиза попадёт в GSYNK как «что нового» — пишите по-русски.
4. Опубликуйте релиз.
5. В приложении: **Инструменты → Обновление GSYNK**, укажите `ВАШ_ЛОГИН/GSYNK`,
   нажмите «Проверить обновление» → «Скачать и установить».

Совет: чтобы протестировать OTA, опубликуйте релиз `v2.0.0` с приложенным APK,
установите на телефон `apk/GSYNK-v1.2.0-debug.apk`, выпустите релиз `v2.0.1` —
и обновитесь изнутри приложения.

В папке `apk/` лежат обе сборки: `GSYNK-v1.2.0-debug.apk` (старая) и
`GSYNK-v2.0.0-debug.apk` (текущая) — удобно для проверки обновления.

---

## Готовый сценарий сборки на GitHub

Файл `.github/workflows/build.yml` собирает APK на каждый тег и прикрепляет его
к релизу: ничего не нужно делать вручную, кроме `git tag` и `git push`.

```bash
git tag v2.0.1
git push origin v2.0.1
```

Через несколько минут в Releases появится APK, который GSYNK увидит изнутри.
