# Formation — Déployer city en production : tout ce qu'il faut comprendre

> **À qui s'adresse ce document ?** À toute personne qui doit comprendre, exécuter, ou superviser le déploiement de city en production sans avoir d'expertise préalable en Linux, Docker, Kubernetes ou DevOps. **Aucun prérequis** sauf savoir ouvrir un terminal et lire un peu d'anglais technique.
>
> **Format** : 12 chapitres + glossaire. Chaque chapitre = 10-20 minutes de lecture, avec analogies, exemples concrets, schémas ASCII, et liens vers les procédures (`01_preparation_serveur.md`, `02_preparation_application.md`, `03_deploiement_final.md`) qui passent à la pratique.
>
> **Comment l'utiliser** : lecture linéaire la 1ʳᵉ fois, puis référence ponctuelle (le sommaire ci-dessous est cliquable).

---

## Sommaire

1. [Comprendre ce qu'est le déploiement](#chap1)
2. [Le serveur : matériel & système d'exploitation](#chap2)
3. [Linux côté serveur : ce qui change par rapport à Windows](#chap3)
4. [Docker : la conteneurisation expliquée simplement](#chap4)
5. [Kubernetes & K3s : pourquoi et comment](#chap5)
6. [Les concepts du déploiement : images, registres, rollouts, probes](#chap6)
7. [La sécurité : firewall, TLS, secrets, hardening](#chap7)
8. [Les bases de données en production : Postgres, Liquibase, backups](#chap8)
9. [Monitoring & incidents : voir et réagir](#chap9)
10. [CI/CD : automatiser pour fiabiliser](#chap10)
11. [Spécificités city : multi-tenant, plan comptable, MRU, NKC](#chap11)
12. [Mode opératoire jour J : enchaîner les 3 procédures](#chap12)
13. [Glossaire alphabétique de tous les termes](#glossaire)

---

<a id="chap1"></a>
## 1. Comprendre ce qu'est le déploiement

### 1.1 Une analogie : du brouillon à la pièce de théâtre

Quand on développe une application, on l'exécute sur son **laptop** (Windows ici). Tout se passe dans un environnement contrôlé : un seul utilisateur (le dev), une base de données locale, des configurations de test. C'est le **brouillon**.

Le **déploiement en production**, c'est mettre cette application sur un serveur **accessible aux utilisateurs réels**, 24h/24, avec des données réelles, des sauvegardes, un nom de domaine, un cadenas HTTPS, une supervision. C'est la **représentation en public**.

Entre les deux, il y a énormément de différences :

| Aspect | Sur le laptop (dev) | En production |
|--------|---------------------|---------------|
| Disponibilité | quand le dev veut | 24/7, indispensable |
| Utilisateurs | 1 (le dev) | beaucoup, en parallèle |
| Données | jouet, recréables | réelles, irrécupérables si perdues |
| Sécurité | minimale | maximale |
| Performances | « ça suffit » | mesurées, alarmées |
| Mises à jour | en direct | planifiées, réversibles |

**Le déploiement = combler tous ces écarts.**

### 1.2 Les 3 étapes universelles d'un déploiement

Quels que soient les outils, tout déploiement passe par **3 phases**, qu'on retrouve dans nos 3 procédures :

1. **Préparer l'hébergement** (procédure 1) : avoir une machine, un OS, le réseau, les outils de base.
2. **Préparer l'application** (procédure 2) : transformer le code en quelque chose de déployable, gérer les configurations, les secrets, les versions.
3. **Mettre en service** (procédure 3) : démarrer l'app, vérifier, ouvrir au public, surveiller.

Cette séquence est valable que vous déployiez sur un cloud public, sur un serveur loué, ou sur une vieille tour dans un placard. Seuls les outils changent.

### 1.3 Pourquoi pas juste « copier le code sur le serveur » ?

C'était la méthode années 2000. Les problèmes :
- **Conflits de versions** : « ça marche chez moi avec Java 21 » mais le serveur a Java 17.
- **Rollback impossible** : si la nouvelle version casse, comment revenir vite à l'ancienne ?
- **Données mélangées** : une variable de test laissée traîner peut détruire la prod.
- **Pas reproductible** : si on doit recréer le serveur, personne ne se souvient de toutes les commandes faites à la main.

Les **conteneurs** (Docker) et **l'orchestration** (Kubernetes) résolvent ces problèmes : on emballe l'app + ses dépendances dans une image immuable, on la déploie n'importe où, on rollback en une commande. C'est ce qu'on va faire avec city.

---

<a id="chap2"></a>
## 2. Le serveur : matériel & système d'exploitation

### 2.1 Qu'est-ce qu'un serveur ?

Physiquement, c'est un ordinateur — souvent **sans clavier ni écran**, posé dans un rack dans une salle climatisée (datacenter) ou un placard technique (« salle serveur »). Il a :

- Des **CPU** (processeurs) avec plusieurs **cœurs** : on parle souvent de **vCPU** (virtual CPU) car les CPU modernes ont des techniques (Hyperthreading) qui doublent leur capacité apparente. 8 vCPU ≈ 4 cœurs physiques.
- De la **RAM** (mémoire vive) : où l'application tourne. Quand on dit `2 Gi` (gibioctet), c'est 2 × 2³⁰ octets, soit ~2,15 Go.
- Des **disques** : SSD (rapide) ou HDD (lent), parfois en **RAID** (plusieurs disques en miroir = si un meurt, l'autre prend le relais).
- Une **carte réseau** : débit en Gbps (gigabits par seconde).
- Un **BMC/iLO/iDRAC** : ordinateur dans l'ordinateur qui permet l'administration à distance même si le serveur est éteint (allumage, reformatage, console virtuelle).

### 2.2 Le système d'exploitation (OS)

L'OS est le « chef d'orchestre » du matériel. Il y a deux familles principales en 2026 :

- **Windows Server** : payant, interface graphique disponible, populaire en entreprise pour Active Directory et applications Microsoft.
- **Linux** : gratuit, sans interface graphique en serveur (administration par SSH), populaire pour le web et le cloud (90 % des serveurs Internet).

Notre serveur passe de **Windows Server 2016** (vieux, fin de vie) à **Ubuntu Server 24.04 LTS** :
- **Ubuntu** = distribution Linux populaire, soutenue par l'entreprise Canonical.
- **Server** = sans interface graphique (gain de RAM et de surface d'attaque).
- **24.04** = version d'avril 2024 (le « 04 » est l'année 2024, le « 04 » le mois). LTS sortent tous les 2 ans en avril des années paires.
- **LTS** (Long Term Support) = supportée 5 ans (sécurité incluse), voire 12 ans avec ESM. Idéal pour la prod.

### 2.3 Pourquoi reformater plutôt qu'upgrader ?

Pas de chemin direct Windows → Linux : il faut effacer le disque et installer Linux à la place. C'est :
- **Définitif** : si on rate la sauvegarde, on perd les données Windows.
- **Long** : ~2 heures pour l'installation propre + ~4 heures de hardening.
- **Préférable** : un système fraîchement installé est plus propre qu'un système traîné depuis 8 ans.

### 2.4 Bare-metal vs virtuel

- **Bare-metal** = on installe Linux directement sur le matériel. Performances max, mais 1 serveur = 1 OS.
- **Virtuel** (VM) = un hyperviseur (VMware, Proxmox, KVM) découpe le serveur en plusieurs VM. Plus flexible, légère perte de performance.

Pour city V1, **bare-metal** est suffisant et plus simple.

---

<a id="chap3"></a>
## 3. Linux côté serveur : ce qui change par rapport à Windows

### 3.1 Pas d'interface graphique

L'admin Linux serveur se fait via **terminal** (ligne de commande). On s'y connecte par **SSH** (Secure Shell), un canal chiffré : depuis Windows on utilise PuTTY, MobaXterm, Windows Terminal ; depuis Linux/Mac, simplement la commande `ssh`.

Pour comprendre les commandes :
```bash
sudo apt update && sudo apt install nginx
```
- `sudo` = exécuter en tant qu'administrateur (équivalent de « clic droit > Exécuter en tant qu'admin » sous Windows).
- `apt` = gestionnaire de paquets Ubuntu/Debian (l'équivalent du Microsoft Store mais en mode texte).
- `update` = met à jour la liste des paquets disponibles.
- `install nginx` = installe le paquet `nginx`.
- `&&` = enchaîne deux commandes : la 2ᵉ s'exécute si la 1ʳᵉ a réussi.

### 3.2 L'arborescence Linux

| Dossier           | Rôle                                     | Équivalent Windows           |
|-------------------|------------------------------------------|------------------------------|
| `/`               | racine du système                        | `C:\`                        |
| `/home/cityadmin` | dossier personnel                        | `C:\Users\cityadmin`         |
| `/etc`            | fichiers de configuration système        | `C:\Windows\System32\config` |
| `/var/log`        | logs                                     | `C:\Windows\Logs`            |
| `/var/lib`        | données des services                     | `C:\ProgramData`             |
| `/usr/local/bin`  | exécutables ajoutés par l'admin          | `C:\Program Files`           |
| `/tmp`            | fichiers temporaires (effacés au reboot) | `C:\Windows\Temp`            |

### 3.3 Les utilisateurs et les permissions

Sous Linux :
- `root` (UID 0) = super-admin tout-puissant. **On ne se connecte JAMAIS directement en root** en prod.
- Un utilisateur normal (`cityadmin`) appartient au groupe `sudo`, ce qui lui permet d'utiliser `sudo` ponctuellement.
- Chaque fichier a un **propriétaire** + un **groupe** + des **permissions** (lire, écrire, exécuter) pour 3 catégories : propriétaire, groupe, autres.

Notation des permissions :
- `rwx` = read/write/execute. Équivalent numérique : `7` (`4+2+1`).
- `chmod 600 secret.txt` = lecture+écriture pour le propriétaire seulement (RW pour lui, rien pour les autres). Idéal pour un fichier de mot de passe.
- `chmod 755 script.sh` = propriétaire RWX, groupe et autres RX. Idéal pour un exécutable accessible à tous.

### 3.4 Les services (systemd)

Un service = un programme qui tourne en arrière-plan (équivalent Windows Service). Linux moderne utilise **systemd** :

```bash
sudo systemctl status ssh         # voir l'état du service SSH
sudo systemctl restart ssh        # le redémarrer
sudo systemctl enable ssh         # le démarrer automatiquement au boot
sudo systemctl disable cups       # ne plus le démarrer au boot
sudo journalctl -u ssh -f         # suivre les logs du service en temps réel
```

### 3.5 LVM : disques élastiques

**LVM** (Logical Volume Manager) = abstraction au-dessus des disques :
- **PV** (Physical Volume) = un disque physique (ex : `/dev/sdb`).
- **VG** (Volume Group) = ensemble de PVs, comme un grand pool d'espace.
- **LV** (Logical Volume) = « partition virtuelle » dans le VG, redimensionnable à chaud.

Avantage : si la partition `/var/lib/rancher` se remplit, on fait `lvextend -L +50G` puis `resize2fs` → 50 Go de plus sans reboot. Sans LVM, il faudrait sauvegarder, reformater, restaurer.

### 3.6 Logs et `journalctl`

Tout ce que les services écrivent passe par **systemd-journald**. On les lit avec :
```bash
journalctl --since "1 hour ago"             # 1 dernière heure
journalctl -u city-backend.service --tail   # logs d'un service précis
journalctl -p err --since today             # uniquement les erreurs du jour
```

Format JSON disponible (`-o json`) pour ingestion par Loki/Promtail.

---

<a id="chap4"></a>
## 4. Docker : la conteneurisation expliquée simplement

### 4.1 Le problème que Docker résout

Imaginez : un dev a écrit l'application sur son laptop avec **Java 21, Postgres 18, libssl 3.4**. On copie le code sur le serveur qui a **Java 17, Postgres 14, libssl 1.1** → ça plante.

Avant Docker, on utilisait des **VM** (machines virtuelles) : on emballait tout l'OS dans un fichier de plusieurs Go. Lourd, lent à démarrer.

**Docker** = on emballe **uniquement** l'application + ses dépendances (pas tout l'OS), via un mécanisme du noyau Linux (`namespaces` + `cgroups`) qui isole les processus comme s'ils étaient dans une mini-machine. Résultat :
- **Image** = ~100 Mo (vs ~2 Go pour une VM).
- **Démarrage** = ~1 seconde (vs ~30 s pour une VM).
- **Portable** : cette image tournera **identiquement** sur le laptop, le serveur, le cloud.

### 4.2 Vocabulaire Docker

| Terme | Définition simple |
|-------|-------------------|
| **Image** | « modèle » figé, immuable, qu'on construit une fois. Identifiée par un **tag** (ex : `city-backend:1.0.0`). |
| **Conteneur** | instance qui tourne, créée à partir d'une image. On peut en lancer plusieurs en parallèle depuis la même image. |
| **Dockerfile** | recette qui décrit comment construire l'image (« copie le code, installe Maven, compile, lance ce binaire »). |
| **Registre** | dépôt distant qui stocke les images (Docker Hub, ghcr.io, Harbor). Comme un GitHub pour les images. |
| **Couche** (layer) | chaque ligne du Dockerfile produit une couche ; les couches identiques entre images sont partagées (gain disque + bande passante). |
| **Volume** | dossier persistant monté dans le conteneur. Survit au redémarrage. |
| **Network** | réseau virtuel qui relie plusieurs conteneurs. |

### 4.3 Lecture d'un Dockerfile

Notre `Dockerfile.backend` simplifié :
```dockerfile
FROM eclipse-temurin:21.0.5_11-jdk-noble AS builder    # 1. partir d'une image Java 21 avec Maven
WORKDIR /build                                           # 2. se mettre dans /build
COPY pom.xml .                                           # 3. copier le pom
RUN ./mvnw dependency:go-offline                         # 4. télécharger les libs (mis en cache)
COPY src ./src                                           # 5. copier le code
RUN ./mvnw package                                       # 6. compiler

FROM eclipse-temurin:21.0.5_11-jre-noble                 # 7. nouvelle étape : image Java sans le JDK
COPY --from=builder /build/target/app.jar /app/          # 8. récupérer le jar de l'étape 1
EXPOSE 8080                                              # 9. déclarer le port utilisé
ENTRYPOINT ["java", "-jar", "/app/app.jar"]              # 10. commande à exécuter
```

Le fait d'avoir **2 stages** = on bénéficie de Maven pour compiler (1ʳᵉ étape lourde) mais l'image finale ne contient que le JRE et le jar (légère). C'est ce qu'on appelle **multi-stage build**.

### 4.4 Commandes Docker indispensables

```bash
docker images                                       # lister les images locales
docker ps                                           # lister les conteneurs en cours
docker ps -a                                        # tous, y compris les arrêtés
docker logs -f city-backend                         # suivre les logs en direct
docker exec -it city-backend bash                   # ouvrir un shell dans le conteneur
docker run --rm -it ubuntu:24.04 bash               # conteneur jetable pour tester
docker pull ghcr.io/myorg/city-backend:1.0.0        # télécharger une image
docker push ghcr.io/myorg/city-backend:1.0.0        # publier
docker system prune -a                              # nettoyer les images/volumes inutilisés
```

### 4.5 Docker Compose

**Un seul** conteneur, c'est rare en prod. On a souvent : appli + base + cache + reverse proxy. Compose décrit tout dans un fichier YAML :

```yaml
services:
  postgres:
    image: postgres:18.3
    volumes: [postgres-data:/var/lib/postgresql/data]
  backend:
    image: city-backend:1.0.0
    depends_on: [postgres]
    environment:
      DB_HOST: postgres                        # nom de service = nom DNS interne
```

`docker compose up -d` lance tous ces services dans le bon ordre. C'est notre **plan B** si on ne veut pas de Kubernetes.

---

<a id="chap5"></a>
## 5. Kubernetes & K3s : pourquoi et comment

### 5.1 Quand Compose ne suffit plus

Compose, c'est super sur 1 serveur. Mais :
- **Pas d'auto-restart sophistiqué** : si un conteneur a une fuite mémoire et plante toutes les 10 min, Compose le relance bêtement à l'identique.
- **Pas d'auto-scale** : si la charge double, il faut intervenir manuellement.
- **Pas de rolling update zéro downtime** : pour mettre à jour, on coupe et on redémarre (coupure de service).
- **Pas de réelle distribution sur plusieurs serveurs** : Compose ne sait pas vraiment gérer un cluster.

**Kubernetes (K8s)** résout tout ça en proposant un **modèle déclaratif** : on décrit l'état souhaité (« 2 backend toujours, scale jusqu'à 6 si CPU > 70% »), et K8s s'arrange pour y arriver et **maintenir cet état** quoi qu'il arrive.

### 5.2 Anatomie d'un cluster Kubernetes

```
       ┌──────────────────────────┐
       │  Control plane (cerveau) │
       │  - API server            │   ← reçoit les ordres (kubectl)
       │  - Scheduler             │   ← décide où placer les pods
       │  - Controller manager    │   ← maintient l'état désiré
       │  - etcd                  │   ← base de données (clé/valeur)
       └────────────┬─────────────┘
                    │
       ┌────────────┴────────────┐
       │  Worker nodes (muscles) │
       │  ┌──────┐  ┌──────┐     │
       │  │Pod A │  │Pod B │     │   ← conteneurs qui exécutent l'app
       │  └──────┘  └──────┘     │
       │  - kubelet              │   ← agent qui exécute les Pods
       │  - kube-proxy           │   ← gère le réseau
       └─────────────────────────┘
```

Sur city, le serveur est **à la fois control plane ET worker** (single-node).

### 5.3 K3s vs Kubernetes vanilla

**K3s** = distribution Kubernetes par Rancher Labs, optimisée pour les petits déploiements :
- Tient en **un seul binaire** (~60 Mo).
- Embarque **Traefik** (reverse proxy) prêt à l'emploi.
- Embarque **local-path** (provisionneur de volumes simple).
- Empreinte mémoire ~500 Mo (vs 2 Go pour vanilla).
- API 100 % compatible K8s : on apprend une fois, ça marche partout.

### 5.4 Les objets Kubernetes principaux

| Objet | Définition simple | Analogie |
|-------|-------------------|----------|
| **Pod** | 1 ou plusieurs conteneurs qui partagent réseau/volumes ; unité d'exécution de base | « un conteneur emballé avec ses copains nécessaires » |
| **Deployment** | « assure-toi que N replicas de ce Pod tournent » | « le contremaître qui surveille les ouvriers » |
| **StatefulSet** | comme Deployment mais pour des composants à état (DB) avec identité stable | « ouvriers nominatifs avec leur casier » |
| **Service** | adresse IP/DNS interne stable qui pointe vers les Pods d'un Deployment | « numéro de standard téléphonique » |
| **Ingress** | règle pour exposer un Service à l'extérieur via un nom de domaine | « pancarte à l'entrée du bâtiment » |
| **ConfigMap** | dictionnaire de paires clé/valeur non sensibles | « post-it avec les paramètres » |
| **Secret** | comme ConfigMap mais pour les valeurs sensibles | « post-it dans une enveloppe scellée » |
| **PersistentVolumeClaim (PVC)** | demande de stockage persistant | « bon de commande pour 200 Go de disque » |
| **PersistentVolume (PV)** | le stockage réellement attribué | « les 200 Go vraiment alloués » |
| **Namespace** | dossier logique qui isole les objets | « bureau dans un open-space » |
| **Job / CronJob** | tâche ponctuelle (Job) ou planifiée (CronJob) | « livreur qui passe une fois » / « facteur quotidien » |
| **HorizontalPodAutoscaler (HPA)** | crée/supprime automatiquement des Pods selon CPU/RAM | « manager qui embauche/débauche selon l'activité » |
| **NetworkPolicy** | règle de pare-feu entre Pods | « badge qui dit qui parle à qui » |
| **PodDisruptionBudget (PDB)** | « garde au moins N Pods dispo pendant les opérations » | « pause cigarette pas tous en même temps » |
| **Probe** | sonde de santé (liveness/readiness/startup) | « visite médicale » |

### 5.5 La commande `kubectl`

C'est notre **télécommande** pour parler au cluster.

```bash
kubectl get pods -n city                                # lister les Pods du namespace city
kubectl describe pod city-backend-abc123 -n city        # détails d'un Pod
kubectl logs -f city-backend-abc123 -n city             # suivre les logs
kubectl exec -it city-backend-abc123 -n city -- bash    # ouvrir un shell dedans
kubectl apply -f manifest.yaml                          # appliquer un manifest
kubectl delete -f manifest.yaml                         # le retirer
kubectl rollout status deploy/city-backend -n city      # attendre que le rollout finisse
kubectl rollout undo deploy/city-backend -n city        # rollback à la version précédente
kubectl scale deploy/city-backend --replicas=4 -n city  # changer le nombre de replicas manuellement
kubectl top pods -n city                                # CPU/RAM utilisés par Pod
```

### 5.6 Le pattern « déclaratif »

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: city-backend
  namespace: city
spec:
  replicas: 2                        # je veux 2 instances
  template:
    spec:
      containers:
        - name: backend
          image: ghcr.io/myorg/city-backend:1.0.0
          ports:
            - containerPort: 8080
```

On dit ce qu'on veut ; K8s s'occupe de :
- créer les 2 Pods,
- les redémarrer s'ils crashent,
- les recréer ailleurs si le nœud meurt,
- maintenir 2 Pods toujours, sauf consigne contraire.

C'est l'inverse du **mode impératif** où on fait `docker run` à la main et on espère que ça reste en l'air.

### 5.7 Helm & Kustomize

Quand on a beaucoup de YAML, on les organise :
- **Kustomize** (intégré à kubectl) : empile des YAML « overlays » par environnement (`base/`, `overlays/staging/`, `overlays/prod/`). On utilise Kustomize pour city.
- **Helm** : packager officiel, manifests = templates avec variables, distribués comme « charts ». Idéal pour installer des composants tiers (cert-manager, Prometheus). On utilise Helm pour les composants tiers.

---

<a id="chap6"></a>
## 6. Les concepts du déploiement : images, registres, rollouts, probes

### 6.1 Le cycle de vie d'une release

```
[1] Code       → git tag v1.0.0
                       ↓
[2] CI build   → image city-backend:1.0.0
                       ↓
[3] Registry   → ghcr.io/org/city-backend:1.0.0
                       ↓
[4] Manifest   → backend-deployment.yaml référence :1.0.0
                       ↓
[5] Apply      → kubectl apply -f ...
                       ↓
[6] Rollout    → K8s lance des nouveaux Pods, attend qu'ils soient READY,
                  retire les anciens un par un (ZÉRO DOWNTIME)
                       ↓
[7] Validation → smoke tests, monitoring, soak
```

### 6.2 Les probes (sondes de santé)

K8s ne suppose JAMAIS qu'un Pod est sain juste parce qu'il a démarré. Il pose 3 types de questions :

| Probe | Question | Si KO |
|-------|----------|-------|
| **startup** | « As-tu fini de démarrer ? » | redémarre le Pod après N échecs ; arrête de demander dès la 1ʳᵉ réussite |
| **readiness** | « Es-tu prêt à recevoir du trafic ? » | retire le Pod du Service (plus de trafic) mais ne le tue pas |
| **liveness** | « Es-tu encore vivant ? » | tue le Pod, K8s en relance un nouveau |

Pour Spring Boot, on utilise les endpoints Actuator :
- `/actuator/health/liveness`
- `/actuator/health/readiness`

Sans probes, K8s ne saurait pas que la JVM est plantée et continuerait à lui envoyer du trafic.

### 6.3 Rolling update zéro downtime

Stratégie par défaut d'un Deployment : on remplace les Pods un par un.

```
État initial :   [v1] [v1]
                  ↓
Étape 1 :        [v1] [v1] [v2]    ← K8s lance v2 en plus
                  ↓
Étape 2 :        [v1] [v2]         ← v2 est READY, K8s tue 1 v1
                  ↓
Étape 3 :        [v2] [v2] [v2]    ← K8s lance le 2ᵉ v2
                  ↓
État final :     [v2] [v2]         ← v2 ready, K8s tue le dernier v1
```

**À aucun moment il n'y a 0 Pod**. Avec `maxSurge: 1` et `maxUnavailable: 0`, le service reste accessible en permanence.

### 6.4 Rollback

Si v2 a un bug :
```bash
kubectl rollout undo deploy/city-backend -n city
```
K8s revient à v1 par le même mécanisme rolling. C'est **applicatif** uniquement — la base de données, elle, n'est pas rollbackée. D'où la règle d'or Liquibase : **toujours additif**, on n'efface jamais une table avec des données prod, on en crée une nouvelle ou on ajoute des colonnes nullables.

### 6.5 Versioning : SemVer

`MAJEUR.MINEUR.PATCH`
- **MAJEUR** = breaking change (API qui change). Ex : `1.0.0` → `2.0.0`.
- **MINEUR** = nouvelle fonctionnalité, rétro-compatible. Ex : `1.0.0` → `1.1.0`.
- **PATCH** = bugfix uniquement. Ex : `1.0.0` → `1.0.1`.

Pour les pré-releases : `1.1.0-rc.1` (release candidate), `1.1.0-beta.1`.

### 6.6 Tag d'image vs digest

Un **tag** (ex : `1.0.0`) est juste une étiquette posée sur une image. **Rien n'empêche techniquement** de retirer le tag d'une image et le poser sur une autre — ça arrive avec `latest`. Pour une référence vraiment immuable, on utilise le **digest** (empreinte SHA256) :

```
ghcr.io/myorg/city-backend@sha256:8a7e3a...
```

Le digest est garanti unique par contenu. À utiliser dans les manifests prod si paranoïaque.

---

<a id="chap7"></a>
## 7. La sécurité : firewall, TLS, secrets, hardening

### 7.1 Le modèle « défense en profondeur »

On ne se contente JAMAIS d'**une seule** mesure de sécurité. Si une couche est percée, les autres tiennent. Pour city, les couches :

```
Couche 7  Application : @PreAuthorize, validation JWT, isolation multi-tenant
Couche 6  Conteneur   : non-root user, read-only FS, drop capabilities
Couche 5  Cluster     : NetworkPolicies, PodSecurityStandards restricted
Couche 4  Hôte        : UFW (firewall), fail2ban, sysctl, SSH key-only
Couche 3  Réseau      : VLAN, pare-feu amont (FAI/box), pas de DMZ public
Couche 2  Physique    : salle serveur fermée, BIOS sous mot de passe
Couche 1  Humaine     : 2FA, formation, principe du moindre privilège
```

### 7.2 SSH : la porte d'entrée

SSH = canal chiffré qui remplace telnet/rsh des années 90. Trois durcissements clés :

1. **Désactiver le mot de passe** (`PasswordAuthentication no`) → seules les **clés SSH** (paires publique/privée Ed25519) sont acceptées.
2. **Désactiver root** (`PermitRootLogin no`) → on ne peut plus se connecter directement avec le compte le plus puissant.
3. **Port custom** (2222 au lieu de 22) → réduit le bruit de fond des bots qui scannent le port 22 en permanence.

**fail2ban** complète : il lit les logs SSH, et si une IP fait 3 essais échoués en 10 min, il l'ajoute à un ban iptables pour 1 h. Récidive : ban 1 semaine.

### 7.3 Pare-feu : UFW

**iptables** = mécanisme noyau Linux pour filtrer les paquets. **UFW** (Uncomplicated FireWall) est une surcouche simple :

```bash
sudo ufw default deny incoming     # par défaut, bloquer tout entrant
sudo ufw allow 443/tcp             # autoriser HTTPS
sudo ufw enable                    # activer
```

Politique standard : **default deny inbound, allow outbound** (on bloque tout en entrée sauf ce qu'on liste, on laisse sortir).

### 7.4 TLS / HTTPS

**TLS** (Transport Layer Security) = protocole qui chiffre le trafic HTTP. Quand un navigateur affiche le cadenas, ça veut dire :
1. Le serveur prouve son identité avec un **certificat** signé par une **autorité de certification** (CA) que le navigateur connaît.
2. Le navigateur et le serveur négocient une clé de session via Diffie-Hellman.
3. Tout le trafic ensuite est chiffré.

**Let's Encrypt** = CA gratuite qui automatise la délivrance de certificats. Validation par **ACME** (le serveur prouve qu'il contrôle le domaine) :
- **HTTP-01** : LE demande de servir un fichier précis sur `http://city.example.mr/.well-known/acme-challenge/...`. Si vu, certificat délivré.
- **DNS-01** : LE demande d'ajouter un enregistrement TXT au DNS. Plus complexe mais permet les wildcards `*.city.example.mr`.

**cert-manager** = opérateur Kubernetes qui automatise tout ça : il observe les Ingress, demande les certificats, les renouvelle 30 jours avant expiration.

### 7.5 Headers de sécurité HTTP

Au-delà de TLS, on configure des **headers** que le navigateur respectera :

| Header | Effet |
|--------|-------|
| `Strict-Transport-Security: max-age=31536000` | force HTTPS pour 1 an, même si l'utilisateur tape `http://` |
| `Content-Security-Policy: default-src 'self'` | refuse de charger des scripts/images d'origines non listées (anti-XSS) |
| `X-Content-Type-Options: nosniff` | empêche le navigateur de deviner le type d'un fichier (anti-MIME confusion) |
| `X-Frame-Options: SAMEORIGIN` | empêche d'embarquer le site dans une iframe (anti-clickjacking) |
| `Referrer-Policy: strict-origin-when-cross-origin` | limite l'info envoyée dans `Referer` |

Configurés dans Traefik (middleware) ou nginx (vhost), pas dans le code applicatif.

### 7.6 Secrets : ne jamais commiter

Un mot de passe commité dans git est **toujours** récupérable, même si supprimé ensuite (history). 3 techniques :

- **Sealed-Secrets** (Bitnami) : chiffrement asymétrique, le résultat (`SealedSecret`) peut être commité ; seul le cluster peut déchiffrer.
- **SOPS + age/GPG** : chiffre des fichiers YAML au repos ; les humains autorisés ont la clé privée.
- **External Secrets + Vault** : un coffre centralisé, les Pods demandent leurs secrets via un opérateur K8s.

**Rotation** = changer périodiquement. Si un mot de passe leak, dégâts limités à la fenêtre avant rotation.

### 7.7 Le principe du moindre privilège

Chaque composant n'a que les droits dont il a strictement besoin :
- Le backend tourne en **UID 10001** (pas root) → s'il est compromis, l'attaquant est dans une cage.
- Système de fichiers en **read-only** sauf `/tmp` et `/app/logs` → l'attaquant ne peut pas modifier le code applicatif.
- **Drop capabilities** (drop ALL, add seulement NET_BIND_SERVICE pour nginx) → pas de droits noyau étendus.
- **NetworkPolicies** → le backend ne peut parler qu'à postgres + DNS + SMTP, pas à n'importe quoi.

---

<a id="chap8"></a>
## 8. Les bases de données en production : Postgres, Liquibase, backups

### 8.1 Postgres : pourquoi celui-là

**PostgreSQL** = base relationnelle open source, robuste, conforme SQL standard, riche en fonctionnalités (JSON, GIS, full-text search). Choix par défaut en 2026 pour la majorité des projets web. Versions :
- **18** = sortie en 2024, supportée jusqu'en 2029.
- **JDBC** = driver Java pour parler à Postgres (`postgresql:42.7.x` dans le pom.xml city).

### 8.2 Configuration côté serveur

Quelques paramètres critiques pour 16 Go de RAM :

| Paramètre | Valeur | Effet |
|-----------|--------|-------|
| `shared_buffers` | 2GB | cache page mémoire (en gros 25 % de la RAM) |
| `effective_cache_size` | 6GB | hint au planner sur le cache OS dispo |
| `work_mem` | 16MB | mémoire par opération de tri/hash |
| `max_connections` | 100 | connexions simultanées max (chaque conn = ~10 Mo) |
| `wal_compression` | on | compresse les WAL (write-ahead logs) |
| `log_min_duration_statement` | 1000 | log les requêtes lentes (>1 s) |

### 8.3 Liquibase : le contrôle de version pour le schéma

Pourquoi : sans outil, modifier la DB manuellement crée des **divergences** (« sur le serveur de Marc la table `clients` a 12 colonnes, en prod elle en a 11 »). Liquibase apporte :

- Un **changelog** (XML/YAML/SQL) listant tous les changesets.
- Une table `DATABASECHANGELOG` qui mémorise quels changesets ont été appliqués.
- Au démarrage de Spring Boot, Liquibase compare et applique uniquement les nouveaux.

Règle d'or : **un changeset déjà appliqué est immuable**. On ne le modifie pas, on en ajoute un nouveau. Sinon Liquibase détecte un changement de checksum et refuse de démarrer.

Format type :
```xml
<changeSet id="003-add-clients-table" author="alice">
  <createTable tableName="clients" schemaName="clients">
    <column name="id" type="bigint" autoIncrement="true">
      <constraints primaryKey="true"/>
    </column>
    <column name="hotel_id" type="bigint">
      <constraints nullable="false"/>
    </column>
    <column name="nom" type="varchar(200)"/>
  </createTable>
  <sql>
    ALTER TABLE clients.clients ADD CONSTRAINT chk_hotel_id CHECK (hotel_id > 0);
  </sql>
</changeSet>
```

### 8.4 Sauvegardes : 3-2-1

Règle communément acceptée : **3** copies, sur **2** supports différents, dont **1** off-site.

Pour city :
- Copie 1 : la DB en cours.
- Copie 2 : `pg_dump` quotidien sur le PVC `backup-postgres` (même serveur).
- Copie 3 : copie chiffrée GPG envoyée par rsync sur un NAS distant ou S3.

**`pg_dump`** = exporte la DB dans un fichier SQL. Avantage : portable, lisible. Inconvénient : pour de très grosses DB (TB), `pg_basebackup` + WAL archiving est plus rapide.

**Tester la restauration** = obligatoire au moins une fois par mois. Une sauvegarde non testée n'existe pas. Le script `postgres-restore.sh` automatise ça avec confirmation interactive.

### 8.5 PITR (Point-In-Time Recovery)

Backup quotidien = on peut perdre jusqu'à 24 h de données (RPO = 24 h). Pour aller plus loin, on archive les **WAL** (write-ahead logs) en continu vers un stockage externe, et on peut restaurer à n'importe quel moment précis (RPO ≈ minutes). C'est plus complexe (outils : pgBackRest, wal-g) — **hors scope V1 city**.

### 8.6 Connexions et pooling

Chaque connexion Postgres = ~10 Mo de RAM. Avec 100 connexions max, le backend Spring Boot ne doit pas en ouvrir n'importe combien. **HikariCP** (intégré Spring Boot) gère un **pool** : on configure 5-20 connexions, elles sont réutilisées entre requêtes. Vérifier `application.yml` city :
```yaml
hikari:
  maximum-pool-size: 20
  leak-detection-threshold: 60000
```
`leak-detection-threshold: 60000` = si une connexion est tenue plus de 60 s sans être rendue, log un warning (= probable bug : oubli de fermer un transaction).

---

<a id="chap9"></a>
## 9. Monitoring & incidents : voir et réagir

### 9.1 Les 3 piliers de l'observabilité

| Pilier | Outil city | Utilité |
|--------|------------|---------|
| **Métriques** | Prometheus + Grafana | « combien ? » (CPU 70%, latence 250ms, 5xx ratio 0.3%) |
| **Logs** | Loki + Promtail | « que s'est-il passé ? » (erreurs, requêtes lentes) |
| **Traces** | (non installé V1) | « qui appelle qui ? » (trace d'une requête à travers plusieurs services) |

### 9.2 Prometheus : le scrapeur de métriques

Prometheus interroge périodiquement chaque cible (toutes les 15 s) en HTTP : `GET /actuator/prometheus`. La cible répond du texte au format particulier :
```
http_server_requests_seconds_count{method="GET",uri="/api/clients",status="200"} 1247
http_server_requests_seconds_sum{method="GET",uri="/api/clients",status="200"} 87.34
jvm_memory_used_bytes{area="heap"} 1073741824
```

Prometheus stocke ça dans une **base time-series** (chiffres dans le temps). **PromQL** = langage de requête :
```
rate(http_server_requests_seconds_count{status=~"5.."}[5m])
                                                       ─────────
                                                       sur 5 min
```
= taux de 5xx par seconde sur les 5 dernières minutes.

### 9.3 Grafana : l'affichage

Grafana se connecte aux datasources (Prometheus, Loki, Postgres directement…) et affiche les requêtes en graphiques. **Dashboard** = page avec plusieurs panels. On peut :
- Importer des dashboards prêts (par ID depuis grafana.com).
- Construire les siens.

Dashboards utiles pour city :
- **JVM (Micrometer)** id 4701 — heap, threads, GC.
- **Spring Boot 3.x** id 17346 — latence par endpoint, taux d'erreur.
- **PostgreSQL** id 9628 — connexions, locks, requêtes lentes.

### 9.4 Loki : les logs

Comme Prometheus mais pour les logs : Promtail collecte les logs des conteneurs et les pousse à Loki. On les requête avec **LogQL** (similaire à PromQL) :
```
{namespace="city",app="city-backend"} |= "ERROR"
```
= toutes les lignes du backend city contenant "ERROR".

Avantage de Loki vs ELK (Elasticsearch) : beaucoup plus léger, indexe seulement les **labels** (pas le contenu).

### 9.5 Alertmanager : être prévenu

On définit des règles dans Prometheus :
```yaml
- alert: BackendHighErrorRate
  expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.01
  for: 5m
  annotations:
    summary: "5xx ratio dépasse 1% depuis 5 min"
```
**Alertmanager** reçoit les alertes et route vers email/SMS/Slack/PagerDuty selon des règles (heure, sévérité…).

### 9.6 SLO, SLI, SLA

Pour piloter sérieusement la prod :
- **SLI** (Service Level Indicator) = la métrique qu'on suit (ex : uptime, latence p95).
- **SLO** (Objective) = l'objectif interne (ex : 99,5 % uptime, latence p95 < 500 ms).
- **SLA** (Agreement) = le contrat avec le client (ex : 99 % uptime, sinon remboursement).

Ne pas confondre : on définit le SLO **plus strict** que le SLA pour avoir une marge.

### 9.7 Anatomie d'un incident

Un incident type :
1. **Détection** : Alertmanager déclenche `BackendHighErrorRate`, mail + SMS à l'astreinte.
2. **Triage** : l'astreinte regarde Grafana → confirme. Loki → cherche les ERROR récents.
3. **Mitigation** : action rapide pour stopper l'hémorragie (rollback, scale-up, throttle).
4. **Investigation** : trouver la **cause racine** (root cause).
5. **Résolution** : fix de fond.
6. **Post-mortem** : doc partagée qui explique ce qui s'est passé, ce qui a marché, ce qui n'a pas marché. **Sans blâme** — on cherche les défauts du système, pas du coupable.

### 9.8 Le runbook

Ce document, c'est le **manuel d'astreinte**. Il liste les procédures pour les incidents fréquents (« si X arrive, faire Y »). Mis à jour à chaque incident (« ah, on aurait dû documenter ça »).
Voir [`runbook_exploitation.md`](runbook_exploitation.md).

---

<a id="chap10"></a>
## 10. CI/CD : automatiser pour fiabiliser

### 10.1 Pourquoi automatiser

Un déploiement manuel = ~50 commandes dans un certain ordre. Probabilité d'erreur humaine sur 50 commandes : élevée. À chaque déploiement, c'est la loterie.

Un déploiement **CI/CD** = un pipeline qui exécute toujours la même séquence, dans le bon ordre, avec des contrôles. Une fois validé, il est répétable des dizaines de fois sans risque.

### 10.2 Continuous Integration (CI)

À chaque push dans git, **automatiquement** :
1. Récupérer le code.
2. Installer les dépendances.
3. **Compiler** (le compilateur trouve les erreurs syntaxe).
4. **Tester** (les tests trouvent les régressions).
5. **Linter** (analyse statique, conventions de code).

Si une étape échoue, le build est **rouge** et le code n'est pas mergeable. Effet : on détecte les régressions en 5 min, pas en 5 jours.

### 10.3 Continuous Deployment / Delivery (CD)

Suite logique : si le build est vert, on **construit l'image Docker** et on la pousse au registre. Selon la maturité :
- **Continuous Delivery** : prête à déployer, mais le déploiement est déclenché manuellement.
- **Continuous Deployment** : déploiement automatique en prod (réservé aux équipes très matures).

Pour city V1 : **Delivery + approval manuel** avant prod (équilibre raisonnable).

### 10.4 Pipeline city : 7 étapes

```
1. checkout         → git clone
2. test             → mvnw verify, ng test
3. audit            → multitenant-check, sync-tech, lint
4. build            → Jib backend, docker buildx frontend
5. push             → ghcr.io
6. deploy staging   → kubectl apply (auto)
[7. deploy prod]    → APPROVAL MANUEL → kubectl apply
```

Implémentations dispo :
- `ressources/ci/Jenkinsfile` : pour Jenkins LTS.
- `ressources/ci/github-actions-build.yml` : pour GitHub Actions.

### 10.5 Environments : dev, staging, prod

| Env | Public | Données | Stabilité attendue | Auto-deploy ? |
|-----|--------|---------|---------------------|----------------|
| **dev** | les devs | jouet | volatile | oui à chaque push |
| **staging** (= pré-prod) | tech + product | proches de prod (anonymisées) | stable | oui à chaque push sur `main` |
| **prod** | utilisateurs réels | réelles | très stable | sur tag git uniquement, avec approval |

**Règle** : ne JAMAIS pousser une image en prod sans qu'elle soit passée par staging.

### 10.6 Rollback automatique vs manuel

Avantage du déploiement immutable (image taguée) : un rollback est trivial (`kubectl rollout undo`). On peut même configurer un **auto-rollback** si les probes échouent dans la fenêtre de rollout. À utiliser avec prudence — parfois mieux vaut une coupure courte pour investiguer qu'un rollback automatique qui masque le problème.

---

<a id="chap11"></a>
## 11. Spécificités city : multi-tenant, plan comptable, MRU, NKC

### 11.1 Multi-tenant logique vs physique

Deux approches pour servir plusieurs hôtels :

**Multi-tenant physique** = 1 instance par hôtel (1 base, 1 backend, 1 frontend par client). Isolation maximale, coûteux à scaler, complexe à patcher (50 clients = 50 mises à jour).

**Multi-tenant logique** = 1 seule instance qui sert tous les hôtels, séparation par `hotel_id`. C'est le choix city.

Le défi : **garantir qu'un user de l'hôtel A ne voit JAMAIS les données de l'hôtel B**. Mécanismes en place dans le code :
- `JwtAuthenticationFilter` extrait `hotelId` du JWT, le pose dans `TenantContext` (ThreadLocal).
- `@TenantId` sur chaque entité tenant + `CityTenantIdentifierResolver` → Hibernate ajoute automatiquement `WHERE hotel_id = ?` à toutes les requêtes.
- `@RequireTenant` (aspect AOP) → refuse l'appel si `TenantContext` vide.

**Côté infra** : rien à faire de spécial. **Mais** : les logs doivent contenir `hotel_id` (déjà via MDC) pour pouvoir filtrer en cas d'enquête. Les backups protègent toutes les données indistinctement.

### 11.2 Sentinel ROOT (hotel_id = 0)

Pour les opérations système (admin, batch, scheduler) qui doivent lire/écrire **toutes les hôtels**, on a un **sentinel** : `hotel_id = 0`. Hibernate bypass alors le filtre tenant. C'est pourquoi le `CHECK (hotel_id > 0)` en SQL réserve `0` au sentinel et interdit aux données métier de s'y mettre par erreur.

### 11.3 Plan comptable mauritanien

`plan_comptable_mauritanien.pdf` à la racine = référentiel des comptes (numéros normés à 6 chiffres). Le module finance (à venir) doit générer des journaux conformes. **Aucun impact infra** : c'est de la donnée applicative chargée en seed.

### 11.4 Devise MRU

**MRU** (Ouguiya mauritanien) = devise nationale. Codes ISO 4217. Le frontend affiche les montants formatés selon la locale (fr/ar/en) mais avec MRU partout. **Aucun impact infra**.

### 11.5 Timezone Africa/Nouakchott

NKC = aéroport de Nouakchott (capitale). UTC+0 (pas de DST). Important pour :
- Les **horaires de tâches planifiées** (night audit à midi NKC, backup à 02:00 NKC).
- Les **logs** datés en heure locale pour cohérence avec les utilisateurs.
- Les **factures** : la date doit refléter la date du business, pas UTC.

Configuration en place :
- `application.yml` : `time-zone: Africa/Nouakchott`.
- Conteneur Docker : `TZ=Africa/Nouakchott` env + symlink `/etc/localtime`.
- Postgres : `timezone = 'Africa/Nouakchott'` dans `postgresql.conf`.
- Cron K8s : `timeZone: "Africa/Nouakchott"` (K8s ≥ 1.27).
- Serveur OS : `timedatectl set-timezone Africa/Nouakchott`.

### 11.6 i18n : fr, ar, en

L'arabe est **RTL** (right-to-left). Le frontend gère ça via la directive `dir="rtl"` quand `currentLang === 'ar'`. Les fichiers de traduction `assets/i18n/{fr,ar,en}.json` sont chargés au runtime — **aucune action serveur** tant que le build prod inclut bien ces 3 fichiers.

### 11.7 Rôles métier

Cf. `roles_utilisateurs.txt` à la racine. Rôles : `SUPERADMIN`, `ADMIN`, `GERANT`, `RECEPTION`, `RESTAURANT`, `RESREC`, `MAGASIN`, `MENAGE`, `NIGHTAUDIT`. Sécurisés via `@PreAuthorize` sur chaque endpoint backend. **Aucun impact infra** sauf : créer un user SUPERADMIN initial au seed (cf. procédure 2 §7.3).

### 11.8 Sessions

`application.yml` :
- 80 sessions concurrentes max.
- 3 sessions par user.

Stateless JWT = les sessions sont en réalité juste des JWT côté client. Côté serveur, on suit dans `core.user_sessions` qui est connecté pour respecter ces limites. Aucune action infra : le backend gère.

### 11.9 Modes de paiement

Cf. `modes_paiements.txt` : Espèces, Chèque, **Bankily** (mobile money mauritanien), Carte bancaire. **Bankily** = penser à ouvrir le port HTTPS sortant pour éventuels appels API. Sinon transparent.

### 11.10 Night audit

Tâche planifiée à **12:00 NKC** (cf. `règles_night_audit.txt`) qui contrôle les check-in du jour, identifie les no-show, génère les nuitées manquantes. Implémenté en Spring `@Scheduled` côté backend. **Côté infra** : s'assurer que le serveur est UP à 12h et que la timezone est bonne.

---

<a id="chap12"></a>
## 12. Mode opératoire jour J : enchaîner les 3 procédures

### 12.1 Vue d'ensemble du planning

```
J-7   Préparer le matériel et logistique
       ─ commande de hardware si neuf
       ─ achat domaine + paramétrage DNS
       ─ ouverture ports amont avec le réseau
       ─ génération clés SSH et clés GPG
       ─ création des comptes ghcr.io / Jenkins
       ─ formation rapide de l'équipe (ce document)

J-3   Sauvegarde Windows Server 2016        [procédure 1 §A]
       ─ image bare-metal (Veeam)
       ─ data applicative
       ─ test de restauration sur VM
       ─ PV de sauvegarde signé

J-2   Reformatage + Ubuntu 24.04            [procédure 1 §B]
       ─ boot USB Ubuntu installer
       ─ partitionnement LVM
       ─ premier boot + apt upgrade
       ─ timezone NKC

J-1   Hardening + Docker + K3s              [procédure 1 §C-§G]
       ─ SSH durci, UFW, fail2ban
       ─ unattended-upgrades, sysctl, auditd
       ─ Docker engine
       ─ K3s + Helm + cert-manager
       ─ Lynis audit clean
       ─ checklist 1 cochée à 100%

J-1   Préparation application                [procédure 2]
       ─ build images backend + frontend
       ─ push ghcr.io
       ─ création secrets cluster
       ─ test staging end-to-end
       ─ checklist 2 cochée à 100%

J0    Déploiement prod                       [procédure 3]
   H-30 ─ pré-vol checks
   H0   ─ apply manifests : postgres, backend, frontend, ingress
   H+30 ─ smoke tests applicatifs
   H+45 ─ test isolation multi-tenant (CRITIQUE)
   H+60 ─ activation monitoring + alertes
   H+120 ─ go-live officiel, communication users

J0+1   Soak test 24h
       ─ surveillance Grafana
       ─ aucun OOMKill, aucune fuite multi-tenant
       ─ backup quotidien OK à 02:00 NKC

J0+7   Premier audit Lynis post-prod
       ─ revue sécurité
       ─ ajustements si nécessaire
```

### 12.2 Composition d'équipe recommandée

| Rôle | Procédure 1 | Procédure 2 | Procédure 3 | Astreinte |
|------|-------------|-------------|-------------|-----------|
| Sysadmin Linux | ⭐ pilote | review | review | oui J0+ |
| DevOps | review | ⭐ pilote | ⭐ pilote | oui J0+ |
| Tech lead backend | — | review code | review go/no-go | sur appel |
| Chef de projet | logistique | go/no-go staging | go/no-go prod | non |
| Communication | — | rédige les comms users | déclenche les comms | non |

### 12.3 Communication & comptes-rendus

À chaque jalon (J-3, J-1, J0, J0+1) :
- Un mail au chef de projet + IT lead avec ce qui a été fait, ce qui reste.
- Mise à jour du wiki ops.
- Si bloquant : appel immédiat, pas d'attente.

### 12.4 Mode dégradé

Si à J-1 le serveur n'est pas prêt :
- **Reporter le go-live** plutôt que d'aller en mode panique.
- Communiquer au client la nouvelle date.
- Identifier la cause, fix, replanifier.

**Une mise en prod ratée perd plus de confiance qu'un délai assumé.**

---

<a id="glossaire"></a>
## 13. Glossaire alphabétique

> Tous les termes qui apparaissent dans les procédures, par ordre alphabétique. Si le terme a déjà été expliqué dans un chapitre, on renvoie au chapitre.

**ACME** — Automatic Certificate Management Environment. Protocole utilisé par Let's Encrypt pour valider la possession d'un domaine et délivrer un certificat. Cf. [§7.4](#chap7).

**Actuator** — Module Spring Boot qui expose des endpoints de monitoring (`/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`). Activé par défaut dans city.

**Alertmanager** — Composant Prometheus qui reçoit les alertes et les route vers email/SMS/Slack selon des règles. Cf. [§9.5](#chap9).

**Ansible** — Outil d'automatisation de configuration serveur (non utilisé V1 city — on reste sur des scripts shell + procédures écrites).

**API Server (Kubernetes)** — Composant central du control plane qui expose l'API Kubernetes ; toutes les commandes `kubectl` parlent à lui. Cf. [§5.2](#chap5).

**Apt** — Gestionnaire de paquets Debian/Ubuntu. `apt install nginx` = installer nginx. Cf. [§3.1](#chap3).

**Artifact** — Livrable produit par un build : jar, image Docker, archive tar.gz. Stocké dans un registre.

**Auditd** — Service Linux qui enregistre les actions sensibles (modifs `/etc/passwd`, etc.) pour traçabilité et conformité.

**Bare-metal** — Installation d'un OS directement sur le matériel, sans hyperviseur. Cf. [§2.4](#chap2).

**Base image** — Image Docker de départ d'un Dockerfile (ex : `eclipse-temurin:21-jre-noble`). Doit être récente et minimale.

**BIOS / UEFI** — Firmware au démarrage du serveur. UEFI est moderne, BIOS legacy. Cf. [§2.1](#chap2).

**Bootstrap** — Phase initiale d'un système. En Spring Boot, le `bootstrap.yml` charge avant `application.yml`. En NgRx, `bootstrap$` réhydrate l'état au démarrage.

**Brotli** — Algorithme de compression plus efficace que gzip pour le web. Activé dans nginx-frontend.

**BMC / iDRAC / iLO** — Mini-ordinateur dans le serveur permettant l'administration à distance (allumage, console). Constructeur : iLO (HP), iDRAC (Dell), BMC (générique).

**CA (Certificate Authority)** — Autorité qui signe des certificats TLS. Let's Encrypt est une CA gratuite.

**CD (Continuous Delivery / Deployment)** — Phase qui suit la CI : construit l'image et la pousse au registre, puis déploie. Cf. [§10.3](#chap10).

**cert-manager** — Opérateur Kubernetes qui automatise la délivrance et le renouvellement des certificats Let's Encrypt. Cf. [§7.4](#chap7).

**Certificate (TLS)** — Document signé par une CA prouvant l'identité d'un domaine. Permet HTTPS.

**Changelog (Liquibase)** — Fichier listant les changesets à appliquer à la DB. Cf. [§8.3](#chap8).

**Changeset** — Une modification atomique de schéma DB dans Liquibase.

**Checksum** — Empreinte d'un fichier (MD5, SHA256). Permet de vérifier qu'un téléchargement est intègre.

**CI (Continuous Integration)** — Phase qui s'exécute à chaque push : compile, teste, lint. Cf. [§10.2](#chap10).

**ClamAV** — Antivirus open source. Utile sur les serveurs qui reçoivent des uploads d'utilisateurs.

**ClusterIssuer** — Objet cert-manager qui définit comment obtenir des certificats (Let's Encrypt staging ou prod).

**ClusterIP** — Type de Service Kubernetes accessible **uniquement** depuis l'intérieur du cluster.

**CNI (Container Network Interface)** — Plugin réseau Kubernetes (flannel, Calico, Cilium). K3s utilise flannel par défaut.

**Compose** — Voir Docker Compose.

**Conteneur** — Processus isolé via namespaces+cgroups Linux, exécutant une image Docker. Cf. [§4.2](#chap4).

**ConfigMap** — Objet K8s qui stocke des paires clé/valeur non sensibles. Cf. [§5.4](#chap5).

**Containerd** — Runtime de conteneurs (couche basse de Docker). Aussi utilisé directement par K8s.

**Control plane** — Cerveau d'un cluster K8s (API server, scheduler, controllers, etcd). Cf. [§5.2](#chap5).

**Cooperative deployment / cutover** — Voir Cutover.

**CORS (Cross-Origin Resource Sharing)** — Mécanisme HTTP qui contrôle quels domaines peuvent appeler l'API. Configuré dans `application.yml` via `app.cors.allowed-origins`.

**Cosign** — Outil pour signer des images Docker cryptographiquement, prouvant leur origine.

**CPU request/limit** — Voir Request/Limit.

**Cron / CronJob** — Tâche planifiée. K8s a un objet `CronJob` qui exécute un Job selon une expression cron.

**CSP (Content Security Policy)** — Header HTTP qui restreint quelles ressources le navigateur peut charger. Anti-XSS. Cf. [§7.5](#chap7).

**CSR (Certificate Signing Request)** — Demande de certificat envoyée à une CA. cert-manager génère ces CSR automatiquement.

**Cutover** — Moment où le trafic bascule du système legacy vers le nouveau. Cf. [§Procédure 3 F](#chap12).

**daemon.json** — Fichier de configuration du démon Docker (`/etc/docker/daemon.json`).

**DataSource (Spring)** — Configuration de connexion à la DB (URL, user, password). Pool géré par HikariCP.

**Deployment** — Objet K8s qui maintient N replicas d'un Pod. Cf. [§5.4](#chap5).

**Distroless** — Image Docker sans shell ni outils, ne contenant que l'application. Réduit la surface d'attaque.

**DNS** — Système qui traduit les noms de domaine en adresses IP. Cf. [Architecture §5](#chap1) et [§7.4](#chap7).

**dnsutils** — Paquet Ubuntu qui fournit `dig`, `nslookup`. Utile pour debug DNS.

**Docker** — Plateforme de conteneurisation. Cf. [§4](#chap4).

**Docker Compose** — Outil pour orchestrer plusieurs conteneurs via un fichier YAML. Cf. [§4.5](#chap4).

**Docker Hub** — Registre public d'images Docker. Quotas de pull. Pour la prod, préférer ghcr.io ou Harbor.

**Dockerfile** — Recette de construction d'une image Docker. Cf. [§4.3](#chap4).

**dpkg** — Couche bas-niveau d'apt (gestionnaire de paquets `.deb`).

**DST (Daylight Saving Time)** — Heure d'été. Mauritanie n'en a pas — bonne nouvelle pour la planification.

**Dumb-init / Tini** — Mini-binaires PID 1 qui forward correctement les signaux Unix dans un conteneur. Important pour les rolling restart K8s.

**E2E (End-to-End test)** — Test qui simule un parcours utilisateur complet. Cf. [§Procédure 2 §10](#chap10).

**ECDSA / Ed25519** — Algorithmes de signature cryptographique modernes. Ed25519 est recommandé pour les clés SSH.

**Effects (NgRx)** — Side-effects réactifs côté frontend (login, logout, refreshToken).

**ELK** — Stack Elasticsearch + Logstash + Kibana. Alternative lourde à Loki pour les logs.

**ESM (Ubuntu)** — Extended Security Maintenance : extension payante du support sécurité pour les LTS Ubuntu (jusqu'à 12 ans).

**etcd** — Base clé/valeur distribuée utilisée par K8s pour stocker l'état du cluster.

**EXT4** — Système de fichiers Linux par défaut. Robuste, performant.

**Failover** — Bascule automatique vers un système de secours en cas de panne. HA Postgres = failover postgres.

**fail2ban** — Service qui bannit automatiquement les IP qui font trop d'essais de mot de passe SSH. Cf. [§7.2](#chap7).

**Feign** — Client HTTP déclaratif Spring Cloud (utilisé pour Dolibarr).

**Flannel** — Plugin CNI par défaut de K3s. N'applique pas les NetworkPolicies (utiliser Calico pour ça).

**Flux / ArgoCD** — Outils GitOps qui déploient automatiquement K8s à partir d'un dépôt git. Hors scope V1 city.

**Fournisseur cloud (AWS, GCP, Azure)** — Infra as a service. City = bare-metal, pas cloud.

**fsGroup** — UID de groupe propriétaire des volumes montés dans un Pod K8s.

**G1GC** — Garbage Collector Java moderne, optimisé pour des heap moyennes (< 16 Go). Activé via `-XX:+UseG1GC`.

**GCR / GHCR / Harbor** — Registres d'images Docker. ghcr.io = GitHub Container Registry, gratuit pour les repos privés.

**Gibioctet (Gi) vs Gigaoctet (G)** — `Gi` = 2³⁰ octets (1 073 741 824). `G` = 10⁹ (1 000 000 000). K8s utilise `Gi`.

**Git tag** — Référence figée vers un commit. Sert à marquer les releases. SemVer avec préfixe `v` : `v1.0.0`.

**Go-live** — Moment où le service ouvre aux vrais utilisateurs. Cf. [Procédure 3](#chap12).

**GPG (GnuPG)** — Outil de chiffrement et signature. Utilisé pour signer les tags git et chiffrer les backups.

**Grafana** — Outil de dashboards pour Prometheus/Loki. Cf. [§9.3](#chap9).

**Gzip** — Algorithme de compression standard du web. Pré-compression activée dans nginx-frontend.

**HA (High Availability)** — Configuration sans point unique de défaillance. Cf. [Architecture §9](#chap1).

**Hardening** — Durcissement de la sécurité d'un système. Cf. [§Procédure 1 C](#chap12) et [§7](#chap7).

**Helm** — Gestionnaire de paquets pour Kubernetes. Cf. [§5.7](#chap5).

**Helm chart** — Paquet Helm = templates K8s + valeurs par défaut.

**HikariCP** — Pool de connexions JDBC haute performance, intégré Spring Boot. Cf. [§8.6](#chap8).

**Hibernate** — ORM Java (Object-Relational Mapping). Module de Spring Data JPA.

**Hostname** — Nom court du serveur (ex : `city-prod-01`). Configuré via `hostnamectl`.

**HPA (HorizontalPodAutoscaler)** — Objet K8s qui ajuste automatiquement le nombre de Pods selon CPU/RAM. Cf. [§5.4](#chap5).

**HSTS (HTTP Strict Transport Security)** — Header HTTP qui force HTTPS pour 1 an. Cf. [§7.5](#chap7).

**HTTP-01 / DNS-01** — Méthodes de validation ACME pour Let's Encrypt. Cf. [§7.4](#chap7).

**HWE (Hardware Enablement)** — Kernel plus récent dispo sur Ubuntu LTS. Optionnel.

**Hyperviseur** — Logiciel de virtualisation (VMware, Proxmox, KVM, Hyper-V).

**iLO / iDRAC / IPMI** — Voir BMC.

**Image (Docker)** — Modèle figé pour créer des conteneurs. Cf. [§4.2](#chap4).

**Immutable infrastructure** — Principe : on ne modifie jamais une infra en place, on la recrée. Avec K8s + images immuables, c'est le pattern par défaut.

**Ingress** — Objet K8s qui expose un Service à l'extérieur via un nom de domaine. Cf. [§5.4](#chap5).

**IngressRoute (Traefik CRD)** — Variante Traefik d'Ingress avec plus de fonctionnalités.

**initContainer** — Conteneur qui s'exécute AVANT les conteneurs principaux d'un Pod. Utile pour préparer l'environnement.

**iptables / nftables** — Mécanisme noyau Linux pour filtrer les paquets. UFW est une surcouche simple.

**JDK / JRE** — Java Development Kit (avec compilateur) / Java Runtime Environment (sans). Image runtime = JRE seulement.

**Jenkins** — Serveur CI/CD self-hosted. Cf. [§10.4](#chap10).

**Jenkinsfile** — Pipeline Jenkins déclaré en Groovy.

**Jib** — Plugin Maven Google qui construit une image Docker sans Docker daemon. Cf. [§Procédure 2 §2.1](#chap10).

**JJWT** — Bibliothèque Java pour signer/vérifier les JWT.

**JPA (Java Persistence API)** — Standard Java pour la persistance objet-relationnel. Implémentation : Hibernate.

**JSON Web Token (JWT)** — Jeton signé contenant des claims. Utilisé pour l'authentification stateless. Cf. [Architecture §8](#chap1).

**journalctl** — Outil pour lire les logs systemd-journald. Cf. [§3.6](#chap3).

**JVM (Java Virtual Machine)** — Machine virtuelle qui exécute Java.

**JWT_SECRET** — Clé symétrique HMAC pour signer les JWT. Doit être longue (>= 256 bits) et secrète.

**K3s** — Distribution Kubernetes légère. Cf. [§5.3](#chap5).

**K8s** — Abréviation de Kubernetes (8 lettres entre K et s).

**Kaniko** — Alternative à Jib pour construire des images sans daemon Docker, dans un Pod K8s.

**Karma + Jasmine** — Framework de tests Angular (palier 1 city).

**Keycloak** — Serveur d'authentification SSO open source. Pas utilisé V1 city (auth maison JWT).

**Kops / kubeadm** — Outils pour bootstrapper un cluster K8s « vanilla ». K3s remplace ça pour les petits déploiements.

**Kube-proxy** — Composant K8s qui gère le routage réseau vers les Services.

**kubectl** — Client en ligne de commande pour parler à l'API Kubernetes. Cf. [§5.5](#chap5).

**kubeconfig** — Fichier `~/.kube/config` qui contient les identifiants pour parler au cluster.

**kubelet** — Agent K8s qui tourne sur chaque nœud, exécute les Pods.

**Kustomize** — Outil de composition de manifests K8s. Intégré à kubectl. Cf. [§5.7](#chap5).

**Latency p50/p95/p99** — Latence médiane / 95ᵉ percentile / 99ᵉ percentile. p99 = 1 % des requêtes sont plus lentes.

**Layer (Docker)** — Cf. Couche.

**Let's Encrypt** — Autorité de certification gratuite, automatisée. Cf. [§7.4](#chap7).

**Lifecycle hooks (K8s)** — `preStop` / `postStart` : actions à exécuter lors du démarrage/arrêt d'un conteneur.

**Liquibase** — Outil de versionning de schéma DB. Cf. [§8.3](#chap8).

**Liveness probe** — Sonde qui vérifie qu'un Pod est vivant. Cf. [§6.2](#chap6).

**LKM (Loadable Kernel Module)** — Module noyau chargeable. Auditer avec `lsmod`.

**LMDE / Debian / Ubuntu** — Distributions Linux. Ubuntu = base Debian + ajouts Canonical.

**LMS (Limited Memory Sets)** — N/A pour city.

**LoadBalancer (Service K8s)** — Type de Service exposé via un load balancer cloud (AWS ELB, GCP LB). Pas dispo en bare-metal sans MetalLB.

**local-path-provisioner** — Provisionneur de PVC simple intégré K3s, qui crée des dossiers sur l'hôte.

**Loki** — Stockage de logs léger, scalable horizontalement. Cf. [§9.4](#chap9).

**LogQL** — Langage de requête de Loki, similaire à PromQL.

**LTS (Long Term Support)** — Version supportée longtemps. Ubuntu LTS = 5 ans (12 ESM). Java LTS = 21, 25.

**Lynis** — Outil d'audit de sécurité Linux. Cf. [§7.1](#chap7).

**LVM** — Logical Volume Manager. Cf. [§3.5](#chap3).

**Manifest (K8s)** — Fichier YAML qui décrit un objet K8s. Cf. [§Procédure 2 §4](#chap10).

**MapStruct** — Générateur de mappers Java compile-time. Utilisé pour DTO ↔ entité.

**MaxRAMPercentage** — Option JVM moderne qui définit le heap max comme un pourcentage de la RAM du conteneur. `75 %` = bon défaut.

**MDC (Mapped Diagnostic Context)** — Mécanisme SLF4J qui ajoute des champs aux lignes de log. City utilise MDC pour `hotel_id` et `user_id`. Cf. [Architecture §8](#chap1).

**Mémoire heap / non-heap (JVM)** — Heap = objets Java. Non-heap = code chargé, métadonnées, threads. La RAM totale d'un Pod Java doit prévoir les deux.

**Migrations (DB)** — Synonyme de changesets Liquibase.

**Multi-stage build** — Dockerfile avec plusieurs `FROM`, où les étapes tardives copient depuis les précédentes. Cf. [§4.3](#chap4).

**Multi-tenant** — Une seule instance pour plusieurs clients (« tenants »). Cf. [§11.1](#chap11).

**Namespace (K8s)** — Dossier logique qui isole les objets. City utilise le namespace `city`.

**Namespace (Linux kernel)** — Mécanisme d'isolation utilisé par les conteneurs (PID, net, mount, user, ipc, uts, cgroup).

**NetworkPolicy** — Pare-feu entre Pods. Cf. [§5.4](#chap5).

**NFS / GlusterFS / Longhorn** — Stockages distribués. Hors scope V1 city.

**Nginx** — Serveur web ultra-rapide. Sert le frontend Angular et accessoirement comme reverse proxy.

**ngx-translate** — Bibliothèque i18n Angular. Cf. [`cityfrontend/CLAUDE.md §1`](../cityfrontend/CLAUDE.md).

**Node (K8s)** — Machine du cluster (worker ou control plane). Single-node city = 1 nœud.

**NodePort** — Type de Service K8s qui expose un port sur tous les nœuds. Pas utilisé en city (Ingress préféré).

**NPM (Node Package Manager)** — Gestionnaire de paquets JavaScript.

**NTP (Network Time Protocol)** — Protocole de synchronisation de l'heure. Sur Ubuntu : `chrony`. Cf. [§Procédure 1 §B.5](#chap12).

**OOMKilled** — Out Of Memory Killed : un processus a dépassé sa limite mémoire et a été tué par le kernel. Cf. [§Procédure 3 §E](#chap12).

**OpenFeign** — Voir Feign.

**OpenSSL** — Bibliothèque cryptographique. Outils CLI : `openssl rand -base64 48` pour générer un secret.

**OPA (Open Policy Agent)** — Moteur de policies. Hors scope V1.

**Operator (K8s)** — Pattern où un Pod surveille un type de ressource et automatise sa gestion. cert-manager est un operator.

**Opaque (Secret K8s)** — Type par défaut d'un Secret = paires clé/valeur arbitraires.

**Ouguiya (MRU)** — Devise mauritanienne. Cf. [§11.4](#chap11).

**OWASP Top 10** — Liste des 10 vulnérabilités web les plus courantes. Référence pour les revues de sécurité.

**Pod** — Unité d'exécution K8s = 1+ conteneur. Cf. [§5.4](#chap5).

**PodDisruptionBudget (PDB)** — Garantie qu'au moins N Pods restent dispos. Cf. [§5.4](#chap5).

**PodSecurityStandards (restricted, baseline, privileged)** — Niveaux de sécurité pour les Pods. City utilise `restricted` (le plus strict).

**Pool de connexions** — Cf. HikariCP.

**Postgres (PostgreSQL)** — SGBD relationnel. Cf. [§8](#chap8).

**Postgres operator** — Operator K8s qui gère Postgres en HA (Zalando, CloudNativePG). Hors scope V1.

**postgresql.conf** — Fichier principal de config Postgres.

**PriorityClass** — Objet K8s qui définit la priorité des Pods. Si pression, K8s évince d'abord les basses priorités.

**Probe** — Sonde de santé. Cf. [§6.2](#chap6).

**Promtail** — Agent qui collecte les logs et les pousse à Loki.

**Prometheus** — Système de métriques time-series. Cf. [§9.2](#chap9).

**PromQL** — Langage de requête Prometheus.

**PSS (PodSecurityStandards)** — Voir PodSecurityStandards.

**PV (PersistentVolume) / PVC (PersistentVolumeClaim)** — Stockage persistant K8s. Cf. [§5.4](#chap5).

**Pwgen** — Générateur de mots de passe aléatoires (`pwgen -ynsBv 32 1`).

**RAID 0 / 1 / 5 / 10** — Configurations de redondance disque. RAID1 = miroir (2 disques), recommandé pour les data critiques.

**RAM (Random Access Memory)** — Mémoire vive. Cf. [§2.1](#chap2).

**Rancher** — Plateforme de gestion K8s. Édite K3s.

**Readiness probe** — Sonde qui dit si un Pod est prêt à recevoir du trafic. Cf. [§6.2](#chap6).

**Registre (image Docker)** — Stockage distant d'images. ghcr.io, Docker Hub, Harbor.

**Rolling update** — Stratégie de mise à jour qui remplace les Pods un par un. Cf. [§6.3](#chap6).

**Rollback** — Retour à la version précédente. Cf. [§6.4](#chap6).

**Root** — Super-utilisateur Linux (UID 0). À ne pas utiliser au quotidien.

**RPO (Recovery Point Objective)** — Quantité de données qu'on accepte de perdre. Cf. [Architecture §9](#chap1).

**RTL (Right-to-Left)** — Sens de lecture arabe. Géré côté frontend.

**RTO (Recovery Time Objective)** — Délai pour remettre le service en marche. Cf. [Architecture §9](#chap1).

**Runbook** — Manuel d'astreinte. Cf. [§9.8](#chap9) et [`runbook_exploitation.md`](runbook_exploitation.md).

**Sealed-Secrets** — Secrets K8s chiffrés versionnables. Cf. [§7.6](#chap7).

**Sécurité par défaut** — Principe : valeurs par défaut sécurisées, l'utilisateur doit explicitement assouplir.

**SELinux / AppArmor** — Modules de sécurité Linux MAC (Mandatory Access Control). AppArmor par défaut sur Ubuntu.

**SemVer (Semantic Versioning)** — Convention de numérotation des versions. Cf. [§6.5](#chap6).

**Service (K8s)** — Adresse stable pour les Pods. Cf. [§5.4](#chap5).

**Service mesh** — Couche réseau avancée (Istio, Linkerd). Hors scope V1.

**SHA-256** — Algorithme de hachage cryptographique. Utilisé pour les digests d'images.

**Shell** — Programme d'interaction texte (bash, zsh, sh).

**SIGTERM / SIGKILL** — Signaux Unix. SIGTERM = arrêt poli, SIGKILL = arrêt forcé. K8s envoie SIGTERM, attend `terminationGracePeriodSeconds`, puis SIGKILL.

**Single-node** — Cluster K8s avec un seul nœud. Cf. [§5.3](#chap5).

**SLA / SLI / SLO** — Cf. [§9.6](#chap9).

**Smoke test** — Test rapide post-déploiement. Cf. [Procédure 3 C](#chap12).

**SMTP** — Protocole d'envoi de mail. Backend city = client SMTP.

**Soak test** — Test prolongé sous charge. Cf. [Procédure 3 E](#chap12).

**SOPS (Secrets OPerationS)** — Chiffrement de fichiers YAML/JSON pour git. Cf. [§7.6](#chap7).

**SPA (Single Page Application)** — Architecture Angular : une seule page HTML, JS qui change le contenu.

**Spring Boot** — Framework Java pour API REST. Cf. [`citybackend/CLAUDE.md`](../citybackend/CLAUDE.md).

**Spring Cloud** — Suite de bibliothèques Spring pour microservices (Feign, Stream Kafka).

**SSH** — Secure Shell : connexion distante chiffrée. Cf. [§7.2](#chap7).

**ssh-keygen** — Outil de génération de paires de clés SSH.

**Staging** — Environnement de pré-production. Cf. [§10.5](#chap10).

**StatefulSet** — Deployment pour applications à état (Postgres). Cf. [§5.4](#chap5).

**Startup probe** — Sonde qui couvre la phase de démarrage. Cf. [§6.2](#chap6).

**Stateful / Stateless** — Avec / sans état. Frontend = stateless. Postgres = stateful.

**STDOUT / STDERR** — Sorties standard d'un programme. Les conteneurs poussent leurs logs sur STDOUT.

**Sudo** — Commande pour exécuter en tant qu'admin. Cf. [§3.1](#chap3).

**Surface d'attaque** — Ensemble des points d'entrée potentiels d'un attaquant. Réduire = enlever ce qui n'est pas indispensable.

**Swap** — Mémoire virtuelle sur disque. Désactivée pour K8s. Cf. [§Procédure 1 E.5](#chap12).

**SWE (Spring Web Endpoint)** — N/A.

**systemctl** — Commande pour gérer les services systemd. Cf. [§3.4](#chap3).

**systemd** — Init system Linux moderne. Gère les services au démarrage.

**Sysctl** — Paramètres noyau Linux. Cf. [§3.5](#chap3) et [§7](#chap7).

**Tag (Docker)** — Étiquette d'une image. Cf. [§4.2](#chap4) et [§6.6](#chap6).

**Tag (git)** — Référence figée vers un commit. Cf. [§6.5](#chap6).

**Tainted node (K8s)** — Nœud avec une « marque » qui repousse les Pods sauf ceux qui tolèrent la marque.

**TCP / UDP** — Protocoles transport. TCP = fiable (HTTP, SSH). UDP = rapide non fiable (DNS, NTP).

**Telemetry** — Données envoyées par l'app sur son comportement (métriques, traces). City expose via Actuator.

**Temurin** — Distribution OpenJDK officielle Eclipse. `eclipse-temurin:21-jre` = image runtime Java 21.

**TenantContext** — ThreadLocal city qui porte le `hotel_id` courant. Cf. [`CLAUDE.md` racine §6.1](../CLAUDE.md).

**Termination grace period** — Délai accordé à un Pod pour se terminer proprement avant SIGKILL. Défaut 30 s, on monte à 45 s pour le backend Spring (rolling restart).

**Testcontainers** — Bibliothèque Java qui lance des conteneurs (DB, kafka) pour les tests d'intégration.

**TLS (Transport Layer Security)** — Successeur de SSL. Chiffre HTTPS. Cf. [§7.4](#chap7).

**TLS termination** — Endroit où la connexion TLS est déchiffrée. Pour city = Traefik (le backend reçoit du HTTP en clair sur le réseau cluster).

**TPM (Trusted Platform Module)** — Puce hardware qui stocke des clés cryptographiques. Permet le déverrouillage automatique LUKS.

**Traefik** — Reverse proxy moderne, intégré K3s. Cf. [Architecture §1](#chap1).

**TTL (Time To Live)** — Durée de validité (DNS, cache).

**Type-safe** — Garantie de type. TypeScript = JavaScript type-safe.

**TypeScript** — Sur-ensemble de JavaScript avec typage statique.

**UEFI** — Voir BIOS.

**UFW (Uncomplicated Firewall)** — Surcouche iptables. Cf. [§7.3](#chap7).

**UID / GID** — Numéros d'utilisateur/groupe Linux. UID 0 = root. UID 10001 = recommandé pour les conteneurs.

**Ubuntu** — Distribution Linux. Cf. [§2.2](#chap2).

**Unattended-upgrades** — Service Ubuntu qui installe les patches sécurité automatiquement. Cf. [§Procédure 1 C.4](#chap12).

**Upstream** — Source amont. « Upgrade upstream » = aller chercher la version la plus récente officielle.

**User namespace** — Namespace Linux pour mapper les UID. Permet aux conteneurs « rootless » d'avoir un root virtuel.

**Vault (HashiCorp)** — Coffre-fort de secrets entreprise. Hors scope V1 city.

**Veeam** — Logiciel de backup commercial. Utilisé pour la sauvegarde Windows pré-reformatage.

**Volume (Docker / K8s)** — Stockage persistant attaché à un conteneur. Cf. [§4.2](#chap4) et [§5.4](#chap5).

**vCPU** — CPU virtuel. Hyperthreading expose 2 vCPU par cœur physique.

**WAF (Web Application Firewall)** — Pare-feu applicatif (ModSecurity, CrowdSec). Inspecte le contenu HTTP.

**WAL (Write-Ahead Log)** — Journal de transactions Postgres. Permet la récupération après crash.

**Webhook** — Appel HTTP automatique déclenché par un événement (push git, fin de build).

**WireMock** — Stub HTTP pour les tests. Utilisé pour stubber Dolibarr.

**Wildcard cert** — Certificat valide pour `*.example.mr`. Nécessite ACME DNS-01.

**WireGuard** — VPN moderne, simple. Alternative à OpenVPN.

**WORKDIR** — Instruction Dockerfile qui définit le dossier courant.

**X-Forwarded-For / X-Real-IP** — Headers HTTP qui transportent l'IP cliente à travers les reverse proxies.

**Yaml** — Format de fichier hiérarchique lisible. K8s manifests + Compose en sont écrits.

**Zoneless (Angular)** — Mode Angular sans Zone.js. **Désactivé en city** car incompatible avec SweetAlert2 et jQuery/DataTables.

**Zypper / dnf / pacman** — Gestionnaires de paquets d'autres distros (openSUSE, Fedora, Arch). N/A en Ubuntu.

---

## En conclusion

Vous savez maintenant ce que recouvrent **tous les termes** des procédures. La meilleure façon d'ancrer ces concepts est de les manipuler :

1. **Lire intégralement** [`01_preparation_serveur.md`](01_preparation_serveur.md), [`02_preparation_application.md`](02_preparation_application.md), [`03_deploiement_final.md`](03_deploiement_final.md). Avec ce glossaire en référence, ça doit être lisible.
2. **Faire un labo** : sur un VPS ou une VM, installer Ubuntu, Docker, K3s, déployer une app simple. 1 journée bien investie pour gagner 1 mois en prod.
3. **Suivre une formation** : Linux Foundation propose des certifs (LFCS, CKA) très opérationnels. Pour Spring Boot/Angular, les docs officielles sont excellentes.
4. **Lire les post-mortems publics** : Cloudflare, GitHub, AWS publient leurs incidents. Énorme pédagogie sur ce qui peut casser en prod.

Bon déploiement. 🚀
