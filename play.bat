@echo off
title 2009scape (synced)
REM ============================================================
REM  One-click: pull your latest save, play, then push the save.
REM  Use THIS instead of launch.bat to keep your save in sync.
REM ============================================================

echo [sync] Pulling your latest save from GitHub...
pushd "%~dp0game\data"
git pull --no-edit
popd
echo.

call "%~dp0launch.bat"

echo.
echo [sync] Saving your progress to GitHub...
pushd "%~dp0game\data"
git add -A
git diff --cached --quiet && (echo [sync] No changes to save.) || (git commit -m "save %DATE% %TIME%" && git push)
popd
echo.
echo [sync] Done. Your progress is backed up. Safe to close this window.
pause
