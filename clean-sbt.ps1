# Script di pulizia completa per sbt su Windows
# Uso: .\clean-sbt.ps1

Write-Host "=== Pulizia completa sbt ===" -ForegroundColor Cyan

# 1. Terminare tutti i processi Java/sbt
Write-Host "`n1. Terminando processi Java/sbt..." -ForegroundColor Yellow
taskkill /F /IM java.exe /T 2>&1 | Out-Null
taskkill /F /IM sbt.bat /T 2>&1 | Out-Null
Start-Sleep -Seconds 2
Write-Host "   ✓ Processi terminati" -ForegroundColor Green

# 2. Rimuovere directory server sbt
Write-Host "`n2. Rimuovendo directory server sbt..." -ForegroundColor Yellow
$sbtServerPath = "$env:USERPROFILE\.sbt\server"
if (Test-Path $sbtServerPath) {
    Remove-Item -Path $sbtServerPath -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "   ✓ Directory server rimossa" -ForegroundColor Green
} else {
    Write-Host "   - Directory server non trovata" -ForegroundColor Gray
}

# 3. Rimuovere lock file
Write-Host "`n3. Rimuovendo lock file..." -ForegroundColor Yellow
$lockFiles = Get-ChildItem -Path "$env:USERPROFILE\.sbt" -Recurse -Filter "*lock*" -ErrorAction SilentlyContinue
$lockFiles += Get-ChildItem -Path "$env:USERPROFILE\.sbt" -Recurse -Filter "*.lock" -ErrorAction SilentlyContinue
if ($lockFiles) {
    $lockFiles | Remove-Item -Force -ErrorAction SilentlyContinue
    Write-Host "   ✓ $($lockFiles.Count) lock file rimossi" -ForegroundColor Green
} else {
    Write-Host "   - Nessun lock file trovato" -ForegroundColor Gray
}

# 4. Verificare processi Java residui
Write-Host "`n4. Verificando processi Java residui..." -ForegroundColor Yellow
$javaProcs = Get-Process | Where-Object {$_.ProcessName -like "*java*"} -ErrorAction SilentlyContinue
if ($javaProcs) {
    Write-Host "   ⚠ ATTENZIONE: Processi Java ancora attivi:" -ForegroundColor Red
    $javaProcs | Select-Object ProcessName, Id | Format-Table
} else {
    Write-Host "   ✓ Nessun processo Java attivo" -ForegroundColor Green
}

Write-Host "`n=== Pulizia completata ===" -ForegroundColor Cyan
Write-Host "`nOra puoi eseguire sbt con:" -ForegroundColor Yellow
Write-Host '  $env:SBT_OPTS="-Dsbt.server.autostart=false -Dsbt.server=false"; sbt -no-server -batch "project schemaJVM" "compile"' -ForegroundColor White

