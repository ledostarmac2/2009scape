@echo off
setlocal
title Install Infinity Blade cache

set ROOT=%~dp0
set CACHE=%ROOT%game\data\cache
set BACKUP=%ROOT%game\data\custom-items\infinity-blade\cache-backup-14666
set OBJ=%ROOT%..\Custom Items\Infinity Blade\v2.obj
set LOCAL=%USERPROFILE%\cache\runescape
set JAVAC=C:\Users\btarabocchia\Java\temurin-17.0.19+10\bin\javac.exe

echo [infinity-blade] Stopping only the 2009scape server/client (bundled JRE) to free the cache...
powershell -NoProfile -Command "Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*2009scape*jre*' } | Stop-Process -Force -ErrorAction SilentlyContinue"

if exist "%JAVAC%" (
  echo [infinity-blade] Compiling standalone PatchInfinityBladeVisual...
  "%JAVAC%" --release 11 -cp "%ROOT%game\client.jar" -d "%ROOT%tools" "%ROOT%tools\PatchInfinityBladeVisual.java" || goto :fail
) else (
  echo [infinity-blade] JDK not found at %JAVAC% - using pre-compiled PatchInfinityBladeVisual.class
)

echo [infinity-blade] Applying Infinity Blade model and item cache patch...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\client.jar" PatchInfinityBladeVisual "%CACHE%" "%BACKUP%" "%OBJ%" all 14666 14666 14667 1305 "Infinity Blade" || goto :fail

echo [infinity-blade] Verifying server cache...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\server.jar" VerifyServerCache "%CACHE%" || goto :fail

echo [infinity-blade] Mirroring patched cache to local client cache...
if exist "%LOCAL%" rmdir /s /q "%LOCAL%"
mkdir "%LOCAL%"
copy /y "%CACHE%\main_file_cache.*" "%LOCAL%\" >nul

echo [infinity-blade] Done.
exit /b 0

:fail
echo [infinity-blade] Failed. Check the output above.
exit /b 1
