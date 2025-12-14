@echo off
cd /d "%~dp0"
echo Compiling...
javac -d out src/*.java
if %errorlevel% neq 0 (
    echo Compilation failed!
    pause
    exit /b 1
)
echo Running SketchJam with overdrive DSP...
java --add-opens java.desktop/com.sun.media.sound=ALL-UNNAMED -cp out Main
pause

