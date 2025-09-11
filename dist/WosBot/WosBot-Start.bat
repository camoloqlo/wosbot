@echo off
title WosBot v1.5.x_BETA - EXE by Stargaterunner - Launcher
echo ========================================
echo  WosBot v1.5.x_BETA - EXE by Stargaterunner
echo  Original by CamoDev
echo ========================================
echo Starting WosBot...

:: Set Tesseract environment variable (try both locations)
set TESSDATA_PREFIX=%~dp0lib\tesseract
if not exist "%TESSDATA_PREFIX%" set TESSDATA_PREFIX=%~dp0app\lib\tesseract
echo Setting TESSDATA_PREFIX to: %TESSDATA_PREFIX%

:: Start WosBot
echo Starting WosBot - EXE by Stargaterunner...
"%~dp0WosBot.exe"

echo.
echo WosBot closed. EXE created by Stargaterunner.
pause