@echo off
cd /d "%~dp0"
title GSYNK-AI

rem --- ищем Python ---
set "PY="
py -3 --version >nul 2>nul && set "PY=py -3"
if not defined PY (
  python --version >nul 2>nul && set "PY=python"
)
if not defined PY (
  echo [GSYNK-AI] Python не найден!
  echo Поставьте Python 3.10 или новее с сайта python.org
  echo и при установке обязательно отметьте пункт "Add to PATH".
  pause
  exit /b 1
)

if not exist ".venv\Scripts\python.exe" (
  echo === GSYNK-AI: первая установка, качаю библиотеки. Это один раз и займет несколько минут ===
  %PY% -m venv .venv || goto :fail
  ".venv\Scripts\python.exe" -m pip install --upgrade pip || goto :fail
  ".venv\Scripts\python.exe" -m pip install torch --index-url https://download.pytorch.org/whl/cpu || goto :fail
  ".venv\Scripts\python.exe" -m pip install -r requirements.txt || goto :fail
  echo === Пробую включить видеокарту через DirectML (это необязательно) ===
  ".venv\Scripts\python.exe" -m pip install torch-directml || echo DirectML не встал, не страшно: обучение пойдет на процессоре на 100 процентов.
  echo === Установка готова, запускаю GSYNK-AI ===
)

start "" ".venv\Scripts\pythonw.exe" app.py
exit /b 0

:fail
echo.
echo [GSYNK-AI] Установка не удалась. Проверьте интернет, закройте это окно и запустите run.bat еще раз.
pause
exit /b 1
