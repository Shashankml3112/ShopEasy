@echo off
rem Double-click this file to build and start ShopEasy (needs JDK 17+ installed).
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
    echo Java JDK was not found. Install JDK 17 or newer, then try again.
    pause
    exit /b 1
)
if not exist bin mkdir bin
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d bin @sources.txt
if errorlevel 1 (
    echo Build failed.
    del sources.txt
    pause
    exit /b 1
)
del sources.txt
echo Starting ShopEasy... a browser window will open. Close this window to stop the server.
start "" http://localhost:8080
java -cp bin com.shop.ShopApp
pause
