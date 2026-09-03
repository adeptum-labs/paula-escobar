@echo off
rem Compiles the miniaudio shim into the static library native-image links.
setlocal
set "out=%~1"
set "src=%~dp0..\src\main\c"
if not exist "%out%" mkdir "%out%"
where cl >nul 2>nul || call :vcvars || exit /b 1
cl /nologo /O2 /c "%src%\paulaaudio.c" /Fo"%out%\paulaaudio.obj" || exit /b 1
lib /nologo /OUT:"%out%\paulaaudio.lib" "%out%\paulaaudio.obj" || exit /b 1
exit /b 0

:vcvars
set "vswhere=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
for /f "usebackq delims=" %%i in (`"%vswhere%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "vsroot=%%i"
if not defined vsroot exit /b 1
call "%vsroot%\VC\Auxiliary\Build\vcvarsall.bat" x64 >nul
exit /b %errorlevel%
