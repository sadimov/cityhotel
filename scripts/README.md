# Scripts SQL — city hotel

Scripts SQL utilitaires pour seed/reset de la base de données dev.

## Usage rapide — seed test data complet

Réinitialise la DB locale avec ~351 lignes de test sur 21 tables (2 hôtels NKC + DKR, 15 users tous rôles, 30 clients, 20 sociétés, 30 chambres, 20 réservations, 26 nuitées, 30 produits, 20 commandes, 30 tâches ménage, etc.).

```powershell
# Pré-requis : DB cityprojectdb existe + Liquibase a créé le schéma (boot backend OK 1 fois)

# Charger l'ensemble du seed dans l'ordre des dépendances FK
$env:PGPASSWORD = "admin"
psql -U postgres -d cityprojectdb -f scripts/seed_test_data.sql                  # core (roles, hotels, dbusers, parametres)
psql -U postgres -d cityprojectdb -f scripts/seed_part2_client.sql               # sociétés + clients
psql -U postgres -d cityprojectdb -f scripts/seed_part3_hebergement.sql          # chambres + réservations + nuitées
psql -U postgres -d cityprojectdb -f scripts/seed_part4_inventory_restaurant_menage.sql  # inventory + (1ère tentative restaurant/menage)
psql -U postgres -d cityprojectdb -f scripts/seed_part4b_corrections.sql         # corrections noms tables (categories_menus avec S, etc.)
psql -U postgres -d cityprojectdb -f scripts/seed_part5_taches_commandes.sql     # tâches + commandes
```

## Reset complet DB locale (perte données)

```powershell
# 1. Stopper le backend (Ctrl+C)
# 2. Tuer connexions actives + DROP+CREATE
$env:PGPASSWORD = "admin"
psql -U postgres -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='cityprojectdb' AND pid <> pg_backend_pid();"
psql -U postgres -d postgres -c "DROP DATABASE cityprojectdb;"
psql -U postgres -d postgres -c "CREATE DATABASE cityprojectdb;"

# 3. Relancer le backend → Liquibase recrée tout le schéma
cd citybackend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
# Attendre "Started CitybackendApplication"

# 4. Seed (cf. ci-dessus)
```

## Comptes utilisateurs créés

Tous les comptes ont le hash BCrypt placeholder. **À utiliser uniquement en dev local**.

| Username | Hôtel | Rôle |
|---|---|---|
| `superadmin`     | NKC | SUPERADMIN |
| `admin.nkc`      | NKC | ADMIN |
| `admin.dkr`      | DKR | ADMIN |
| `gerant.nkc`     | NKC | GERANT |
| `gerant.dkr`     | DKR | GERANT |
| `reception1.nkc` | NKC | RECEPTION |
| `reception1.dkr` | DKR | RESREC |
| `reception2.nkc` | NKC | NIGHTAUDIT |
| `reception2.dkr` | DKR | NIGHTAUDIT |
| `restau.nkc`     | NKC | RESTAURANT |
| `restau.dkr`     | DKR | RESTAURANT |
| `magasin.nkc`    | NKC | MAGASIN |
| `magasin.dkr`    | DKR | MAGASIN |
| `menage1.nkc`    | NKC | MENAGE |
| `menage1.dkr`    | DKR | MENAGE |

Si le hash ne match pas votre mot de passe attendu, regénérez via :
```sql
-- Reset 'superadmin' avec hash BCrypt connu pour 'password'
UPDATE core.dbusers
SET password_hash = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'
WHERE username = 'superadmin';
```

## Tables non seedées

Ces tables sont alimentées par les flux applicatifs (UI ou scheduler) :

- `inventory.bons_commande`, `lignes_bons_commande`, `bons_sortie`, `lignes_bons_sortie`, `mouvements_stock` → via UI Magasin
- `restaurant.lignes_commande`, `tickets`, `recettes_articles` → via POS UI
- `finance.factures`, `lignes_factures`, `paiements`, `comptes`, `operations_comptes` → via flux check-out hébergement + POS
- `menage.planning`, `historique` → générés par `MenagePlanningScheduler` cron 12:05 + AOP `@AuditAction`
- `core.user_sessions`, `core.refresh_tokens` → créés au login JWT

## Limitations connues

- Le bug Liquibase split-statements + JSON dans `011-insert-initial-roles.sql` empêche les seeds Liquibase officiels de s'exécuter (changeset marqué EXECUTED mais inserts perdus). Les scripts ici sont un workaround : ils ré-insèrent ce que Liquibase aurait dû faire + ajoutent du test data.
- Les 5 paramètres `core.parametres` insérés ici doublent ceux que Liquibase aurait dû seeder (ON CONFLICT DO NOTHING gère le cas où Liquibase a finalement réussi).
- Pas de couverture cross-tenant (chaque ligne appartient à NKC OU DKR, jamais les 2). Pour tester l'isolation multi-tenant, login avec `admin.nkc` puis tentez d'accéder à un client DKR via l'API → 404 attendu.

## Quoi tester après seed

| Module | URL | User | Test attendu |
|---|---|---|---|
| Login | http://localhost:4200/login | superadmin/Test1234! | dashboard |
| Admin Hôtels | /admin/hotels | superadmin | voir 2 hôtels |
| Clients | /clients | admin.nkc | voir 15 clients NKC |
| Réservations | /reservations | reception1.nkc | voir 10 réservations NKC mix statuts |
| Chambres | /chambres | reception1.nkc | matrice 15 chambres NKC mix statuts |
| Restaurant POS | /restaurant/pos | restau.nkc | catalogue 15 articles + 10 commandes en cours |
| Stocks | /inventory/produits | magasin.nkc | 15 produits NKC + alertes seuil |
| Ménage | /menage/planning | menage1.nkc | 15 tâches NKC mix statuts |

Cross-tenant : `admin.nkc` ne doit JAMAIS voir les données DKR (vérifier UI vide ou 403).

## Refs

- `EXECUTION_PLAN.md` — runbook complet
- `QUICKSTART_WINDOWS.md` — démarrage local 10 min
- `RELEASE_NOTES_v1.0.0.md` — release notes
