@echo off
setlocal
title Install Custom Weapon Skill Guides

set ROOT=%~dp0
set CACHE=%ROOT%game\data\cache
set LOCAL=%USERPROFILE%\cache\runescape

echo [guides] Stopping running Java processes...
taskkill /im java.exe /f >nul 2>nul

echo [guides] Ensuring skill-guide script groups are present in server cache...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\client.jar" CopyMissingScriptGroups "%CACHE%" "%ROOT%game-test\data\cache" 978 982 996 1004 || goto :fail

echo [guides] Patching custom weapon skill guides...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\client.jar" PatchCustomWeaponSkillGuides "%CACHE%" || goto :fail

echo [guides] Verifying server cache...
"%ROOT%jre\bin\java.exe" -cp "%ROOT%tools;%ROOT%game\server.jar" VerifyServerCache "%CACHE%" || goto :fail

echo [guides] Mirroring patched cache to local client cache...
if exist "%LOCAL%" rmdir /s /q "%LOCAL%"
mkdir "%LOCAL%"
copy /y "%CACHE%\main_file_cache.*" "%LOCAL%\" >nul

echo [guides] Done.
exit /b 0

:fail
echo [guides] Failed. Check the output above.
exit /b 1
