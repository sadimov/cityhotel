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

## 🛑 Arrêter

Dans chaque terminal : **Ctrl+C**.

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
