@echo off
title WosBot - Administrator Mode

:: Check:: Check exit code
if %errorLevel% neq 0 (
    echo.
    echo Application exited with error code: %errorLevel%
) else (
    echo.
    echo Application finished successfully.
)

echo Closing window in 3 seconds...
timeout /t 3 /nobreak >nul running as administrator
net session >nul 2>&1
if %errorLevel% == 0 (
    echo Already running as administrator.
    goto :run_jar
) else (
    echo Requesting administrator privileges...
    echo This will open a UAC prompt.
    
    :: Re-run this batch file as administrator
    powershell -Command "Start-Process cmd -ArgumentList '/c \"%~f0\"' -Verb RunAs"
    goto :end
)

:run_jar
echo.
echo ========================================
echo Starting WosBot as Administrator
echo ========================================
echo.

:: Find the latest JAR file automatically
set "TARGET_DIR=%~dp0wos-hmi\target"
set "JAR_PATH="

:: Look for any wos-bot-*.jar file in the target directory
for /f "delims=" %%i in ('dir /b "%TARGET_DIR%\wos-bot-*.jar" 2^>nul') do (
    set "JAR_PATH=%TARGET_DIR%\%%i"
    goto :jar_found
)

:jar_found
if "%JAR_PATH%"=="" (
    echo ERROR: No wos-bot-*.jar file found in: %TARGET_DIR%
    echo.
    echo Please compile the project first with: mvn clean install package
    pause
    goto :end
)

:: Check if Java is available
java -version >nul 2>&1
if %errorLevel% neq 0 (
    echo ERROR: Java is not installed or not in PATH.
    echo Please install Java and ensure it's in your system PATH.
    pause
    goto :end
)

echo JAR Path: %JAR_PATH%
echo Java Version:
java -version
echo.
echo Starting application...
echo.

:: Run the JAR file
cd /d "%~dp0"
java -jar "%JAR_PATH%"

:: Check exit code
if %errorLevel% neq 0 (
    echo.
    echo Application exited with error code: %errorLevel%
) else (
    echo.
    echo Application finished successfully.
)

:end