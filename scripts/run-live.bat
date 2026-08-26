@echo off
REM Launches the backend in LIVE (real trading) mode.
REM Loads .env.live from the repo root, then runs gradlew.bat bootRun.
REM
REM Real orders are placed against a real account. Requires an explicit
REM confirmation before launch to prevent accidental starts
REM (bypass with CONFIRM_LIVE=1 in non-interactive environments).
REM
REM NOTE: kept ASCII-only on purpose - Korean text in a .bat file breaks cmd's
REM parser under the CP949 console codepage (common on Korean Windows). See
REM README for the bilingual explanation of what this script does.
REM
REM Usage: scripts\run-live.bat
REM Prerequisite: copy .env.live.example to .env.live and fill in real LIVE Kiwoom credentials first.

chcp 65001 >nul
setlocal
cd /d "%~dp0.."

if not exist ".env.live" (
    echo [ERROR] .env.live not found. Copy .env.live.example and fill in the values first.
    exit /b 1
)

for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env.live") do (
    if not "%%A"=="" set "%%A=%%B"
)

if not "%TRADING_MODE%"=="LIVE" (
    echo [ERROR] TRADING_MODE in .env.live is not LIVE ^(current: %TRADING_MODE%^). Check the file.
    exit /b 1
)

if "%CONFIRM_LIVE%"=="1" goto :run

echo WARNING: About to start in LIVE mode - real orders will be placed against account %KIWOOM_ACCOUNT_NO%.
set /p CONFIRM=Type 'LIVE' to continue:
if "%CONFIRM%"=="LIVE" goto :run
echo Cancelled.
exit /b 1

:run
echo Starting the backend in LIVE mode ^(DB=%DB_HOST%:%DB_PORT%/%DB_NAME%^)
cd backend
call .\gradlew.bat bootRun
