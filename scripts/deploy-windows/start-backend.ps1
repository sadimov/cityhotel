# ============================================================================
#  Lance le backend Spring Boot (City Hotel) sur le serveur 192.168.100.141
# ============================================================================
#
#  Pré-requis :
#   - Java 21 LTS installé (`java -version` -> 21.x)
#   - PostgreSQL 16+ avec la base `cityprojectdb` initialisée
#   - JAR Spring Boot construit : `citybackend/target/citybackend-1.0.0.jar`
#
#  Variables d'env requises / optionnelles (sinon valeurs par défaut yml) :
#   $env:JWT_SECRET                = "<secret 64+ caractères>"   (REQUIS)
#   $env:DB_USERNAME               = "postgres"                  (REQUIS)
#   $env:DB_PASSWORD               = "<mdp postgres>"            (REQUIS)
#   $env:DB_HOST                   = "localhost"                 (optionnel)
#   $env:DB_PORT                   = "5432"                      (optionnel)
#   $env:DB_NAME                   = "cityprojectdb"             (optionnel)
#   $env:APP_DEPLOYMENT_MODE       = "LOCAL" ou "SAAS"           (défaut SAAS)
#   $env:APP_DEPLOYMENT_HOTEL_CODE = "HOTEL01"                   (mode LOCAL uniquement)
#
#  Approche recommandée : créer `set-env.ps1` à la racine du repo (NE PAS
#  commit) qui exporte toutes ces variables — ce script le source
#  automatiquement avant le lancement.
#
#  Spring Boot écoute par défaut sur 0.0.0.0:8080 — toutes les interfaces
#  réseau, donc accessible depuis le LAN via http://192.168.100.141:8080.
#
#  Pour stopper : Ctrl+C dans cette fenêtre PowerShell.
# ============================================================================

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot  = (Resolve-Path (Join-Path $scriptDir '..\..')).Path
$jarPath   = Join-Path $repoRoot 'citybackend\target\citybackend-1.0.0.jar'

# Source automatique de set-env.ps1 s'il existe à la racine du repo (pratique
# pour ne pas avoir à exporter manuellement à chaque session). Ce fichier
# n'est PAS commit — il contient les secrets de l'environnement de prod.
$envFile = Join-Path $repoRoot 'set-env.ps1'
if (Test-Path $envFile) {
    Write-Host "[~] Source des variables d'env depuis set-env.ps1..." -ForegroundColor DarkGray
    . $envFile
}

if (-not (Test-Path $jarPath)) {
    Write-Host "[!] JAR introuvable : $jarPath" -ForegroundColor Red
    Write-Host "    Construis-le d'abord :" -ForegroundColor Yellow
    Write-Host "      cd citybackend && .\mvnw -DskipTests clean package"
    exit 1
}

# Fail-fast sur les secrets manquants (cf. application-prod.yml).
if (-not $env:JWT_SECRET) {
    Write-Host "[!] La variable d'env JWT_SECRET est obligatoire (>= 64 caractères)." -ForegroundColor Red
    Write-Host "    Exemple :  `$env:JWT_SECRET = 'change-me-with-a-very-long-random-string-of-at-least-64-chars'"
    exit 1
}

# Récap des variables effectives au démarrage (utile pour audit / debug).
$deployMode      = if ($env:APP_DEPLOYMENT_MODE) { $env:APP_DEPLOYMENT_MODE } else { 'SAAS (défaut)' }
$deployHotelCode = if ($env:APP_DEPLOYMENT_HOTEL_CODE) { $env:APP_DEPLOYMENT_HOTEL_CODE } else { '(vide)' }

Write-Host "Démarrage backend City Hotel sur 0.0.0.0:8080..." -ForegroundColor Cyan
Write-Host "  JAR              : $jarPath"
Write-Host "  Profil           : prod"
Write-Host "  Endpoint         : http://192.168.100.141:8080/citybackend"
Write-Host "  Déploiement mode : $deployMode" -ForegroundColor Yellow
Write-Host "  Hôtel code       : $deployHotelCode" -ForegroundColor Yellow
Write-Host ""

java -jar $jarPath `
    --spring.profiles.active=prod `
    --server.address=0.0.0.0 `
    --server.port=8080
