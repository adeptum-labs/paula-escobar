@echo off
rem
rem Paula Escobar is a terminal music player for demoscene and chip music.
rem Copyright © 2026 Adam Waldenberg, Adeptum AB, Org.nr 559494-1824.
rem
rem This program is free software: you can redistribute it and/or modify it
rem under the terms of the GNU General Public License as published by the Free
rem Software Foundation, either version 3 of the License, or (at your option)
rem any later version.
rem
rem This program is distributed in the hope that it will be useful, but
rem WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
rem or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
rem more details.
rem
rem You should have received a copy of the GNU General Public License along
rem with this program. If not, see <https://www.gnu.org/licenses/>.
rem
rem Website: https://www.adeptum.se
rem Contact: info@adeptum.se
rem
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
