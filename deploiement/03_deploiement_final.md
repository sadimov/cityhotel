# Procédure 3 — Déploiement final & go-live

> **📖 Pour les non-spécialistes** : « rolling update », « probe », « cutover », « rollback », « soak test », « 403 Forbidden », « pg_dump » — tous ces termes sont expliqués dans [`formation.md §6`](formation.md) (déploiement) et [`§9`](formation.md) (incident & rollback). Chaque section ci-dessous démarre par un encadré `💡 En clair`.

> **Objectif** : à partir d'un serveur préparé (procédure 1) et d'images + manifests prêts (procédure 2), mettre l'application en production avec TLS, backup actif, monitoring, et passer en exploitation.
> **Durée** : 2 à 4 heures pour le déploiement, **+ 24 heures** de soak test avant validation.
> **Prérequis** : procédures 1 et 2 terminées, DNS configuré, tag de release prêt (`v1.0.0`).

---

## A. Pré-vol — vérifications avant `apply`

> **💡 En clair** — Comme avant un décollage d'avion : 15 minutes de checks avant de pousser le bouton. **Snapshot pré-déploiement** = on archive l'état actuel du cluster avant de toucher quoi que ce soit, pour pouvoir revenir en arrière en cas de problème. **Approval** = quelqu'un d'autre que la personne qui déploie valide formellement (mail/ticket) — protection contre l'erreur humaine ou le sabotage. **`kubectl apply --dry-run=server`** = l'API K8s simule l'application des manifests sans rien changer, et nous dit si tout serait accepté.

### A.1 Quart d'heure de calme

15 minutes avant le déploiement :

| Check | Comment |
|-------|---------|
| Le serveur répond | `ssh cityadmin@<ip> 'uptime && df -h && free -h'` |
| Espace disque ≥ 70 % libre | `df -h /var/lib/rancher` ≥ 70 % |
| Charge CPU < 30 % | `uptime` |
| K3s prêt | `kubectl get nodes` → `Ready` |
| cert-manager prêt | `kubectl -n cert-manager get pods` → tous Running |
| DNS propagé | `dig +short city.example.mr` retourne l'IP attendue |
| Ports ouverts | `nc -zv <ip> 443 80` depuis l'extérieur |
| Registre accessible | `crictl pull ghcr.io/<org>/city-backend:1.0.0` |
| Image signée GPG (option) | `cosign verify` ou empreinte du tag git |
| Tag git existe | `git ls-remote --tags origin v1.0.0` |
| Equipe prévenue | Slack #ops + email aux super-users |

### A.2 Snapshot de l'état pré-déploiement

```bash
# Sauvegarder le kubeconfig + manifests existants au cas où on déploie une mise à jour
mkdir -p ~/snapshots/$(date +%Y%m%d-%H%M)
cd ~/snapshots/$(date +%Y%m%d-%H%M)
kubectl get all,cm,secret,pvc,ingress -A -o yaml > pre-deploy-cluster.yaml
sudo cp -r /etc/rancher/k3s pre-deploy-k3s-config
```

### A.3 Approval

- Tag git GPG signé : `git tag -s v1.0.0 -m "Release 1.0.0" && git push --tags`.
- Approval écrit (mail ou ticket) du chef de projet.

---

## B. Déploiement — choix d'une voie

> **💡 En clair** — On a deux chemins selon ce qu'on a installé en procédure 1 : **K3s** (Kubernetes) si on vise une infra évolutive, **Docker Compose** sinon. Les deux mènent au même résultat fonctionnel ; la différence est l'effort initial vs la flexibilité future. **Ordre du déploiement** = **toujours la base de données en premier** (sinon le backend démarre dans le vide), puis le backend (qui applique Liquibase), puis le frontend, puis l'Ingress (qui expose tout vers Internet).

### B.1 Voie K3s (recommandée pour la cible long terme)

> **💡 En clair** — On applique les manifests un par un dans le bon ordre. Quelques termes K8s :
> - **Namespace** = « dossier » qui isole logiquement les objets (city, monitoring, kube-system…).
> - **Secret** = stockage chiffré pour les mots de passe ; le créer **avant** que les Pods n'en aient besoin.
> - **rollout status** = commande qui attend que tous les Pods d'un Deployment soient `Ready`. Bloque le script tant que ce n'est pas le cas — pratique en CI.
> - **Pod éphémère** (`run --rm`) = on lance un conteneur pour 30 secondes, on s'en sert pour tester, il disparaît. Idéal pour vérifier la connectivité.

#### B.1.1 Création des secrets

> Si vous utilisez **sealed-secrets**, appliquer les `SealedSecret` (déjà chiffrés). Sinon créer les Secrets en ligne **sans les commiter** :

```bash
kubectl create namespace city

# Secrets postgres
kubectl create secret generic city-postgres-secret -n city \
  --from-literal=POSTGRES_DB=cityprojectdb \
  --from-literal=POSTGRES_USER=cityapp \
  --from-literal=POSTGRES_PASSWORD="$(pwgen -ynsBv 32 1)"

# Secrets backend
kubectl create secret generic city-backend-secret -n city \
  --from-literal=DB_USERNAME=cityapp \
  --from-literal=DB_PASSWORD="<même mdp que postgres ci-dessus>" \
  --from-literal=JWT_SECRET="$(openssl rand -base64 48)" \
  --from-literal=MAIL_USERNAME="<smtp_user>" \
  --from-literal=MAIL_PASSWORD="<smtp_pwd>"
```

> Conserver les valeurs **immédiatement** dans le coffre-fort (Bitwarden, Vault, KeePass). Sans cette sauvegarde, vous ne pourrez pas reproduire un cluster identique.

#### B.1.2 ConfigMap & manifests

```bash
cd ~/city-deploiement/ressources/k8s/   # vous avez cloné le dépôt en lecture-seule sur le serveur
kubectl apply -f namespace.yaml
kubectl apply -f priorityclasses.yaml
kubectl apply -f configmap-backend.yaml
kubectl apply -f networkpolicy.yaml
kubectl apply -f poddisruptionbudget.yaml
```

#### B.1.3 Postgres en premier

```bash
kubectl apply -f postgres-statefulset.yaml
kubectl -n city rollout status statefulset/city-postgres --timeout=5m
kubectl -n city logs -l app=city-postgres --tail=20

# Test de connexion depuis un Pod éphémère
kubectl -n city run pgcheck --rm -it --restart=Never \
  --image=postgres:18.3-bookworm -- \
  psql "postgresql://cityapp:$(kubectl -n city get secret city-postgres-secret -o jsonpath='{.data.POSTGRES_PASSWORD}' | base64 -d)@city-postgres:5432/cityprojectdb" \
  -c '\dt'
```

#### B.1.4 Backend (Liquibase s'exécute au démarrage)

```bash
kubectl apply -f backend-deployment.yaml
kubectl apply -f hpa-backend.yaml

# Suivi du démarrage (Liquibase peut prendre 30-90 s)
kubectl -n city logs -f deploy/city-backend | grep -iE 'liquibase|started|error'
```

Critère de succès :
- Logs `liquibase: Update has been successful`.
- `/actuator/health/readiness` répond `UP`.
- `kubectl -n city get pods` → 2/2 backend `Running`.

#### B.1.5 Frontend

```bash
kubectl apply -f frontend-deployment.yaml
kubectl -n city rollout status deploy/city-frontend
```

#### B.1.6 Ingress + TLS

```bash
# Vérifier que ClusterIssuer Let's Encrypt prod est appliqué
kubectl get clusterissuer

# Appliquer l'ingress
kubectl apply -f ingress.yaml

# cert-manager va demander un certificat. Suivre :
kubectl -n city get certificate -w
kubectl -n city describe certificate city-tls
```

Le passage de `Ready: False` à `Ready: True` peut prendre 1-3 min. Si bloqué :
- `kubectl describe order ...` pour voir le challenge ACME.
- Vérifier que port 80 est accessible depuis l'extérieur (HTTP-01).

#### B.1.7 Backup CronJob

```bash
kubectl apply -f backup-cronjob.yaml
# Lancer un job manuel pour valider
kubectl -n city create job backup-test --from=cronjob/city-postgres-backup
kubectl -n city logs -l job-name=backup-test
ls /var/lib/rancher/k3s/storage/backup-postgres-pvc-*/   # vérif fichier
```

### B.2 Voie Docker Compose (plan B simple)

> **💡 En clair** — Plus court : on copie les fichiers Compose sur le serveur, on remplit le `.env`, on lance `docker compose up -d`. Compose s'occupe de l'ordre (postgres avant backend grâce à `depends_on: condition: service_healthy`) et du redémarrage automatique en cas de crash (`restart: unless-stopped`). Pour suivre les logs en direct : `docker compose logs -f`.

```bash
cd /srv/city/compose
docker compose --env-file .env pull
docker compose --env-file .env up -d
docker compose ps

# Suivi des logs combinés
docker compose logs -f --tail=100 city-backend
```

Smoke tests : §C ci-dessous (les commandes `kubectl` deviennent `docker compose exec ...`).

---

## C. Smoke tests post-déploiement

> **💡 En clair** — **Smoke tests** = « est-ce que ça fume pas ? » — vérifications rapides que les fonctions essentielles marchent. On va de la couche basse (DNS, TLS) à la couche haute (login utilisateur, isolation multi-tenant). **wrk** = outil qui martèle l'API avec des requêtes concurrentes pour vérifier que la latence reste acceptable sous charge légère. **403 Forbidden** = code HTTP qui veut dire « identifié mais pas autorisé » — c'est ce qu'on veut voir quand un user A essaie d'accéder aux données d'un hôtel B. Si on voit `200 OK`, c'est une faille critique d'isolation multi-tenant et on rollback immédiatement.

À exécuter **dans cet ordre** :

### C.1 Couches techniques

```bash
# DNS + TLS
curl -vI https://city.example.mr/ 2>&1 | grep -E 'HTTP/|subject:|issuer:'
# → HTTP/2 200, issuer = Let's Encrypt

# Backend health
curl -fsS https://city.example.mr/citybackend/actuator/health | jq .
# → {"status":"UP", "components":{"db":{"status":"UP"}, ...}}

# Backend info (version)
curl -fsS https://city.example.mr/citybackend/actuator/info | jq .

# Frontend chargé
curl -fsS https://city.example.mr/ | grep -i '<title>'
```

### C.2 Couche applicative

```bash
# Login (utilisateur SUPERADMIN initial)
TOKEN=$(curl -fsS -X POST https://city.example.mr/citybackend/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"superadmin","password":"<mdp_initial_à_changer>"}' \
  | jq -r .token)
echo "$TOKEN" | head -c 30; echo "..."

# Endpoint authentifié de référence (à ajuster selon module dispo)
curl -fsS https://city.example.mr/citybackend/api/clients?page=0&size=1 \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### C.3 Multi-tenant

Test critique : un user de l'hôtel A ne voit pas les données de l'hôtel B.

1. Créer 2 users dans 2 hôtels différents (via SUPERADMIN).
2. Login user A → tokenA. Login user B → tokenB.
3. `GET /api/clients` avec tokenA → liste contient uniquement les clients de l'hôtel A.
4. `GET /api/clients/<id-hôtel-B>` avec tokenA → **403 Forbidden** (pas 200, pas 404).
5. Vérifier dans Loki/logs : la requête SQL contient `WHERE hotel_id = ?` avec la bonne valeur.

> **Si ce test échoue, rollback immédiat.** Une fuite multi-tenant est un incident de sécurité majeur.

### C.4 Performance de base

```bash
# Charge légère pour valider que l'app tient le rythme
sudo apt install -y wrk
wrk -t4 -c40 -d30s -H "Authorization: Bearer $TOKEN" \
  https://city.example.mr/citybackend/api/clients?size=20

# Critères :
#  - latence p95 < 500 ms
#  - aucune 5xx
#  - kubectl top pods : CPU < 1 vCPU/pod, RAM < 2 Gi/pod
```

### C.5 Backup → restore

Test critique end-to-end :
```bash
# Sur le serveur
ls /var/lib/rancher/k3s/storage/backup-*/   # un fichier .sql.gz récent existe
# Tenter une restore sur une DB temporaire
kubectl -n city run pgrestore-test --rm -it --restart=Never \
  --image=postgres:18.3-bookworm \
  --env="PGPASSWORD=$(kubectl -n city get secret city-postgres-secret -o jsonpath='{.data.POSTGRES_PASSWORD}' | base64 -d)" \
  -- bash
# dans le pod :
#   psql -h city-postgres -U cityapp -c "CREATE DATABASE testrestore"
#   gunzip < /backup/<latest>.sql.gz | psql -h city-postgres -U cityapp -d testrestore
#   psql -h city-postgres -U cityapp -d testrestore -c "\dt clients.*"
#   DROP DATABASE testrestore;
```

---

## D. Activation du monitoring

> **💡 En clair** — On allume Prometheus + Grafana + Loki et on configure les alertes minimales. Sans alerte, un incident peut durer 8h avant qu'un utilisateur s'en aperçoive ; avec alerte, l'équipe est notifiée en 2 min. **Alertmanager** = composant Prometheus qui reçoit les alertes et les route vers email/SMS/Slack selon des règles. **Dashboard** = page Grafana avec plusieurs graphiques utiles pour avoir une vision en un coup d'œil. **port-forward** = tunnel temporaire `kubectl port-forward` qui rend une UI privée du cluster (Prometheus, Grafana) accessible localement, sans l'exposer sur Internet.

### D.1 Vérifications

```bash
# Prometheus scrape le backend ?
kubectl -n monitoring port-forward svc/kube-prom-prometheus 9090:9090 &
# Naviguer http://localhost:9090/targets → city-backend doit être UP

# Grafana
kubectl -n monitoring port-forward svc/kube-prom-grafana 3000:80 &
# Login admin / mdp depuis Secret kube-prom-grafana
```

### D.2 Alertes minimales à configurer

| Alerte | Seuil | Destinataire |
|--------|-------|--------------|
| Backend pod down | 1 min | ops@city.example.mr + SMS |
| Postgres pod down | 30 s | ops + chef projet |
| `actuator/health` != UP | 2 min | ops |
| Latence p95 > 2 s | 5 min | ops |
| 5xx ratio > 1 % | 5 min | ops |
| Disque /var/lib/rancher > 80 % | 30 min | ops |
| Disque /var/lib/rancher > 90 % | 5 min | ops + chef projet |
| Échec backup quotidien | 1 occurrence | ops + chef projet |
| Cert TLS expire < 14 j | 1 fois/jour | ops |
| OOMKilled (Pod) | 1 fois | ops |
| HPA scale max atteint | 15 min | ops |

### D.3 Logs

Vérifier dans Loki :
```bash
# Logs ERROR sur les 30 dernières min
{namespace="city"} |= "ERROR" | json | line_format "{{.timestamp}} {{.app}} {{.message}}"

# Activité par hôtel
{app="city-backend"} | regexp "\\[(?P<hotel>\\d+)\\]" | __error__="" | by(hotel) count_over_time(5m)
```

---

## E. Soak test 24 heures

> **💡 En clair** — **Soak test** = « test de trempage » : on laisse l'application tourner 24 h sous trafic réel (ou simulé) pour détecter les bugs lents — fuites mémoire, blocages de connexions DB, incidents nocturnes (par ex. le night audit qui plante à 12h00 NKC). Beaucoup de bugs ne se voient pas à l'instant T mais après plusieurs heures. **OOMKilled** = erreur où Linux tue un processus parce qu'il a dépassé sa limite mémoire — signe d'une fuite ou d'un mauvais réglage `JAVA_TOOL_OPTIONS`. Aucun OOMKilled = bonne santé mémoire.

Pendant 24 h après le déploiement, surveiller :

| Métrique | Limite |
|----------|--------|
| Aucun pod en `CrashLoopBackOff` | 0 |
| Latence p95 stable | écart-type < 30 % |
| Mémoire JVM stable (pas de leak) | usage stabilisé après 1 h |
| Pas d'OOMKill | 0 |
| Backup quotidien à 02:00 NKC | exécuté + non vide |
| Aucune connexion DB qui leak | `pg_stat_activity` < 50 |
| Logs sans `Tenant context missing` | 0 occurrence |
| Logs sans `WHERE hotel_id` manquant | 0 occurrence |
| Login E2E périodique (cron 5 min) | 100 % succès |

Toute anomalie = ticket immédiat. Pas de go-live définitif tant que le soak n'est pas vert.

---

## F. Bascule définitive (cutover)

> **💡 En clair** — **Cutover** = moment où on redirige le trafic du système legacy (ancien) vers le nouveau. Concrètement : on change l'enregistrement DNS pour qu'il pointe vers la nouvelle IP. **TTL DNS** = durée pendant laquelle les caches DNS gardent l'ancienne valeur ; on le baisse à 60 s avant le cutover pour que tout le monde voie le changement vite. **Rollback** = revenir à l'état précédent si le déploiement échoue (`kubectl rollout undo`). Important : le **rollback applicatif** est facile, le **rollback de la base** ne l'est pas — d'où la règle Liquibase « toujours additif ».

Si le serveur **remplace** une production existante (par exemple, un legacy sous Windows) :

### F.1 Plan de bascule

1. **J-1** : annoncer la fenêtre de maintenance aux utilisateurs (mail + bandeau dans l'app legacy).
2. **J0 H-30 min** : passer le legacy en lecture-seule (selon possibilité). Sinon, fermer l'accès.
3. **J0 H-15 min** : `pg_dump` final du legacy → `pg_restore` dans Postgres city. Si format incompatible, prévoir un script de migration de données.
4. **J0 H0** : modifier le DNS pour pointer vers le nouveau serveur (TTL court, e.g. 60 s).
5. **J0 H+10 min** : vérifier que le trafic atterrit bien sur le nouveau serveur.
6. **J0 H+30 min** : ouvrir aux utilisateurs. Surveiller les retours.
7. **J0 H+24 h** : si tout va bien, archiver le serveur legacy (image Veeam → froid).

### F.2 Plan de rollback en cas d'incident

| Type d'incident | Action immédiate |
|-----------------|------------------|
| Backend ne démarre pas | `kubectl rollout undo deploy/city-backend` (revient version N-1) |
| Migration Liquibase échoue | **NE PAS rolldown** — Liquibase est additif. Diagnostiquer l'échec, fixer en code, redeployer. |
| Données corrompues | `pg_restore` du dernier backup propre + investigation |
| TLS cassé | Fallback HTTP temporaire (interdit en prod régulière, mais peut éviter une coupure totale) |
| Charge écrasante | `kubectl scale deploy/city-backend --replicas=6` + plan B serveur plus gros |
| Faille de sécurité publiée | Fenêtre de maintenance d'urgence, image patchée déployée |

### F.3 Fermeture officielle

Une fois le go-live validé (J0+24h) :
- Dépose dans le wiki ops d'un PV de mise en production signé.
- Bascule du contact d'astreinte sur le mode permanent.
- Communication interne « production city ouverte ».
- Mise à jour de [`runbook_exploitation.md`](runbook_exploitation.md) avec les valeurs réelles (IP, URL, logins admin, etc.).

---

## G. Communication aux utilisateurs

> **💡 En clair** — Une mise en prod réussie techniquement peut être perçue comme un échec si les utilisateurs sont surpris ou bloqués. On les prévient **avant** (J-1 : « le service sera coupé entre tel et tel moment »), on leur affiche une **page de maintenance** pendant la coupure (mieux qu'un simple plantage), et on les informe **après** que tout est revenu. La page de maintenance retourne un code HTTP `503 Service Unavailable` avec un header `Retry-After` — les robots et apps connectées comprennent et reviendront plus tard.

Rédiger 3 messages :

1. **Pré-bascule** (J-1) : « Le service sera momentanément indisponible le YYYY-MM-DD entre HH:MM et HH:MM (heure de Nouakchott). Une nouvelle version améliorée sera mise en ligne. »
2. **Pendant la bascule** : page de maintenance statique (servir une page HTML statique via Traefik avec route prioritaire pour `Host(city.example.mr)` répondant 503 + Retry-After).
3. **Post-bascule** : « City Hotel est de nouveau disponible avec sa nouvelle version. Si vous rencontrez un souci, contactez ops@city.example.mr. »

---

## H. Documentation finale

> **💡 En clair** — Une fois la prod en place, on **fige par écrit** ce qui a été fait : URL réelle, version déployée, contacts d'astreinte, mots de passe dans le coffre. Sans cette doc, l'équipe d'exploitation héritera de connaissances dans la tête de quelques personnes — fragile au moindre départ. **Wiki ops** = espace partagé (Confluence, Notion, GitBook…) accessible à toute l'équipe technique. **Plan de continuité** (BCP) = document légal/contractuel qui décrit comment l'entreprise reprend le service après un sinistre.

À l'issue de la procédure 3, mettre à jour :

| Document | Contenu à mettre à jour |
|----------|--------------------------|
| `deploiement/runbook_exploitation.md` | URL réelle, IP, port SSH, contacts, calendrier des backups |
| Wiki ops | Liens vers Grafana, kubeconfig, secrets coffre |
| `CLAUDE.md` racine | Section `## 12. Production` à ajouter (URL, version déployée, date go-live) |
| Plan de continuité | Ajouter le serveur city-prod-01 au registre des actifs |
| Légal / contrat client | Mention que la prod est en place et la SLA active |

---

## I. Aller plus loin (post-go-live)

> **💡 En clair** — La V1 est le point de départ, pas l'arrivée. Améliorations possibles selon les besoins futurs :
> - **HA Postgres** = configurer 2 serveurs Postgres en réplication, l'un prend le relais si l'autre tombe. **Patroni** = outil qui gère ce basculement automatique.
> - **WAF** (Web Application Firewall) = pare-feu qui inspecte le contenu HTTP et bloque les attaques web courantes (SQL injection, XSS).
> - **CDN** (Content Delivery Network) = réseau de serveurs répartis dans le monde qui mettent en cache les fichiers statiques pour accélérer le chargement.
> - **Tests de chaos** = on tue volontairement des conteneurs en prod pour vérifier que le système se rétablit (philosophie Netflix « Chaos Monkey »).
> - **Pentest** (penetration testing) = audit où des spécialistes simulent une attaque réelle pour trouver des failles avant les vrais attaquants.

| Amélioration | Quand l'envisager |
|--------------|-------------------|
| HA Postgres (Patroni / Citus) | dès 2 hôtels actifs ou utilisation 24/7 |
| 2ᵉ nœud K3s + storage Longhorn | budget hardware disponible |
| WAF (ModSecurity, CrowdSec) | si trafic public important ou attaques détectées |
| CDN (CloudFlare) | si latence internationale problématique |
| Tests de chaos (kill aléatoire) | une fois la stack maîtrisée par l'équipe |
| Audit pentest externe | annuel obligatoire selon contrat client |
| Migration palier 2 (Java 25 + SB 4) | Q4 2026 (cf. CLAUDE.md §3) |

---

**Fin de la procédure 3.** Le serveur est en production, monitoring actif, backups vérifiés, équipe formée. Bonne vigie.
