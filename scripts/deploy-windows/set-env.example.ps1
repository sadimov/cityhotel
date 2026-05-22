# ============================================================================
#  Template des variables d'environnement runtime — City Hotel backend
# ============================================================================
#
#  Usage :
#   1. Copier ce fichier à la racine du repo sous le nom `set-env.ps1`
#      (à NE PAS commit — contient les secrets de prod).
#   2. Remplacer les valeurs `REMPLACE-PAR-...` par les vrais secrets.
#   3. `start-backend.ps1` source automatiquement ce fichier au démarrage.
#
#  Alternative manuelle (sans set-env.ps1) :
#      Exécuter chaque ligne dans la session PowerShell avant
#      `start-backend.ps1`. Plus fragile (perdu à la fermeture du shell).
# ============================================================================

# ─── Secrets obligatoires (fail-fast si absents) ────────────────────────────

# JWT_SECRET : clé HMAC pour signer les tokens (>= 64 caractères).
# Génération recommandée : `openssl rand -base64 64` ou un coffre-fort.
$env:JWT_SECRET = "REMPLACE-PAR-UN-SECRET-DE-64-CARS-MIN-XXXXXXXXXXXXXXXXXXXXXXXXXXXX"

# Identifiants PostgreSQL.
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "REMPLACE-PAR-LE-MOT-DE-PASSE-POSTGRES"

# ─── Configuration BD (optionnel, valeurs par défaut OK pour la plupart) ────

$env:DB_HOST = "localhost"        # ou IP serveur PG si distant
$env:DB_PORT = "5432"
$env:DB_NAME = "cityprojectdb"

# ─── Mode de déploiement (consigne user 2026-05-22) ────────────────────────

# LOCAL : un seul hôtel sur ce serveur (déploiement on-premise chez le client)
# SAAS  : plusieurs hôtels sur ce serveur (hébergement central City Hotel)
# Cf. deploiement/MODES_DEPLOIEMENT.md pour les détails.
$env:APP_DEPLOYMENT_MODE = "SAAS"

# En mode LOCAL : code de l'hôtel unique de ce serveur (alphanum, ≤ 10 chars).
# Doit correspondre à un Hotel.hotelCode existant en BD.
# En mode SAAS : laisser vide.
$env:APP_DEPLOYMENT_HOTEL_CODE = ""

# ─── (Optionnel) Surcharges runtime ────────────────────────────────────────

# Désactiver la planification night audit (utile en CI/test) :
# $env:CITY_NIGHT_AUDIT_RUN_CRON   = "-"
# $env:CITY_NIGHT_AUDIT_ALERT_CRON = "-"

# Surcharger l'IP du serveur LAN si différente de 192.168.100.141 :
# Mettre à jour aussi `cityfrontend/src/environments/environment.prod.ts`
# et `app.cors.allowed-origins` dans application.yml (puis rebuild JAR).
