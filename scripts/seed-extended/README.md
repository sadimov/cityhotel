# Seed Extended — Tour 52 (jeu de donnees test exhaustif)

Complement aux scripts `scripts/seed_*.sql` (Tour 42). Couvre **TOUTES** les tables
metier des 7 schemas avec coherence FK et contraintes metier respectees.

> Pre-requis : avoir execute en prealable les 6 scripts de seed initial dans l'ordre
> (cf. `scripts/README.md`). Le seed initial doit avoir cree :
> - 2 hotels `NKC` et `DKR` dans `core.hotels`
> - 15 dbusers dans `core.dbusers`
> - 30 clients + 20 societes
> - 30 chambres + 20 reservations + 26 nuitees + 8 types_chambres
> - 30 produits + 10 fournisseurs + 10 categories_produits + 30 articles_menus
> - 30 taches menage + 20 commandes restaurant + 10 personnel menage
> - 10 sequences numerotation (FACT, PAY, BC, BS, RES par hotel)

## Ce que ce seed AJOUTE

| Schema | Table | Lignes ajoutees |
|---|---|---|
| core | parametres | +6 nouveaux (total final: 11) |
| hebergement | types_chambres (SALLE) | +4 (2 par hotel) |
| hebergement | chambres (salles) | +4 (2 salles par hotel) |
| hebergement | tarifs_chambres | +36 (3 saisons x 6 types CHAMBRE+SALLE x 2 hotels) |
| inventory | bons_commande | 10 (5 par hotel) |
| inventory | lignes_bons_commande | 30 |
| inventory | bons_sortie | 10 |
| inventory | lignes_bons_sortie | 24 |
| inventory | mouvements_stock | 35 (ENTREE depuis BC LIVRE + SORTIE depuis BS LIVRE + AJUSTEMENT + PERTE) |
| inventory | types_services_hoteliers | 8 (4 par hotel) |
| inventory | services_hoteliers | 16 (8 par hotel) |
| restaurant | recettes_articles | 51 (NKC: 27 + DKR: 24) — recettes pour 10 articles par hotel |
| restaurant | lignes_commande | 36 (2-4 lignes par commande seedee Tour 42) |
| restaurant | tickets | 16 (10 CAISSE pour commandes SERVIE + 6 CUISINE pour VALIDEE/EN_PREPARATION/PRETE) |
| finance | factures | 16 (8 par hotel - mix BROUILLON/EMISE/PARTIELLEMENT_PAYEE/PAYEE) |
| finance | lignes_factures | ~45-60 (nuitees + commandes + services + minibar + frais) |
| finance | paiements | 16 (mix 7 modes de paiement) |
| finance | affectations_paiements | 22 (7 legacy + 15 granulaires Tour 45) |
| finance | comptes | 30 (clients + societes principaux) |
| finance | operations_comptes | 30 (14 DEBIT facturation + 16 CREDIT paiement) |
| menage | planning | 42 (7 jours x 3 personnel x 2 hotels) |
| menage | historique | ~42 (30 CREATION + 4 TRANSITION (EN_COURS) + 2 TRANSITION (TERMINEE) + 6 ASSIGNATION) |

## Ordre d'execution

```powershell
$env:PGPASSWORD = "admin"

# Pre-requis : seeds initiaux deja executes (Tour 42 — scripts/seed_*.sql)

# Seeds extended (Tour 52) :
psql -U postgres -d cityprojectdb -f scripts/seed-extended/01-core-extra.sql
psql -U postgres -d cityprojectdb -f scripts/seed-extended/02-hebergement-extra.sql
psql -U postgres -d cityprojectdb -f scripts/seed-extended/03-inventory-extra.sql
psql -U postgres -d cityprojectdb -f scripts/seed-extended/04-restaurant-extra.sql
psql -U postgres -d cityprojectdb -f scripts/seed-extended/05-finance-extra.sql
psql -U postgres -d cityprojectdb -f scripts/seed-extended/06-menage-extra.sql
psql -U postgres -d cityprojectdb -f scripts/seed-extended/07-validation.sql

# OU en une seule commande :
psql -U postgres -d cityprojectdb -f scripts/seed-extended/run-all.sql
```

## Idempotence

Tous les scripts sont **idempotents** :
- INSERT avec `ON CONFLICT DO NOTHING` ou clauses `WHERE NOT EXISTS`
- Recreations conditionnelles avec sous-requete `SELECT ... WHERE NOT EXISTS`

Relance possible sans risque de doublons.

## Coherence metier garantie

- `Reservation.dateArrivee < dateDepart` (heritee du seed initial)
- `LigneFacture.facture_id` toujours valide (insert par jointure)
- `Facture.montant_paye = SUM(AffectationPaiement.montantAffecte)` (controle script 05)
- `Facture.statut` coherent avec montant paye (BROUILLON/EMISE/PARTIELLEMENT_PAYEE/PAYEE)
- `MouvementStock` : type ENTREE incremente, SORTIE decremente
- `BonSortie.statut = LIVRE` -> mouvements stock SORTIE existent (Mode coherent)
- `Commande.statut = SERVIE` -> ticket existant
- `Compte.solde_actuel = SUM(operations DEBIT) - SUM(operations CREDIT)` (auxiliaire client : positif = client doit)

## Multi-tenant

- Toutes les lignes ajoutees portent `hotel_id IN (id_NKC, id_DKR)` resolu via jointure `core.hotels`.
- Aucune ligne avec `hotel_id = 0` (sentinel ROOT reserve).
- Toutes les contraintes `CHECK (hotel_id > 0)` respectees.

## Devise

MRU partout. Pas de TVA (taux_tva = 0, conformement au scope POS actuel).

## Tour 52 — Notes

- ⚠️ Aucune modification du `pom.xml`, `application.yml`, `db.changelog-master.xml`, code Java.
- ⚠️ Aucun nouveau changeset Liquibase. Le seed est un **script SQL standalone**.
- ⚠️ Si vous reseedez de zero : DROP + CREATE DATABASE -> Liquibase -> seed initial -> seed-extended.
