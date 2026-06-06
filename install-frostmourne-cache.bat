@echo off
setlocal
title Install Frostmourne cache

set ROOT=%~dp0
set CACHE=%ROOT%game\data\cache
set BACKUP=%ROOT%game\data\custom-items\frostmourne\cache-backup-14545
set OBJ=%ROOT%..\Custom Items\Frostmourne\Meshy_AI_Azure_Frostblade_0606135119_texture.obj
set LOCAL=%USERPROFILE%\cache\runescape
set JAVAC=C:\Users\btarabocchia\Java\temurin-17.0.19+10\bin\javac.exe

echo [frostmourne] Stopping only the 2009scape server/client (bundled JRE) to free the cache...
powershell -NoProfile -Command "Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*2009scape*jre*' } | Stop-Process -Force -ErrorAction SilentlyContinue"

if exist "%JAVAC%" (
  echo [frostmourne] Compiling standalone PatchInfinityBladeVisual...
  "%JAVAC%" --release 11 -cp "%ROOT%game\client.jar" -d "%ROOT%tools" "%ROOT%tools\PatchInfinityBladeVisual.java" || goto :fail
) else (
  echo [frostmourne] JDK not found at %JAVAC% - using pre-compiled PatchInfinityBladeVisual.class
)

echo [frostmourne] Applying Frostmourne model and item cache patch...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\client.jar" PatchInfinityBladeVisual "%CACHE%" "%BACKUP%" "%OBJ%" all 14545 14668 14669 13899 "Frostmourne" flip-length || goto :fail

echo [frostmourne] Verifying server cache...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\server.jar" VerifyServerCache "%CACHE%" || goto :fail

echo [frostmourne] Mirroring patched cache to local client cache...
if exist "%LOCAL%" rmdir /s /q "%LOCAL%"
mkdir "%LOCAL%"
copy /y "%CACHE%\main_file_cache.*" "%LOCAL%\" >nul

echo [frostmourne] Done.
exit /b 0

:fail
echo [frostmourne] Failed. Check the output above.
exit /b 1
