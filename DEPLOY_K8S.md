# Déploiement Kubernetes / K3s — city hotel v1.0.0

Guide détaillé pour déployer city hotel sur Kubernetes (k8s production cloud) ou K3s (cluster léger edge / on-premise / single-node).

**Prérequis** : avoir lu `EXECUTION_PLAN.md` (sections 5 et 6) pour le contexte build images Docker.

---

## 1. Architecture cible

```
                          ┌──────────────────┐
                          │   Cloudflare /   │  (DNS + CDN + DDoS)
                          │   internet DNS   │
                          └─────────┬────────┘
                                    │ TLS 443
                          ┌─────────▼────────┐
                          │  Ingress NGINX   │  (cert-manager + Let's Encrypt)
                          │   ou Traefik     │
                          └─────────┬────────┘
                                    │
              ┌─────────────────────┼─────────────────────┐
              │                     │                     │
       ┌──────▼──────┐      ┌──────▼──────┐      ┌──────▼──────┐
       │ frontend    │      │ backend     │      │ /actuator/  │
       │ Service     │      │ Service     │      │ (interne)   │
       │ ClusterIP   │      │ ClusterIP   │      │             │
       └──────┬──────┘      └──────┬──────┘      └─────────────┘
              │ port 80            │ port 8080
       ┌──────▼──────┐      ┌──────▼───────────┐
       │ frontend    │      │ backend          │
       │ Deployment  │      │ Deployment       │
       │ replicas: 2 │      │ replicas: 3      │
       │ (nginx)     │      │ (Spring Boot)    │
       └─────────────┘      └────────┬─────────┘
                                     │ port 5432
                            ┌────────▼─────────┐
                            │ postgres         │
                            │ StatefulSet      │
                            │ replicas: 1      │
                            │ PV 50Gi          │
                            └────────┬─────────┘
                                     │
                            ┌────────▼─────────┐
                            │ backup-cronjob   │ (02:00 NKC quotidien)
                            │ pg_dump → S3     │
                            └──────────────────┘
```

**Composants** :
- 3 deployments : frontend (HA 2 réplicas), backend (HA 3 réplicas), postgres (1 réplica StatefulSet, anti-pattern multi-instance pour PG sans Patroni)
- 1 ingress (Traefik ou NGINX, avec cert-manager Let's Encrypt)
- 1 backup CronJob (pg_dump quotidien vers S3-compatible)
- NetworkPolicies pour isoler postgres (pas d'accès direct depuis le frontend)
- HPA (Horizontal Pod Autoscaler) sur le backend (CPU/RAM threshold)
- PodDisruptionBudget (garantir qu'au moins N réplicas restent durant maintenance)

**Multi-tenant isolation réseau** : la clé d'isolation reste applicative (Hibernate `@TenantId`). Une isolation **réseau** par hôtel demanderait des namespaces multiples (1 par hôtel) — overkill pour v1.0.0.

---

## 2. Pré-requis

| Outil | Version min | Install |
|---|---|---|
| `kubectl` | 1.30+ | `winget install Kubernetes.kubectl` |
| `helm` | 3.15+ (optionnel, pour ingress/cert-manager) | `winget install Helm.Helm` |
| `kustomize` | 5.x (intégré à kubectl ≥ 1.21) | inclus dans `kubectl` |
| Cluster K8s | 1.30+ | voir options ci-dessous |

### 2.1 Options cluster

#### A) K3s — single-node léger (test, edge, on-premise)
```bash
# Linux serveur (1 commande)
curl -sfL https://get.k3s.io | sh -

# kubeconfig auto-généré à /etc/rancher/k3s/k3s.yaml
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $USER ~/.kube/config

kubectl get nodes              # confirme le node Ready
```

K3s embarque **Traefik** (ingress) + **CoreDNS** + **local-path-provisioner** (PV par défaut sur `/var/lib/rancher/k3s/storage`). Suffisant pour démarrer.

#### B) kind — cluster local Docker (dev/CI)
```bash
winget install Kubernetes.kind
kind create cluster --name city --config deploiement/ressources/k8s/kind-config.yaml   # à créer
kubectl cluster-info --context kind-city
```

#### C) Cloud managé (production réelle)
- **GKE** Google Cloud : `gcloud container clusters create city --num-nodes=3 --machine-type=e2-standard-4`
- **EKS** AWS : `eksctl create cluster --name city --region eu-west-3 --nodes 3 --node-type t3.large`
- **AKS** Azure : `az aks create -g city-rg -n city-cluster --node-count 3 --node-vm-size Standard_D4s_v5`
- **OVH/Scaleway** Europe : interface web pour créer un cluster managé

### 2.2 Modules complémentaires (cluster cloud uniquement, K3s les a déjà)
```bash
# cert-manager (Let's Encrypt automatique)
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/latest/download/cert-manager.yaml

# Ingress NGINX (alternatif à Traefik)
helm upgrade --install ingress-nginx ingress-nginx \
  --repo https://kubernetes.github.io/ingress-nginx \
  --namespace ingress-nginx --create-namespace
```

---

## 3. Préparer les manifests

Tous les manifests sont dans `deploiement/ressources/k8s/` (committés au Tour 1). Inventaire :

| Fichier | Rôle |
|---|---|
| `namespace.yaml` | Namespace `city-hotel` |
| `configmap-backend.yaml` | Config non-sensible (profil prod, DB url, mail host) |
| `secret-backend.yaml.example` | **Template** : copier en `secret-backend.yaml` puis remplir avec vrais secrets |
| `secret-postgres.yaml.example` | **Template** : POSTGRES_PASSWORD |
| `postgres-statefulset.yaml` | StatefulSet PG 18 + PVC 50 Gi + Service ClusterIP |
| `backend-deployment.yaml` | Deployment 3 réplicas Spring Boot + Service ClusterIP |
| `frontend-deployment.yaml` | Deployment 2 réplicas nginx + Service ClusterIP |
| `ingress.yaml` | Routes `/` → frontend, `/citybackend` → backend, TLS via cert-manager |
| `letsencrypt-issuer.yaml` | ClusterIssuer cert-manager (staging + prod) |
| `networkpolicy.yaml` | Isolement postgres (seul backend peut le contacter) |
| `poddisruptionbudget.yaml` | minAvailable: 1 backend pendant rolling updates |
| `backup-cronjob.yaml` | pg_dump quotidien 02:00 NKC + upload S3 |
| `kustomization.yaml` | Orchestre tout l'apply via `kubectl apply -k` |

### 3.1 Créer les secrets (à partir des templates)

```bash
cd deploiement/ressources/k8s

cp secret-backend.yaml.example secret-backend.yaml
cp secret-postgres.yaml.example secret-postgres.yaml
```

Éditer `secret-backend.yaml` :
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: backend-secrets
  namespace: city-hotel
type: Opaque
stringData:
  jwt-secret: "<min 64 chars random — openssl rand -base64 64>"
  db-username: "cityapp"
  db-password: "<password fort>"
  mail-password: "<smtp password ou vide>"
```

Éditer `secret-postgres.yaml` :
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: postgres-secrets
  namespace: city-hotel
type: Opaque
stringData:
  postgres-password: "<password fort root postgres>"
```

⚠️ **Ne JAMAIS commiter `secret-backend.yaml` ni `secret-postgres.yaml`** (ajouter à `.gitignore` si pas déjà — vérifier avec `git check-ignore deploiement/ressources/k8s/secret-*.yaml`).

### 3.2 Adapter `ingress.yaml` au domaine cible

```yaml
spec:
  rules:
    - host: cityhotel.example.com           # ← votre domaine
      http:
        paths: [...]
  tls:
    - hosts: [cityhotel.example.com]        # ← votre domaine
      secretName: cityhotel-tls
```

### 3.3 Adapter `letsencrypt-issuer.yaml` à votre email
```yaml
spec:
  acme:
    email: ops@city-hotel.example.com       # ← votre email pour expiration certs
```

---

## 4. Build et push des images Docker

### 4.1 Backend via Jib (sans Docker daemon)
```bash
cd citybackend
./mvnw jib:build \
  -Djib.to.image=ghcr.io/sadimov/citybackend:1.0.0 \
  -Djib.to.auth.username=$GHCR_USER \
  -Djib.to.auth.password=$GHCR_PAT
```

### 4.2 Frontend via Docker
```bash
docker build \
  -f deploiement/ressources/docker/Dockerfile.frontend \
  -t ghcr.io/sadimov/cityfrontend:1.0.0 \
  .

echo $GHCR_PAT | docker login ghcr.io -u $GHCR_USER --password-stdin
docker push ghcr.io/sadimov/cityfrontend:1.0.0
```

### 4.3 Vérifier les images dans le registry
- GitHub Container Registry : https://github.com/sadimov?tab=packages

---

## 5. Déploiement initial

### 5.1 Apply via kustomize (recommandé)
```bash
cd deploiement/ressources/k8s
kubectl apply -k .
# OU avec kustomize CLI :
kustomize build . | kubectl apply -f -
```

Cela applique dans l'ordre : namespace → secrets → configmap → postgres-statefulset → backend-deployment + service → frontend-deployment + service → ingress → letsencrypt-issuer → networkpolicy → poddisruptionbudget → backup-cronjob.

### 5.2 Vérifier le déploiement
```bash
kubectl -n city-hotel get all
kubectl -n city-hotel get pvc                     # postgres-data Bound
kubectl -n city-hotel get certificate             # cityhotel-tls Ready=True
kubectl -n city-hotel get ingress                 # ADDRESS = IP/hostname Ingress
```

✅ État final attendu :
```
NAME                            READY   STATUS    RESTARTS   AGE
pod/backend-xxxxxxxx-aaaaa      1/1     Running   0          2m
pod/backend-xxxxxxxx-bbbbb      1/1     Running   0          2m
pod/backend-xxxxxxxx-ccccc      1/1     Running   0          2m
pod/frontend-xxxxxxxx-ddddd     1/1     Running   0          2m
pod/frontend-xxxxxxxx-eeeee     1/1     Running   0          2m
pod/postgres-0                  1/1     Running   0          2m
```

### 5.3 Logs au démarrage
```bash
kubectl -n city-hotel logs -f deployment/backend                # suivre le boot Spring Boot + Liquibase
kubectl -n city-hotel logs -f statefulset/postgres
kubectl -n city-hotel logs -f deployment/frontend
```

### 5.4 Premier login
1. Ouvrir https://cityhotel.example.com (votre domaine)
2. Login : `superadmin` / `SuperAdmin123!`
3. **Rotation immédiate** du mot de passe (manuelle BDD ou via futur endpoint `/auth/change-password`)

---

## 6. HPA (Horizontal Pod Autoscaler)

Activer l'autoscaling du backend selon CPU :
```bash
kubectl -n city-hotel autoscale deployment backend --min=2 --max=10 --cpu-percent=70
kubectl -n city-hotel get hpa
```

Pour CPU+RAM combiné, créer manuellement :
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: backend-hpa
  namespace: city-hotel
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: backend
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target: { type: Utilization, averageUtilization: 70 }
    - type: Resource
      resource:
        name: memory
        target: { type: Utilization, averageUtilization: 80 }
```

⚠️ **Pré-requis HPA** : metrics-server installé sur le cluster. K3s l'embarque ; sur cloud managé, vérifier `kubectl top nodes` (si erreur → `kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml`).

---

## 7. NetworkPolicies — isolement postgres

Le `networkpolicy.yaml` interdit tout trafic vers le pod postgres SAUF depuis les pods backend (label `app=backend`). Vérifier :

```bash
# Test : depuis frontend, postgres doit être inaccessible
kubectl -n city-hotel exec -it deployment/frontend -- nc -zv postgres 5432
# attendu : Connection refused / timeout

# Test : depuis backend, postgres OK
kubectl -n city-hotel exec -it deployment/backend -- nc -zv postgres 5432
# attendu : Connection succeeded
```

⚠️ NetworkPolicies ne fonctionnent QU'AVEC un CNI compatible (Calico, Cilium, Flannel + canal). Sur K3s par défaut (Flannel) : **non supporté nativement**, il faut basculer sur Calico ou Cilium :
```bash
# Désactiver Flannel à l'install K3s
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--flannel-backend=none --disable-network-policy" sh -
# Puis installer Calico
kubectl apply -f https://raw.githubusercontent.com/projectcalico/calico/v3.27.0/manifests/calico.yaml
```

---

## 8. Backup PostgreSQL

`backup-cronjob.yaml` lance `pg_dump` chaque jour à 02:00 NKC, compresse, upload S3.

### 8.1 Configurer credentials S3 (template à adapter)
```yaml
env:
  - { name: AWS_ACCESS_KEY_ID, valueFrom: { secretKeyRef: { name: backup-s3, key: access-key } } }
  - { name: AWS_SECRET_ACCESS_KEY, valueFrom: { secretKeyRef: { name: backup-s3, key: secret-key } } }
  - { name: S3_BUCKET, value: "city-hotel-backups" }
  - { name: AWS_DEFAULT_REGION, value: "eu-west-3" }
```

### 8.2 Tester un backup à la demande
```bash
kubectl -n city-hotel create job --from=cronjob/postgres-backup backup-manual-$(date +%s)
kubectl -n city-hotel logs -f job/backup-manual-XXXXX
```

### 8.3 Restore (urgence)
```bash
# Télécharger le dump depuis S3
aws s3 cp s3://city-hotel-backups/cityprojectdb-2026-05-10.sql.gz .

# Décompresser
gunzip cityprojectdb-2026-05-10.sql.gz

# Restore via le pod postgres
kubectl -n city-hotel cp cityprojectdb-2026-05-10.sql postgres-0:/tmp/restore.sql
kubectl -n city-hotel exec -it postgres-0 -- psql -U postgres -d cityprojectdb -f /tmp/restore.sql
```

---

## 9. Monitoring (optionnel mais recommandé en prod)

### 9.1 Prometheus + Grafana (Helm)
```bash
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --repo https://prometheus-community.github.io/helm-charts \
  --namespace monitoring --create-namespace
```

Spring Boot expose les metrics Micrometer sur `/actuator/prometheus` (déjà activé dans `application.yml`). Créer un `ServiceMonitor` pour scraper :

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: city-backend
  namespace: monitoring
spec:
  selector:
    matchLabels:
      app: backend
  namespaceSelector:
    matchNames: [city-hotel]
  endpoints:
    - port: http
      path: /citybackend/actuator/prometheus
      interval: 30s
```

### 9.2 Dashboards Grafana
- Spring Boot 3 dashboard officiel : ID `12900` ou `19004` (importer dans Grafana UI)
- PostgreSQL : ID `9628`
- Kubernetes cluster : ID `15660`

---

## 10. Mise à jour (rolling)

```bash
# 1. Build + push nouvelle image
cd citybackend && ./mvnw jib:build -Djib.to.image=ghcr.io/sadimov/citybackend:1.0.1
docker build -f deploiement/ressources/docker/Dockerfile.frontend -t ghcr.io/sadimov/cityfrontend:1.0.1 .
docker push ghcr.io/sadimov/cityfrontend:1.0.1

# 2. Update les Deployments (rolling, zéro downtime grâce au PDB minAvailable: 1)
kubectl -n city-hotel set image deployment/backend backend=ghcr.io/sadimov/citybackend:1.0.1
kubectl -n city-hotel set image deployment/frontend frontend=ghcr.io/sadimov/cityfrontend:1.0.1

# 3. Suivre le rollout
kubectl -n city-hotel rollout status deployment/backend
kubectl -n city-hotel rollout status deployment/frontend

# 4. Rollback si problème
kubectl -n city-hotel rollout undo deployment/backend
```

⚠️ **Liquibase migrations** : le nouveau backend exécute les changesets en attente AU DÉMARRAGE de chaque pod. Si la migration est lourde et longue, le 1er pod prend du temps à devenir Ready (les autres attendent grâce à `maxSurge: 1`). Pour les migrations critiques, dépouiller via un Job dédié AVANT le rollout :

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: liquibase-migrate-1.0.1
spec:
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: migrate
          image: ghcr.io/sadimov/citybackend:1.0.1
          command: ["java", "-jar", "/app/citybackend.jar"]
          args: ["--spring.profiles.active=migrate-only"]   # profil custom à ajouter
```

---

## 11. K3s spécifique — ressources réduites

K3s = single-node ou cluster minimaliste. Adapter les ressources :

```yaml
# backend-deployment.yaml override (kustomize)
spec:
  replicas: 1                    # au lieu de 3
  resources:
    requests:
      cpu: "200m"
      memory: "512Mi"
    limits:
      cpu: "1000m"
      memory: "1024Mi"           # au lieu de 2Gi
```

K3s ingress par défaut = **Traefik** (port 80/443 directement sur le node). Ingress YAML standard (apiVersion `networking.k8s.io/v1`) compatible.

---

## 12. Troubleshooting

| Symptôme | Cause probable | Fix |
|---|---|---|
| `pod/backend-xxx CrashLoopBackOff` | JWT_SECRET invalide ou DB inaccessible | `kubectl -n city-hotel logs deployment/backend` → vérifier le message d'erreur |
| `kubectl get certificate` reste `Ready=False` longtemps | cert-manager / Let's Encrypt rate limit ou DNS pas propagé | `kubectl -n cert-manager logs deployment/cert-manager` ; vérifier `dig <domaine>` retourne l'IP du LB |
| `502 Bad Gateway` sur l'ingress | Backend pas Ready ou healthcheck échoue | `kubectl -n city-hotel describe pod backend-xxx` → voir Events |
| `403 Forbidden` constant | NetworkPolicy mal configurée OU ConfigMap manquant | `kubectl -n city-hotel describe networkpolicy` ; `kubectl -n city-hotel get configmap` |
| `pod/postgres-0 Pending` | PVC pas Bound (pas de StorageClass par défaut) | `kubectl get sc` ; créer une StorageClass ou adapter `storageClassName` dans le StatefulSet |
| `OOMKilled` sur backend | RAM insuffisante (Liquibase + JIT au boot) | Augmenter `resources.limits.memory` à 2Gi minimum |
| `429 Too Many Requests` | RateLimitFilter actif (10/60s/IP) | OK fonctionnellement ; ajuster dans `application.yml` si trop strict |

---

## 13. Désinstallation complète

```bash
# Supprimer toutes les ressources city-hotel
kubectl delete -k deploiement/ressources/k8s/

# ⚠️ Le PVC postgres-data n'est PAS supprimé par défaut (sécurité données)
# Pour supprimer aussi les données :
kubectl -n city-hotel delete pvc postgres-data-postgres-0

# Supprimer le namespace
kubectl delete namespace city-hotel
```

---

## 14. Liens utiles

| | |
|---|---|
| Manifests K8s | `deploiement/ressources/k8s/` |
| Architecture détaillée | `deploiement/architecture.md` |
| Runbook exploitation | `deploiement/runbook_exploitation.md` |
| Checklist go-live | `deploiement/checklist_go_live.md` |
| Doc K3s officielle | https://docs.k3s.io |
| cert-manager | https://cert-manager.io |
| Spring Boot Actuator + Prometheus | https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics |
| Plan d'exécution général | `EXECUTION_PLAN.md` |
| Quickstart Windows local | `QUICKSTART_WINDOWS.md` |

---

**Fin Déploiement K8s/K3s v1.0.0**
