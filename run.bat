@echo off
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 goto :nojava

if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
if errorlevel 1 goto :failed
del sources.txt

java -cp out tank.TankGame
exit /b %errorlevel%

:nojava
echo Java compiler not found.
echo Install a JDK 21 or newer (for example Eclipse Temurin), then try again.
pause
exit /b 1

:failed
echo Compilation failed.
pause
exit /b 1
