# Déploiement city — Procédures de mise en production

> **Périmètre** : passer du poste de développement Windows (citybackend Spring Boot 3.4 / Java 21 + cityfrontend Angular 21.2) à un serveur Linux conteneurisé en production. Le serveur cible est **physique**, actuellement sous Windows Server 2016, à reformater en **Ubuntu Server 24.04 LTS** (Noble Numbat — LTS jusqu'en avril 2029, support standard jusqu'en avril 2034 avec ESM).

> **Doctrine** : aucune rétrogradation par rapport au palier 1 city (cf. `CLAUDE.md` racine §3). Les versions ci-dessous sont des **planchers**.

---

## 1. Sommaire

| # | Document | Public visé | Durée estimée |
|---|----------|-------------|---------------|
| 📖 | [`formation.md`](formation.md) | **Démarrer ici si non-spécialiste** — explique tous les termes | 1-2 h de lecture |
| 0 | [`architecture.md`](architecture.md) | Tous | 15 min de lecture |
| 1 | [`01_preparation_serveur.md`](01_preparation_serveur.md) | Sysadmin | **0,5 à 1 jour ouvré** |
| 2 | [`02_preparation_application.md`](02_preparation_application.md) | Dev / DevOps | **0,5 jour** |
| 3 | [`03_deploiement_final.md`](03_deploiement_final.md) | DevOps | **0,5 jour** + 24 h de soak |
| 4 | [`runbook_exploitation.md`](runbook_exploitation.md) | Exploitation | référence permanente |
| 5 | [`checklist_go_live.md`](checklist_go_live.md) | Tous | go/no-go final |

> **Tous les fichiers procédures contiennent désormais des encadrés `💡 En clair`** qui résument chaque section en français courant. Pour une explication complète des termes (Docker, K8s, TLS, hardening…), lire `formation.md`.

---

## 2. Pré-requis matériels (serveur cible)

| Composant | Minimum | Recommandé | Notes |
|-----------|---------|------------|-------|
| CPU | 4 cœurs x86_64 | 8 cœurs (Intel Xeon / AMD EPYC) | Java 21 + Postgres 18 |
| RAM | 8 Go | **16 Go** | JVM ~2 Go, PG ~2 Go, OS + reverse proxy ~1 Go, marge build/CI |
| Disque OS | 50 Go SSD | 100 Go NVMe | `/` ext4 |
| Disque data | 100 Go SSD | **500 Go NVMe en RAID1** | volumes Postgres + backups + logs |
| Réseau | 1 Gbps | 1 Gbps + IP publique fixe | NAT/firewall amont autorisé |
| TPM | — | TPM 2.0 | pour chiffrement LUKS si besoin |
| Hyperviseur | — | non requis | bare-metal ou VM ESXi/Proxmox/KVM |

**Rappel parc actuel** : Windows Server 2016 → fin de support standard 2022, ESM jusqu'en janvier 2027. Le reformatage est **obligatoire** : aucun upgrade in-place Windows → Linux n'existe.

---

## 3. Pré-requis humains

- 1 sysadmin Linux (procédure 1) avec accès console physique ou IPMI/iDRAC/iLO.
- 1 DevOps (procédures 2 et 3) avec accès git du dépôt city + droits CI/CD.
- 1 DBA ou backend dev pour la première migration Liquibase (lecture-seule du `structure_cityprojectdb*.sql` pour validation).
- 1 contact réseau pour ouvrir 80/443 entrants et configurer DNS.

---

## 4. Choix techniques (résumé)

| Domaine | Choix retenu | Alternative |
|---------|--------------|-------------|
| OS serveur | **Ubuntu Server 24.04 LTS** (sans GUI) | Debian 12 ; RHEL 9 si licence dispo |
| Runtime conteneurs | **Docker Engine 27+** (containerd) | Podman 5 (rootless) |
| Orchestration | **K3s 1.31+** (Kubernetes léger single-node, scale-out possible) | Docker Compose (plan B simple) |
| Reverse proxy + TLS | **Traefik 3** (livré par K3s) | Nginx + certbot |
| Certificats | **Let's Encrypt** via cert-manager (K3s) ou certbot (compose) | certificat acheté |
| Base de données | **PostgreSQL 18.3** (image officielle) | RDS / Cloud SQL si cloud |
| Stockage volumes | local-path-provisioner (K3s) sur LV LVM dédié | NFS / Longhorn pour HA |
| Build images | **Jib 3.4.4** (déjà dans pom.xml palier 1) côté backend ; multi-stage Docker côté frontend | `docker build` complet |
| CI/CD | **Jenkins LTS** (mentionné CLAUDE.md §3) ou GitHub Actions | GitLab CI |
| Registre images | **GitHub Container Registry (ghcr.io)** privé | Harbor self-hosted |
| Monitoring | Prometheus + Grafana (Spring Actuator déjà exposé) | Datadog si budget |
| Logs | Loki + Promtail | ELK stack |
| Sauvegardes | `pg_dump` quotidien + rétention 30 j local + 90 j off-site | pgBackRest pour PITR |
| Antivirus / IDS | ClamAV (sur upload) + auditd + Lynis périodique | Wazuh agent |

---

## 5. Architecture cible (vue rapide)

```
                    Internet
                       │ 443/80
                       ▼
              ┌──────────────────┐
              │  Pare-feu amont  │  (autorise 22/443/80 → serveur)
              └────────┬─────────┘
                       │
              ┌────────▼─────────────────────────────────────────┐
              │  Serveur Ubuntu 24.04 LTS (single-node K3s)      │
              │                                                   │
              │  ┌─────────────┐   ┌─────────────────────────┐   │
              │  │  Traefik 3  │──▶│ Ingress city-frontend   │   │
              │  │  (TLS LE)   │   │ Service: 80 → nginx     │   │
              │  └──────┬──────┘   │ Pod: city/frontend:tag  │   │
              │         │          └─────────────────────────┘   │
              │         │          ┌─────────────────────────┐   │
              │         └─────────▶│ Ingress city-backend    │   │
              │                    │ Service: 8080           │   │
              │                    │ Pod: city/backend:tag   │   │
              │                    │ (Spring Boot 3.4 / J21) │   │
              │                    └────────────┬────────────┘   │
              │                                 │ JDBC           │
              │                    ┌────────────▼────────────┐   │
              │                    │ StatefulSet postgres-18 │   │
              │                    │ PVC: 200 Gi local-path  │   │
              │                    └─────────────────────────┘   │
              │                                                   │
              │  CronJob backup-postgres ─── PVC backups ─── rsync off-site
              └──────────────────────────────────────────────────┘
```

Cf. [`architecture.md`](architecture.md) pour le détail (dimensionnement, ports, secrets, volumes).

---

## 6. Ordre d'exécution recommandé

```
J-7     Commander hardware si renouvellement, ouvrir DNS + ports amont
J-3     Sauvegarde complète Windows Server 2016 (procédure 1 §A)
J-2     Reformatage + installation Ubuntu (procédure 1 §B-§C)
J-1     Hardening + Docker + K3s (procédure 1 §D-§G)
J-1     Génération images, push registre (procédure 2)
J0      Déploiement DB + migrations + backend + frontend (procédure 3)
J0      Smoke tests + go-live
J0+1    Soak test 24 h, monitoring fin
J0+7    Premier audit Lynis post-prod, ajustements
```

---

## 7. Sécurité — principes non négociables

1. **Aucun secret en clair** dans les manifests git. Utiliser des `Secret` k8s, `sealed-secrets`, ou un coffre (Vault / sops). Voir §[02 §5](02_preparation_application.md).
2. **TLS partout**. HTTP → 301 HTTPS. HSTS + cipher suites modernes (TLS 1.2 minimum, 1.3 préféré).
3. **Multi-tenant** : `JWT_SECRET` rotaté à chaque release majeure. CORS verrouillé sur le domaine de prod (cf. `app.cors.allowed-origins`).
4. **SSH** : pas de mot de passe, clé publique uniquement, port custom + fail2ban, root désactivé. Voir §[01 §D](01_preparation_serveur.md).
5. **Mises à jour** : `unattended-upgrades` activé pour les CVE Ubuntu, redémarrages contrôlés.
6. **Logs** : Africa/Nouakchott horodatage, `hotel_id` + `user_id` MDC déjà actifs côté backend (cf. `application.yml:138`).
7. **Backup chiffré** GPG avant rsync off-site.

---

## 8. Conformité projet city

- **Devise** : MRU (déjà dans `environment.ts:20`).
- **Timezone** : Africa/Nouakchott (déjà `application.yml:82`).
- **Plan comptable** : mauritanien (cf. `plan_comptable_mauritanien.pdf`). Aucun impact infra.
- **Multi-tenant** : `hotel_id` propagé via JWT. Aucun changement infra (le tenant context est applicatif). Le déploiement reste mono-instance multi-tenant logique — **pas une instance par hôtel**.
- **3 langues** (fr/ar/en) : statiques servies depuis `assets/i18n/` du build Angular. Aucune action serveur.
- **Sessions** : 80 simultanées max + 3 sessions/user, déjà configurées (`application.yml:128`). Dimensionner Tomcat `threads.max=100` (par défaut).
- **Heures critiques** : night audit planifié à midi (cf. `règles_night_audit.txt`). Vérifier que le serveur a NTP actif (chrony) et que la timezone est cohérente.

---

## 9. Dépendances projet à vérifier avant prod

| Élément | Vérification |
|---------|--------------|
| `pom.xml` | spring-boot 3.4.5, java release 21, Liquibase activé |
| `application.yml` profil `prod` | `ddl-auto: validate`, env vars `DB_*`, `JWT_SECRET` |
| `package.json` engines | node ≥ 24.0.0, npm ≥ 10.9.0 |
| `environment.ts` | un build prod doit injecter `apiUrl` réel via `fileReplacements` (à créer dans angular.json — **point ouvert**, voir §[02 §1.3](02_preparation_application.md)) |
| Logs path | `logs/citybackend.log` → à monter sur volume persistant ou rediriger stdout en container |
| Liquibase changelog | `db/changelog/db.changelog-master.xml` doit exister et lister les changesets |

> Les points ouverts seront listés dans la check-list go-live ([`checklist_go_live.md`](checklist_go_live.md)).

---

## 10. Hors périmètre de ce dossier

- Choix du nom de domaine et achat (à fournir par le client).
- Configuration du pare-feu **amont** (FAI / box) — couvert au niveau procédure mais à exécuter par le réseau.
- Stratégie de **reprise après sinistre multi-site** — couverte uniquement par les backups off-site, pas de réplication PG synchrone proposée à ce stade.
- **Audit RGPD / loi mauritanienne sur les données** — à valider avec le légal du client.
- Code applicatif (city/citybackend, city/cityfrontend) — **aucune modification ici**, le code est traité dans une autre session.
