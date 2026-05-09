---
name: init
description: Génère ou met à jour le fichier CLAUDE.md (racine, citybackend, cityfrontend, ou n'importe quel sous-module city) en s'appuyant sur l'état réel du dépôt. À utiliser après un changement structurel majeur (nouveau module, nouvelle stack, nouveau pattern adopté).
---

# init — Génération/MAJ des CLAUDE.md city

Skill local au projet city. Variante adaptée du skill stdlib `init` pour respecter la structure documentée dans le projet (racine + sous-CLAUDE.md backend/frontend, et potentiellement sous-CLAUDE.md par module métier).

## Quand l'utiliser

- Après l'ajout d'un nouveau module métier (ex. `reporting`).
- Après un changement de stack (Spring Boot 3 → 4, Angular 20 → 21).
- Après l'adoption d'un nouveau pattern transverse (ex. NgRx, OAuth2 Resource Server).
- Avant une release majeure : aligner les CLAUDE.md avec la réalité du code.

## Cibles possibles

1. **Racine** : `CLAUDE.md` — vue produit, multi-tenant, état des modules, règles métier.
2. **Backend** : `citybackend/CLAUDE.md` — stack, packages, patterns Spring, Liquibase, sécurité.
3. **Frontend** : `cityfrontend/CLAUDE.md` — stack, arborescence, conventions, état/i18n/UI.
4. **Module** : `citybackend/src/main/java/com/cityprojects/citybackend/<module>/CLAUDE.md` (optionnel) ou `cityfrontend/src/app/features/<module>/CLAUDE.md`.

## Démarche

### 1. Inventaire de l'état réel

```bash
# Modules backend implémentés vs vides
find citybackend/src/main/java/com/cityprojects/citybackend/{entity,repository,service,controller}/* \
  -maxdepth 1 -type d | while read d; do
  count=$(find "$d" -name "*.java" | wc -l)
  echo "$d: $count java"
done

# Modules frontend implémentés
find cityfrontend/src/app/features -maxdepth 1 -type d 2>/dev/null

# Stack actuelle
grep "<version>" citybackend/pom.xml | head -10
node -e "console.log(JSON.stringify(require('./cityfrontend/package.json').dependencies, null, 2))"
```

### 2. Croiser avec les sources de vérité

- `Tech_DevOPS/TECHNOLOGIES_DEVOPS_A_UTILISER.md` (catalogue de catégories, **pas** de versions).
- `PROMPTS/prompt_*.txt` (intention métier des modules).
- `ERREURS_AUDIT_A_EVITER.html` (anti-patterns à mentionner).
- `plan_comptable_mauritanien.pdf` (conformité finance).
- `modes_paiements.txt`, `règles_night_audit.txt`, `roles_utilisateurs.txt`.

### 3. Sections obligatoires d'un CLAUDE.md

#### CLAUDE.md racine
1. Contexte produit (SaaS multi-tenant hôtelier).
2. Architecture du dépôt (arborescence).
3. Stack technique cible (avec doctrine "pas de rétrogradation").
4. **État des modules** (tableau ✅ / 🟡 / ❌ / ⚠️) — **MAJ obligatoire**.
5. Schémas PostgreSQL.
6. Règles métier critiques (multi-tenant, comptabilité, rôles, night audit, paiements, i18n).
7. Conventions de nommage.
8. Commandes courantes.
9. Workflow recommandé.
10. Anti-patterns interdits.

#### citybackend/CLAUDE.md
1. Stack & versions cibles.
2. Arborescence des packages.
3. Patterns standards (entité, repository, service, controller, DTO + mapper).
4. Sécurité (JWT, TenantContext, MDC).
5. Numérotation comptable.
6. Liquibase.
7. Tests.
8. Logs & monitoring.
9. Erreurs récurrentes à éviter.
10. Pour démarrer un nouveau module.

#### cityfrontend/CLAUDE.md
1. Stack.
2. Arborescence.
3. Conventions.
4. Patterns (service HTTP, DataTables, i18n, guards, intercepteurs).
5. Identité graphique.
6. Sidebar.
7. State management.
8. Erreurs récurrentes.
9. Lancer un nouveau composant.
10. Build & déploiement.

### 4. Cohérence à vérifier

- Les versions citées dans CLAUDE.md sont alignées sur celles du `pom.xml` / `package.json` (lancer `sync-tech` pour valider).
- Le tableau §4 (état des modules) reflète l'état réel — **principal piège** : éviter de marquer ✅ un module dont les dossiers backend sont vides.
- Les anti-patterns mentionnés correspondent à des cas réels rencontrés (croisés avec `ERREURS_AUDIT_A_EVITER.html`).
- Les commandes (`./mvnw`, `npm start`, slash commands `/integrate-module`, etc.) sont à jour.

### 5. Workflow

1. Lire le CLAUDE.md cible existant (s'il existe).
2. Lire les sources de vérité (§2).
3. Inventorier l'état réel (§1).
4. Identifier les écarts.
5. Proposer un **diff** précis (jamais réécrire de zéro sauf demande explicite).
6. Demander confirmation avant écriture.
7. Si écriture : préserver les sections personnalisées par l'utilisateur (notes spécifiques, identité graphique, etc.).

## Pièges classiques à éviter

- ❌ Réécrire un CLAUDE.md alors qu'un patch suffisait.
- ❌ Marquer ✅ un module dont l'implémentation est vide (cf. erreur initiale du CLAUDE.md racine §4 — backend "fait" alors que tous les controllers/entities/services métier sont des dossiers vides).
- ❌ Ajouter des conventions qu'on n'applique pas dans le code réel.
- ❌ Documenter une lib qui n'est pas dans le `pom.xml` / `package.json`.
- ❌ Citer des versions différentes de la stack réelle (le `sync-tech` doit être passé d'abord).
- ❌ Supprimer les notes personnelles de l'utilisateur (ex. CLAUDE.md racine §11 "Notes spécifiques").
- ❌ Confondre "le dossier `/MODULE/` existe à la racine" avec "le module est implémenté". Le contenu de `/CLIENTS/`, `/INVENTORY/`, etc. est du code source brut **mélangé**, à intégrer après cartographie. Ce n'est pas l'implémentation du module — l'implémentation se trouve **uniquement** dans `citybackend/` et `cityfrontend/` après le travail d'intégration.

## Sortie

```
═══════════════════════════════════════════════════════════════
  init — cible : <fichier>
═══════════════════════════════════════════════════════════════

  Sections à mettre à jour : N
   - §X : <raison>
   - §Y : <raison>

  Sections à conserver telles quelles : M

  Diff proposé (extrait) :
  ────────────────────────
   ## 4. État des modules

  -| clients       | ✅ fait            | ✅ fait           |
  +| clients       | ⚠️ à intégrer     | ⚠️ à intégrer    |

   ...

  ⏵ Confirmer pour appliquer.
═══════════════════════════════════════════════════════════════
```

## Bonus : init d'un sous-module

Si la cible est un sous-module métier (ex. `citybackend/src/main/java/com/cityprojects/citybackend/finance/CLAUDE.md`), structure :
1. Périmètre fonctionnel du module (ex. factures, paiements, opérations comptables).
2. Liens avec les autres modules (clients, hebergement, inventory).
3. Règles métier propres (numérotation FACT-2026-MR-, plan comptable, MRU).
4. Tables PostgreSQL du schéma.
5. Endpoints exposés et rôles requis.
6. Pièges spécifiques (concurrence numérotation, idempotence Dolibarr).
