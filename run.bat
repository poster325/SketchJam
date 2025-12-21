@echo off
cd /d "%~dp0"
java --add-opens java.desktop/com.sun.media.sound=ALL-UNNAMED -cp "out;out/com/sketchjam" Main
pause
