---
name: fewer-permission-prompts
description: Scanne les transcripts récents pour identifier les commandes Bash et tools MCP read-only fréquemment réclamées en permission, puis propose une allowlist priorisée à ajouter dans .claude/settings.json (projet) ou settings.local.json. Réduit la friction du workflow city sans accorder de permissions destructives.
---

# fewer-permission-prompts — Allowlist de commandes safe

Skill local au projet city. Variante de `fewer-permission-prompts` adaptée au workflow Maven / npm / Liquibase / git utilisé sur ce dépôt.

## Objectif

Réduire le nombre de prompts de permission affichés à l'utilisateur en autorisant **uniquement** des commandes :
- Read-only (lecture, recherche, status).
- Idempotentes et locales.
- Sans effet de bord sur la base ou le réseau (sauf cas explicitement autorisé : `npm install`, `mvnw dependency:resolve`).

## Démarche

### 1. Identifier les commandes récurrentes

Examiner les permissions réclamées dans les sessions récentes (par exemple les 3 derniers tours du `PROMPTS_TOURS.md`).

Catégories typiques pour le projet city :

| Catégorie | Commandes type | Risque |
|---|---|---|
| Git read | `git status`, `git diff`, `git log`, `git show`, `git branch` | nul |
| Git stage | `git add <fichier>` | faible (réversible) |
| Maven read | `./mvnw -q -DskipTests compile`, `./mvnw test`, `./mvnw dependency:resolve`, `./mvnw -version` | faible |
| Maven build | `./mvnw clean package` | faible (target/ régénéré) |
| npm read | `npm ls`, `npm outdated`, `npm view <pkg>` | nul |
| npm install | `npm install`, `npm ci` | faible (node_modules) |
| npm test/lint | `npm test --watch=false`, `npm run lint`, `npm run build` | faible |
| Angular CLI | `ng version`, `ng generate component <…>` | faible |
| TypeScript | `npx tsc --noEmit` | nul |
| psql read | `psql -U postgres -d cityprojectdb -c "\dt"`, `psql ... -c "SELECT ..."` | faible si SELECT only |
| Liquibase read | `./mvnw liquibase:status`, `liquibase status` | faible |
| Recherche | `find`, `grep`, `rg` (préférer Grep tool) | nul |

### 2. Catégories à NE JAMAIS allowlist

❌ Liste interdite :
- `git push`, `git push --force`, `git reset --hard`, `git clean -fd`, `git checkout .`
- `git commit` (l'utilisateur arbitre via `prep-commit`)
- `npm publish`
- `psql ... -c "DROP|TRUNCATE|UPDATE|DELETE|INSERT ..."`
- `liquibase update` / `liquibase rollback` (production)
- `docker push`, `docker rm`, `docker volume rm`
- Scripts de déploiement, `kubectl apply`, `helm upgrade`
- `mvn deploy`
- Tout `rm -rf` ou équivalent.

### 3. Format à ajouter dans settings

#### Cible projet (versionné) : `.claude/settings.json`
Pour les permissions partagées par toute l'équipe.

```json
{
  "permissions": {
    "allow": [
      "Bash(git status)",
      "Bash(git diff:*)",
      "Bash(git log:*)",
      "Bash(git show:*)",
      "Bash(git branch:*)",
      "Bash(git add:*)",
      "Bash(./mvnw -q *)",
      "Bash(./mvnw test:*)",
      "Bash(./mvnw -DskipTests compile)",
      "Bash(./mvnw -DskipTests verify)",
      "Bash(./mvnw dependency:resolve)",
      "Bash(./mvnw -version)",
      "Bash(npm ls)",
      "Bash(npm outdated)",
      "Bash(npm install)",
      "Bash(npm ci)",
      "Bash(npm test:*)",
      "Bash(npm run lint)",
      "Bash(npm run build)",
      "Bash(npx tsc --noEmit)",
      "Bash(ng version)",
      "Bash(ng generate component:*)",
      "Bash(ng generate service:*)",
      "Bash(ng generate module:*)"
    ]
  }
}
```

#### Cible perso (non versionné) : `.claude/settings.local.json`
Pour les permissions sensibles à la machine de l'utilisateur (psql en local, etc.).

```json
{
  "permissions": {
    "allow": [
      "Bash(psql -U postgres -d cityprojectdb -c \"\\dt*\")",
      "Bash(psql -U postgres -d cityprojectdb -c \"SELECT *\")"
    ]
  }
}
```

### 4. Démarche

1. Lire le `settings.json` actuel (`.claude/settings.json`).
2. Lire (s'il existe) `.claude/settings.local.json`.
3. Pour chaque commande candidate :
   - Vérifier qu'elle est read-only ou idempotente.
   - Vérifier qu'elle n'écrit pas en base de données.
   - Vérifier qu'elle n'a pas d'effet réseau destructif.
4. Proposer le diff exact à appliquer.
5. **Demander confirmation** avant écriture.
6. Préciser si la permission va dans `settings.json` (versionné, partagé) ou `settings.local.json` (perso).

### 5. Sortie

```
═══════════════════════════════════════════════════════════════
  fewer-permission-prompts — analyse session courante
═══════════════════════════════════════════════════════════════

  Commandes réclamées (top N) :
   12× ./mvnw -q -DskipTests compile
    8× git status
    7× npm test -- --watch=false
    5× ./mvnw test
    4× psql -U postgres -d cityprojectdb -c "SELECT ..."

  Allowlist proposée — .claude/settings.json :
  ────────────────────────────────────────────
  + "Bash(./mvnw -q *)"
  + "Bash(./mvnw test:*)"
  + "Bash(git status)"
  + "Bash(npm test:*)"

  Allowlist proposée — .claude/settings.local.json :
  ──────────────────────────────────────────────────
  + "Bash(psql -U postgres -d cityprojectdb -c \"SELECT *\")"

  Refus :
  ───────
  - git commit (toujours validation manuelle via prep-commit)
  - mvn deploy (jamais d'automatisation deploy)

  ⏵ Confirmer pour appliquer.
═══════════════════════════════════════════════════════════════
```

## Garde-fou

Si l'utilisateur demande d'autoriser quelque chose de la liste interdite (§ 2), **refuser** et expliquer pourquoi (réversibilité, blast radius). Renvoyer vers le skill `security-review` ou la slash command `/prep-commit`.
