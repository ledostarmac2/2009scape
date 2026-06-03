@echo off
title 2009scape - push save
REM Save your progress to GitHub. Run this AFTER playing
REM (close the game first so the save file is fully written).
pushd "%~dp0game\data"
echo Saving your progress to GitHub...
git add -A
git diff --cached --quiet && (echo No changes to save.) || (git commit -m "save %DATE% %TIME%" && git push)
popd
echo.
echo Done.
pause
