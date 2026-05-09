# Checklist Go-Live city — version cible v1.0.0

> **À cocher dans l'ordre.** Une seule case rouge = stop.
> Imprimer cette checklist le jour J. Tracer les heures et les signatures.

---

## Préambule (date du go-live planifié)

- [ ] Date : __________ Heure NKC : __________
- [ ] Tag version : v____________
- [ ] Chef projet présent : __________
- [ ] Responsable infra présent : __________
- [ ] Astreinte N1 disponible : __________
- [ ] DBA / dev backend joignable : __________

---

## Phase 0 — Sauvegarde Windows Server 2016 (avant reformatage)

| # | Item | Vérification | OK |
|---|------|--------------|----|
| 0.1 | Image disque bare-metal | Restore test sur VM bac-à-sable réussie | ☐ |
| 0.2 | Données applicatives | Checksum source = checksum NAS | ☐ |
| 0.3 | Configurations exportées | `hostinfo.txt`, `network.txt`, `services.txt` archivés | ☐ |
| 0.4 | Comptes & mots de passe Windows | Coffre-fort à jour | ☐ |
| 0.5 | PV de sauvegarde signé | Document daté, 2 signatures | ☐ |

---

## Phase 1 — Serveur préparé (procédure 1)

| # | Item | Commande / preuve | OK |
|---|------|-------------------|----|
| 1.1 | Ubuntu 24.04 LTS installé | `lsb_release -a` | ☐ |
| 1.2 | Hostname + timezone NKC | `hostnamectl ; timedatectl` | ☐ |
| 1.3 | Comptes locaux : root verrouillé, cityadmin sudo | `passwd -S root \| grep "L "` | ☐ |
| 1.4 | SSH durci (clé only, port 2222, AllowUsers) | `sudo sshd -T \| grep -E 'passwordauth\|port \|allowusers'` | ☐ |
| 1.5 | UFW actif, default deny | `sudo ufw status verbose` | ☐ |
| 1.6 | fail2ban running | `sudo fail2ban-client status sshd` | ☐ |
| 1.7 | Unattended-upgrades configuré | `sudo unattended-upgrade --dry-run -d` | ☐ |
| 1.8 | sysctl hardening appliqué | `sysctl net.ipv4.tcp_syncookies` (=1) | ☐ |
| 1.9 | auditd + règles city | `sudo auditctl -l \| grep city` | ☐ |
| 1.10 | LVM partitionné comme prévu | `df -h ; lvs` | ☐ |
| 1.11 | Docker fonctionnel | `docker run --rm hello-world` | ☐ |
| 1.12 | K3s Ready (si voie K3s) | `kubectl get nodes \| grep Ready` | ☐ |
| 1.13 | Helm fonctionnel | `helm version` | ☐ |
| 1.14 | cert-manager pods Running | `kubectl -n cert-manager get pods` | ☐ |
| 1.15 | Swap désactivé | `swapon --show` (vide) | ☐ |
| 1.16 | Lynis audit sans CRITICAL | `sudo lynis show details \| grep -ci critical` (=0) | ☐ |
| 1.17 | NTP synchronisé | `chronyc tracking \| grep "Leap status"` | ☐ |
| 1.18 | Bannière SSH légale | `ssh -p 2222 cityadmin@<ip>` montre la bannière | ☐ |

---

## Phase 2 — Application prête (procédure 2)

| # | Item | Vérification | OK |
|---|------|--------------|----|
| 2.1 | Image city-backend poussée | `crictl pull ghcr.io/<org>/city-backend:<tag>` | ☐ |
| 2.2 | Image city-frontend poussée | idem | ☐ |
| 2.3 | Tag git signé GPG | `git tag -v v<...>` | ☐ |
| 2.4 | `environment.prod.ts` créé (point ouvert §1.3) | présent dans le bundle frontend | ☐ |
| 2.5 | Manifests k8s validés | `kubectl apply -k ... --dry-run=server` clean | ☐ |
| 2.6 | Secrets en place dans cluster | `kubectl -n city get secret` | ☐ |
| 2.7 | ConfigMap en place | `kubectl -n city get cm` | ☐ |
| 2.8 | NetworkPolicy + PDB déclarés | `kubectl -n city get netpol,pdb` | ☐ |
| 2.9 | CI verte sur le tag de release | rapport CI archivé | ☐ |
| 2.10 | Tests staging tous passés | rapport staging archivé | ☐ |

---

## Phase 3 — DNS & TLS

| # | Item | Vérification | OK |
|---|------|--------------|----|
| 3.1 | DNS A `city.example.mr` propagé | `dig +short city.example.mr A @1.1.1.1` | ☐ |
| 3.2 | Port 80 joignable extérieur | `nc -zv <ip> 80` depuis extérieur | ☐ |
| 3.3 | Port 443 joignable extérieur | `nc -zv <ip> 443` | ☐ |
| 3.4 | ClusterIssuer Let's Encrypt prod actif | `kubectl get clusterissuer letsencrypt-prod` | ☐ |
| 3.5 | Test certificat staging réussi | (étape préalable, supprimé après) | ☐ |

---

## Phase 4 — Déploiement (procédure 3 §B)

| # | Item | Vérification | OK |
|---|------|--------------|----|
| 4.1 | Postgres StatefulSet Running | `kubectl -n city get sts city-postgres` | ☐ |
| 4.2 | Backend Liquibase OK | logs `Update has been successful` | ☐ |
| 4.3 | Backend `/actuator/health` UP | `curl ... \| jq .status` | ☐ |
| 4.4 | Frontend Pods Running | `kubectl -n city get deploy city-frontend` | ☐ |
| 4.5 | Ingress + cert TLS Ready | `kubectl -n city get cert city-tls` Ready=True | ☐ |
| 4.6 | HPA backend déclaré | `kubectl -n city get hpa` | ☐ |
| 4.7 | Backup CronJob déclaré | `kubectl -n city get cj` | ☐ |
| 4.8 | Backup test manuel réussi | fichier `.sql.gz` non vide | ☐ |
| 4.9 | Restore test réussi | DB temporaire reconstituée | ☐ |

---

## Phase 5 — Smoke tests applicatifs

| # | Item | Vérification | OK |
|---|------|--------------|----|
| 5.1 | https://city.example.mr/ retourne 200 | curl -I | ☐ |
| 5.2 | HTTP redirect → HTTPS | `curl -I http://city.example.mr/` retourne 301/308 | ☐ |
| 5.3 | Header HSTS présent | `curl -I https://...` contient `Strict-Transport-Security` | ☐ |
| 5.4 | Login SUPERADMIN OK | obtient un JWT | ☐ |
| 5.5 | Endpoint authentifié OK | `/api/clients` retourne 200 | ☐ |
| 5.6 | Test isolement multi-tenant | user A ne voit PAS données hôtel B (403) | ☐ |
| 5.7 | i18n FR/AR/EN | `assets/i18n/{fr,ar,en}.json` chargés | ☐ |
| 5.8 | Pas d'erreur 5xx pendant 5 min de navigation | logs Loki `5xx ratio = 0` | ☐ |
| 5.9 | Latence p95 < 500 ms | wrk 30s | ☐ |
| 5.10 | Aucune connexion DB qui leak | `pg_stat_activity` < 30 | ☐ |

---

## Phase 6 — Monitoring

| # | Item | Vérification | OK |
|---|------|--------------|----|
| 6.1 | Prometheus scrape backend UP | targets page | ☐ |
| 6.2 | Grafana dashboards importés | JVM + Postgres + Spring Boot | ☐ |
| 6.3 | Loki ingère les logs | requête test renvoie des lignes | ☐ |
| 6.4 | Alertes minimales configurées | Alertmanager → ops email | ☐ |
| 6.5 | Test alerte (kill un pod) | mail/SMS reçu en < 2 min | ☐ |

---

## Phase 7 — Sécurité finale

| # | Item | Vérification | OK |
|---|------|--------------|----|
| 7.1 | Aucun secret en clair dans git | grep depot | ☐ |
| 7.2 | Mot de passe SUPERADMIN initial changé | login avec nouveau mdp OK | ☐ |
| 7.3 | Headers de sécurité front | `curl -I` contient CSP, X-Content-Type-Options, Referrer-Policy | ☐ |
| 7.4 | TLS 1.3 supporté, 1.0/1.1 désactivés | `nmap --script ssl-enum-ciphers -p 443 city.example.mr` | ☐ |
| 7.5 | Pas de port DB exposé Internet | `nmap <ip-publique>` ne montre pas 5432 | ☐ |
| 7.6 | Audit Lynis post-prod | `sudo lynis audit system` sans CRITICAL | ☐ |

---

## Phase 8 — Communication

| # | Item | OK |
|---|------|----|
| 8.1 | Email pré-bascule envoyé J-1 | ☐ |
| 8.2 | Page maintenance affichée pendant cutover | ☐ |
| 8.3 | Email post-bascule + URL nouvelle | ☐ |
| 8.4 | Astreinte mise au courant des numéros | ☐ |
| 8.5 | Wiki ops mis à jour | ☐ |

---

## Phase 9 — Soak test 24 heures

À cocher après J0+24 h, avant validation officielle.

| # | Item | Vérification | OK |
|---|------|--------------|----|
| 9.1 | Aucun pod CrashLoopBackOff | `kubectl -n city get pods` | ☐ |
| 9.2 | Aucun OOMKill | `kubectl -n city describe pod \| grep -i oom` | ☐ |
| 9.3 | Mémoire JVM stabilisée | dashboard Grafana | ☐ |
| 9.4 | Backup quotidien à 02:00 NKC OK | fichier présent | ☐ |
| 9.5 | Aucune fuite multi-tenant détectée | logs Loki | ☐ |
| 9.6 | Aucun message `Tenant context missing` | `{namespace="city"} \|= "Tenant context"` | ☐ |
| 9.7 | Login E2E continu (cron 5 min) | 100 % succès sur 24 h | ☐ |
| 9.8 | Charge moyenne CPU < 60 % | dashboard | ☐ |
| 9.9 | Disque < 60 % | `df -h` | ☐ |

---

## Validation finale

```
Date : __________
Heure : __________

J'atteste que la mise en production de City Hotel v____________ est conforme
à la procédure de déploiement, validée fonctionnellement et opérationnellement.

Chef de projet            Responsable infra            Astreinte
__________________        __________________            __________________
   signature                 signature                    signature
```

> Une copie scannée signée doit être archivée dans le dossier projet et au coffre-fort de l'entreprise.
