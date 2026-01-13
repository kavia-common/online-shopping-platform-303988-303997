@echo off
rem Forward to the kotlin_frontend wrapper.
set SCRIPT_DIR=%~dp0
call "%SCRIPT_DIR%\..\gradlew.bat" %*
