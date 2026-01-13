@echo off
rem Forward to the workspace root Gradle wrapper.
set SCRIPT_DIR=%~dp0
call "%SCRIPT_DIR%\..\gradlew.bat" %*
