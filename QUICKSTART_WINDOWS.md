# 🚀 Quickstart Windows — tester city hotel en 10 minutes

Guide ultra-court pour démarrer city hotel sur votre poste Windows dev. **0 Docker requis** — tout en local.

---

## ⏱ Pré-requis (à installer une fois — ~15 min total)

Ouvrez **PowerShell en administrateur** :

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK
winget install --id OpenJS.NodeJS.LTS
winget install --id PostgreSQL.PostgreSQL.18
winget install --id Git.Git
```

Redémarrez votre terminal pour que les `PATH` soient à jour.

**Vérification** :
```powershell
java -version       # → 21.x
node -v             # → v24.x
psql --version      # → psql 18.x
git --version       # → 2.4x+
```

---

## 1️⃣ Cloner le projet

```powershell
cd C:\dev                       # ou n'importe quel dossier
git clone https://github.com/sadimov/cityhotel.git
cd cityhotel
git checkout v1.0.0
```

---

## 2️⃣ Créer la base PostgreSQL

```powershell
# Adaptez le mot de passe postgres à celui choisi à l'install (souvent 'postgres' ou 'admin')
$env:PGPASSWORD = "postgres"
psql -U postgres -c "CREATE DATABASE cityprojectdb;"
```

✅ Si OK : `CREATE DATABASE`. Si erreur "database exists" → tant mieux, déjà fait.

---

## 3️⃣ Définir les variables d'environnement (session courante)

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$env:JWT_SECRET = "dev-only-do-not-use-in-prod-please-rotate-immediately-32+chars-min"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "postgres"
$env:MAIL_PASSWORD = ""
```

⚠️ Adaptez `JAVA_HOME` au chemin réel d'install (vérifier avec `Get-ChildItem 'C:\Program Files\Eclipse Adoptium'`).

---

## 4️⃣ Démarrer le backend

**Terminal 1** (PowerShell, dans `cityhotel/`) :
```powershell
cd citybackend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

⏳ Attendre **30-60 secondes** (premier démarrage = Liquibase crée tous les schémas + seeds).

✅ **Boot OK** quand vous voyez :
```
Started CitybackendApplication in X.XXX seconds
```

Backend dispo sur : **http://localhost:8080/citybackend**

---

## 5️⃣ Installer + démarrer le frontend

**Terminal 2** (autre PowerShell, dans `cityhotel/`) :
```powershell
cd cityfrontend
npm ci                          # ~2-3 min au premier lancement
npm start                       # ng serve port 4200
```

✅ **Boot OK** quand vous voyez :
```
** Angular Live Development Server is listening on localhost:4200 **
```

Frontend dispo sur : **http://localhost:4200**

---

## 6️⃣ Se connecter

Ouvrez votre navigateur sur **http://localhost:4200/login** :

- **Username** : `superadmin`
- **Password** : `SuperAdmin123!`

✅ Vous arrivez sur le dashboard ! Vous pouvez :
- Créer un hôtel via le menu Administration → Hôtels
- Créer un user pour cet hôtel
- Vous reconnecter avec ce user pour explorer les modules métier (Clients, Réservations, Restaurant, Stocks, Ménage, Facturation)

---

## 7️⃣ (Bonus) Lancer les tests

**Terminal 3** :
```powershell
cd citybackend
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
.\mvnw.cmd verify
```

✅ Attendu : `Tests run: 147` (Surefire) + `Tests run: 62` (Failsafe) = **209 tests verts**.

---

## 8️⃣ Comptabilité — qu'est-ce qui marche sans Dolibarr ?

city hotel sépare deux niveaux de comptabilité :

### ✅ Comptabilité **auxiliaire client** — fonctionnelle nativement (v1.0.0)

Tout ce qui touche au **suivi par client / par hôtel** marche en standalone :

| Fonctionnalité | Statut v1.0.0 | Module |
|---|---|---|
| Factures (BROUILLON → EMISE → PAYEE / ANNULEE) | ✅ | finance |
| Numérotation séquentielle par hôtel + exercice (zéro trou) | ✅ | finance (`NumerotationService` lock pessimiste) |
| Lignes facture (NUITEE / PRODUIT / COMMANDE / SERVICE / DIVERS) | ✅ | finance |
| Paiements 12 modes (Espèces, Bankily, Carte, MASRIVI, SEDAD, etc.) | ✅ | finance |
| Affectation paiement → ligne facture | ✅ | finance |
| Comptes auxiliaires CLIENT/SOCIETE (`CPT-CLI-{id}`, `CPT-SOC-{id}`) | ✅ | finance |
| Soldes débit/crédit par client | ✅ | finance |
| Audit trail mouvements compte (`OperationCompte`) | ✅ | finance |
| Relevé de compte client / société | ✅ | finance |
| Devise MRU (ouguiya) | ✅ | hardcoded |

### ❌ Comptabilité **générale SYSCOHADA** — nécessite Dolibarr (Vague 3)

Tout ce qui relève du **plan comptable général** est **EXTERNALISÉ vers Dolibarr** par doctrine (cf. CLAUDE.md racine §6.2 / Tour 20). Le bridge Feign n'est **pas livré en v1.0.0** :

| Fonctionnalité | Statut v1.0.0 | Cible |
|---|---|---|
| Plan Comptable Général SYSCOHADA classes 1-9 | ❌ | Dolibarr |
| Écritures partie double (Débit X / Crédit Y) | ❌ | Dolibarr |
| Journaux (caisse, banque, ventes, achats) | ❌ | Dolibarr |
| Balance générale | ❌ | Dolibarr |
| Compte de résultat | ❌ | Dolibarr |
| Bilan | ❌ | Dolibarr |
| Grand livre | ❌ | Dolibarr |
| FEC (Fichier des Écritures Comptables) | ❌ | Dolibarr |
| Exercices clôturés (Article 14 OHADA) | ❌ | Dolibarr |
| Mapping `TypeLigneFacture → 70x`, `ModePaiement → 51x/53x` | ❌ | bridge à câbler |

### Pour la démo / phase pilote

Vous pouvez utiliser city hotel **sans Dolibarr** tant que :
- Vous restez sur des fonctions opérationnelles (réservations, check-in/out, POS restaurant, stocks, ménage, facturation client)
- Vous n'avez **pas** de besoin légal immédiat de produire balance / bilan / FEC

L'export comptable vers Dolibarr (manuel ou auto via bridge) sera implémenté en **Vague 3**. En attendant, vous pouvez :
- Exporter les factures + paiements en CSV/Excel (à câbler par export JasperReports — TODO Vague 3)
- Saisir manuellement les écritures dans un Dolibarr séparé (workflow temporaire, non scalable)

---

## 9️⃣ Installer Dolibarr en local (préparation Vague 3)

Vous pouvez installer Dolibarr dès maintenant pour tester l'intégration future. Le bridge Feign de city → Dolibarr arrive en **Vague 3** ; le service tournera ainsi côte à côte.

### Option A — DoliWamp (Windows tout-en-un, recommandé pour test rapide)

```powershell
winget install Dolibarr.DoliWamp
```

DoliWamp embarque Apache + MySQL + PHP + Dolibarr. Lance le **DoliWamp Server Manager** depuis le menu Démarrer, démarre les services, puis ouvre **http://localhost** dans un navigateur. L'assistant d'installation Dolibarr se lance.

Configuration :
- Login admin Dolibarr : `admin` (mot de passe à choisir)
- Activer le module **Comptabilité** (Configuration → Modules → Comptabilité-finance → Comptabilité)
- Activer le module **Entrepôts/Stocks** si vous voulez aussi sync stocks
- Configurer le **Plan Comptable** : **SYSCOHADA** (Configuration → Modules → Comptabilité → Plan comptable → SYSCOHADA — disponible nativement Dolibarr 19+)
- Devise : **MRU** (Configuration → Société → Devise principale)

### Option B — Docker (Linux / macOS / Windows + Docker Desktop)

```powershell
docker run -d --name dolibarr `
  -e DOLI_DB_HOST=host.docker.internal `
  -e DOLI_DB_NAME=dolibarr `
  -e DOLI_DB_USER=postgres `
  -e DOLI_DB_PASSWORD=admin `
  -e DOLI_INSTALL_AUTO=1 `
  -e DOLI_ADMIN_LOGIN=admin `
  -e DOLI_ADMIN_PASSWORD="<choix-admin-password>" `
  -p 8081:80 `
  dolibarr/dolibarr:21
```

Dolibarr accessible sur **http://localhost:8081**. À noter : Dolibarr préfère MySQL/MariaDB ; pour PostgreSQL, ajustez `DOLI_DB_TYPE=pgsql`. Plus simple : laisser Dolibarr créer sa propre BDD MySQL via l'image officielle.

### Option C — Container managé MySQL + Dolibarr (le plus propre)

```powershell
docker network create dolibarr-net
docker run -d --name dolibarr-mysql --network dolibarr-net `
  -e MYSQL_ROOT_PASSWORD=root `
  -e MYSQL_DATABASE=dolibarr `
  -e MYSQL_USER=dolibarr `
  -e MYSQL_PASSWORD=dolibarr `
  mariadb:11

docker run -d --name dolibarr --network dolibarr-net `
  -e DOLI_DB_HOST=dolibarr-mysql `
  -e DOLI_DB_NAME=dolibarr `
  -e DOLI_DB_USER=dolibarr `
  -e DOLI_DB_PASSWORD=dolibarr `
  -e DOLI_INSTALL_AUTO=1 `
  -e DOLI_ADMIN_LOGIN=admin `
  -e DOLI_ADMIN_PASSWORD="<choix-admin-password>" `
  -p 8081:80 `
  dolibarr/dolibarr:21
```

### Configurer l'API REST Dolibarr (préparation bridge city)

Dans Dolibarr admin :
1. **Configuration → Modules → Outils interface → Web services API REST** : activer le module
2. **Outils admin → API REST → Générer une clé pour un utilisateur** : générer la clé pour le compte admin (cette clé sera consommée par city via `DOLAPIKEY` header)
3. Tester via **http://localhost:8081/api/index.php/explorer/** (Swagger UI Dolibarr)
4. Documenter la clé dans `.env` city :
   ```env
   # Pour le bridge city → Dolibarr (Vague 3)
   DOLIBARR_API_URL=http://localhost:8081/api/index.php
   DOLIBARR_API_KEY=<clé générée>
   ```

⚠️ **La clé Dolibarr est sensible** — ne la commitez JAMAIS. Le bridge city la chiffrera en BDD via Jasypt en Vague 3.

### Multi-tenant Dolibarr

À arbitrer en Vague 3 (cf. CLAUDE.md §6.2 TODO bridge) :
- **Option 1** : 1 instance Dolibarr **par hôtel client** (isolation forte, coûteux)
- **Option 2** : 1 instance Dolibarr **partagée** + ventilation analytique par hôtel (économique, requiert mapping `analytic_account` city ↔ Dolibarr)

### Quand activer le bridge

Le bridge `DolibarrFeignClient` + `DolibarrSyncService` + `ParametrageComptable` (mapping `TypeLigneFacture → compte 70x`, `ModePaiement → compte 51x/53x`) sera livré en **Vague 3** ; voir `CLAUDE.md` §6.2 TODO bridge Dolibarr.

Tant que le bridge n'est pas livré, ne tentez pas de configurer une URL Dolibarr dans city — l'application l'ignorera (pas de code consommateur).

---

## 🛑 Arrêter

Dans chaque terminal : **Ctrl+C**.

Pour Dolibarr Docker : `docker stop dolibarr dolibarr-mysql`.

---

## 🆘 Problèmes courants

| Erreur | Solution |
|---|---|
| `psql: command not found` | Ajoutez `C:\Program Files\PostgreSQL\18\bin` à votre `PATH`, redémarrez PowerShell |
| `Access denied for user 'postgres'` | Ajustez `$env:DB_PASSWORD` à votre vrai mot de passe PostgreSQL |
| `Java 17 detected, expected 21` | Vérifiez que `JAVA_HOME` pointe vers JDK 21 : `echo $env:JAVA_HOME` |
| `npm ERR! ENOENT package.json` | Vous n'êtes pas dans `cityfrontend/`. Faites `cd cityfrontend` d'abord |
| `Port 8080 already in use` | Une ancienne instance tourne. `taskkill /F /IM java.exe` puis relancer |
| `Port 4200 already in use` | `taskkill /F /IM node.exe` puis relancer |
| Login refusé | Vérifiez bien `superadmin` / `SuperAdmin123!` (sensible à la casse). Sinon : reset DB → step 2 + redémarrer backend (Liquibase recrée le seed) |

---

## 📚 Pour aller plus loin

- **Plan d'exécution complet** (build prod, Docker, CI/CD) : `EXECUTION_PLAN.md`
- **Déploiement Kubernetes/K3s** : `DEPLOY_K8S.md`
- **Documentation projet** : `CLAUDE.md` (racine), `citybackend/CLAUDE.md`, `cityfrontend/CLAUDE.md`
- **Notes release** : `RELEASE_NOTES_v1.0.0.md`

---

**🎉 Bon test !**
