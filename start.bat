@echo off
setlocal
cd /d "%~dp0"
call mvnw.cmd -DskipTests package
if errorlevel 1 exit /b %errorlevel%
start "" "http://localhost:8080"
java -jar target\order-automation-0.1.0-SNAPSHOT.jar
