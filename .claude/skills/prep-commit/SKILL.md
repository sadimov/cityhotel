---
name: prep-commit
description: Prépare un commit propre sur le projet city — audit module, tests, lint, message Conventional Commits, vérification anti-secrets. À invoquer avant TOUT commit qui touche un module métier ou plus de 5 fichiers.
---

# prep-commit — Préparation de commit city

Skill jumeau de `/prep-commit [scope]`. Empêche les commits négligents (secrets, tests rouges, multi-tenant cassé).

## Étapes

### 1. État du repo
```bash
git status
git diff --stat HEAD
git diff --cached --stat
```
Identifier :
- Fichiers modifiés (par module).
- Fichiers ajoutés (nouveaux composants, entités).
- Fichiers supprimés (vérifier qu'aucun ne casse une dépendance).

### 2. Anti-patterns rapides (grep ciblé)

```bash
# Secrets
grep -rn -E "(password|apiKey|api_key|DOLAPIKEY|secret)\s*=\s*[\"'][^\"']+" \
  citybackend/src cityfrontend/src

# console.log oubliés
grep -rn "console\.log" cityfrontend/src/app

# TODO / FIXME bloquants
grep -rn -E "(TODO|FIXME|XXX)" --include="*.java" --include="*.ts" \
  citybackend/src cityfrontend/src
```

Bloquer si secret détecté ; signaler les autres.

### 3. Audit ciblé par module touché

Pour chaque module dans le diff (clients, hebergement, etc.) :
- Invoquer le skill `audit-module` mentalement (ou `/audit-module <module>`).
- Invoquer le skill `multitenant-check` (ou `/multitenant-check <chemin>`).

🔴 Bloquer si finding CRITIQUE détecté.

### 4. Tests

```bash
# Backend touché
cd citybackend && ./mvnw test -q -fae

# Frontend touché
cd cityfrontend && npm test -- --watch=false --browsers=ChromeHeadless
```

Si rouge → résumer les échecs et **ARRÊTER**. Ne pas committer.

### 5. Lint

```bash
cd cityfrontend && npm run lint
cd citybackend && ./mvnw -q compile  # vérifier que ça compile au minimum
```

### 6. Message Conventional Commits

Format :
```
<type>(<scope>): <résumé court à l'impératif, < 72 caractères>

<corps optionnel : pourquoi, contexte, BREAKING CHANGE>

Refs: #<ticket>
```

Types : `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `perf`, `style`, `build`, `ci`.
Scope : module touché (`auth`, `clients`, `hebergement`, `finance`, `restaurant`, `menage`, `inventory`, `admin`, `profile`, `reporting`, `dolibarr`, `core`, `infra`).

Exemples :
```
feat(inventory): ajoute relevé fournisseur paginé
fix(finance): empêche les trous de numérotation FACT en concurrence
refactor(hebergement): extrait NumerotationService vers package finance
chore(deps): bump Spring Boot 3.2 → 3.4
```

### 7. Confirmation

Proposer la commande exacte mais **ne pas committer** sans le "OK" explicite :
```bash
git add <fichiers exacts>
git commit -m "feat(inventory): ajoute relevé fournisseur paginé"
```

## Règles strictes

- ❌ **Jamais** `git add -A` automatique.
- ❌ **Jamais** `--no-verify`.
- ❌ **Jamais** `--no-gpg-sign` sans demande explicite.
- ❌ **Bloquer** si `.env`, `application-local.yml`, `*.key`, `*.pem` détecté dans le diff.
- ❌ **Bloquer** si tests rouges.
- ❌ **Bloquer** si finding multi-tenant CRITIQUE.
- ✅ Si pre-commit hook échoue : créer un **nouveau** commit après fix, pas `--amend`.

## Sortie

```
═══════════════════════════════════════════════════════════════
  prep-commit — scope: <X>
═══════════════════════════════════════════════════════════════
  Fichiers modifiés     : N
  Modules touchés       : <liste>
  Audit modules         : ✅ / 🔴 (X bloquants)
  Multi-tenant check    : ✅ / 🔴
  Tests backend         : ✅ / 🔴 (X échecs)
  Tests frontend        : ✅ / 🔴 (X échecs)
  Lint                  : ✅ / 🟡 (X warnings)
  Secrets / console.log : ✅ / 🔴

  Commande proposée :
  ───────────────────
  git add <…>
  git commit -m "<type>(<scope>): <message>"

  ⏵ En attente de votre OK pour exécuter.
═══════════════════════════════════════════════════════════════
```
