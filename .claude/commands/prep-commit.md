---
description: Préparer un commit propre : audit, tests, message conventionnel
argument-hint: [scope] (ex. inventory, finance, all)
allowed-tools: Read, Bash(git:*), Bash(./mvnw:*), Bash(npm:*)
---

Prépare un commit pour le scope **$ARGUMENTS** (par défaut tous les changements stagés).

## Étapes

1. **Diff** : `git status` + `git diff --stat HEAD`. Identifier les fichiers modifiés.

2. **Audit ciblé** : pour chaque module touché, lancer mentalement la checklist `/audit-module` et `/multitenant-check`. Bloquer si CRITIQUE détecté.

3. **Tests** :
   - Backend touché → `cd citybackend && ./mvnw test -q -fae`.
   - Frontend touché → `cd cityfrontend && npm test -- --watch=false --browsers=ChromeHeadless`.
   - Si rouge → afficher le résumé des échecs et **arrêter**.

4. **Lint** :
   - `cd cityfrontend && npm run lint` (si défini).

5. **Message** : proposer un message au format **Conventional Commits** :
   ```
   <type>(<scope>): <résumé court à l'impératif>
   
   <corps optionnel : pourquoi, contexte, BREAKING CHANGE>
   
   Refs: #ticket
   ```
   Types courants : `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `perf`, `style`.
   Scope : module touché (`inventory`, `finance`, `auth`…).

6. **Confirmation** : proposer la commande exacte mais ne **pas** committer sans le "OK" de l'utilisateur :
   ```
   git add <fichiers>
   git commit -m "feat(inventory): ajoute relevé fournisseur paginé"
   ```

## Règles

- **Jamais** `git commit -A` automatique. L'utilisateur valide la sélection.
- **Jamais** de `--no-verify`.
- Si secrets détectés dans le diff (mot `password`, `apiKey`, fichier `.env`) → bloquer.
