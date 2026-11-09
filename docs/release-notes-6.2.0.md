# GSYNK 6.2.0 EXP — GREAL 3D вернулся

**versionCode:** 40 · **versionName:** 6.2.0 · **дизайн:** GREAL 5.7.2 (3D-сферы)

## Главное

Пользователь уточнил, что «старый дизайн с приложениями» — это **не** плоская лента 5.6.2,
а объёмный GREAL, где приложения были **кружочками**: слоёные 3D-сферы с орбитальным
кольцом, иконкой в центре и подписью снизу. Возвращаем именно его, беря за основу
последний такой релиз **5.7.2**.

- Из `v5.7.2` восстановлены `GrealDesign.kt`, `GrealInfinite.kt`, `GrealSettingsScreen.kt`,
  `StartScreen.kt`, `Components.kt`, `Effects.kt`, `Gioui.kt`, `GiouiScreens.kt`,
  `HomeTiles.kt`, `MainScreen.kt`, `SettingsScreen.kt`, `Catalog.kt`, `Skin.kt` и тема.
- `Gioui.UI_VERSION` снова `GREAL — Grape Realism Engine`.
- Работают GPU-аврора (AGSL на Android 13+), физические текстуры, фаски, активные 3D-углы,
  `grealDepthTransform`, бесконечные 3D-плитки, антистресс GREAL и «жидкие» окна.
- Удалены все файлы Script UI / Blades / XMB.

## Расписание переехало в GAUTO

Из GSYNK удалены как пользовательская функция:

- плитки `расписание`, `новое правило`, `статус расписания`, `автоматизации жизни`;
- группа `Catalog.Group.SCHEDULE`;
- экраны `ScheduleScreen.kt`, `RuleEditorSheet.kt`, `LifeAutomationsScreen.kt`;
- маршруты `panel:schedule`, `panel:schedule_new`, `panel:lifeauto`,соответствующие строки `homeTileLines`;
- кнопка «Синхронизировать» в настройках и передача правил через NFC;
- `ApplyService`, `ApplyReceiver`, `BootReceiver` и разрешения `RECEIVE_BOOT_COMPLETED`,
  `SCHEDULE_EXACT_ALARM` из манифеста; карточка «Точные будильники» из панели доступов;
- автопланирование правил при старте `MainViewModel`.

Низкоуровневые `RuleEngine.setBrightnessPercent/setVolumePercent` оставлены как внутренние
помощники для сцен и железных инструментов — отдельного расписания они больше не образуют.
Полноценные правила, готовые наборы, блокировка YouTube, ч/б экран, привычки, фокус,
геймификация и OTA теперь в **GAUTO** (`GrapeGG-developer/GAUTO`).

## Совместимость

- Только портрет, поиска в «Пуске» нет, все прочие маршруты и формат `.gsai` сохранены.
- GSYNK-AI для Windows остаётся отдельной версией 1.1.1; служебные prerelease не смещают Android latest.
- 6.0.0 Script UI и 6.1.0 (классика 5.6.2) остаются в истории релизов, но не являются текущими.
