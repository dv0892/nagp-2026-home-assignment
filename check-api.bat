@echo off
setlocal enabledelayedexpansion

:: --- CONFIGURATION ---
set "URL=http://8.233.80.16/api/v1/actuator/health"
set "INTERVAL=1"
:: ---------------------

echo Monitoring %URL% every %INTERVAL% seconds...
echo Press Ctrl+C to stop.
echo ===================================================

:loop
:: Use curl to fetch only the HTTP status code
for /f "delims=" %%I in ('curl -s -o /dev/null -w "%%{http_code}" "%URL%"') do set "status=%%I"

:: Get current timestamp for logging
set "timestamp=%time:~0,8%"

if "%status%"=="200" (
    echo [%date% %timestamp%] SUCCESS: API is UP (Status: %status%)
) else (
    echo [%date% %timestamp%] !!! ALERT !!! API IS DOWN (Status: %status%)
    :: Optional: Unleash a system beep to get your attention
    echo  
)

:: Wait for the specified interval before checking again
timeout /t %INTERVAL% >lbl
goto loop