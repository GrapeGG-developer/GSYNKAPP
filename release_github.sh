#!/usr/bin/env bash
# Публикация релиза GSYNK на GitHub с прикреплением APK.
#
# Использование:
#   GH_TOKEN=<токен> bash release_github.sh <тег> <путь-к-apk> "<название>" <файл-описания> [<коммит>]
#
# Пример:
#   GH_TOKEN=ghp_... bash release_github.sh v2.0.1 apk/GSYNK-v2.0.1-debug.apk \
#       "GSYNK 2.0.1" docs/release-notes-2.0.1.md 305fc2c
#
# Токен передаётся только через переменную окружения и нигде не сохраняется.
set -e

REPO="GrapeGG-developer/GSYNKAPP"
TAG="$1"; APK="$2"; TITLE="$3"; NOTES="$4"; TARGET="$5"

if [ -z "$TAG" ] || [ -z "$APK" ] || [ -z "$TITLE" ] || [ -z "$NOTES" ]; then
  echo "Использование: GH_TOKEN=... bash release_github.sh <тег> <apk> \"<название>\" <описание.md> [<коммит>]"
  exit 1
fi
if [ -z "$GH_TOKEN" ]; then
  echo "Нужен токен: GH_TOKEN=<токен> bash release_github.sh ..."
  exit 1
fi

API="https://api.github.com/repos/$REPO"
AUTH=(-H "Authorization: token $GH_TOKEN" -H "Accept: application/vnd.github+json")

echo "→ Создаю релиз $TAG…"
BODY=$(python3 - "$TAG" "$TITLE" "$NOTES" "$TARGET" <<'PY'
import json, sys, pathlib
tag, title, notes, target = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
payload = {
    "tag_name": tag,
    "name": title,
    "body": pathlib.Path(notes).read_text(),
    "draft": False,
    "prerelease": False,
}
if target:
    payload["target_commitish"] = target
print(json.dumps(payload))
PY
)

RESPONSE=$(curl -s -X POST "${AUTH[@]}" -H "Content-Type: application/json" \
  -d "$BODY" "$API/releases")

RELEASE_ID=$(printf '%s' "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('id',''))")
if [ -z "$RELEASE_ID" ]; then
  echo "Релиз уже существует — беру его id:"
  RELEASE_ID=$(curl -s "${AUTH[@]}" "$API/releases/tags/$TAG" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))")
fi

if [ -z "$RELEASE_ID" ]; then
  echo "Не удалось создать релиз. Ответ GitHub:"
  printf '%s\n' "$RESPONSE" | head -20
  exit 1
fi
echo "  релиз id: $RELEASE_ID"

NAME=$(basename "$APK")
echo "→ Загружаю $NAME ($(du -h "$APK" | cut -f1))…"
UP=$(curl -s -X POST "${AUTH[@]}" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary "@$APK" \
  "https://uploads.github.com/repos/$REPO/releases/$RELEASE_ID/assets?name=$NAME")

printf '%s' "$UP" | python3 -c "
import sys, json
d = json.load(sys.stdin)
if 'browser_download_url' in d:
    print('  APK прикреплён:', d['browser_download_url'], f\"({d.get('size',0)} байт)\")
else:
    print('  ошибка загрузки:', d.get('message', d))
"

echo "Готово: https://github.com/$REPO/releases/tag/$TAG"
