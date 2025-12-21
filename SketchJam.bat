@echo off
cd /d "%~dp0"
if not exist "out" mkdir out
javac -d out src/*.java 2>nul
start "" javaw --add-opens java.desktop/com.sun.media.sound=ALL-UNNAMED -cp out Main





