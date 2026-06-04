@echo off
setlocal
title Install Emberblade cache

set ROOT=%~dp0
set CACHE=%ROOT%game\data\cache
set BACKUP=%ROOT%game\data\custom-items\emberblade\cache-backup
set OBJ=%ROOT%game\data\custom-items\emberblade\emberblade_model_1.obj
set LOCAL=%USERPROFILE%\cache\runescape

echo [emberblade] Stopping running Java processes...
taskkill /im java.exe /f >nul 2>nul

echo [emberblade] Restoring clean server cache baseline...
copy /y "%BACKUP%\main_file_cache.dat2" "%CACHE%\main_file_cache.dat2" >nul
copy /y "%BACKUP%\main_file_cache.idx7" "%CACHE%\main_file_cache.idx7" >nul
copy /y "%BACKUP%\main_file_cache.idx19" "%CACHE%\main_file_cache.idx19" >nul
copy /y "%BACKUP%\main_file_cache.idx255" "%CACHE%\main_file_cache.idx255" >nul

echo [emberblade] Applying Emberblade model and item cache patch...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\client.jar" PatchEmberbladeVisual "%CACHE%" "%BACKUP%" "%OBJ%" all || goto :fail

echo [emberblade] Verifying server cache...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\server.jar" VerifyServerCache "%CACHE%" || goto :fail

echo [emberblade] Verifying Emberblade client definition...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\client.jar" VerifyEmberbladeVisual "%CACHE%" || goto :fail

echo [emberblade] Mirroring patched cache to local client cache...
if exist "%LOCAL%" rmdir /s /q "%LOCAL%"
mkdir "%LOCAL%"
copy /y "%CACHE%\main_file_cache.*" "%LOCAL%\" >nul

echo [emberblade] Verifying local client cache...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\client.jar" VerifyEmberbladeVisual "%LOCAL%" || goto :fail

echo [emberblade] Done.
exit /b 0

:fail
echo [emberblade] Failed. Check the output above.
exit /b 1
