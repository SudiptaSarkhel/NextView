@echo off
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
  echo ERROR: JDK not found. Install JDK 17 or newer and add its bin folder to PATH.
  pause
  exit /b 1
)
where jpackage >nul 2>nul
if errorlevel 1 (
  echo ERROR: jpackage not found. Install a full JDK 17 or newer.
  pause
  exit /b 1
)

if exist out rmdir /s /q out
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir out
mkdir build
mkdir dist
echo Compiling NestView...
javac -encoding UTF-8 -d out src\NestView.java
if errorlevel 1 goto failed
jar --create --file build\NestView.jar --main-class NestView -C out .
if errorlevel 1 goto failed
echo Creating Windows installer...
jpackage --type exe --name NestView --app-version 3.0.0 --vendor "Student Project" --description "A simple desktop file explorer" --input build --main-jar NestView.jar --main-class NestView --icon assets\nestview.ico --dest dist --win-menu --win-shortcut
if errorlevel 1 goto failed

echo.
echo Success! Installer created in dist\NestView-3.0.0.exe
pause
exit /b 0

:failed
echo.
echo Build failed. Check that a full JDK is installed and that WiX Toolset is available if jpackage requests it.
pause
exit /b 1
