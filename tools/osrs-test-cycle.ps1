# DEPRECATED: OSRS import now runs against the live game instance via import-osrs-items.ps1.
# This wrapper preserves the old entry point name but targets game/ on port 43595.
param(
    [switch]$NoLaunch,
    [string]$Only = ""
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$args = @("-ExecutionPolicy", "Bypass", "-File", "$root\import-osrs-items.ps1")
if ($NoLaunch) { $args += "-NoLaunch" }
if ($Only) { $args += @("-Only", $Only) }
Write-Host "osrs-test-cycle.ps1 -> import-osrs-items.ps1 (unified main-game path)" -ForegroundColor Yellow
& powershell @args
