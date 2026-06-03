@echo off
title 2009scape - connect this PC to your save repo
REM ============================================================
REM  Run this ONCE on a NEW computer (after installing the same
REM  2009scape release) to connect its game\data folder to your
REM  private save repo and pull everything down.
REM  Safe to re-run: if already connected, it just pulls.
REM ============================================================
pushd "%~dp0game\data"
if exist ".git" (
  echo This data folder is already connected. Pulling latest save...
  git pull --no-edit
) else (
  echo Connecting this PC to your private save repo...
  git init
  git branch -M main
  git remote add origin https://github.com/ledostarmac2/2009scape-save.git
  git fetch origin
  echo Applying your saved progress ^(local data folder will be overwritten by the cloud save^)...
  git reset --hard origin/main
  git branch --set-upstream-to=origin/main main
  echo Connected and pulled your save.
)
popd
echo.
pause
