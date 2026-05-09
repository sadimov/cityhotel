# Procédure 2 — Préparation de l'application & gestion du code

> **📖 Pour les non-spécialistes** : si « image Docker », « registre », « SemVer », « CI/CD », « SealedSecret » sont nouveaux pour vous, lisez d'abord [`formation.md §4`](formation.md) (Docker) et [`§7`](formation.md) (CI/CD + secrets). Chaque section ci-dessous démarre par un encadré `💡 En clair` qui explique l'enjeu en français courant.

> **Objectif** : à partir du code Windows (citybackend + cityfrontend), produire des **artefacts de production** : deux images Docker (back + front), des manifests Kubernetes (ou un `docker-compose.prod.yml`), une chaîne CI/CD reproductible, et une stratégie de gestion des secrets.
> **Durée** : ~4 heures la première fois, puis quelques minutes par release (CI/CD).
> **Prérequis** : procédure 1 terminée + accès git en push au dépôt city + accès `docker login` au registre choisi.

> **Rappel** : aucune modification du code applicatif dans cette procédure. Tous les paramètres se passent par variables d'environnement (déjà câblées dans `application.yml` profil `prod`) et par `fileReplacements` Angular au build.

---

## 1. Stratégie de versioning et de release

> **💡 En clair** — Une **release** est une version officielle du logiciel (ex : `v1.0.0`) qu'on déploie en prod. **SemVer** (Semantic Versioning) = convention pour numéroter : `MAJEUR.MINEUR.PATCH` — augmenter MAJEUR si on casse la compatibilité, MINEUR si on ajoute une fonctionnalité, PATCH pour un bugfix. **Tag git signé GPG** = un point de l'historique git verrouillé avec une signature cryptographique pour prouver « c'est bien le tech lead qui a déclaré cette version ». **Tag d'image Docker** = nom unique d'une version d'image dans le registre (ex : `city-backend:1.0.0`). Règle : `latest` interdit en prod (on ne sait jamais ce qu'il pointe), toujours un tag immuable.

### 1.1 Branches & tags

- Branche `main` = état déployable. Toute fusion sur `main` doit passer la CI verte (`mvn verify` + `ng build --configuration=production` + audits city).
- Tags SemVer signés GPG : `v1.0.0`, `v1.0.1`, `v1.1.0-rc.1`. Format : `vMAJOR.MINOR.PATCH[-PRE]`.
- Convention de commit : Conventional Commits (cf. skill `prep-commit`). Le tag est généré par CI à partir du dernier commit de release.
- **L'image Docker porte le même tag que le tag git.** Pas d'image `latest` en prod (sauf le tag flottant pour le `dev`).

### 1.2 Tags d'image

| Tag image | Sémantique |
|-----------|------------|
| `ghcr.io/<org>/city-backend:1.2.3` | release immutable, GPG signée |
| `ghcr.io/<org>/city-backend:1.2.3-sha-abcdef0` | tag de traçabilité (git short sha) |
| `ghcr.io/<org>/city-backend:edge` | flottant, alimenté par chaque push sur `main` (pré-prod uniquement) |
| `ghcr.io/<org>/city-backend:latest` | **interdit en prod** (utilisable seulement sur poste dev) |

Idem pour `city-frontend`.

### 1.3 Build prod du frontend Angular — point ouvert

Le `environment.ts` actuel est codé en dur sur `http://localhost:8080/citybackend`. Le palier 1 city utilise NgModule + Angular CLI 21. Pour la prod, **il faut** :

- créer (côté code, dans une autre session) `cityfrontend/src/environments/environment.prod.ts` avec `production: true` et `apiUrl: '/citybackend'` (chemin relatif derrière Traefik) ;
- déclarer dans `cityfrontend/angular.json` un `fileReplacements` pour la configuration `production`.

> **À traiter dans la session de dev** (ce fichier ne le fait pas). En attendant, le Dockerfile frontend supporte une variable `BUILD_CONFIG` (par défaut `production`) ; dès que `environment.prod.ts` existe, le build sera correct.

Workaround temporaire (sans toucher au code) : injecter `apiUrl` à l'exécution via un fichier `assets/config.json` chargé au démarrage de l'app — mais cela demande aussi du code. **Préférer la création de `environment.prod.ts`** dès que possible.

### 1.4 Versions à figer dans les images

| Composant | Version palier 1 | Image source |
|-----------|------------------|--------------|
| Backend JRE | Java 21 | `eclipse-temurin:21.0.5_11-jre-noble` (pas alpine — incompat libc avec libpostgres natif) |
| Frontend nginx | 1.27 stable | `nginx:1.27-alpine` |
| Postgres | 18.3 | `postgres:18.3-bookworm` |
| Traefik | 3.2.x | `traefik:v3.2.3` |
| Build node | 24 LTS | `node:24-bookworm` |
| Build Maven | Maven 3.9 sur JDK 21 | `eclipse-temurin:21.0.5_11-jdk-noble` (multi-stage) |

Toutes ces versions sont les planchers. Vérifier sur Docker Hub la dernière patch au moment du build.

---

## 2. Conteneurisation des composants

> **💡 En clair** — On emballe l'application dans des « images Docker » : des paquets autonomes contenant **tout** ce qu'il faut pour la faire tourner (Java, code, dépendances, fichiers de config). Une fois qu'une image est construite, elle se lance pareil partout (laptop, serveur, cloud). **Multi-stage** = technique pour produire une image légère : une 1ʳᵉ étape compile le code (avec tout l'outillage), une 2ᵉ étape ne garde que le résultat (sans les outils de build). **Jib** = plugin Maven Google qui produit l'image directement depuis le pom.xml, sans avoir besoin de Docker installé sur la machine de build (super pratique en CI). **Distroless / JRE seulement** = image de base minimaliste qui ne contient pas de shell ni d'outils — réduit la surface d'attaque.

### 2.1 Backend — deux options

**Option A : Jib (recommandé palier 1, déjà déclaré dans CLAUDE.md)**

Le `pom.xml` city peut intégrer Jib (déjà mentionné CLAUDE.md §3 : `Jib 3.4.4`). Génération sans démon Docker, idéale en CI :

```bash
cd citybackend
./mvnw -DskipTests \
  -Djib.to.image=ghcr.io/<org>/city-backend:1.2.3 \
  -Djib.to.auth.username=$GHCR_USER \
  -Djib.to.auth.password=$GHCR_TOKEN \
  -Djib.from.image=eclipse-temurin:21.0.5_11-jre-noble \
  -Djib.container.jvmFlags="-XX:MaxRAMPercentage=75,-XX:+UseG1GC,-Duser.timezone=Africa/Nouakchott" \
  -Djib.container.environment=SPRING_PROFILES_ACTIVE=prod \
  -Djib.container.ports=8080 \
  -Djib.container.workingDirectory=/app \
  -Djib.container.user=10001 \
  com.google.cloud.tools:jib-maven-plugin:3.4.4:build
```

> Si le `pom.xml` ne contient pas Jib en build plugin, le passage en CLI (`com.google.cloud.tools:jib-maven-plugin:3.4.4:build`) fonctionne sans modification du pom.

**Option B : Multi-stage Docker classique**

Voir [`ressources/docker/Dockerfile.backend`](ressources/docker/Dockerfile.backend). Avantage : self-contained ; inconvénient : nécessite un démon Docker.

```bash
docker build -f deploiement/ressources/docker/Dockerfile.backend \
             -t ghcr.io/<org>/city-backend:1.2.3 \
             citybackend/
```

### 2.2 Frontend — multi-stage nginx

Voir [`ressources/docker/Dockerfile.frontend`](ressources/docker/Dockerfile.frontend).

```bash
docker build -f deploiement/ressources/docker/Dockerfile.frontend \
             --build-arg API_BASE=/citybackend \
             -t ghcr.io/<org>/city-frontend:1.2.3 \
             cityfrontend/
```

L'image embarque :
- Le bundle Angular en `/usr/share/nginx/html/`.
- Une config `nginx.conf` qui **ne touche pas** au backend (Traefik joue ce rôle), mais qui :
  - Active gzip/brotli,
  - Rewrite `try_files` SPA → `index.html`,
  - Force les headers de sécurité (CSP, X-Content-Type-Options, Referrer-Policy),
  - Cache long pour `*.js|*.css|*.woff2`, court pour `index.html` (1 min).

### 2.3 Postgres — image officielle

**Aucune image custom**. Configuration via env vars + ConfigMap pour `postgresql.conf` (cf. `ressources/k8s/postgres-statefulset.yaml`).

Tunings minimaux pour le profil city (16 Gi RAM serveur) :
```
shared_buffers = 2GB
effective_cache_size = 6GB
work_mem = 16MB
maintenance_work_mem = 512MB
max_connections = 100
wal_compression = on
log_min_duration_statement = 1000
log_line_prefix = '%t [%p] %u@%d '
log_lock_waits = on
log_temp_files = 0
checkpoint_timeout = 15min
max_wal_size = 4GB
random_page_cost = 1.1     # SSD/NVMe
```

---

## 3. Mode "Docker Compose" (plan B simple)

> **💡 En clair** — Au lieu de demander à Kubernetes de tout orchestrer, on écrit un fichier `docker-compose.prod.yml` qui décrit les 5 conteneurs (Traefik, frontend, backend, postgres, backup) et leurs liens. Une seule commande `docker compose up -d` lance tout. **`.env`** = fichier (non versionné) qui contient les valeurs sensibles (mots de passe, domaine) injectées au démarrage. **Variables substituées** = le compose remplace `${POSTGRES_PASSWORD}` par la valeur du `.env` au moment du `up`. Le tout reste utilisable ensuite, on peut redéployer en un seul `docker compose pull && up -d`.

Pour démarrer en **moins d'une heure** sans K8s :

```bash
# Sur le serveur, après procédure 1 §F
cd /srv/city/compose
# Copier (ou git clone restreint) les fichiers depuis deploiement/ressources/docker/ :
#   - docker-compose.prod.yml
#   - .env.prod.example  (à renommer .env et compléter)
#   - traefik/ (dynamique config)
#   - nginx/  (config frontend si pas dans l'image)
cp .env.prod.example .env
chmod 600 .env
$EDITOR .env                          # remplir DB_PASSWORD, JWT_SECRET, ACME_EMAIL, DOMAIN

docker compose --env-file .env config       # vérification syntaxique
docker compose --env-file .env pull         # tirer les images
docker compose --env-file .env up -d        # démarrer
docker compose ps
```

Voir [`ressources/docker/docker-compose.prod.yml`](ressources/docker/docker-compose.prod.yml).

---

## 4. Mode Kubernetes (K3s) — manifests

> **💡 En clair** — Avec Kubernetes, on ne fait pas `docker run`. On **décrit** dans des fichiers YAML l'état souhaité (« je veux 2 backends, 1 postgres avec 200 Go de disque, accessible via tel domaine ») et K8s s'arrange pour faire converger la réalité vers cette description. Concepts clés :
> - **Manifest** = fichier YAML qui décrit un objet K8s (Deployment, Service, etc.).
> - **Deployment** = consigne « assure-toi que N replicas de ce conteneur tournent toujours ».
> - **StatefulSet** = comme Deployment mais pour des composants à état (postgres) : nom de pod stable, volume attaché.
> - **Service** = adresse IP/DNS interne stable qui pointe vers les pods (les pods peuvent être recréés, le Service reste).
> - **Ingress** = règle qui expose un Service à l'extérieur via un nom de domaine.
> - **HPA** (HorizontalPodAutoscaler) = ajoute automatiquement des pods quand la charge monte.
> - **Probes** = sondes (liveness = est-il vivant ? readiness = est-il prêt à servir ? startup = a-t-il fini de démarrer ?).
> - **Kustomize** = outil qui assemble plusieurs YAML pour produire le manifest final, avec possibilité de remplacer des valeurs par environnement.

Layout `ressources/k8s/` :

```
namespace.yaml                — namespace `city`
configmap-backend.yaml        — paramètres non sensibles (TZ, profile, log level)
secret-backend.yaml.example   — JWT_SECRET, MAIL_PASSWORD (à kustomize ou sealed-secrets)
secret-postgres.yaml.example  — POSTGRES_PASSWORD
postgres-statefulset.yaml     — StatefulSet 1 replica + Service ClusterIP + PVC 200 Gi
backend-deployment.yaml       — Deployment 2 replicas + Service + HPA
frontend-deployment.yaml      — Deployment 2 replicas + Service
ingress.yaml                  — IngressRoute Traefik avec cert-manager
hpa-backend.yaml              — HorizontalPodAutoscaler CPU 70% + 2..6 replicas
networkpolicy.yaml            — NetworkPolicies (backend → postgres only ; frontend → backend only)
poddisruptionbudget.yaml      — PDB minAvailable 1 sur backend/frontend
backup-cronjob.yaml           — CronJob pg_dump quotidien 02:00 NKC
letsencrypt-issuer.yaml       — ClusterIssuer cert-manager (à appliquer après DNS prêt)
kustomization.yaml            — agrégateur Kustomize
```

Application :

```bash
# Toujours d'abord en --dry-run
kubectl apply -k deploiement/ressources/k8s/ --dry-run=server -o yaml | less

# Puis pour de vrai (procédure 3)
kubectl apply -k deploiement/ressources/k8s/
```

### 4.1 Stratégie de déploiement

Backend : `RollingUpdate` `maxUnavailable=0` `maxSurge=1` → zéro downtime. Probe :
- `livenessProbe` `/citybackend/actuator/health/liveness` toutes les 30 s, 3 échecs avant kill.
- `readinessProbe` `/citybackend/actuator/health/readiness` toutes les 10 s.
- `startupProbe` initialDelay 60 s, period 10 s, failureThreshold 30 (couvre démarrage Liquibase).

Frontend : `RollingUpdate` standard.

Postgres : `OnDelete` (StatefulSet) — jamais de rolling automatique sur la DB.

### 4.2 Affinité, taints, priorityClass

Sur single-node, pas de taint. PriorityClass :
- `city-critical` (1000) → postgres
- `city-application` (500) → backend
- `city-presentation` (100) → frontend

Ajouter sur les Pods (cf. manifests).

---

## 5. Gestion des secrets

> **💡 En clair** — Comment stocker des mots de passe et des jetons sans les écrire dans git ? Trois techniques courantes :
> - **Sealed-Secrets** : on chiffre le secret avec une clé publique du cluster. Le résultat (`SealedSecret`) est inoffensif et peut être commité — seul le cluster peut le déchiffrer.
> - **SOPS + age** : on chiffre les fichiers YAML localement avec une clé `age` (ou GPG). Le développeur déchiffre à la volée avec sa clé privée.
> - **Vault / External Secrets** : un coffre-fort centralisé (ex. HashiCorp Vault) auquel les Pods demandent leurs secrets via un opérateur K8s.
>
> **Rotation** = remplacer périodiquement le secret. Si une clé fuite (laptop volé, bug dans un log), elle devient inutile en quelques semaines. C'est la 2ᵉ couche de défense (la 1ʳᵉ étant de ne pas la perdre).

### 5.1 Inventaire des secrets

| Secret | Format | Source | Utilisé par |
|--------|--------|--------|-------------|
| `JWT_SECRET` | 64 caractères aléatoires (base64) | `openssl rand -base64 48` | backend |
| `DB_PASSWORD` | 32 caractères aléatoires | `pwgen -ynsBv 32 1` | backend + postgres init |
| `POSTGRES_PASSWORD` | identique au précédent | idem | postgres image |
| `MAIL_USERNAME` | adresse SMTP | infrastructure mail | backend |
| `MAIL_PASSWORD` | mot de passe SMTP | fournisseur SMTP | backend |
| `ACME_EMAIL` | adresse pour Let's Encrypt | équipe ops | cert-manager |
| `BACKUP_GPG_RECIPIENT` | empreinte GPG du destinataire | clé d'archivage | CronJob backup |
| `OFFSITE_RSYNC_KEY` | clé SSH privée pour rsync | équipe ops | CronJob backup |

### 5.2 Stockage

**Production** : un de ces 3 mécanismes, **jamais** en clair dans git :

1. **Sealed-Secrets** (Bitnami) — recommandé pour single-cluster. Chiffrement asymétrique, manifests `SealedSecret` poussables sur git.
   ```bash
   helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
   helm install sealed-secrets sealed-secrets/sealed-secrets -n kube-system
   # Puis kubeseal pour générer les SealedSecrets
   ```
2. **SOPS + age** — chiffrement local + déchiffrement par CI/CD.
3. **External Secrets Operator** + Vault / AWS Secrets Manager — si vous avez déjà un coffre.

> **À aucun moment** un secret ne doit apparaître dans `git log`, `kubectl get secret -o yaml` exporté, un screenshot, ou un mail.

### 5.3 Rotation

| Secret | Fréquence | Procédure |
|--------|-----------|-----------|
| `JWT_SECRET` | À chaque release majeure (déconnecte tous les users) | Mise à jour Secret + rolling restart backend |
| `DB_PASSWORD` | Trimestrielle | Voir [`runbook §4`](runbook_exploitation.md) |
| `MAIL_PASSWORD` | Annuelle ou si compromis | idem |
| Clés SSH admin | À chaque départ | révoquer dans `~cityadmin/.ssh/authorized_keys` |
| Certificats TLS | Auto (60 j Let's Encrypt) | rien à faire — vérifier alerte cert-manager |

---

## 6. CI/CD

> **💡 En clair** — **CI** (Continuous Integration) = à chaque push de code, on compile + teste automatiquement, pour détecter les régressions tôt. **CD** (Continuous Delivery / Deployment) = construit l'image, la pousse au registre, et (selon les cas) la déploie. **Pipeline** = suite d'étapes définies dans un fichier (Jenkinsfile, github-actions.yml). Avantage : le déploiement n'est plus « le sysadmin tape 50 commandes » (source d'erreurs) mais « le pipeline déroule la même séquence à chaque fois ». **Approval manuel** = gate humain placé avant le déploiement prod : un humain doit cliquer « approuver » — protection contre un push raté qui partirait directement en prod.

### 6.1 Choix : Jenkins LTS (référence CLAUDE.md) ou GitHub Actions

Les deux sont décrits ; au choix de l'équipe. **Recommandation** : GitHub Actions si le code est sur GitHub (gratuit pour repos privés jusqu'à 2000 min/mois), Jenkins si infrastructure existante.

### 6.2 Pipeline minimum (5 étapes)

```
1. checkout              (clone + git lfs si médias)
2. test                   (./mvnw verify  + ng test --watch=false --browsers=ChromeHeadless)
3. audit                  (sync-tech, multitenant-check, lint)
4. build images           (Jib backend + docker build frontend)
5. push registre          (ghcr.io ou Harbor)
[6. deploy staging        (auto sur tag *-rc.*)]
[7. deploy prod           (sur tag final, requiert approval manuel)]
```

Voir [`ressources/ci/Jenkinsfile`](ressources/ci/Jenkinsfile) et [`ressources/ci/github-actions-build.yml`](ressources/ci/github-actions-build.yml).

### 6.3 Approval manuel obligatoire en prod

Le step `deploy prod` doit nécessiter :
- Tag git signé GPG.
- Approval d'un second développeur (PR review + tag).
- Notification Slack/email avant et après.

### 6.4 Rollback

Stratégie immutable : `kubectl rollout undo deployment/city-backend -n city`. Sous Compose : `docker compose pull <image:previous-tag> && docker compose up -d`. La DB n'est **pas** rollbackée — Liquibase doit toujours être additif (cf. `citybackend/CLAUDE.md §6`).

---

## 7. Préparation de la base de données

> **💡 En clair** — La base PostgreSQL démarre **vide** au premier déploiement. C'est **Liquibase** (déjà câblé dans Spring Boot) qui crée toutes les tables à partir de fichiers XML versionnés (`changelog`). Avantage : la structure de la base est dans le code, pas dans la tête du DBA — on peut reproduire à l'identique. Règle d'or Liquibase : **on n'édite JAMAIS un changeset déjà appliqué**, on en ajoute un nouveau. Sinon les autres environnements détectent une divergence et refusent de démarrer. **Seeding** = insertion des données de référence indispensables (rôles, hôtel initial, plan comptable) — sans ces données, l'app ne fonctionne pas.

### 7.1 Schémas

Le snapshot `structure_cityprojectdb02052026-21h46.sql` à la racine donne la photographie attendue. Il liste 8 schémas (`core`, `clients`, `hebergement`, `inventory`, `finance`, `restaurant`, `menage`, `reporting`) — Liquibase doit reconstituer la même structure depuis ses changesets.

### 7.2 Initialisation en prod

**Stratégie retenue** : Liquibase au démarrage du backend (`spring.liquibase.enabled: true`, déjà actif `application.yml:60`). Au tout premier boot prod :

1. Postgres démarre vide.
2. Backend démarre → Liquibase applique tous les changesets.
3. Backend devient `READY`.

> Si le snapshot SQL est plus complet que les changesets Liquibase actuels (probable car le code Liquibase est en cours d'intégration), il faut **soit** finir Liquibase côté code (fait dans l'autre session dev), **soit** initialiser la DB avec le snapshot puis activer Liquibase en `validate` only. À trancher avec l'équipe dev avant la procédure 3.

### 7.3 Seeding initial obligatoire

Avant le go-live :

| Donnée | Source | Comment |
|--------|--------|---------|
| Hôtel "City Hotel" (id=1) | requis pour multi-tenant | changeset Liquibase ou script SQL post-deploy |
| Rôles (`SUPERADMIN`, `ADMIN`, `GERANT`, ...) | `roles_utilisateurs.txt` | changeset Liquibase |
| User SUPERADMIN initial | équipe ops | script post-deploy avec mot de passe à changer au 1ᵉʳ login |
| Plan comptable | `plan_comptable_mauritanien.pdf` | à charger manuellement ou via import (hors scope V1) |
| Modes de paiement | `modes_paiements.txt` | changeset Liquibase |

> **Attention multi-tenant** : tout seed métier hôtel-scopé DOIT poser `hotel_id=1`. Les rôles et le plan comptable sont des référentiels globaux — laisser `hotel_id=NULL` ou `=0` sentinel ROOT (cf. `CLAUDE.md §6.1`).

---

## 8. Préparation des certificats (avant procédure 3)

> **💡 En clair** — Le **certificat TLS** est ce qui transforme HTTP en HTTPS (cadenas dans le navigateur). Sans certificat valide, les navigateurs affichent une page d'erreur rouge. **Let's Encrypt** = autorité gratuite qui délivre des certificats valables 90 jours, à condition de prouver la possession du domaine. Deux mécanismes de preuve : **HTTP-01** (Let's Encrypt vous appelle sur le port 80, vous devez répondre quelque chose de précis) et **DNS-01** (vous ajoutez un enregistrement DNS). HTTP-01 est plus simple ; DNS-01 permet les certificats wildcard `*.example.mr`. **Staging issuer** = environnement de test de Let's Encrypt avec quotas illimités mais certificats invalides — toujours tester là d'abord pour ne pas se faire bannir de la prod par excès d'essais.

### 8.1 DNS

Configurer les enregistrements **avant** la procédure 3 :
```
city.example.mr.        300  IN  A    <IP serveur>
api.city.example.mr.    300  IN  A    <IP serveur>      (optionnel)
```

Vérifier la propagation :
```bash
dig +short city.example.mr A @1.1.1.1
```

### 8.2 Let's Encrypt

- ACME HTTP-01 : port 80 ouvert et joignable depuis Internet.
- Email valide pour les notifications LE (à mettre dans `letsencrypt-issuer.yaml`).
- Limites : 50 certs/semaine/domaine. Tester d'abord avec le `staging` issuer pour ne pas être blacklisté.

### 8.3 Test en staging avant prod

```bash
kubectl apply -f deploiement/ressources/k8s/letsencrypt-issuer.yaml
# L'issuer staging crée un cert "fake" — vérifier que tout marche
kubectl get certificate -A
kubectl describe certificate city-tls -n city
```

Quand tout est vert, basculer sur l'issuer `letsencrypt-prod`.

---

## 9. Préparation du monitoring (optionnel mais recommandé V1)

> **💡 En clair** — Le **monitoring** = surveiller en permanence la santé de l'application pour détecter les problèmes AVANT que les utilisateurs les remontent. Trois piliers :
> - **Métriques** : chiffres dans le temps (CPU, RAM, latence, taux d'erreur). **Prometheus** les collecte, **Grafana** les affiche en graphiques.
> - **Logs** : lignes texte produites par l'app. **Loki** les stocke, **Promtail** les collecte sur les nœuds.
> - **Alertes** : règles qui déclenchent un email/SMS quand un seuil est dépassé (ex : « plus de 1 % de 5xx pendant 5 min »).
>
> **Actuator** = bibliothèque Spring Boot qui expose `/actuator/health` (le service est-il OK ?) et `/actuator/prometheus` (les métriques) — déjà câblé dans `application.yml`.

### 9.1 Prometheus + Grafana

Installation via Helm :
```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install kube-prom prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace \
  -f deploiement/ressources/k8s/values-kube-prom.yaml
```

Le backend expose déjà `/actuator/prometheus` (cf. `application.yml:148`). Annoter les Pods :
```yaml
prometheus.io/scrape: "true"
prometheus.io/path: "/citybackend/actuator/prometheus"
prometheus.io/port: "8080"
```

### 9.2 Loki + Promtail (logs)

```bash
helm install loki grafana/loki-stack -n monitoring \
  --set grafana.enabled=false \
  --set prometheus.enabled=false
```

Filtres prêts à l'emploi pour Loki :
- `{namespace="city"} |= "ERROR"`
- `{namespace="city",app="city-backend"} | json | hotel_id="1"` (extraction MDC)
- `{namespace="city",app="city-backend"} | "WHERE hotel_id" != ""` (audit multi-tenant)

### 9.3 Dashboards Grafana à importer

| ID | Nom | Source |
|----|-----|--------|
| 4701 | JVM (Micrometer) | grafana.com |
| 9628 | PostgreSQL Database | grafana.com |
| 17346 | Spring Boot 3.x Statistics | grafana.com |
| 12159 | K8s Cluster Detail | grafana.com |
| custom | City Hotel — Activité par hôtel | à construire (graph par `hotel_id`) |

---

## 10. Tests pré-prod (sans toucher au serveur de prod)

> **💡 En clair** — On valide tout sur un environnement « jumeau » (dit **staging** ou **pré-prod**) avant de toucher la prod. Idéalement c'est une VM avec exactement la même config que la prod. **E2E** (End-to-End) = test qui simule le parcours complet d'un utilisateur : ouvrir le site, se connecter, créer un client, voir la liste. Si ça marche en staging, on a 95 % de chances que ça marche en prod ; si ça plante en staging, on n'aurait jamais dû le pousser en prod.

Avant de lancer la procédure 3, valider sur un environnement de staging (idéalement un VM identique à la prod) :

| Test | Critère de succès |
|------|-------------------|
| Build backend | jar produit, image poussée |
| Build frontend | bundle < 5 Mo gzip, pas d'erreur ng build |
| Démarrage Liquibase | logs `liquibase: Update has been successful` |
| `/actuator/health` | `{"status":"UP"}` |
| `/actuator/info` | retourne version git |
| Login E2E | utilisateur SUPERADMIN obtient un JWT |
| Multi-tenant | impossible de lire un client d'un autre `hotel_id` (test 403) |
| TLS | cert-manager émet bien un certificat staging |
| Backup | CronJob produit un fichier `.sql.gz` lisible |
| Restore | `pg_restore` du fichier crée une DB cohérente |

Documenter chaque test dans [`checklist_go_live.md`](checklist_go_live.md).

---

## 11. Livrables de fin de procédure 2

> **💡 En clair** — Avant de passer à la procédure 3 (déploiement réel), on s'assure d'avoir les **artefacts** nécessaires : images Docker poussées, manifests validés, secrets stockés, pipeline CI testé, runbook de rollback éprouvé. Un **artefact** = livrable produit par la chaîne de build (image, jar, tar.gz). Si l'un manque, on ne peut pas déployer proprement — c'est comme essayer de monter un meuble IKEA sans visserie.

À l'issue, l'équipe doit posséder :
1. ✅ Deux images Docker poussées (`city-backend:<tag>`, `city-frontend:<tag>`) sur `ghcr.io`.
2. ✅ Un dossier `ressources/k8s/` validé `kubectl apply --dry-run=server` OK.
3. ✅ Un fichier `.env.prod` (chiffré sops/gpg) hors git.
4. ✅ Un pipeline CI/CD qui construit ces images en < 10 min.
5. ✅ Un runbook de rollback testé.
6. ✅ Une checklist de tests pré-prod 100% verte.

→ Passage à la procédure 3 (déploiement final).
