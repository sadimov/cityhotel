# Plan d'exécution — city hotel v1.0.0

Guide pas-à-pas pour démarrer, tester, builder et déployer l'application city hotel.

**Version** : v1.0.0 — **Stack palier 1** : Java 21 + Spring Boot 3.4.5 + Angular 21.2 + PostgreSQL 18.3.

---

## 1. Pré-requis (à installer une fois)

| Outil | Version min | Vérification |
|---|---|---|
| Java JDK | **21 LTS** (Temurin recommandé) | `java -version` → `21.x.x` |
| Node.js | **24 LTS** (Krypton) | `node -v` → `v24.x.x` |
| npm | ≥ 10.9 | `npm -v` |
| PostgreSQL | **18.3** | `psql --version` |
| Git | ≥ 2.40 | `git --version` |
| Docker (optionnel) | dernière | `docker --version` (pour déploiement prod ou Testcontainers) |

### Installation Windows (winget)
```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK
winget install --id OpenJS.NodeJS.LTS
winget install --id PostgreSQL.PostgreSQL.18
winget install --id Git.Git
winget install --id Docker.DockerDesktop
```

### Installation macOS (brew)
```bash
brew install temurin@21 node@24 postgresql@18 git
brew install --cask docker
```

### Installation Linux (apt Debian/Ubuntu)
```bash
sudo apt install openjdk-21-jdk nodejs npm postgresql-18 git
```

### Configuration `JAVA_HOME` (obligatoire si plusieurs JDK)
```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```
```bash
# Linux/macOS
export JAVA_HOME="/usr/lib/jvm/temurin-21-jdk-amd64"   # adapter selon distrib
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## 2. Setup initial (1 fois par poste)

### 2.1 Cloner le repo
```bash
git clone https://github.com/sadimov/cityhotel.git
cd cityhotel
git checkout v1.0.0          # ou main pour suivre le développement
```

### 2.2 Créer la base PostgreSQL
```bash
psql -U postgres -c "CREATE DATABASE cityprojectdb;"
psql -U postgres -c "CREATE USER cityapp WITH ENCRYPTED PASSWORD 'change-me-in-prod';"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE cityprojectdb TO cityapp;"
```

### 2.3 Variables d'environnement (obligatoires en prod)
```powershell
# Windows PowerShell
$env:JWT_SECRET = "$(openssl rand -base64 64 | tr -d '=+/')"   # ≥ 64 chars random
$env:DB_USERNAME = "cityapp"
$env:DB_PASSWORD = "change-me-in-prod"
$env:MAIL_PASSWORD = ""   # vide en dev (pas de SMTP)
```
```bash
# Linux/macOS
export JWT_SECRET="$(openssl rand -base64 64 | tr -d '=+/')"
export DB_USERNAME="cityapp"
export DB_PASSWORD="change-me-in-prod"
export MAIL_PASSWORD=""
```

⚠️ **Boot fail-fast** : Spring Boot refuse de démarrer si `JWT_SECRET` absent ou commence par `mySecretKey` (anti-leak). En dev `application-dev.yml` accepte le default `dev-only-do-not-use-in-prod-...`.

### 2.4 Installer les dépendances frontend
```bash
cd cityfrontend
npm ci                        # strict, utilise package-lock.json
cd ..
```

### 2.5 (Backend) Vérifier que le wrapper Maven est exécutable
```bash
cd citybackend
chmod +x mvnw                 # Linux/macOS uniquement
./mvnw -version               # confirme Maven 3.9.x + Java 21
cd ..
```

---

## 3. Démarrage en local (dev)

### 3.1 Terminal 1 — Backend Spring Boot
```bash
cd citybackend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# OU sous Windows si JAVA_HOME pose problème :
# JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot" ./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Au premier démarrage Liquibase exécute tous les changesets (schémas core/clients/hebergement/inventory/finance/restaurant/menage + seeds + paramètres).

✅ **Boot OK** : `Started CitybackendApplication in X.XXX seconds (process running for Y.YYY)`
- Backend dispo : http://localhost:8080/citybackend
- API : http://localhost:8080/citybackend/api/...
- Actuator health : http://localhost:8080/citybackend/actuator/health (public)
- Actuator détails : http://localhost:8080/citybackend/actuator/info (SUPERADMIN)

### 3.2 Terminal 2 — Frontend Angular
```bash
cd cityfrontend
npm start                     # ng serve --port 4200
```

✅ **Boot OK** : `** Angular Live Development Server is listening on localhost:4200 **`
- Frontend dispo : http://localhost:4200

### 3.3 Premier login
1. Ouvrir http://localhost:4200/login
2. **Username** : `superadmin`
3. **Password initial** : `SuperAdmin123!`

⚠️ Le compte a `mot_passe_temporaire=true`. L'endpoint `/auth/change-password` n'est pas câblé en v1.0.0 (Vague 3). Pour rotation manuelle :
```bash
psql -U postgres -d cityprojectdb -c "UPDATE core.dbusers SET mot_passe = '\$2a\$10\$<bcrypt-hash>' WHERE username = 'superadmin';"
# Générer un hash BCrypt : https://bcrypt-generator.com/ ou via le projet (tests JUnit avec PasswordUtil)
```

---

## 4. Tests

### 4.1 Surefire (unitaires, rapides)
```bash
cd citybackend
./mvnw test
# attendu : Tests run: 147, Failures: 0, Errors: 0, Skipped: 0
```

### 4.2 Failsafe (intégration H2 en mémoire)
```bash
cd citybackend
./mvnw verify
# attendu : Tests run: 62 (Failsafe), Failures: 0, Errors: 0, Skipped: 0
# Surefire 147 + Failsafe 62 = 209 tests verts
```

### 4.3 Tests frontend (Karma + Jasmine)
```bash
cd cityfrontend
npm test -- --watch=false --browsers=ChromeHeadless
```

### 4.4 Tests rapides en CI (skip ITs longs si besoin)
```bash
cd citybackend
./mvnw test -Dtest='!*ConcurrencyIT'
```

---

## 5. Build production

### 5.1 Backend — JAR exécutable
```bash
cd citybackend
./mvnw clean package -DskipTests
# génère : target/citybackend-1.0.0.jar
java -jar target/citybackend-1.0.0.jar --spring.profiles.active=prod
```

### 5.2 Backend — Image OCI via Jib (sans Docker daemon)
```bash
cd citybackend

# Build vers registry distant (push direct)
./mvnw jib:build -Djib.to.image=ghcr.io/sadimov/citybackend:1.0.0

# OU build local en tarball (sans push)
./mvnw jib:buildTar
docker load --input target/jib-image.tar     # si Docker dispo

# OU build vers Docker daemon local
./mvnw jib:dockerBuild
```

Image générée :
- Base : `eclipse-temurin:21-jre-alpine`
- User : `nonroot:nonroot` (sécurité)
- Port : `8080`
- Format : OCI

### 5.3 Frontend — bundle Angular
```bash
cd cityfrontend
npm run build -- --configuration=production
# génère : dist/cityfrontend/browser/ (index.html + chunks JS/CSS)
```

### 5.4 Frontend — Image Docker
```bash
docker build -f deploiement/ressources/docker/Dockerfile.frontend -t cityfrontend:1.0.0 .
```

---

## 6. Déploiement avec docker-compose

### 6.1 Variables `.env` à la racine de `deploiement/ressources/docker/`
Copier le template :
```bash
cp deploiement/ressources/docker/.env.prod.example deploiement/ressources/docker/.env
```

Éditer `.env` avec les vraies valeurs :
```env
JWT_SECRET=<min 64 chars random>
DB_USERNAME=cityapp
DB_PASSWORD=<password fort>
POSTGRES_PASSWORD=<password postgres root>
MAIL_HOST=smtp.example.com
MAIL_USERNAME=noreply@city-hotel.local
MAIL_PASSWORD=<smtp password>
DOCKER_REGISTRY=ghcr.io/sadimov
APP_VERSION=1.0.0
TRAEFIK_HOST=cityhotel.example.com   # adapter au domaine prod
```

### 6.2 Lancer la stack complète
```bash
cd deploiement/ressources/docker
docker compose --env-file .env -f docker-compose.prod.yml up -d
```

Services lancés :
- **traefik** (reverse proxy + SSL Let's Encrypt automatique)
- **cityfrontend** (nginx port 80 derrière traefik)
- **citybackend** (Spring Boot port 8080 derrière traefik)
- **postgres** (PG 18.3, volume persistant `pgdata`, **jamais exposé** publiquement)
- **postgres-backup** (cron 02h00 NKC, dump quotidien, rétention 30j)

### 6.3 Vérifier l'état
```bash
docker compose ps
docker compose logs -f citybackend       # suivre logs backend
docker compose logs -f cityfrontend
docker compose exec postgres psql -U postgres -d cityprojectdb -c "\dt core.*"
```

### 6.4 Arrêter / mettre à jour
```bash
docker compose down                       # arrête sans toucher aux volumes
docker compose pull && docker compose up -d   # rolling update images
docker compose down -v                    # ⚠️ DÉTRUIT les volumes (perte BDD)
```

---

## 7. Commandes admin / dev courantes

### 7.1 Liquibase — état des migrations
```bash
cd citybackend
./mvnw liquibase:status -Dliquibase.changeLogFile=src/main/resources/db/changelog/db.changelog-master.xml
./mvnw liquibase:updateSQL > pending-migrations.sql      # voir le SQL en attente sans l'appliquer
```

### 7.2 Reset complet de la BDD locale (dev uniquement)
```bash
psql -U postgres -c "DROP DATABASE cityprojectdb;"
psql -U postgres -c "CREATE DATABASE cityprojectdb;"
cd citybackend && ./mvnw spring-boot:run    # Liquibase recrée tout
```

### 7.3 Inspecter la BDD
```bash
# Lister les tables d'un schéma
psql -U postgres -d cityprojectdb -c "\dt clients.*"
psql -U postgres -d cityprojectdb -c "\dt finance.*"

# Compter les hôtels actifs
psql -U postgres -d cityprojectdb -c "SELECT hotel_id, hotel_nom, actif FROM core.hotels;"

# Voir les utilisateurs SUPERADMIN
psql -U postgres -d cityprojectdb -c "SELECT u.username, h.hotel_nom, r.nom AS role FROM core.dbusers u LEFT JOIN core.hotels h ON h.hotel_id = u.hotel_id LEFT JOIN core.roles r ON r.role_id = u.role_id WHERE r.nom = 'SUPERADMIN';"
```

### 7.4 Health check + métriques en prod
```bash
curl https://cityhotel.example.com/citybackend/actuator/health
# avec JWT SUPERADMIN :
curl -H "Authorization: Bearer $TOKEN" https://cityhotel.example.com/citybackend/actuator/info
curl -H "Authorization: Bearer $TOKEN" https://cityhotel.example.com/citybackend/actuator/metrics
```

### 7.5 Login + récupération JWT (curl)
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/citybackend/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"superadmin","password":"SuperAdmin123!"}' | jq -r '.data.accessToken')
echo $TOKEN
```

### 7.6 Appel API authentifié
```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/citybackend/api/admin/hotels
```

### 7.7 Refresh du token
```bash
NEW_TOKEN=$(curl -s -X POST http://localhost:8080/citybackend/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}" | jq -r '.data.accessToken')
```

---

## 8. Workflow git recommandé

### 8.1 Branches
- **main** : trunk-based development, tous les commits passent par main (pas de longue branche feature)
- **release** : tag `v<MAJOR>.<MINOR>.<PATCH>` annoté

### 8.2 Cycle de dev
```bash
git pull --rebase origin main           # avant de commencer
# ... travail ...
cd citybackend && ./mvnw verify          # 147+62 tests verts
cd cityfrontend && npm test              # tests Karma
git status && git diff
git add <fichiers ciblés>                # JAMAIS git add -A
git commit -m "<type>(<scope>): <résumé impératif>"
git push origin main
```

### 8.3 Conventional commits
- Types : `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `perf`, `style`, `build`, `ci`
- Scopes : `auth`, `clients`, `hebergement`, `finance`, `restaurant`, `menage`, `inventory`, `admin`, `core`, `infra`, `devops`, `release`
- Exemples :
  ```
  feat(menage): ajoute workflow annulation tâche
  fix(finance): empêche les trous de numérotation FACT en concurrence
  chore(deps): bump Spring Boot 3.4.5 → 3.4.6
  ```

### 8.4 Release v1.X.0
```bash
# 1. S'assurer que main est vert
cd citybackend && ./mvnw verify
cd cityfrontend && npm run build -- --configuration=production

# 2. Créer le tag annoté
git tag -a v1.1.0 -m "city hotel v1.1.0 — <description courte>"

# 3. Pousser le tag
git push origin v1.1.0

# 4. Créer la release notes
cp RELEASE_NOTES_v1.0.0.md RELEASE_NOTES_v1.1.0.md
# éditer le fichier
git add RELEASE_NOTES_v1.1.0.md
git commit -m "docs(release): add RELEASE_NOTES_v1.1.0.md"
git push origin main

# 5. Publier la release sur GitHub
gh release create v1.1.0 --notes-file RELEASE_NOTES_v1.1.0.md --title "v1.1.0 — ..."
# OU UI web : https://github.com/sadimov/cityhotel/releases/new?tag=v1.1.0
```

---

## 9. CI/CD

### 9.1 GitHub Actions (auto sur push/PR)
Pipeline défini dans `deploiement/ressources/ci/github-actions-build.yml` :
- Trigger : push main + PR
- Jobs : test-backend (Surefire+Failsafe avec secrets injectés) → sonar-backend (sur main, skip si SONAR_TOKEN absent) → test-frontend (lint+tsc+ng test) → build-images (Jib back + Docker front, skip PR) → deploy-prod (sur tag `v*`, environment GitHub avec approval natif)
- Secrets requis : `GHCR_PAT`, `K8S_KUBECONFIG`, `SONAR_TOKEN`, `SONAR_HOST_URL`, `JWT_SECRET`, `DB_USERNAME`, `DB_PASSWORD`, `MAIL_PASSWORD`

### 9.2 Jenkinsfile (alternatif)
Pipeline déclaratif dans `deploiement/ressources/ci/Jenkinsfile` :
- `parameters` : RUN_SONAR, DOCKER_REGISTRY, REGISTRY_OWNER
- `tools { jdk 'jdk-21' }`
- Stages : Checkout → Audit & Lint (parallèle) → Tests (parallèle, secrets via withCredentials) → Sonar (conditionnel) → Build & Push images (parallèle Jib + Docker) → Deploy staging → Approval prod → Deploy prod kustomize → Smoke test
- Credentials Jenkins requis : `city-jwt-secret`, `city-db-username`, `city-db-password`, `city-mail-password`, `sonar-token`

### 9.3 Lancer Sonar manuellement
```bash
# Démarrer Sonar local (Docker requis)
docker run -d --name sonarqube -p 9000:9000 sonarqube:latest

# Attendre que Sonar soit prêt sur http://localhost:9000 (admin/admin au premier login)
# Créer un projet "city" et générer un token

cd citybackend
./mvnw sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=$SONAR_TOKEN
```

---

## 10. Troubleshooting

### `Error: Boot fail-fast — JWT_SECRET missing or starts with 'mySecretKey'`
**Cause** : variable d'environnement `JWT_SECRET` absente ou trop faible.
**Fix** : `export JWT_SECRET="$(openssl rand -base64 64)"` puis relancer.

### `Liquibase changeset checksum mismatch`
**Cause** : un changeset déjà appliqué a été modifié.
**Fix** : NE JAMAIS modifier un changeset existant. Créer un changeset additif `00X-fix-...xml`. Si déjà cassé en local : `psql -d cityprojectdb -c "DELETE FROM databasechangelog WHERE id = '<changeset-id>';"` puis relancer.

### `npm ERR! ENOENT: node_modules/.bin/ng`
**Cause** : `npm ci` n'a pas tourné après pull.
**Fix** : `rm -rf cityfrontend/node_modules cityfrontend/package-lock.json && cd cityfrontend && npm ci`.

### `Tests Failsafe rouges : DataSource autoConfigure`
**Cause** : un changeset Liquibase casse H2 (souvent triggers PL/pgSQL Postgres-only mal annotés).
**Fix** : vérifier que les changesets Postgres-only ont bien `dbms="postgresql"`.

### `Java 17 detected, expected 21`
**Cause** : `JAVA_HOME` pointe vers JDK 17.
**Fix** : `export JAVA_HOME="<chemin JDK 21>"` ou utiliser `./mvnw.cmd` Windows avec env var inline.

### `429 Too Many Requests sur /auth/login`
**Cause** : RateLimitFilter actif (10 req/60s/IP).
**Fix** : attendre 60s, ou ajuster la limite dans `application.yml` `resilience4j.ratelimiter.instances.auth-endpoints`.

### `403 Forbidden sur /api/admin/...`
**Cause** : votre user n'a pas le rôle SUPERADMIN.
**Fix** : reconnectez-vous avec le compte `superadmin`, ou attribuez le rôle SUPERADMIN à un autre user via :
```sql
UPDATE core.dbusers SET role_id = (SELECT role_id FROM core.roles WHERE nom = 'SUPERADMIN') WHERE username = '<your-user>';
```

---

## 11. Liens utiles

| | |
|---|---|
| Repo | https://github.com/sadimov/cityhotel |
| Release v1.0.0 | https://github.com/sadimov/cityhotel/releases/tag/v1.0.0 |
| Issues | https://github.com/sadimov/cityhotel/issues |
| Doctrine projet | `CLAUDE.md` racine |
| Conventions backend | `citybackend/CLAUDE.md` |
| Conventions frontend | `cityfrontend/CLAUDE.md` |
| Cartographie historique | `CARTOGRAPHIE_MODULES.md` |
| Notes release | `RELEASE_NOTES_v1.0.0.md` |
| Anti-patterns | `ERREURS_AUDIT_A_EVITER.html` |
| Plan comptable | `plan_comptable_mauritanien.pdf` |
| Modes paiement | `modes_paiements.txt` |
| Règles night audit | `règles_night_audit.txt` |
| Rôles utilisateurs | `roles_utilisateurs.txt` |

---

**Fin Plan d'exécution v1.0.0**
