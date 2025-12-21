@echo off
echo ========================================
echo Packaging SketchJam Application
echo ========================================

echo.
echo Step 1: Cleaning previous builds...
if exist "build" rmdir /s /q build
if exist "out" rmdir /s /q out
if exist "SketchJam.jar" del /q SketchJam.jar
mkdir out
mkdir build\input

echo.
echo Step 2: Compiling Java sources...
javac -d out src\*.java
if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo.
echo Step 3: Creating JAR file...
cd out
jar cfm ..\SketchJam.jar ..\MANIFEST.MF *.class
cd ..

echo.
echo Step 4: Preparing package input...
copy SketchJam.jar build\input\
xcopy /E /I /Y soundfonts build\input\soundfonts
xcopy /E /I /Y distortion build\input\distortion
xcopy /E /I /Y symbols build\input\symbols
if exist fonts xcopy /E /I /Y fonts build\input\fonts

echo.
echo Step 5: Creating Windows application with jpackage...
jpackage ^
    --name "SketchJam" ^
    --app-version "1.0.0" ^
    --vendor "SketchJam" ^
    --description "Visual music creation tool" ^
    --input build\input ^
    --main-jar SketchJam.jar ^
    --main-class Main ^
    --type app-image ^
    --dest build\output ^
    --java-options "--add-opens java.desktop/com.sun.media.sound=ALL-UNNAMED" ^
    --icon icon\favicon.ico

if errorlevel 1 (
    echo jpackage failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo SUCCESS! Application created at:
echo build\output\SketchJam
echo ========================================
echo.
echo You can run the application from:
echo build\output\SketchJam\SketchJam.exe
echo.
pause

