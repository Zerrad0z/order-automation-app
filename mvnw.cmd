@echo off
setlocal
set "MAVEN_PROJECTBASEDIR=%~dp0"
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"
if not defined JAVA_HOME (
  set "JAVACMD=java"
) else (
  set "JAVACMD=%JAVA_HOME%\bin\java.exe"
)
"%JAVACMD%" -classpath "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %*
set "WRAPPER_EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %WRAPPER_EXIT_CODE%
