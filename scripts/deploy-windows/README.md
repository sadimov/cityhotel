# Déploiement City Hotel sur serveur Windows LAN

Configuration cible (consigne 2026-05-20) :

- **Serveur** : Windows à l'adresse `192.168.100.141`
- **Réseau LAN** : `192.168.100.0/24` (passerelle `192.168.100.1`)
- **Frontend** servi sur `http://192.168.100.141:4200`
- **Backend** Spring Boot sur `http://192.168.100.141:8080/citybackend`

## Pré-requis sur le serveur

| Composant | Version | Notes |
|---|---|---|
| Java | 21 LTS | `java -version` doit afficher `21.x` |
| Node.js | 22 ou 24 LTS | `node -v` |
| PostgreSQL | 16+ | Base `cityprojectdb` créée et migrée |
| PowerShell | 5.1+ | Pour exécuter les scripts (préinstallé sur Windows 10/11) |

## Setup initial (une seule fois)

### 1. Ouvrir les ports 4200 et 8080 dans le pare-feu Windows

Clic-droit sur `firewall-rules.ps1` → **Exécuter avec PowerShell en administrateur**.

Le script crée deux règles entrantes (`CityHotel-Frontend-4200`, `CityHotel-Backend-8080`) restreintes au sous-réseau `192.168.100.0/24` — donc accès LAN uniquement, pas exposé Internet.

### 2. Variables d'environnement (sécurité)

Crée un fichier `set-env.ps1` (à ne pas commit) :

```powershell
$env:JWT_SECRET   = "REMPLACE-PAR-UN-SECRET-DE-64-CARS-MIN-XXXXXXXXXXXXXXXXXXXXXXXX"
$env:DB_USERNAME  = "postgres"
$env:DB_PASSWORD  = "<mdp postgres>"
$env:DB_HOST      = "localhost"      # ou IP serveur PG si distant
$env:DB_PORT      = "5432"
$env:DB_NAME      = "cityprojectdb"
```

Source-le avant chaque session : `. .\set-env.ps1`

### 3. Build initial des deux applications

```powershell
# Backend
cd C:\path\to\cityhotel\citybackend
.\mvnw -DskipTests clean package
# → produit target\citybackend-1.0.0.jar (~110 MB)

# Frontend
cd ..\cityfrontend
npm install
# (le build est fait à la volée par ng serve --configuration lan)
```

## Lancement quotidien

Ouvrir **deux fenêtres PowerShell** (le backend et le frontend sont des
processus de longue durée à laisser tourner).

**Fenêtre 1** — Backend :

```powershell
. .\set-env.ps1
.\scripts\deploy-windows\start-backend.ps1
```

Sortie attendue : `Started CitybackendApplication in X.X seconds`

**Fenêtre 2** — Frontend :

```powershell
.\scripts\deploy-windows\start-frontend.ps1
```

Sortie attendue : `** Angular Live Development Server is listening on 0.0.0.0:4200 **`

## Vérification depuis un autre poste du LAN

```powershell
# Test backend health
curl http://192.168.100.141:8080/citybackend/actuator/health
# → {"status":"UP"}

# Ouvrir l'application
start http://192.168.100.141:4200
```

## Configuration appliquée

### Backend — `application.yml`

```yaml
app:
  cors:
    allowed-origins:
      - http://localhost:3000           # dev local
      - http://localhost:4200           # dev local
      - http://192.168.100.141:4200     # LAN frontend
      - http://192.168.100.141:8080     # LAN backend direct (debug)
```

Pour ajouter une autre IP sans recompiler : surcharger via variable d'env au démarrage :

```powershell
java -jar citybackend-1.0.0.jar `
    --spring.profiles.active=prod `
    --app.cors.allowed-origins[0]=http://192.168.100.141:4200 `
    --app.cors.allowed-origins[1]=http://autre-ip:4200
```

### Frontend — `environment.prod.ts`

`apiUrl: 'http://192.168.100.141:8080/citybackend'` — l'IP est compilée dans le bundle au build.
Pour changer : éditer le fichier puis `npm run start:lan` (ou `build:prod`).

### Frontend — `angular.json` configuration `lan`

```json
"lan": {
  "buildTarget": "cityfrontend:build:production",
  "host": "0.0.0.0",
  "allowedHosts": ["all"]
}
```

`host: 0.0.0.0` = écoute toutes interfaces réseau. `allowedHosts: ["all"]` = pas de blocage sur l'IP du serveur (`192.168.100.141` n'est pas dans la whitelist par défaut d'Angular dev-server).

## Dépannage

| Symptôme | Cause probable | Solution |
|---|---|---|
| `Connection refused` depuis un poste LAN | Pare-feu Windows bloque | Réexécuter `firewall-rules.ps1` en admin |
| `CORS error` côté navigateur | Origine non listée | Vérifier `app.cors.allowed-origins` dans `application.yml` |
| `Invalid Host header` | `allowedHosts` non config | Vérifier que `npm run start:lan` est utilisé (pas `npm start`) |
| 401 sur tous les endpoints | `JWT_SECRET` absent ou trop court | `$env:JWT_SECRET` doit faire ≥ 64 caractères |
| 500 au démarrage backend | DB introuvable | Vérifier `$env:DB_HOST`/`DB_PORT`/`DB_USERNAME`/`DB_PASSWORD` |
| Frontend lent au 1er affichage | Build production AOT en cours | Normal au 1er démarrage `ng serve --configuration lan` (~30s) |

## Production "vraie" (futur)

`ng serve` est un dev-server. Pour la prod stricte :

```powershell
cd cityfrontend
npm run build:prod
# → produit dist/cityfrontend/ (HTML/CSS/JS statiques)
```

Puis servir `dist/cityfrontend/browser/` via :

- **IIS** (Windows) — config de réécriture pour SPA `try_files`
- **Nginx** (containerisé)
- **http-server** Node.js pour test rapide : `npx http-server dist/cityfrontend/browser -p 4200 --proxy http://192.168.100.141:8080/citybackend`

Le backend reste lancé via `start-backend.ps1` (ou enregistré comme service Windows via [WinSW](https://github.com/winsw/winsw)).
