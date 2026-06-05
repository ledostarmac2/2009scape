$ErrorActionPreference = "Continue"
$log = "C:\Users\btarabocchia\2009scape\2009scape\tools\save-on-exit.log"
"[$(Get-Date -f s)] watching client PID 18076 for exit..." | Out-File $log -Encoding utf8
Wait-Process -Id 18076 -ErrorAction SilentlyContinue
"[$(Get-Date -f s)] client closed. waiting 15s for server to flush logout-save..." | Add-Content $log
Start-Sleep -Seconds 15
$srv = Get-CimInstance Win32_Process -Filter "ProcessId=17500" -ErrorAction SilentlyContinue
if ($srv -and $srv.CommandLine -like "*server.jar*") {
  "[$(Get-Date -f s)] stopping 2009scape server PID 17500..." | Add-Content $log
  Stop-Process -Id 17500 -Force -ErrorAction SilentlyContinue
  Start-Sleep -Seconds 5
} else {
  "[$(Get-Date -f s)] server PID 17500 not the 2009scape server; leaving it alone." | Add-Content $log
}
Set-Location "C:\Users\btarabocchia\2009scape\2009scape\game\data"
git add -A 2>&1 | Add-Content $log
$pending = git status --porcelain
if ($pending) {
  git commit -m "save $(Get-Date -f 'yyyy-MM-dd HH:mm:ss')" 2>&1 | Add-Content $log
  git push 2>&1 | Add-Content $log
  "[$(Get-Date -f s)] === PUSH COMPLETE ===" | Add-Content $log
} else {
  "[$(Get-Date -f s)] no changes to save." | Add-Content $log
}
