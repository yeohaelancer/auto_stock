@echo off
REM Launches the backend in MOCK (paper trading) mode.
REM Loads .env.mock from the repo root, then runs gradlew.bat bootRun.
REM
REM NOTE: kept ASCII-only on purpose - Korean text in a .bat file breaks cmd's
REM parser under the CP949 console codepage (common on Korean Windows). See
REM README for the bilingual explanation of what this script does.
REM
REM Usage: scripts\run-mock.bat
REM Prerequisite: copy .env.mock.example to .env.mock and fill in the values first.

chcp 65001 >nul
setlocal
cd /d "%~dp0.."

if not exist ".env.mock" (
    echo [ERROR] .env.mock not found. Copy .env.mock.example and fill in the values first.
    exit /b 1
)

for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env.mock") do (
    if not "%%A"=="" set "%%A=%%B"
)

if not "%TRADING_MODE%"=="MOCK" (
    echo [ERROR] TRADING_MODE in .env.mock is not MOCK ^(current: %TRADING_MODE%^). Check the file.
    exit /b 1
)

echo Starting the backend in MOCK mode ^(DB=%DB_HOST%:%DB_PORT%/%DB_NAME%^)
cd backend
call .\gradlew.bat bootRun
