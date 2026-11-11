@echo off
echo ====================================================
echo Starting PostgreSQL 18 Windows Service...
echo ====================================================
net start postgresql-x64-18
if %ERRORLEVEL% EQU 0 (
    echo.
    echo PostgreSQL Service is now RUNNING!
    echo You can now refresh or connect in pgAdmin 4.
) else (
    echo.
    echo Failed to start service automatically.
    echo Please make sure you Right-Click this file and choose:
    echo "Run as administrator"
)
pause

