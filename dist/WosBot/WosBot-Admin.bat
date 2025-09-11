@echo off
title WosBot v1.5.x_BETA - EXE by Stargaterunner - Admin Launcher
echo ========================================
echo  WosBot v1.5.x_BETA - EXE by Stargaterunner
echo  Original by CamoDev
echo ========================================
echo Starting WosBot with Administrator privileges...

:: Set Tesseract environment variable (try both locations)
set TESSDATA_PREFIX=%~dp0lib\tesseract
if not exist "%TESSDATA_PREFIX%" set TESSDATA_PREFIX=%~dp0app\lib\tesseract

:: Check if running as admin
net session >nul 2>&1
if %errorLevel% == 0 (
    echo Administrator privileges confirmed.
    echo Setting TESSDATA_PREFIX to: %TESSDATA_PREFIX%
    echo Starting WosBot - EXE by Stargaterunner...
    "%~dp0WosBot.exe"
    echo.
    echo WosBot closed. EXE created by Stargaterunner.
) else (
    echo Requesting Administrator privileges...
    powershell -Command "$env:TESSDATA_PREFIX='%~dp0app\lib\tesseract'; Start-Process '%~dp0WosBot.exe' -Verb RunAs"
)