@echo off
REM Last-resort Gradle wrapper shim for unusual CI working directories (e.g., app\src).

set ROOT_DIR=%~dp0
set CAND1=%ROOT_DIR%..\gradlew.bat
set CAND2=%ROOT_DIR%..\..\gradlew.bat
set CAND3=%ROOT_DIR%..\..\..\gradlew.bat

if exist "%CAND1%" (
  call "%CAND1%" %*
  exit /b %ERRORLEVEL%
)

if exist "%CAND2%" (
  call "%CAND2%" %*
  exit /b %ERRORLEVEL%
)

if exist "%CAND3%" (
  call "%CAND3%" %*
  exit /b %ERRORLEVEL%
)

where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  gradle %*
  exit /b %ERRORLEVEL%
)

echo ERROR: Could not locate any gradlew.bat above app\src and 'gradle' is not on PATH.
exit /b 127
