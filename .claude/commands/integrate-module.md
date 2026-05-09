---
description: Intègre le code généré du dossier /<MODULE>/ dans citybackend et cityfrontend, en évitant les doublons
argument-hint: <nom_module>  (ex. CLIENTS, INVENTORY, FINANCE, HEBERGEMENT, RESTAURANT, MENAGE)
allowed-tools: Read, Edit, Write, Bash(git diff:*), Bash(ls:*), Bash(grep:*), Bash(find:*)
---

Tu es chargé d'intégrer le code du module **$ARGUMENTS** dans le projet existant.

## ⚠️ Vérité capitale sur les dossiers `/CLIENTS`, `/INVENTORY`, `/FINANCE`, `/HEBERGEMENT`, `/MENAGE`, `/RESTAURANT`

Le **nom du dossier n'est PAS une garantie de contenu**. Ces dossiers ont été produits par des sessions de génération hétérogènes (chatbot, ChatGPT, copies manuelles) et peuvent contenir :

- ✅ Le code du module attendu (cas idéal).
- 🟡 Du code qui appartient en réalité à **un autre module** (ex. une entité `Reservation` dans `/CLIENTS/`, ou un service de paiement dans `/RESTAURANT/`).
- 🟡 Du code **transverse / technique** (Application, SecurityConfig, intercepteurs HTTP, mappers partagés, helpers i18n) à router vers `common/`, `core/`, `config/`, `shared/`.
- 🟡 Des fragments de **modules from-scratch** (admin, profile, reporting) glissés là par erreur.
- 🟡 Des fichiers `.txt`, `endpoints_*.txt`, `entities_services_*.java`, `resultat_chatgpt/*` qui sont des **specs** (à lire en référence) — **pas** du code à copier.
- ❌ Du code obsolète, des doublons, du brouillon.

⛔ **Ne JAMAIS** copier en bloc `./$ARGUMENTS/files_back/` vers `citybackend/.../<module>/`. Toujours filtrer fichier par fichier.

✅ **Source de vérité** : `CARTOGRAPHIE_MODULES.md` (racine du dépôt, produit au Tour 7.5 du `PROMPTS_TOURS.md`). Pour ce tour, n'intégrer que les fichiers dont la colonne **"Domaine réel"** = `$ARGUMENTS`, **peu importe le dossier source** (un fichier finance trouvé dans `/CLIENTS/` sera intégré quand on traitera `finance`, pas maintenant).

Si `CARTOGRAPHIE_MODULES.md` n'existe pas encore : **arrêter** et exécuter le Tour 7.5 (cartographie globale) avant toute intégration.

## Étapes obligatoires

1. **Lecture de la cartographie** :
   - Ouvrir `CARTOGRAPHIE_MODULES.md`.
   - Extraire toutes les lignes où `Domaine réel = $ARGUMENTS`, indépendamment du dossier source (`/CLIENTS/`, `/HEBERGEMENT/`, etc.).
   - Si un fichier candidat n'apparaît pas dans la cartographie → arrêter et demander mise à jour de la cartographie (règle transverse #12 du `PROMPTS_TOURS.md`).

2. **Inventaire cible** : pour chaque fichier source retenu, vérifier s'il existe déjà dans :
   - `citybackend/src/main/java/com/cityprojects/citybackend/<package>/<module>/...` (back)
   - `cityfrontend/src/app/features/<module>/...` (front)
   - `citybackend/src/main/java/com/cityprojects/citybackend/common/...` ou `cityfrontend/src/app/shared|core/...` (transverse)

3. **Diff intelligent** :
   - Si le fichier cible n'existe pas → **copier**.
   - Si le fichier cible existe et est identique → **ignorer**.
   - Si le fichier cible existe mais diffère → **NE PAS écraser**. Présenter un diff résumé et demander confirmation explicite à l'utilisateur. Indiquer si la version source apporte une amélioration (nouvelle méthode, bugfix visible) ou au contraire un retour en arrière.

4. **Cohérence avec les conventions** : pour chaque fichier intégré, vérifier qu'il respecte :
   - Les règles du `CLAUDE.md` racine (multi-tenant, DBUsers, MRU…).
   - Les patterns du `citybackend/CLAUDE.md` (PreAuthorize, hotelId via TenantContext, DTO retournés…) ou du `cityfrontend/CLAUDE.md` selon le cas.
   - L'absence des erreurs listées dans `ERREURS_AUDIT_A_EVITER.html`.

5. **Liquibase** : si le module ajoute des tables, vérifier qu'un changeset existe dans `citybackend/src/main/resources/db/changelog/changes/` ou en créer un. Comparer avec `structure_cityprojectdb*.sql`.

6. **Routing & menu front** : si frontend, vérifier que :
   - Le module est lazy-loaded depuis `app-routing.module.ts`.
   - Les guards `authGuard` + `roleGuard` sont posés avec les rôles du `prompt_*.txt` correspondant dans `/PROMPTS/`.
   - L'entrée du sidebar (`shared/components/sidebar/menu-items.ts`) est présente.

7. **Tests rapides** :
   - Backend : `./mvnw -pl citybackend compile` doit passer.
   - Frontend : `cd cityfrontend && npx tsc --noEmit` doit passer.

8. **Rapport final** : produire un récapitulatif structuré :
   - Fichiers ajoutés (liste).
   - Fichiers ignorés car identiques (compte).
   - Fichiers en conflit nécessitant arbitrage (liste détaillée).
   - Tâches restantes (TODO).

## Règles strictes

- **Ne JAMAIS supprimer** un fichier existant sans confirmation explicite.
- **Ne JAMAIS écraser** un fichier modifié sans confirmation.
- **Ne JAMAIS copier en bloc un dossier `/<MODULE>/`** : la sélection passe **toujours** par `CARTOGRAPHIE_MODULES.md`.
- **Ne JAMAIS** intégrer un fichier dont le `Domaine réel` cartographié ≠ `$ARGUMENTS` — il sera traité au tour de son module réel.
- Les fichiers `endpoints_*.txt`, `entities_services_*.java`, `resultat_chatgpt/*` sont des **specs** : à lire pour comprendre l'intention, **jamais** à copier comme code.
- Toujours référencer les `prompt_*.txt` du dossier `/PROMPTS/` pour valider l'intention métier.
- Si une dépendance manque dans `pom.xml` ou `package.json`, **proposer** l'ajout, ne pas l'imposer.
