@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo SketchJam Windows Executable Packager
echo ============================================================
echo.

:: Check for jpackage
where jpackage >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: jpackage not found. Please install JDK 14 or later.
    echo Download from: https://adoptium.net/
    pause
    exit /b 1
)

:: Set paths
set PROJECT_DIR=%~dp0
set SRC_DIR=%PROJECT_DIR%src
set OUT_DIR=%PROJECT_DIR%out
set DIST_DIR=%PROJECT_DIR%dist
set JAR_FILE=%PROJECT_DIR%SketchJam.jar
set ICON_FILE=%PROJECT_DIR%icon\favicon.ico

:: Clean and create output directories
echo [1/6] Cleaning build directories...
if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%OUT_DIR%"
mkdir "%DIST_DIR%"

:: Create list of Java files
echo [2/6] Compiling Java sources...
set JAVA_FILES=
for %%f in ("%SRC_DIR%\*.java") do (
    set JAVA_FILES=!JAVA_FILES! "%%f"
)

:: Compile Java sources
javac -d "%OUT_DIR%" --add-opens java.desktop/com.sun.media.sound=ALL-UNNAMED %JAVA_FILES%
if %errorlevel% neq 0 (
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)
echo     Compiled successfully.

:: Create JAR with manifest
echo [3/6] Creating JAR file...
cd /d "%OUT_DIR%"
jar cfm "%JAR_FILE%" "%PROJECT_DIR%MANIFEST.MF" *.class
if %errorlevel% neq 0 (
    echo ERROR: JAR creation failed!
    cd /d "%PROJECT_DIR%"
    pause
    exit /b 1
)
cd /d "%PROJECT_DIR%"
echo     JAR created: SketchJam.jar

:: Prepare input directory for jpackage (JAR + resources)
echo [4/6] Preparing application bundle...
set INPUT_DIR=%PROJECT_DIR%jpackage-input
if exist "%INPUT_DIR%" rmdir /s /q "%INPUT_DIR%"
mkdir "%INPUT_DIR%"

:: Copy JAR
copy "%JAR_FILE%" "%INPUT_DIR%\" >nul

:: Create jpackage app-image
echo [5/6] Creating Windows executable with bundled JRE...
echo     This may take a few minutes...

:: Check if icon exists
set ICON_PARAM=
if exist "%ICON_FILE%" (
    set ICON_PARAM=--icon "%ICON_FILE%"
    echo     Using icon: %ICON_FILE%
)

:: Run jpackage
jpackage ^
    --type app-image ^
    --name SketchJam ^
    --input "%INPUT_DIR%" ^
    --main-jar SketchJam.jar ^
    --main-class Main ^
    --dest "%DIST_DIR%" ^
    --java-options "--add-opens=java.desktop/com.sun.media.sound=ALL-UNNAMED" ^
    --java-options "-Xmx512m" ^
    %ICON_PARAM% ^
    --vendor "SketchJam" ^
    --app-version "1.0.0" ^
    --description "Visual Music Creation Tool"

if %errorlevel% neq 0 (
    echo ERROR: jpackage failed!
    pause
    exit /b 1
)

:: Copy resource folders to the app directory
echo [6/6] Copying resources...
set APP_DIR=%DIST_DIR%\SketchJam

:: Copy soundfonts
if exist "%PROJECT_DIR%soundfonts" (
    xcopy /E /I /Y "%PROJECT_DIR%soundfonts" "%APP_DIR%\app\soundfonts" >nul
    echo     Copied soundfonts/
)

:: Copy distortion
if exist "%PROJECT_DIR%distortion" (
    xcopy /E /I /Y "%PROJECT_DIR%distortion" "%APP_DIR%\app\distortion" >nul
    echo     Copied distortion/
)

:: Copy symbols
if exist "%PROJECT_DIR%symbols" (
    xcopy /E /I /Y "%PROJECT_DIR%symbols" "%APP_DIR%\app\symbols" >nul
    echo     Copied symbols/
)

:: Copy icon folder
if exist "%PROJECT_DIR%icon" (
    xcopy /E /I /Y "%PROJECT_DIR%icon" "%APP_DIR%\app\icon" >nul
    echo     Copied icon/
)

:: Copy fonts if they exist
if exist "%PROJECT_DIR%fonts" (
    xcopy /E /I /Y "%PROJECT_DIR%fonts" "%APP_DIR%\app\fonts" >nul
    echo     Copied fonts/
)

:: Cleanup temp input directory
rmdir /s /q "%INPUT_DIR%"

:: Create ZIP for distribution
echo.
echo Creating ZIP distribution...
set ZIP_FILE=%PROJECT_DIR%SketchJam-Windows.zip
if exist "%ZIP_FILE%" del "%ZIP_FILE%"

:: Use PowerShell to create ZIP
powershell -Command "Compress-Archive -Path '%APP_DIR%' -DestinationPath '%ZIP_FILE%' -Force"
if %errorlevel% equ 0 (
    echo     Created: SketchJam-Windows.zip
    :: Get file size
    for %%A in ("%ZIP_FILE%") do set ZIP_SIZE=%%~zA
    set /a ZIP_SIZE_MB=%ZIP_SIZE% / 1048576
    echo     Size: ~!ZIP_SIZE_MB! MB
) else (
    echo     Warning: Could not create ZIP file
)

echo.
echo ============================================================
echo SUCCESS! Executable created at:
echo     %APP_DIR%\SketchJam.exe
echo.
echo Distribution options:
echo   1. Folder: dist\SketchJam\ (copy entire folder)
echo   2. ZIP:    SketchJam-Windows.zip (single file)
echo.
echo No Java installation required on target machine.
echo ============================================================
echo.

pause
