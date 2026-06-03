@echo off
title 2009scape - pull save
REM Pull your latest save from GitHub. Run this BEFORE playing
REM (especially on a second computer). Make sure the game is closed.
pushd "%~dp0game\data"
echo Pulling latest save from GitHub...
git pull --no-edit
popd
echo.
echo Done. You can now launch the game.
pause
