# Architecture cible — Production city

> **📖 Lecture pédagogique** : si certains termes techniques de ce document ne sont pas familiers (Kubernetes, Pod, Ingress, PVC, HPA, Traefik, etc.), commencez par lire [`formation.md`](formation.md) qui les définit tous avec des analogies simples. Les encadrés `💡 En clair` ci-dessous donnent l'idée principale de chaque section sans jargon.

## 1. Vue topologique

> **💡 En clair** — Ce schéma montre le « chemin » qu'emprunte un clic d'utilisateur, depuis son navigateur jusqu'à la base de données. Lecture de haut en bas : Internet → pare-feu de la box → notre serveur → un « videur » (Traefik) qui décide quoi servir → soit le site web (frontend), soit l'API (backend), qui parle à la base PostgreSQL. Tout vit dans **un seul serveur Linux** avec Kubernetes (K3s) à l'intérieur pour gérer les composants.

```
                                    ┌──────────────────┐
                                    │  Utilisateur     │
                                    │  (navigateur)    │
                                    └────────┬─────────┘
                                             │ HTTPS 443
                                             ▼
                                    ┌──────────────────┐
                                    │  DNS A/AAAA      │
                                    │  city.example.mr │
                                    └────────┬─────────┘
                                             │
                                    ┌────────▼─────────┐
                                    │  Pare-feu amont  │
                                    │  (FAI/box/router)│
                                    │  NAT → serveur   │
                                    │  22, 80, 443     │
                                    └────────┬─────────┘
                                             │
                ─────────────────────────────┼──────────────────────────────
                                             │
        ┌────────────────────────────────────▼────────────────────────────────┐
        │  SERVEUR  Ubuntu 24.04 LTS  /  Docker 27 + K3s 1.31                 │
        │  (ex Windows Server 2016 reformaté)                                 │
        │                                                                       │
        │  ┌───────────────────────────────────────────────────────────────┐  │
        │  │  IPTables/UFW host firewall (default deny inbound)            │  │
        │  │  fail2ban (jails sshd, traefik 401/403)                       │  │
        │  └─────┬─────────────────────────────────────────────────────────┘  │
        │        │                                                              │
        │  ┌─────▼─────────────────────────────────────────────────────────┐   │
        │  │  Traefik 3 (DaemonSet K3s, ports host 80/443)                  │  │
        │  │  - Termine TLS (cert-manager + Let's Encrypt HTTP-01)          │  │
        │  │  - HSTS, redirect 80 → 443, security headers                   │  │
        │  │  - Routes :                                                    │  │
        │  │      city.example.mr/         → Service city-frontend          │  │
        │  │      city.example.mr/api/*    → Service city-backend           │  │
        │  │      api.city.example.mr/*    → Service city-backend (option)  │  │
        │  └─────┬──────────────────────────────────────┬────────────────────┘  │
        │        │                                      │                        │
        │  ┌─────▼──────────────────┐         ┌─────────▼─────────────────────┐  │
        │  │  city-frontend         │         │  city-backend                 │  │
        │  │  Deployment 2 replicas │         │  Deployment 2 replicas        │  │
        │  │  nginx:alpine          │         │  eclipse-temurin:21-jre       │  │
        │  │  static build Angular  │         │  ContextPath /citybackend     │  │
        │  │  Port: 80              │         │  Port: 8080                   │  │
        │  │  HPA: 2-4 replicas     │         │  HPA: 2-6 replicas            │  │
        │  │  Resources:            │         │  Resources:                   │  │
        │  │    req 50m/64Mi        │         │    req 500m/1Gi               │  │
        │  │    lim 200m/128Mi      │         │    lim 2/3Gi                  │  │
        │  └────────────────────────┘         └─────┬─────────────────────────┘  │
        │                                            │ JDBC + TLS                │
        │                                  ┌─────────▼─────────────────┐        │
        │                                  │  postgres-18              │        │
        │                                  │  StatefulSet 1 replica    │        │
        │                                  │  PVC: 200 Gi local-path   │        │
        │                                  │  Image: postgres:18-bookworm│       │
        │                                  │  Resources:               │        │
        │                                  │    req 1/2Gi  lim 4/6Gi   │        │
        │                                  └────┬──────────────────────┘        │
        │                                       │ pg_dump                       │
        │                                  ┌────▼──────────────────────┐        │
        │                                  │  CronJob backup-postgres  │        │
        │                                  │  Quotidien 02:00 NKC      │        │
        │                                  │  → PVC backups (50 Gi)    │        │
        │                                  │  → GPG → rsync off-site   │        │
        │                                  └───────────────────────────┘        │
        │                                                                       │
        │  ┌───────────────────────────────────────────────────────────────┐  │
        │  │  Observabilité (namespace `monitoring`)                        │  │
        │  │   - Prometheus (scrape Actuator /actuator/prometheus)          │  │
        │  │   - Grafana (dashboards Spring Boot, JVM, Postgres, Traefik)   │  │
        │  │   - Loki + Promtail (logs containers + journald)               │  │
        │  │   - Alertmanager → email/SMS de garde                          │  │
        │  └───────────────────────────────────────────────────────────────┘  │
        └──────────────────────────────────────────────────────────────────────┘
```

## 2. Plan B — Docker Compose pur (alternative simple)

> **💡 En clair** — Si Kubernetes paraît trop compliqué pour démarrer, **Docker Compose** est l'alternative : un simple fichier `docker-compose.yml` qui dit à Docker « lance ces 5 conteneurs et fais-les se parler ». Moins puissant mais opérationnel en 30 minutes. Les images Docker construites restent les mêmes — on pourra migrer vers K3s plus tard sans refaire le travail.

Si l'opérateur n'est pas familier avec Kubernetes, une mise en route immédiate peut se faire en **Docker Compose** :

```
docker compose (sur le même serveur) :
  ├─ traefik                : reverse proxy + TLS Let's Encrypt
  ├─ city-frontend          : nginx:alpine + build Angular
  ├─ city-backend           : eclipse-temurin:21-jre + jar Spring Boot
  ├─ city-postgres          : postgres:18-bookworm + volume
  └─ city-backup            : alpine + pg_dump + cron + rsync
```

C'est **moins évolutif** mais **opérationnel en 30 min**. Voir [`02 §3`](02_preparation_application.md). Le passage à K3s reste possible plus tard sans changer les images.

---

## 3. Dimensionnement initial (1 hôtel = 80 sessions max)

> **💡 En clair** — Combien de puissance pour combien d'utilisateurs ? Ce tableau dit, pour chaque composant, **le minimum (request)** dont il a besoin pour démarrer correctement et **le maximum (limit)** qu'on l'autorise à consommer. `m` = millicore (`500m` = ½ cœur CPU). `Gi` = gibioctet de RAM. **HPA** = mécanisme qui crée automatiquement de nouveaux pods backend quand la charge monte, et les supprime quand elle redescend. Avec ces réglages, un serveur **8 vCPU / 16 Go** tient 1 hôtel à 80 sessions sans transpirer.

| Composant | Replicas | CPU req → lim | RAM req → lim | Storage |
|-----------|----------|---------------|---------------|---------|
| city-frontend | 2 | 50 m → 200 m | 64 Mi → 128 Mi | — |
| city-backend | 2 | 500 m → 2000 m | 1 Gi → 3 Gi | — |
| postgres-18 | 1 | 1000 m → 4000 m | 2 Gi → 6 Gi | 200 Gi |
| Traefik | 1 (host) | 100 m → 500 m | 128 Mi → 256 Mi | — |
| Observabilité | optionnel | — | — | 50 Gi (rétention 30 j) |
| Backups | CronJob | éphémère | éphémère | 50 Gi + off-site |

**Total à provisionner sur le nœud** : ~5 vCPU / 12 Gi RAM / 350 Gi data + 100 Gi OS = **8 vCPU / 16 Gi RAM / 500 Gi disque** recommandés.

**Multi-hôtel** (dès le 2ᵉ tenant et au-delà) : multi-tenancy logique = **mêmes pods + même DB**. Scale horizontal du backend (HPA jusqu'à 6 replicas) couvre ~500 sessions concurrentes. Au-delà, prévoir lecture-réplique Postgres + Redis pour cache de session (hors scope V1).

---

## 4. Ports utilisés

> **💡 En clair** — Un **port** est un « numéro de porte » sur la machine : derrière chaque numéro un service écoute (port 22 = SSH, port 443 = HTTPS, port 5432 = PostgreSQL). La règle d'or de la sécurité : **ne laisser ouvertes vers Internet que les portes strictement nécessaires** (ici : 22 pour l'admin, 80/443 pour les utilisateurs). PostgreSQL reste **fermé à l'extérieur** — seuls les autres conteneurs du cluster peuvent lui parler.

| Port | Sens | Source | Cible | Usage |
|------|------|--------|-------|-------|
| 22 | inbound | IP admin whitelist | sshd | administration |
| 80 | inbound | 0.0.0.0/0 | Traefik | redirection vers 443 + ACME HTTP-01 |
| 443 | inbound | 0.0.0.0/0 | Traefik | applicatif HTTPS |
| 6443 | inbound | localhost only | K3s API | kubectl local |
| 9090 | inbound | VPN admin | Prometheus | scrape (réservé interne) |
| 3000 | inbound | VPN admin | Grafana | dashboards |
| 5432 | **fermé en externe** | — | Postgres | accès via cluster K8s only |
| 25 / 587 | outbound | backend | SMTP relay | mails sortants (welcome, reset) |
| 443 | outbound | serveur | ACME / registre / kafka externe | mises à jour, pull images, events |

> **Aucun port DB exposé sur Internet.** Les seuls ports publics sont 80/443. SSH limité par fail2ban + IP whitelist.

---

## 5. Domaines & DNS

> **💡 En clair** — Un nom de domaine (`city.example.mr`) doit pointer vers l'**adresse IP** du serveur. C'est le **DNS** qui fait la traduction (comme un annuaire téléphonique : « numéro » = nom, « ligne » = IP). Quand un utilisateur tape `https://city.example.mr` dans son navigateur, son ordinateur demande au DNS « quelle est l'IP ? » puis se connecte. **Let's Encrypt** = service gratuit qui fournit le « cadenas » (certificat TLS/HTTPS) à condition de prouver qu'on contrôle bien le domaine — d'où la nécessité d'avoir le port 80 ouvert (la preuve passe par là).

| Enregistrement | Type | Cible | Usage |
|----------------|------|-------|-------|
| `city.example.mr` | A | IP publique serveur | UI principale |
| `api.city.example.mr` | A | IP publique serveur | (option) endpoint API distinct |
| `*.city.example.mr` | A | IP publique serveur | (option) sous-domaine par hôtel |

ACME HTTP-01 nécessite que le port 80 soit joignable depuis Let's Encrypt. Si DNS-01 préféré (wildcard), prévoir un compte API chez le registrar et un secret cert-manager.

---

## 6. Volumes persistants

> **💡 En clair** — Les conteneurs sont **éphémères** : si on supprime un conteneur, tout ce qu'il contient disparaît. C'est OK pour le frontend ou le backend (sans état), pas pour PostgreSQL ! Un **volume persistant (PV)** est un dossier sur le disque de l'hôte que Kubernetes « branche » dans le conteneur — comme une clé USB qu'on garde même si on change d'ordinateur. **PVC** = la *demande* d'un volume (« je veux 200 Go »), **PV** = le volume réel attribué. Sur un seul serveur, on utilise `local-path` qui crée simplement un dossier sur `/var/lib/rancher`.

| Volume | Type | Capacité | Backup ? | Notes |
|--------|------|----------|----------|-------|
| `postgres-data` | local-path PVC | 200 Gi | quotidien | sur LV LVM dédié `/var/lib/rancher/k3s/storage` |
| `backup-postgres` | local-path PVC | 50 Gi | rsync off-site | rétention 30 jours |
| `traefik-acme` | hostPath ou Secret | 1 Mi | snapshot manuel | certificats Let's Encrypt |
| `loki-data` | local-path PVC | 50 Gi | non (logs reconstruisibles) | rétention 30 j |
| `grafana-data` | local-path PVC | 5 Gi | snapshot hebdo | dashboards + datasources |

> **Le disque physique du serveur doit être partitionné en 2 LV** : `/` (50 Gi) + `/var/lib/rancher` (le reste, idéalement RAID1). Voir [`01 §C`](01_preparation_serveur.md).

---

## 7. Secrets

> **💡 En clair** — Un **secret** est une donnée sensible (mot de passe, clé d'API, jeton) qu'**on ne doit JAMAIS écrire en clair dans un fichier suivi par git**. Kubernetes a un objet dédié (`Secret`) qui stocke ces valeurs séparément des manifests publics. Les conteneurs y accèdent comme à des variables d'environnement, sans que le mot de passe apparaisse dans le code. La **rotation** = changer le mot de passe régulièrement, pour qu'un mot de passe volé devienne vite inutile.

| Secret | Stockage | Rotation |
|--------|----------|----------|
| `JWT_SECRET` | Secret k8s `city-backend-secret` | à chaque release majeure |
| `DB_PASSWORD` (postgres) | Secret k8s `city-postgres-secret` | trimestrielle |
| `MAIL_PASSWORD` (SMTP) | Secret k8s `city-backend-secret` | annuelle |
| Certificats TLS | gérés par cert-manager | auto (60 j) |
| Clés SSH admin | `~/.ssh/authorized_keys` (poste admin) | à chaque départ d'admin |
| Clé GPG backup | hors-serveur, 2FA pour la passphrase | annuelle |

**Aucun secret ne doit être commité dans git.** Les fichiers `*.example` du dossier `ressources/` sont des templates ; les valeurs réelles vivent uniquement dans le cluster.

---

## 8. Flux de données sensibles

> **💡 En clair** — On suit ici **où passent les données sensibles** (mots de passe, jetons, données métier) pour s'assurer qu'elles ne fuitent pas. Le **JWT** (JSON Web Token) = jeton signé que le navigateur stocke après login et envoie à chaque requête, prouvant l'identité de l'utilisateur sans renvoyer son mot de passe. **MDC** (Mapped Diagnostic Context) = système de logs qui ajoute automatiquement le `hotel_id` et `user_id` à chaque ligne de log, pour pouvoir filtrer les actions d'un hôtel particulier en cas d'enquête.

```
JWT (Bearer)         : navigateur ──HTTPS──▶ Traefik ──HTTP cluster──▶ backend
                       backend valide signature, extrait userId+hotelId, set TenantContext

Mots de passe DB     : Secret k8s ─env──▶ backend Pod (jamais loggé)

Backups Postgres     : pg_dump ──gzip──▶ PVC backup ──gpg encrypt──▶ rsync ──▶ off-site

Logs metiers         : container stdout ──▶ Promtail ──▶ Loki
                       (champs MDC `hotel_id`, `user_id` filtrables)
```

Le backend NE DOIT JAMAIS logger un mot de passe, un JWT, ou un body de requête `POST /auth/login`. Le `JwtAuthenticationFilter` doit masquer le token. À vérifier en review (cf. `ERREURS_AUDIT_A_EVITER.html`).

---

## 9. Disponibilité & résilience

> **💡 En clair** — **RTO** (Recovery Time Objective) = en combien de temps on remet le service en marche après une panne. **RPO** (Recovery Point Objective) = combien de données on accepte de perdre au pire. Ici, frontend/backend = stateless donc redémarrage en 30 s et 0 perte. Postgres = backup quotidien donc au pire on perd 24 h de données. **HA** (Haute Disponibilité) = configuration où aucun composant n'a de point unique de défaillance — coûteuse, on n'y va que si le métier l'exige.

| Composant | Redondance | RTO | RPO |
|-----------|------------|-----|-----|
| city-frontend | 2 replicas | 30 s | 0 (stateless) |
| city-backend | 2-6 replicas (HPA) | 30 s | 0 (stateless, JWT côté client) |
| postgres-18 | 1 replica + backup quotidien | 4 h | 24 h (backup) ou 0 si WAL archiving (option) |
| Serveur physique | aucune (single-node) | 1-3 j en cas de panne hardware | 24 h via restore off-site |

> Pour passer en HA réelle (RTO < 5 min, RPO < 5 min) : 3 nœuds K3s + Postgres en streaming replication ou Patroni. **Hors scope V1.**

---

## 10. Coûts ordre de grandeur

> **💡 En clair** — Un budget grossier pour comparer avec d'autres options. Le serveur est amorti sur 5 ans (achat ~7 500 USD ÷ 5 = 1 500 USD/an). Tous les logiciels sont gratuits (open source). Si on basculait en cloud managé (AWS RDS pour la base, EKS pour Kubernetes…), le coût serait multiplié par ~5 mais la charge ops serait moindre. Choix dépend du budget et de l'expertise interne.

| Poste | Coût annuel approximatif |
|-------|--------------------------|
| Hardware serveur (amorti 5 ans) | 1 500 USD/an |
| Bande passante 1 Gbps illimitée | 600 USD/an |
| Domaine `.mr` | 50 USD/an |
| Certificats TLS (Let's Encrypt) | 0 |
| Stockage off-site (50 Gi) | 60 USD/an |
| Logiciels OSS (Ubuntu, Docker, K3s, Postgres, Traefik, Prometheus...) | 0 |
| **Total infra prod (1 serveur)** | **~2 200 USD/an** |
| Astreinte humaine (4h/mois) | hors scope |

Tout passage à du cloud managed (RDS Postgres, CloudFront, EKS) multiplierait le coût par ~5.
