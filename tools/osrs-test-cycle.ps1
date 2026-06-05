# OSRS item import -> test cycle for 2009scape (game-test instance only).
# Compiles the importer, restores a clean cache baseline, runs the import,
# mirrors the cache to the client cache dir, and relaunches the TEST server+client.
# Never touches the live server on port 43595.
param([switch]$NoLaunch)
$ErrorActionPreference = 'Stop'
$root        = 'C:\Users\btarabocchia\2009scape\2009scape'
$jdk         = 'C:\Users\btarabocchia\Java\temurin-26.0.1+8\bin'
$jre         = "$root\jre\bin"
$cache       = "$root\game-test\data\cache"
$backup      = "$root\game-test\osrs-import-backups"
$models      = "$root\data\import\osrs-model-groups"
$objout      = "$root\data\import\osrs-model-objs"
$clientcache = "$env:USERPROFILE\cache\runescape"
$gtdir       = "$root\game-test"
$cp = @("$root\tools","$gtdir\client.jar",
        "$root\tools\runelite-src\cache\build\libs\cache-1.12.29-SNAPSHOT.jar",
        "$root\tools\lib\asm-9.7.1.jar") -join ';'

Write-Host "== [1/6] compile importer ==" -ForegroundColor Cyan
& "$jdk\javac.exe" -cp $cp -d "$root\tools" "$root\tools\ImportOsrsItemModels.java"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "== [2/6] stop TEST server+client (preserve live 43595) ==" -ForegroundColor Cyan
$live = (Get-NetTCPConnection -State Listen -LocalPort 43595 -EA SilentlyContinue).OwningProcess
$kill = @()
$kill += (Get-NetTCPConnection -State Listen -LocalPort 43599,43600 -EA SilentlyContinue).OwningProcess
$kill += (Get-NetTCPConnection -State Established -EA SilentlyContinue | Where-Object { $_.RemotePort -in 43599,43600 }).OwningProcess
$kill = $kill | Where-Object { $_ -and $_ -ne $live } | Select-Object -Unique
foreach ($p in $kill) { Write-Host "  killing PID $p"; Stop-Process -Id $p -Force -EA SilentlyContinue }
Start-Sleep -Milliseconds 800

Write-Host "== [3/6] restore clean cache baseline ==" -ForegroundColor Cyan
foreach ($f in 'main_file_cache.dat2','main_file_cache.idx7','main_file_cache.idx19','main_file_cache.idx255') {
  Copy-Item "$backup\$f" "$cache\$f" -Force
}

Write-Host "== [4/6] run import ==" -ForegroundColor Cyan
& "$jdk\java.exe" -cp $cp ImportOsrsItemModels $cache $backup $models $objout
if ($LASTEXITCODE -ne 0) { throw "import failed (cache left at clean baseline; not mirrored)" }

Write-Host "== [5/6] mirror cache -> client cache ==" -ForegroundColor Cyan
Copy-Item "$cache\main_file_cache.*" $clientcache -Force

if ($NoLaunch) { Write-Host "NoLaunch set; done."; return }

Write-Host "== [6/6] relaunch test server + client ==" -ForegroundColor Cyan
Start-Process -FilePath "$jre\java.exe" `
  -ArgumentList '-Dsun.net.useExclusiveBind=false','-Xmx2G','-Xms2G','-jar','server.jar' `
  -WorkingDirectory $gtdir `
  -RedirectStandardOutput "$gtdir\server-test-live.log" -RedirectStandardError "$gtdir\server-test-live.err.log"
for ($i=0; $i -lt 30; $i++) {
  if (Get-NetTCPConnection -State Listen -LocalPort 43600 -EA SilentlyContinue) { break }
  Start-Sleep -Seconds 1
}
Start-Process -FilePath "$jre\java.exe" `
  -ArgumentList '-Xmx1G','-Xms1G','-jar','client.jar' `
  -WorkingDirectory $gtdir `
  -RedirectStandardOutput "$gtdir\client-test-live.log" -RedirectStandardError "$gtdir\client-test-live.err.log"
Write-Host "Launched. Log in and check the items." -ForegroundColor Green
