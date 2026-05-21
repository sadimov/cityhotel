# ============================================================================
#  Lance le frontend Angular (City Hotel) en mode LAN sur 0.0.0.0:4200
# ============================================================================
#
#  Pré-requis :
#   - Node.js 22 LTS (ou 24 LTS) installé
#   - `npm install` déjà exécuté dans cityfrontend/
#
#  Configuration "lan" (angular.json) :
#   - host = 0.0.0.0 (accepte les connexions du LAN)
#   - allowedHosts = "all" (pas de host-check sur l'IP du serveur)
#   - buildTarget = production (utilise environment.prod.ts -> IP serveur LAN)
#
#  Accès : http://192.168.100.141:4200 depuis n'importe quel poste du LAN.
#
#  Note : `ng serve` est un serveur de dev. Pour une vraie prod, builder le
#  dist/ et le servir via IIS, Nginx ou http-server. Le mode lan est un
#  raccourci adapté aux environnements de pré-production interne.
# ============================================================================

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot  = (Resolve-Path (Join-Path $scriptDir '..\..')).Path
$frontDir  = Join-Path $repoRoot 'cityfrontend'

if (-not (Test-Path (Join-Path $frontDir 'node_modules'))) {
    Write-Host "[!] node_modules absent — exécute d'abord :" -ForegroundColor Yellow
    Write-Host "      cd cityfrontend && npm install"
    exit 1
}

Set-Location $frontDir

Write-Host "Démarrage frontend City Hotel sur 0.0.0.0:4200..." -ForegroundColor Cyan
Write-Host "  Workdir   : $frontDir"
Write-Host "  Config    : lan (production build + host 0.0.0.0)"
Write-Host "  Endpoint  : http://192.168.100.141:4200"
Write-Host ""

npm run start:lan
