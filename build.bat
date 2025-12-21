@echo off
echo ========================================
echo Building SketchJam Application
echo ========================================

echo.
echo Step 1: Cleaning previous build...
if exist "out" rmdir /s /q out
if exist "SketchJam.jar" del /q SketchJam.jar
mkdir out

echo.
echo Step 2: Compiling Java sources...
REM Compile utility package first (no external dependencies)
javac -d out src/com/sketchjam/util/*.java
if errorlevel 1 (
    echo Compilation of util package failed!
    pause
    exit /b 1
)

REM Compile element package (depends on util)
javac -cp out -d out src/com/sketchjam/element/*.java
if errorlevel 1 (
    echo Compilation of element package failed!
    pause
    exit /b 1
)

REM Compile audio package (depends on util, io)
javac -cp out -d out src/com/sketchjam/audio/*.java 2>nul

REM Compile io package (depends on element, audio)
javac -cp out -d out src/com/sketchjam/io/*.java 2>nul

REM Compile main sources (backward compatibility)
javac -cp out -d out src/*.java
if errorlevel 1 (
    echo Compilation of main sources failed!
    pause
    exit /b 1
)

echo.
echo Step 3: Creating JAR file...
cd out
jar cfm ../SketchJam.jar ../MANIFEST.MF *.class com
cd ..

echo.
echo Step 4: Done!
echo JAR file created: SketchJam.jar
echo.
pause
