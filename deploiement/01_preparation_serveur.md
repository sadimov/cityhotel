# Procédure 1 — Préparation du serveur

> **📖 Pour les non-spécialistes** : ce document utilise beaucoup de termes système (UFW, sysctl, LVM, fail2ban, K3s, Helm…). Si l'un d'eux est flou, lisez d'abord [`formation.md §3`](formation.md) (Linux serveur) et [`§5`](formation.md) (Kubernetes). Chaque section ci-dessous commence par un encadré `💡 En clair` qui résume l'idée en français courant.

> **Objectif** : transformer un serveur physique sous Windows Server 2016 en hôte de production Linux durci, prêt à recevoir les conteneurs city.
> **Durée** : 4 à 8 heures, dont ~1 h de sauvegarde Windows et ~2 h d'installation/hardening Linux.
> **Prérequis** : accès console physique (clavier+écran) ou IPMI/iDRAC/iLO, clé USB ≥ 8 Go, câble réseau, mot de passe BIOS.

---

## A. Sauvegarde du serveur Windows Server 2016 (avant reformatage)

> **💡 En clair** — Avant d'effacer le serveur Windows, on photocopie tout ce qui est dessus pour pouvoir restaurer en cas de problème. **Image disque bare-metal** = copie bit-à-bit du disque entier (Veeam, wbadmin) qui permet de tout remettre comme avant. **State system** = sauvegarde des paramètres système Windows. La règle d'or : ne jamais reformater sans avoir TESTÉ la restauration sur une autre machine — une sauvegarde non testée n'existe pas.

> **Règle d'or** : aucun reformatage avant validation écrite des sauvegardes par le responsable du service. Conservez les images **hors site**.

### A.1 Inventaire avant arrêt

Sur Windows :

```powershell
# Sur le serveur Windows, ouvrir PowerShell en admin
Get-ComputerInfo | Out-File C:\Backup\hostinfo.txt
Get-NetIPConfiguration | Out-File C:\Backup\network.txt
Get-Service | Where-Object { $_.Status -eq 'Running' } | Out-File C:\Backup\services.txt
Get-Disk; Get-Partition; Get-Volume | Out-File C:\Backup\disks.txt
ipconfig /all > C:\Backup\ipconfig.txt
route print > C:\Backup\route.txt
Get-WindowsFeature | Where-Object Installed | Out-File C:\Backup\features.txt
```

Notez :
- Adresses IP, masques, gateway, DNS (si fixes).
- Comptes locaux et applicatifs hébergés sur le serveur.
- Certificats installés dans `MMC → Certificats → Ordinateur local`.
- Tâches planifiées (`schtasks /query /v /fo list > C:\Backup\schtasks.txt`).
- Logs IIS, applicatifs (à archiver si litige juridique).

### A.2 Sauvegarde de données

| Type | Outil | Destination |
|------|-------|-------------|
| Image disque complète (bare-metal) | `wbadmin` ou Veeam Agent for Windows Free | NAS / disque externe USB ≥ 1 To |
| Données applicatives | Robocopy + checksum | NAS / disque externe |
| Bases SQL Server (si présentes) | SSMS → backup `.bak` puis copy | NAS off-site |
| Active Directory (si DC) | `wbadmin start systemstatebackup` | NAS off-site |
| Registre | `reg save HKLM\... C:\Backup\hklm.hiv` | inclure dans archive |

Image bare-metal :
```cmd
wbadmin start backup -backupTarget:\\nas\backup\srv01 -include:C:,D: -allCritical -quiet
```
Veeam Agent (recommandé) : `Configuration` → `Image Volume` → `External Storage`.

### A.3 Validation

- Restauration **test** d'une partie de l'image sur une VM bac-à-sable. Sans cette validation, considérer la sauvegarde comme inexistante.
- Faire signer le procès-verbal de sauvegarde au responsable.
- **Conservation** : 3 ans minimum (obligation légale fiscale en Mauritanie pour des données financières).

---

## B. Installation d'Ubuntu Server 24.04 LTS

> **💡 En clair** — On installe Linux à la place de Windows. **Ubuntu Server** = distribution Linux gratuite, professionnelle, sans interface graphique (on l'administre uniquement par ligne de commande via SSH). **LTS** (Long Term Support) = version supportée 5 ans, idéale en prod. **UEFI** = nouveau standard de démarrage des PC (remplace l'ancien BIOS). **LVM** = système qui découpe les disques en partitions « élastiques » qu'on peut agrandir à chaud. **Secure Boot** = mécanisme qui vérifie que le système d'exploitation est bien authentique avant de démarrer.

### B.1 Préparation de la clé d'installation

Sur un poste Linux ou Windows :
- Télécharger l'ISO officielle : `https://releases.ubuntu.com/24.04/ubuntu-24.04.x-live-server-amd64.iso` (vérifier SHA-256 publié sur la même page).
- Graver sur clé USB :
  - Linux : `sudo dd if=ubuntu-24.04-live-server-amd64.iso of=/dev/sdX bs=4M status=progress conv=fsync && sync`
  - Windows : Rufus → mode `DD Image`.

### B.2 BIOS/UEFI du serveur

Avant boot USB :
- **Effacer les disques** intégralement (Secure Erase si SSD, sinon zéro fill).
- Activer **UEFI** (pas Legacy/BIOS).
- Activer **Secure Boot** (Ubuntu 24.04 supporte signed kernels).
- Désactiver **Wake-on-LAN** sauf si nécessaire.
- Mettre à jour le firmware (BIOS, BMC, RAID).
- Configurer le contrôleur RAID (si dispo) : **RAID1 sur 2 disques** pour `/var/lib/rancher` (data critique).

### B.3 Installation guidée

Boot sur la clé USB → installeur Ubuntu :

| Étape | Choix |
|-------|-------|
| Langue | English (logs/messages plus clairs) |
| Keyboard | French ou US selon clavier physique |
| Type d'install | **Ubuntu Server (minimized)** — pas de snaps optionnels, pas de docker via snap |
| Network | Configuration **fixe IPv4** (cf. fiche réseau préparée), DNS `1.1.1.1` + `9.9.9.9` |
| Proxy | si requis par votre réseau |
| Mirror | défaut (`mr.archive.ubuntu.com` ou `archive.ubuntu.com`) |
| Storage | **Custom storage layout** (voir partitionnement ci-dessous) |
| Profile | hostname `city-prod-01`, username `cityadmin` (jamais `admin` ou `root`) |
| SSH | **Activer SSH** + import clé publique GitHub/Launchpad si dispo |
| Snaps | **AUCUN** (pas de docker-snap, pas de microk8s-snap) |

### B.4 Partitionnement recommandé (LVM)

Disque OS (50 Gi) + Disque data (≥ 200 Gi, idéalement RAID1) :

```
/dev/sda                            (50 Gi - OS)
├── /boot/efi          ESP   1 Gi   FAT32
├── /boot              ext4  2 Gi
└── LVM PV → vg-os
    ├── lv-root        ext4 30 Gi   /
    ├── lv-var         ext4 10 Gi   /var      (logs, journald)
    ├── lv-tmp         ext4  4 Gi   /tmp      mount nodev,nosuid,noexec
    └── lv-swap        swap  2 Gi               (= RAM/8 sur serveur 16 Gi)

/dev/sdb (RAID1)                    (≥ 200 Gi - data)
└── LVM PV → vg-data
    ├── lv-rancher     ext4 150 Gi  /var/lib/rancher    (volumes K3s)
    ├── lv-backup      ext4  50 Gi  /srv/backup
    └── (réserve pour extension `lvextend`)
```

Notes :
- `/tmp` en `nodev,nosuid,noexec` : mitigation classique.
- `/var/lib/rancher` séparé : permet de redimensionner sans toucher à `/`.
- LVM permet `lvextend -L +50G && resize2fs` à chaud.
- **Pas de chiffrement LUKS** par défaut (impose une saisie console à chaque boot, incompatible avec un déploiement non assisté). À activer uniquement si le serveur est physiquement dans un lieu non sécurisé, avec TPM2 pour le déverrouillage automatique (`systemd-cryptenroll --tpm2-device=auto`).

### B.5 Premier boot

```bash
ssh cityadmin@<IP-serveur>      # depuis un poste admin
sudo apt update && sudo apt full-upgrade -y
sudo timedatectl set-timezone Africa/Nouakchott
sudo hostnamectl set-hostname city-prod-01
sudo apt install -y chrony curl gnupg lsb-release ca-certificates apt-transport-https \
                    htop iotop iftop ncdu jq tree git rsync zip unzip vim tmux \
                    bash-completion lsof tcpdump dnsutils auditd
sudo systemctl enable --now chrony auditd
```

Vérifications :
```bash
timedatectl                     # NTP sync + Africa/Nouakchott
chronyc tracking                # source NTP healthy
df -h                           # partitions correctes
free -h                         # RAM
ip a                            # interface réseau
```

---

## C. Hardening (durcissement) Linux

> **💡 En clair** — « Hardening » = rendre le serveur plus difficile à pirater en désactivant tout ce qui n'est pas indispensable. Comme sécuriser une maison : on retire le double des clés sous le paillasson, on met des barreaux, on installe une alarme. Les outils :
> - **SSH** = protocole pour se connecter à distance au serveur (remplace telnet, qui était en clair).
> - **UFW** = pare-feu simple qui bloque par défaut tout trafic entrant et n'autorise que ce qu'on liste.
> - **fail2ban** = surveille les logs et bannit automatiquement les IP qui font trop d'essais de mot de passe.
> - **sysctl** = paramètres profonds du noyau Linux (anti-spoofing, anti-DDoS).
> - **auditd** = enregistre toute action sensible (modification de `/etc/passwd`, etc.) pour traçabilité.
> - **Lynis** = outil qui scanne le serveur et donne une note de sécurité avec des conseils.

### C.1 Comptes et SSH

```bash
# Désactiver le compte root pour login direct
sudo passwd -l root

# Créer un groupe sudo dédié si pas déjà fait, vérifier appartenance de cityadmin
groups cityadmin    # doit contenir 'sudo'
```

Configurer SSH (cf. `ressources/server/sshd_config.hardened`) :

```bash
sudo cp /etc/ssh/sshd_config /etc/ssh/sshd_config.bak
sudo cp deploiement/ressources/server/sshd_config.hardened /etc/ssh/sshd_config.d/00-city-hardening.conf
sudo sshd -t                    # validation syntaxique AVANT redémarrage
sudo systemctl reload ssh
```

Points clés appliqués :
- `Port 2222` (port custom pour réduire le bruit, à adapter)
- `PermitRootLogin no`
- `PasswordAuthentication no` (clé seulement)
- `KbdInteractiveAuthentication no`
- `MaxAuthTries 3`
- `LoginGraceTime 30`
- `AllowUsers cityadmin`
- Algorithmes modernes uniquement (Ed25519, Curve25519, AES-GCM)

> **AVANT de couper PasswordAuthentication, vérifier que votre clé SSH fonctionne déjà** dans une 2ᵉ session ouverte. Sinon vous restez verrouillé dehors.

### C.2 Pare-feu UFW

```bash
sudo apt install -y ufw
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 2222/tcp comment 'SSH custom port'   # ou 22 si port standard
sudo ufw allow 80/tcp comment 'HTTP (Traefik / ACME)'
sudo ufw allow 443/tcp comment 'HTTPS (Traefik)'
sudo ufw allow from 10.42.0.0/16 to any port 6443 comment 'K3s API local'
sudo ufw logging medium
sudo ufw enable
sudo ufw status verbose
```

Si le firewall amont (FAI) sait déjà filtrer, UFW reste **obligatoire** comme défense en profondeur.

### C.3 fail2ban

```bash
sudo apt install -y fail2ban
sudo cp deploiement/ressources/server/fail2ban-jail.local /etc/fail2ban/jail.local
sudo systemctl enable --now fail2ban
sudo fail2ban-client status sshd
```

Jails activées : `sshd`, `traefik-auth` (à câbler après installation Traefik).

### C.4 Mises à jour automatiques

```bash
sudo apt install -y unattended-upgrades apt-listchanges
sudo cp deploiement/ressources/server/50unattended-upgrades.local \
        /etc/apt/apt.conf.d/52unattended-upgrades-city
sudo dpkg-reconfigure -plow unattended-upgrades
```

Politique appliquée :
- `security` updates installées automatiquement chaque nuit.
- `updates` (non-security) installées hebdomadaire.
- Mail à `ops@city.example.mr` si quelque chose échoue.
- **Pas** de redémarrage automatique en heures ouvrables ; planifier `Unattended-Upgrade::Automatic-Reboot-Time "03:30"` si nécessaire.

### C.5 Sysctl & limites

```bash
sudo cp deploiement/ressources/server/sysctl-hardening.conf \
        /etc/sysctl.d/99-city-hardening.conf
sudo sysctl --system

# Limites PAM (open files pour Postgres + JVM)
echo '* soft nofile 65536' | sudo tee -a /etc/security/limits.conf
echo '* hard nofile 65536' | sudo tee -a /etc/security/limits.conf
```

Réglages appliqués (extrait) :
- `net.ipv4.tcp_syncookies=1`, anti SYN flood
- `net.ipv4.conf.all.rp_filter=1` (anti spoofing)
- `kernel.kptr_restrict=2`, `kernel.dmesg_restrict=1`
- `fs.suid_dumpable=0`
- `vm.swappiness=10` (favorise RAM, important pour Postgres)
- `kernel.unprivileged_userns_clone=1` (requis par K3s rootless si activé)

### C.6 Audit & intégrité

```bash
# auditd déjà installé en B.5 — règles minimales city
sudo tee /etc/audit/rules.d/city.rules <<'EOF'
-w /etc/passwd -p wa -k passwd_changes
-w /etc/shadow -p wa -k shadow_changes
-w /etc/sudoers -p wa -k sudoers_changes
-w /etc/ssh/sshd_config -p wa -k sshd_changes
-w /var/log/auth.log -p wa -k authlog
-w /etc/kubernetes/ -p wa -k k8s_changes
EOF
sudo augenrules --load
sudo systemctl restart auditd

# Lynis : audit périodique
sudo apt install -y lynis
sudo lynis audit system --cronjob > /var/log/lynis-$(date +%F).log
```

Programmer Lynis tous les dimanches :
```bash
sudo crontab -e
# 0 4 * * 0 lynis audit system --cronjob > /var/log/lynis-$(date +\%F).log
```

### C.7 Antivirus (optionnel mais recommandé pour serveur recevant des uploads)

```bash
sudo apt install -y clamav clamav-daemon
sudo systemctl enable --now clamav-freshclam clamav-daemon
# Scan ad-hoc des uploads applicatifs (à câbler dans le backend si besoin)
```

### C.8 Désactiver les services inutiles

```bash
# Vérifier ce qui tourne
systemctl list-units --type=service --state=running

# Désactiver si présent (varie selon install)
for svc in cups avahi-daemon bluetooth ModemManager; do
  sudo systemctl is-enabled $svc 2>/dev/null && sudo systemctl disable --now $svc
done
```

### C.9 Bannière légale

```bash
sudo tee /etc/issue.net <<'EOF'
*****************************************************************
*  ACCES RESTREINT — SERVEUR PROD CITY HOTEL                     *
*  Toute connexion est journalisee. Acces non-autorise interdit. *
*****************************************************************
EOF
echo 'Banner /etc/issue.net' | sudo tee -a /etc/ssh/sshd_config.d/00-city-hardening.conf
sudo systemctl reload ssh
```

---

## D. Installation Docker Engine + Compose

> **💡 En clair** — **Docker** = logiciel qui exécute des « conteneurs » (mini-machines isolées qui contiennent une appli + ses dépendances). On installe Docker **depuis le dépôt officiel** (pas via `snap`) pour avoir la dernière version stable et pouvoir la verrouiller. **containerd** = moteur d'exécution des conteneurs (couche basse). **docker-buildx** = builder moderne capable de construire des images multi-plateformes. **docker compose** (sans tiret) = la version moderne livrée comme plugin, pour orchestrer plusieurs conteneurs à la fois. **`apt-mark hold`** = empêche `apt upgrade` de mettre à jour Docker accidentellement (on veut le faire manuellement après tests).

> Source officielle Docker (PAS le snap d'Ubuntu, PAS le paquet `docker.io`).

```bash
# Ajout du dépôt Docker officiel
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
     -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Donner accès au compte cityadmin (re-login requis ensuite)
sudo usermod -aG docker cityadmin

# Verrouiller la version pour éviter une mise à jour intempestive
sudo apt-mark hold docker-ce docker-ce-cli containerd.io
```

Configuration `daemon.json` :
```bash
sudo tee /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "100m", "max-file": "5" },
  "live-restore": true,
  "userland-proxy": false,
  "no-new-privileges": true,
  "default-ulimits": { "nofile": { "Soft": 65536, "Hard": 65536 } }
}
EOF
sudo systemctl restart docker
docker version && docker compose version
```

---

## E. Installation K3s (Kubernetes léger)

> **💡 En clair** — **Kubernetes** (K8s) = chef d'orchestre des conteneurs : il les lance, les redémarre s'ils crashent, les scale automatiquement, route le trafic vers les bons. Son défaut : c'est lourd (souvent ~2 Go RAM rien que pour le système). **K3s** = version allégée de Kubernetes (signée Rancher) qui tient en ~500 Mo, parfait pour un serveur unique. Pratique : il embarque déjà **Traefik** (le reverse proxy qui termine HTTPS) et **local-path** (le provisionneur de volumes). **Helm** = gestionnaire de paquets pour Kubernetes (équivalent de `apt` pour Ubuntu) — utile pour installer cert-manager, Prometheus en une commande. **kubeconfig** = fichier qui contient les identifiants pour parler au cluster avec `kubectl`. **cert-manager** = automatise l'obtention et le renouvellement des certificats Let's Encrypt.

> Choisi pour la simplicité monoserveur. Si vous voulez **uniquement** Compose, sautez cette section et passez à §F.

### E.1 Installation single-node

```bash
# Désactiver Traefik par défaut si on veut un cert-manager + Traefik custom (recommandé)
# OU le garder (suffisant pour démarrer) — choix retenu : on garde Traefik intégré.
curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION="v1.31.4+k3s1" \
  INSTALL_K3S_EXEC="--write-kubeconfig-mode 644 --disable=servicelb" \
  sh -

# Vérification
sudo systemctl status k3s
sudo kubectl get nodes
sudo kubectl get pods -A
```

### E.2 kubeconfig pour cityadmin

```bash
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $(id -u):$(id -g) ~/.kube/config
chmod 600 ~/.kube/config
echo 'export KUBECONFIG=$HOME/.kube/config' >> ~/.bashrc
source ~/.bashrc
kubectl get nodes
```

### E.3 Helm (utile pour cert-manager, prometheus-stack, etc.)

```bash
curl -fsSL https://baltocdn.com/helm/signing.asc | sudo gpg --dearmor -o /usr/share/keyrings/helm.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/helm.gpg] https://baltocdn.com/helm/stable/debian/ all main" \
  | sudo tee /etc/apt/sources.list.d/helm-stable-debian.list
sudo apt update && sudo apt install -y helm
helm version
```

### E.4 cert-manager (TLS Let's Encrypt automatique)

```bash
helm repo add jetstack https://charts.jetstack.io
helm repo update
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace \
  --version v1.16.2 \
  --set crds.enabled=true \
  --set prometheus.enabled=false

kubectl -n cert-manager get pods -w
```

Issuer Let's Encrypt (production) — à appliquer après que le DNS pointe vers le serveur :

```bash
kubectl apply -f deploiement/ressources/k8s/letsencrypt-issuer.yaml
```

### E.5 Désactiver le swap (exigé par Kubernetes)

```bash
sudo swapoff -a
sudo sed -i.bak '/[[:space:]]swap[[:space:]]/d' /etc/fstab
free -h    # vérifier swap=0
```

> Si Postgres montre des signes de swapping ultérieurement, augmenter la RAM physique plutôt que de réactiver le swap.

---

## F. Plan B — Pas de Kubernetes, Docker Compose seulement

> **💡 En clair** — Si l'équipe juge K3s trop complexe, on saute toute la section E et on s'arrête à Docker. **Docker Compose** est un fichier `.yml` qui décrit plusieurs conteneurs et leurs liens (« le backend dépend du postgres », « le frontend est exposé sur le port 80 », etc.). Plus simple, moins puissant : pas d'auto-scale, pas de rolling update sophistiqué. Suffit largement pour 1 hôtel. La migration K3s reste possible plus tard sans refaire les images.

Si votre équipe préfère démarrer **sans** K3s, sautez §E et :

```bash
# Création des dossiers de prod
sudo mkdir -p /srv/city/{compose,data/postgres,data/traefik-acme,backup,logs}
sudo chown -R cityadmin:cityadmin /srv/city
```

Le déploiement complet en Compose est documenté dans [`02 §3`](02_preparation_application.md) et [`03 §B`](03_deploiement_final.md).

---

## G. Validation finale du serveur

> **💡 En clair** — Avant de passer à la suite (déploiement de l'application), on coche méthodiquement chaque case de cette checklist. Une seule case rouge = on ne passe pas. Cette discipline évite de découvrir un problème plus tard (par exemple : « tiens, le serveur n'a pas l'heure synchronisée, du coup nos certificats TLS sont rejetés »). **smoke test** = test rapide qui confirme que les briques de base fonctionnent.

Checklist avant de passer à la procédure 2 :

| # | Vérification | Commande |
|---|--------------|----------|
| 1 | OS Ubuntu 24.04 LTS | `lsb_release -a` |
| 2 | Timezone Africa/Nouakchott | `timedatectl` |
| 3 | NTP synchronisé | `chronyc tracking` |
| 4 | SSH durci, mot de passe désactivé | `sudo sshd -T \| grep -E 'passwordauth\|permitroot'` |
| 5 | UFW actif, default deny | `sudo ufw status verbose` |
| 6 | fail2ban running | `sudo fail2ban-client status` |
| 7 | unattended-upgrades configuré | `sudo unattended-upgrade --dry-run -d 2>&1 \| tail` |
| 8 | sysctl appliqué | `sysctl net.ipv4.tcp_syncookies` (=1) |
| 9 | auditd running + règles | `sudo auditctl -l` |
| 10 | Docker fonctionnel | `docker run --rm hello-world` |
| 11 | K3s ready (si retenu) | `kubectl get nodes \| grep Ready` |
| 12 | Helm fonctionnel | `helm list -A` |
| 13 | cert-manager prêt | `kubectl -n cert-manager get pods` |
| 14 | Espace disque suffisant | `df -h` |
| 15 | Audit Lynis sans CRITICAL | `sudo lynis show details \| grep -i critical` |

Reporter chaque ligne dans [`checklist_go_live.md`](checklist_go_live.md). Si une seule case est rouge, **ne pas passer** à la procédure 2.

---

## H. Documentation & passage de relais

> **💡 En clair** — Une fois le serveur prêt, on **transmet** les informations à l'équipe d'exploitation : où est le serveur, comment s'y connecter, qui a quels droits. Sans cette transmission écrite, le sysadmin qui a installé devient un « single point of failure » humain — s'il quitte, plus personne ne sait administrer. **PV** (procès-verbal) = document signé qui acte que la livraison s'est bien passée, archivé par l'entreprise.

À la fin de la procédure 1 :

1. Mettre à jour le **wiki ops** avec :
   - IP du serveur, hostname, port SSH custom.
   - Nom de la clé SSH admin acceptée et empreinte.
   - Comptes locaux (cityadmin) et procédure de rotation.
   - Liens vers ce dossier `deploiement/`.
2. Remettre une **copie chiffrée** de la clé SSH `cityadmin` au coffre-fort de l'entreprise (ne pas conserver hors site sans chiffrement).
3. Signer le PV de mise en service du serveur avec le responsable.
4. **Aucun service métier ne tourne encore** — on a uniquement préparé l'hôte.
