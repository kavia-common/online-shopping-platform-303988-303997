@echo off
set DIR=%~dp0
set WRAPPER_JAR=%DIR%\gradle\wrapper\gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
  echo Missing gradle-wrapper.jar. Please add the standard Gradle wrapper jar to gradle/wrapper/.
  exit /b 1
)

REM Some wrapper jars may not include a Main-Class attribute in the manifest.
REM Invoke the wrapper main class explicitly.
java -cp "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
