@echo off

pushd %~dp0\bin
echo Building database configuration...
mysql_install_db -d ../data
start /B mysqld.exe
timeout 2 > NUL
echo Initializing data store...
echo create database global; use global; source ../template/global.sql; | mysql -u root
taskkill /im mysqld.exe /f
popd
echo Database initialized.