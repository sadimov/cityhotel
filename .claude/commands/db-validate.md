---
description: Valide la cohérence du schéma PostgreSQL avec les entités JPA et le snapshot SQL
argument-hint: [--fix-changeset]
allowed-tools: Read, Bash(psql:*), Bash(grep:*), Bash(rg:*), Bash(find:*)
---

Compare trois sources de vérité du schéma :

1. **Snapshot SQL** : `structure_cityprojectdb*.sql` à la racine.
2. **Base réelle** : `psql -U postgres -d cityprojectdb -c "\dn+"` puis `\dt <schema>.*` pour chaque schéma `core`, `clients`, `inventory`, `finance`, `hebergement`, `restaurant`, `menage`.
3. **Entités JPA** : `citybackend/src/main/java/com/cityprojects/**/entity/*.java`.

## Checks

- Toutes les tables du SQL sont-elles couvertes par une entité JPA ?
- Toutes les entités JPA correspondent-elles à une table existante (nom + schéma + colonnes) ?
- Les types de colonnes Java/SQL sont-ils cohérents (`Long`↔`bigint`, `BigDecimal`↔`numeric`, `Instant`↔`timestamptz`, etc.) ?
- Toutes les tables hôtel-scopées ont-elles bien `hotel_id NOT NULL` ?
- Y a-t-il des **index manquants** sur les colonnes `hotel_id` et FK ?
- Y a-t-il des contraintes d'unicité oubliées (ex. `(hotel_id, numero)` sur factures) ?

## Sortie

```
=== Schémas présents en base ===
core, clients, inventory, finance, hebergement, restaurant, menage

=== Tables sans entité JPA ===
inventory.transfert_stock     (utilisé ? sinon → DROP)

=== Entités JPA sans table ===
com.cityprojects.menage.entity.Planning  → table menage.planning manquante

=== Divergences de colonnes ===
finance.facture
  - col `montant_ht` (BD: numeric(15,2) NOT NULL) absente de l'entité Facture
  - col `tax_id` (entité: Long) ↔ DB: integer

=== Index manquants ===
inventory.bon_commande.hotel_id
finance.facture.hotel_id, finance.facture.client_id
```

Si `--fix-changeset` est passé, **proposer** un nouveau fichier Liquibase corrigeant les écarts (sans l'appliquer automatiquement à la base).
