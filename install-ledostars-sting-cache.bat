@echo off
setlocal
title Install Ledostar's Sting cache

set ROOT=%~dp0
set CACHE=%ROOT%game\data\cache
set BACKUP=%ROOT%game\data\custom-items\ledostars-sting\cache-backup-14547
set OBJ=%ROOT%..\Custom Items\Ledostar's Sting\Meshy_AI_Crimson_Crescent_Dagg_0606142921_texture.obj
set LOCAL=%USERPROFILE%\cache\runescape
set JAVAC=C:\Users\btarabocchia\Java\temurin-17.0.19+10\bin\javac.exe

echo [ledostars-sting] Stopping only the 2009scape server/client (bundled JRE) to free the cache...
powershell -NoProfile -Command "Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*2009scape*jre*' } | Stop-Process -Force -ErrorAction SilentlyContinue"

if exist "%JAVAC%" (
  echo [ledostars-sting] Compiling shared PatchInfinityBladeVisual patcher...
  "%JAVAC%" --release 11 -cp "%ROOT%game\client.jar" -d "%ROOT%tools" "%ROOT%tools\PatchInfinityBladeVisual.java" || goto :fail
) else (
  echo [ledostars-sting] JDK not found at %JAVAC% - using pre-compiled PatchInfinityBladeVisual.class
)

echo [ledostars-sting] Applying Ledostar's Sting model and item cache patch...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\client.jar" PatchInfinityBladeVisual "%CACHE%" "%BACKUP%" "%OBJ%" all 14547 14674 14675 8850 "Ledostar's Sting" shield-fit keep-icon flip-length || goto :fail

echo [ledostars-sting] Verifying server cache...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\server.jar" VerifyServerCache "%CACHE%" || goto :fail

echo [ledostars-sting] Mirroring patched cache to local client cache...
if exist "%LOCAL%" rmdir /s /q "%LOCAL%"
mkdir "%LOCAL%"
copy /y "%CACHE%\main_file_cache.*" "%LOCAL%\" >nul

echo [ledostars-sting] Done.
exit /b 0

:fail
echo [ledostars-sting] Failed. Check the output above.
exit /b 1
