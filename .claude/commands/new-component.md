---
description: Scaffold d'un composant Angular avec service, modèle, routing, i18n et binding DataTables si besoin
argument-hint: <nom-component> <feature> [--type=list|form|detail|dashboard]
allowed-tools: Read, Edit, Write, Bash(ng:*), Bash(ls:*)
---

Génère un composant Angular **$ARGUMENTS** dans `cityfrontend/src/app/features/<feature>/`.

## Workflow

1. **Détermination du type** :
   - `list` : liste paginée avec DataTables, recherche, actions (créer / éditer / supprimer).
   - `form` : formulaire réactif (création + édition).
   - `detail` : vue lecture seule + onglets si besoin.
   - `dashboard` : agrégats, charts (Chart.js).

2. **Si l'entité backend correspondante existe** (vérifier `citybackend/src/main/java/com/cityprojects/<feature>/`) :
   - Récupérer le DTO Java.
   - Générer l'interface TS correspondante dans `cityfrontend/src/app/features/<feature>/models/`.

3. **Génération via Angular CLI** quand possible :
   ```bash
   cd cityfrontend && ng generate component features/<feature>/components/<nom-component> --skip-tests=false
   ```

4. **Service HTTP** (si pas déjà existant) :
   - `features/<feature>/services/<entite>.service.ts`.
   - Méthodes CRUD typées avec les modèles.
   - URL via `environment.apiUrl`.

5. **Routing** : ajouter la route dans `<feature>-routing.module.ts` avec :
   - `authGuard`.
   - `roleGuard` + `data: { roles: [...] }` selon le sidebar (cf. `Prompts_Backend_Frontend.txt`).
   - Lazy load si module entier.

6. **i18n** :
   - Ajouter les clés dans `src/assets/i18n/fr.json`, `ar.json`, `en.json`.
   - Pattern : `<feature>.<entité>.<champ|action>`.

7. **Template** :
   - **Type `list`** : utiliser `<app-data-table>` (wrapper du `shared/`) ou DataTables direct si besoin.
     - Boutons Créer / Éditer / Supprimer avec `*ngIf="auth.hasRole(...)"`.
     - SweetAlert2 pour la confirmation de suppression.
     - États loading / empty / error.
   - **Type `form`** : `ReactiveFormsModule`, validation, soumission sécurisée (loader + try/catch HTTP).
   - **Type `detail`** : skeleton loader pendant fetch, erreur 404 redirige.

8. **Tests** :
   - `<nom-component>.component.spec.ts` : test de création + un test fonctionnel de base.

## Règles de sortie

- **Toujours** importer `TranslateModule` dans le module feature.
- **Toujours** désinscrire les observables (`takeUntil(this.destroy$)` ou pipe `async`).
- **Jamais** de `console.log` résiduel.
- **Jamais** d'URL en dur — `environment.apiUrl` toujours.
- Les rôles autorisés doivent être confirmés par l'utilisateur si non détectables depuis le sidebar.

## Récap final

Liste les fichiers créés/modifiés et les TODO restants (ex. : "ajouter colonnes spécifiques au DataTable", "personnaliser les validations métier").
