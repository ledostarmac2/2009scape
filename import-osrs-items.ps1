# =============================================================================
#  OSRS ITEM IMPORT - one command, end to end.
#
#    1. Edit tools\osrs-import\manifest.json  (add {"newId": ..., "osrsId": ...})
#    2. Run:  powershell -ExecutionPolicy Bypass -File import-osrs-items.ps1
#
#  Stops the live server/client, runs the full import pipeline (models, item
#  defs, icon cameras, server stats), then relaunches the server + client so
#  you can try the items on (spawn with  ::item <newId>  in game).
#
#  Docs: docs\osrs-item-import.md
#  Flags: -NoLaunch (skip relaunch)  -Only 14676,14677 (re-import subset)
#
#  Player saves: scripts must NEVER modify core_data.location in ledostar.json.
# =============================================================================
param(
    [switch]$NoLaunch,
    [string]$Only = ""
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

Write-Host "== stop live server + client ==" -ForegroundColor Cyan
Get-Process java -ErrorAction SilentlyContinue |
    Where-Object { $_.Path -like "$root\jre*" } |
    Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 800

Write-Host "== run import pipeline ==" -ForegroundColor Cyan
$pyArgs = @("$root\tools\osrs_import_pipeline.py", "run")
if ($Only) { $pyArgs += @("--only", $Only) }
python @pyArgs
if ($LASTEXITCODE -ne 0) { throw "pipeline failed - cache may be partially patched; backups in game\osrs-import-backups" }

Write-Host "== finalize item configs (level gates, GE, tradeable) ==" -ForegroundColor Cyan
python "$root\tools\_finalize_osrs_items.py"
if ($LASTEXITCODE -ne 0) { throw "finalize failed" }

Write-Host "== patch skill guides in cache ==" -ForegroundColor Cyan
& "$root\install-skill-guides.bat"
if ($LASTEXITCODE -ne 0) { throw "skill guide install failed" }

if ($NoLaunch) { Write-Host "NoLaunch set; done." -ForegroundColor Green; return }

Write-Host "== relaunch server ==" -ForegroundColor Cyan
Start-Process -FilePath "$root\jre\bin\java.exe" `
    -ArgumentList '-Dsun.net.useExclusiveBind=false','-Xmx2G','-Xms2G','-jar','server.jar' `
    -WorkingDirectory "$root\game" `
    -RedirectStandardOutput "$root\game\server-import.log" -RedirectStandardError "$root\game\server-import.err.log"
for ($i = 0; $i -lt 60; $i++) {
    if (Get-NetTCPConnection -State Listen -LocalPort 43595 -ErrorAction SilentlyContinue) { break }
    Start-Sleep -Seconds 1
}
if (-not (Get-NetTCPConnection -State Listen -LocalPort 43595 -ErrorAction SilentlyContinue)) {
    throw "server did not come up on 43595; check game\server-import.err.log"
}

Write-Host "== relaunch client ==" -ForegroundColor Cyan
Start-Process -FilePath "$root\jre\bin\java.exe" `
    -ArgumentList '-Xmx1G','-Xms1G','-jar','client.jar' `
    -WorkingDirectory "$root\game" `
    -RedirectStandardOutput "$root\game\client-import.log" -RedirectStandardError "$root\game\client-import.err.log"

Write-Host "Done. Log in and spawn items with ::item <id>." -ForegroundColor Green
