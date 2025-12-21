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
javac -d out src/*.java
if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo.
echo Step 3: Creating JAR file...
cd out
jar cfm ../SketchJam.jar ../MANIFEST.MF *.class
cd ..

echo.
echo Step 4: Done!
echo JAR file created: SketchJam.jar
echo.
pause





