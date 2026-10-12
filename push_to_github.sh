#!/usr/bin/env bash
# Публикация GSYNK на GitHub одной командой.
# Использование: bash push_to_github.sh ВАШ_ЛОГИН/GSYNK [ветка]
set -e

REPO="$1"
BRANCH="${2:-main}"

if [ -z "$REPO" ]; then
  echo "Укажите репозиторий: bash push_to_github.sh ВАШ_ЛОГИН/GSYNK"
  exit 1
fi

cd "$(dirname "$0")"

if [ ! -d .git ]; then
  git init
fi

git add -A
if ! git diff --cached --quiet; then
  git commit -m "GSYNK 2.0.0: мультитул — остров, NFC, игры, задачи, OTA"
fi

if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "https://github.com/$REPO.git"
else
  git remote add origin "https://github.com/$REPO.git"
fi

git branch -M "$BRANCH"
echo "Отправляю в https://github.com/$REPO (ветка $BRANCH)…"
echo "Если попросят пароль — вставьте Personal Access Token (галочка repo)."
git push -u origin "$BRANCH"
echo "Готово: https://github.com/$REPO"
