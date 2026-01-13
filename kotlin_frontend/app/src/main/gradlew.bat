@echo off
REM Gradle wrapper shim for CI environments that (incorrectly) run from app\src\main.
REM Delegates to the real wrapper at kotlin_frontend\gradlew.bat.

setlocal enabledelayedexpansion
set "SCRIPT_DIR=%~dp0"
set "REAL_WRAPPER=%SCRIPT_DIR%..\..\..\gradlew.bat"

if not exist "%REAL_WRAPPER%" (
  echo Error: expected Gradle wrapper at: %REAL_WRAPPER% 1>&2
  echo Current directory: %CD% 1>&2
  exit /b 127
)

call "%REAL_WRAPPER%" %*
exit /b %ERRORLEVEL%
