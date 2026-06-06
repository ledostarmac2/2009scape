@echo off
setlocal
title Install Master Sword cache

set ROOT=%~dp0
set CACHE=%ROOT%game\data\cache
set BACKUP=%ROOT%game\data\custom-items\master-sword\cache-backup-14670
set OBJ=%ROOT%..\Custom Items\Master Sword\Meshy_AI_Violet_Moonblade_0606135542_texture.obj
set LOCAL=%USERPROFILE%\cache\runescape
set JAVAC=C:\Users\btarabocchia\Java\temurin-17.0.19+10\bin\javac.exe

echo [master-sword] Stopping only the 2009scape server/client (bundled JRE) to free the cache...
powershell -NoProfile -Command "Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*2009scape*jre*' } | Stop-Process -Force -ErrorAction SilentlyContinue"

if exist "%JAVAC%" (
  echo [master-sword] Compiling shared PatchInfinityBladeVisual patcher...
  "%JAVAC%" --release 11 -cp "%ROOT%game\client.jar" -d "%ROOT%tools" "%ROOT%tools\PatchInfinityBladeVisual.java" || goto :fail
) else (
  echo [master-sword] JDK not found at %JAVAC% - using pre-compiled PatchInfinityBladeVisual.class
)

echo [master-sword] Applying Master Sword model and item cache patch...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\client.jar" PatchInfinityBladeVisual "%CACHE%" "%BACKUP%" "%OBJ%" all 14656 14670 14671 1305 "Master Sword" || goto :fail

echo [master-sword] Verifying server cache...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\server.jar" VerifyServerCache "%CACHE%" || goto :fail

echo [master-sword] Mirroring patched cache to local client cache...
if exist "%LOCAL%" rmdir /s /q "%LOCAL%"
mkdir "%LOCAL%"
copy /y "%CACHE%\main_file_cache.*" "%LOCAL%\" >nul

echo [master-sword] Done.
exit /b 0

:fail
echo [master-sword] Failed. Check the output above.
exit /b 1
