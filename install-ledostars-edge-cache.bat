@echo off
setlocal
title Install Ledostar's Edge cache

set ROOT=%~dp0
set CACHE=%ROOT%game\data\cache
set BACKUP=%ROOT%game\data\custom-items\ledostars-edge\cache-backup-14546
set OBJ=%ROOT%..\Custom Items\Ledostar's Edge\Meshy_AI_Starfall_Blade_0606140713_texture.obj
set LOCAL=%USERPROFILE%\cache\runescape
set JAVAC=C:\Users\btarabocchia\Java\temurin-17.0.19+10\bin\javac.exe

echo [ledostars-edge] Stopping only the 2009scape server/client (bundled JRE) to free the cache...
powershell -NoProfile -Command "Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*2009scape*jre*' } | Stop-Process -Force -ErrorAction SilentlyContinue"

if exist "%JAVAC%" (
  echo [ledostars-edge] Compiling shared PatchInfinityBladeVisual patcher...
  "%JAVAC%" --release 11 -cp "%ROOT%game\client.jar" -d "%ROOT%tools" "%ROOT%tools\PatchInfinityBladeVisual.java" || goto :fail
) else (
  echo [ledostars-edge] JDK not found at %JAVAC% - using pre-compiled PatchInfinityBladeVisual.class
)

echo [ledostars-edge] Applying Ledostar's Edge model and item cache patch...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\client.jar" PatchInfinityBladeVisual "%CACHE%" "%BACKUP%" "%OBJ%" all 14546 14672 14673 13899 "Ledostar's Edge" flip-length || goto :fail

echo [ledostars-edge] Verifying server cache...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\server.jar" VerifyServerCache "%CACHE%" || goto :fail

echo [ledostars-edge] Mirroring patched cache to local client cache...
if exist "%LOCAL%" rmdir /s /q "%LOCAL%"
mkdir "%LOCAL%"
copy /y "%CACHE%\main_file_cache.*" "%LOCAL%\" >nul

echo [ledostars-edge] Done.
exit /b 0

:fail
echo [ledostars-edge] Failed. Check the output above.
exit /b 1
