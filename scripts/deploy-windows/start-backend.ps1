# ============================================================================
#  Lance le backend Spring Boot (City Hotel) sur le serveur 192.168.100.141
# ============================================================================
#
#  Pré-requis :
#   - Java 21 LTS installé (`java -version` -> 21.x)
#   - PostgreSQL 16+ avec la base `cityprojectdb` initialisée
#   - JAR Spring Boot construit : `citybackend/target/citybackend-1.0.0.jar`
#
#  Variables d'env requises (sinon valeurs par défaut d'application.yml) :
#   $env:JWT_SECRET     = "<secret 64+ caractères>"
#   $env:DB_USERNAME    = "postgres"
#   $env:DB_PASSWORD    = "<mdp postgres>"
#   $env:DB_HOST        = "localhost"      # ou IP du serveur PG si distant
#   $env:DB_PORT        = "5432"
#   $env:DB_NAME        = "cityprojectdb"
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

Write-Host "Démarrage backend City Hotel sur 0.0.0.0:8080..." -ForegroundColor Cyan
Write-Host "  JAR       : $jarPath"
Write-Host "  Profil    : prod"
Write-Host "  Endpoint  : http://192.168.100.141:8080/citybackend"
Write-Host ""

java -jar $jarPath `
    --spring.profiles.active=prod `
    --server.address=0.0.0.0 `
    --server.port=8080
