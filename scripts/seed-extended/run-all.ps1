# ============================================================================
# SEED EXTENDED — run-all.ps1 (Windows PowerShell)
# Tour 52 — execute les 7 scripts du seed extended.
#
# Pre-requis :
#   - PostgreSQL 16+ accessible via psql sur PATH
#   - Variable d'environnement PGPASSWORD ou prompt interactif
#   - DB cityprojectdb existante avec seed initial deja execute (Tour 42)
#
# Usage :
#   .\scripts\seed-extended\run-all.ps1
#   ou specifier user/host/port :
#   .\scripts\seed-extended\run-all.ps1 -User postgres -Db cityprojectdb -Server localhost -Port 5432
# ============================================================================

param(
    [string]$User   = "postgres",
    [string]$Db     = "cityprojectdb",
    [string]$Server = "localhost",
    [int]$Port      = 5432,
    [string]$Password = ""
)

if ($Password -ne "") {
    $env:PGPASSWORD = $Password
} elseif (-not $env:PGPASSWORD) {
    Write-Host "Pas de PGPASSWORD defini. Le mot de passe sera demande de facon interactive." -ForegroundColor Yellow
}

$ScriptDir = $PSScriptRoot

$files = @(
    "01-core-extra.sql",
    "02-hebergement-extra.sql",
    "03-inventory-extra.sql",
    "04-restaurant-extra.sql",
    "05-finance-extra.sql",
    "06-menage-extra.sql",
    "08-resync-numerotation.sql",
    "07-validation.sql"
)

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  SEED EXTENDED (Tour 52) - Demarrage" -ForegroundColor Cyan
Write-Host "  Cible : $User@$Server`:$Port/$Db" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

foreach ($file in $files) {
    $fullPath = Join-Path $ScriptDir $file
    if (-not (Test-Path $fullPath)) {
        Write-Host "ERREUR : Fichier manquant : $fullPath" -ForegroundColor Red
        exit 1
    }
    Write-Host ""
    Write-Host ">>> Execution : $file" -ForegroundColor Green
    & psql -U $User -h $Server -p $Port -d $Db -v ON_ERROR_STOP=1 -f $fullPath
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERREUR sur $file (exit code = $LASTEXITCODE). Arret." -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  SEED EXTENDED - Termine avec succes" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
