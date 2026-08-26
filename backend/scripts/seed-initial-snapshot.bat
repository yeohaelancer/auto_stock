@echo off
REM Seeds one initial account snapshot on deployment day 1 (Windows cmd/batch
REM version - same logic as seed-initial-snapshot.sh). See seed-initial-snapshot.sql
REM for the background on why this is needed.
REM
REM MOCK mode only. Aborts immediately if TRADING_MODE=LIVE.
REM
REM NOTE: kept ASCII-only on purpose - Korean text in a .bat file breaks cmd's
REM parser under the CP949 console codepage (common on Korean Windows).
REM
REM Usage: backend\scripts\seed-initial-snapshot.bat [env file path, default ..\..\.env.mock]

chcp 65001 >nul
setlocal
cd /d "%~dp0"

set "ENV_FILE=%~1"
if "%ENV_FILE%"=="" set "ENV_FILE=..\..\.env.mock"

if not exist "%ENV_FILE%" (
    echo [ERROR] %ENV_FILE% not found.
    exit /b 1
)

for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ENV_FILE%") do (
    if not "%%A"=="" set "%%A=%%B"
)

if "%TRADING_MODE%"=="LIVE" (
    echo [ERROR] Cannot run this script while TRADING_MODE=LIVE.
    echo         LIVE balance must only come from the real lookup APIs - never seeded.
    exit /b 1
)

if "%DB_PASSWORD%"=="" (
    echo [ERROR] DB_PASSWORD is not set. Check %ENV_FILE%.
    exit /b 1
)

if "%DB_HOST%"=="" set "DB_HOST=localhost"
if "%DB_PORT%"=="" set "DB_PORT=3306"
if "%DB_NAME%"=="" set "DB_NAME=JDWORKS"
if "%DB_USER%"=="" set "DB_USER=jdwadmin"
set "ACCOUNT_ID=%KIWOOM_ACCOUNT_NO%"
if "%TRADING_MODE%"=="" set "TRADING_MODE=MOCK"
if "%MOCK_INITIAL_CAPITAL%"=="" set "MOCK_INITIAL_CAPITAL=10000000"

echo Seeding initial account snapshot: account=%ACCOUNT_ID%, mode=%TRADING_MODE%, capital=%MOCK_INITIAL_CAPITAL%

REM The mysql client has no psql-style ":var" file substitution, so we prepend
REM session variables (SET @var=...) to a temp SQL file, then append
REM seed-initial-snapshot.sql's body and run it as one script.
set "TMP_SQL=%TEMP%\seed-initial-snapshot-%RANDOM%.sql"
> "%TMP_SQL%" echo SET @account_id = '%ACCOUNT_ID%';
>> "%TMP_SQL%" echo SET @trading_mode = '%TRADING_MODE%';
>> "%TMP_SQL%" echo SET @initial_capital = %MOCK_INITIAL_CAPITAL%;
type seed-initial-snapshot.sql >> "%TMP_SQL%"

set "MYSQL_PWD=%DB_PASSWORD%"
mysql -h %DB_HOST% -P %DB_PORT% -u %DB_USER% %DB_NAME% < "%TMP_SQL%"
set "RC=%ERRORLEVEL%"
del "%TMP_SQL%" >nul 2>&1

if not "%RC%"=="0" (
    echo [ERROR] Seeding failed ^(exit code %RC%^).
    exit /b %RC%
)

echo Done - intradaySignalScan^(^) will no longer be skipped from today.
