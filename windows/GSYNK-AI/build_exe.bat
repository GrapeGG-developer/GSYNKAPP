@echo off
cd /d "%~dp0"
title Сборка GSYNK-AI.exe

rem --- ищем Python ---
set "PY="
py -3 --version >nul 2>nul && set "PY=py -3"
if not defined PY (
  python --version >nul 2>nul && set "PY=python"
)
if not defined PY (
  echo [GSYNK-AI] Python не найден! Поставьте Python 3.10+ с python.org и отметьте "Add to PATH".
  pause
  exit /b 1
)

rem --- если библиотек еще нет, ставим сами (run.bat заранее не нужен) ---
if not exist ".venv\Scripts\python.exe" (
  echo === Ставлю библиотеки GSYNK-AI, это займет несколько минут ===
  %PY% -m venv .venv || goto :fail
  ".venv\Scripts\python.exe" -m pip install --upgrade pip || goto :fail
  ".venv\Scripts\python.exe" -m pip install torch --index-url https://download.pytorch.org/whl/cpu || goto :fail
  ".venv\Scripts\python.exe" -m pip install -r requirements.txt || goto :fail
  ".venv\Scripts\python.exe" -m pip install torch-directml || echo DirectML не встал, не страшно.
)

".venv\Scripts\python.exe" -m pip install pyinstaller || goto :fail
".venv\Scripts\python.exe" -m PyInstaller --noconfirm --onefile --windowed --name GSYNK-AI --icon icon.ico app.py || goto :fail

echo.
echo === Готово: dist\GSYNK-AI.exe ===
echo Запускайте exe из папки dist. Автообновление внутри программы само заменит его при выходе новой версии.
pause
exit /b 0

:fail
echo.
echo [GSYNK-AI] Сборка не удалась. Проверьте интернет и попробуйте снова.
pause
exit /b 1
