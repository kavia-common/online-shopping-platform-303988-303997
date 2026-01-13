@echo off
setlocal

REM Gradle wrapper shim for repo\app workdir (if CI runs from this module).
if exist "%~dp0..\kotlin_frontend\gradlew.bat" (
  call "%~dp0..\kotlin_frontend\gradlew.bat" %*
  exit /b %ERRORLEVEL%
)
if exist "%~dp0..\gradlew.bat" (
  call "%~dp0..\gradlew.bat" %*
  exit /b %ERRORLEVEL%
)

echo ERROR: Could not find kotlin_frontend\gradlew.bat or ..\gradlew.bat from repo\app shim. 1>&2
exit /b 127
