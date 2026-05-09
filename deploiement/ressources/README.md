# Ressources & templates de déploiement

> Tous les fichiers ici sont des **templates** ou des **scripts** prêts à être copiés sur le serveur ou dans la CI.
> Lire les procédures `01_*.md`, `02_*.md`, `03_*.md` à la racine de `deploiement/` pour le contexte.
> Les fichiers `*.example` doivent être renommés et **chiffrés** avant tout commit.

## Arborescence

```
ressources/
├── README.md                                    (ce fichier)
│
├── server/                                      Hardening de l'hôte Ubuntu
│   ├── sshd_config.hardened                     drop-in /etc/ssh/sshd_config.d/
│   ├── sysctl-hardening.conf                    /etc/sysctl.d/99-city-hardening.conf
│   ├── fail2ban-jail.local                      /etc/fail2ban/jail.local
│   ├── 50unattended-upgrades.local              /etc/apt/apt.conf.d/52unattended-...
│   └── ufw-init.sh                              script d'initialisation UFW
│
├── docker/                                      Conteneurisation
│   ├── Dockerfile.backend                       multi-stage Java 21 / JRE 21 noble
│   ├── Dockerfile.frontend                      multi-stage Node 24 / nginx 1.27
│   ├── nginx-frontend.conf                      vhost nginx (statics + SPA + sec headers)
│   ├── docker-compose.prod.yml                  plan B sans Kubernetes
│   └── .env.prod.example                        variables compose (à renommer .env)
│
├── k8s/                                         Manifests Kubernetes (K3s)
│   ├── kustomization.yaml                       agrégateur Kustomize
│   ├── namespace.yaml                           namespace + PriorityClasses
│   ├── configmap-backend.yaml                   variables non sensibles backend
│   ├── secret-postgres.yaml.example             /!\ template, à sceller
│   ├── secret-backend.yaml.example              /!\ template, à sceller
│   ├── postgres-statefulset.yaml                Postgres 18 + Service + PVC 200 Gi
│   ├── backend-deployment.yaml                  Spring Boot + Service + HPA
│   ├── frontend-deployment.yaml                 nginx + Service
│   ├── ingress.yaml                             Traefik IngressRoute + TLS LE
│   ├── networkpolicy.yaml                       isolation entre couches
│   ├── poddisruptionbudget.yaml                 minAvailable=1
│   ├── backup-cronjob.yaml                      pg_dump quotidien 02:00 NKC
│   └── letsencrypt-issuer.yaml                  ClusterIssuer cert-manager
│
├── scripts/                                     Scripts d'exploitation
│   ├── postgres-backup.sh                       backup pg_dump + GPG + rotation
│   └── postgres-restore.sh                      restore manuel sécurisé
│
└── ci/                                          CI/CD
    ├── Jenkinsfile                              pipeline Jenkins LTS
    └── github-actions-build.yml                 workflow GitHub Actions équivalent
```

## Convention pour les fichiers `*.example`

- Ne jamais committer la version réelle (sans suffixe).
- La version réelle vit chiffrée :
  - **sealed-secrets** : `kubeseal --format=yaml < secret-backend.yaml > sealed-secret-backend.yaml` puis commit.
  - **SOPS+age** : `sops --encrypt --age <pubkey> secret-backend.yaml > secret-backend.enc.yaml`.
- Les valeurs (`POSTGRES_PASSWORD`, `JWT_SECRET`) doivent être générées avec :
  - `pwgen -ynsBv 32 1`
  - `openssl rand -base64 48`

## Ce que ces ressources NE font PAS

- **Aucune modification du code applicatif** (citybackend/, cityfrontend/). Si le code a besoin d'évoluer pour la prod (ex. `environment.prod.ts`), c'est traité dans une session de dev séparée.
- **Aucune installation automatique** : tout reste documenté en commandes à lancer manuellement par le sysadmin / devops, par sécurité (audit trail).
- **Aucune action destructive automatique** : `pg_restore`, `kubectl delete`, `lvextend` sont volontairement interactifs ou exigent confirmation.

## Pour modifier ces ressources

Versioning : ce dossier suit le code dans le repo principal city. Toute modif passe par PR + review (ops + tech lead). Tag les changements infra avec `infra/` en préfixe de PR si convention adoptée.
