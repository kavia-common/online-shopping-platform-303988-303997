@echo off
REM Ultra deep-path delegator wrapper under app\src\main\java\com.

set SCRIPT_DIR=%~dp0
set CANDIDATE=%SCRIPT_DIR%..\..\..\..\..\..\..\gradlew.bat

if exist "%CANDIDATE%" (
  call "%CANDIDATE%" %*
  exit /b %ERRORLEVEL%
)

echo ERROR: Could not find Gradle wrapper at %CANDIDATE%
exit /b 127
