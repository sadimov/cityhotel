---
description: Audite le code d'un module contre ERREURS_AUDIT_A_EVITER.html et les règles métier
argument-hint: <module> [--scope=back|front|all]
allowed-tools: Read, Bash(grep:*), Bash(rg:*), Bash(find:*)
---

Lance un audit du module **$ARGUMENTS**.

## ⚠️ Périmètre d'audit — important

Le périmètre d'audit est le **module intégré** dans `citybackend/` et `cityfrontend/`, pas le dossier source `/$ARGUMENTS/` à la racine.

Le dossier source `/CLIENTS/`, `/INVENTORY/`, etc. peut contenir du code qui appartient à d'autres modules : ce n'est **pas** ce qu'on audite ici. Pour vérifier qu'une intégration a correctement aiguillé les fichiers, croiser avec `CARTOGRAPHIE_MODULES.md` (racine) :
- Tout fichier présent sous `service/$ARGUMENTS/` ou `features/$ARGUMENTS/` dont la cartographie indique `Domaine réel ≠ $ARGUMENTS` → **finding 🟠 Important** (fichier mal routé lors de l'intégration).

## Démarche

1. **Charger le référentiel** : lire `ERREURS_AUDIT_A_EVITER.html` à la racine pour la liste des anti-patterns à détecter.

2. **Charger les règles** : lire `CLAUDE.md` racine + `citybackend/CLAUDE.md` + `cityfrontend/CLAUDE.md`.

3. **Charger l'intention** : lire le prompt original dans `PROMPTS/prompt_<module>*.txt`.

4. **Charger la cartographie** (si elle existe) : `CARTOGRAPHIE_MODULES.md`. Identifier les fichiers candidats à un bad routing.

5. **Audit backend** (`citybackend/src/main/java/com/cityprojects/citybackend/<package>/<module>/`) :
   - Toutes les requêtes JPA filtrent-elles par `hotel_id` ?
   - Tous les endpoints ont-ils `@PreAuthorize` ?
   - Le controller renvoie-t-il un DTO (jamais une entité) ?
   - Les services écrivent-ils en `@Transactional` (sans `readOnly`) ?
   - Les `@OneToMany`/`@ManyToOne` sont-ils en `LAZY` ?
   - Les exceptions métier sont-elles des clés i18n ?
   - Le `hotelId` est-il extrait de `TenantContext`, jamais du DTO ?

6. **Audit frontend** (`cityfrontend/src/app/features/<module>/`) :
   - Les souscriptions sont-elles désinscrites (`takeUntil`, pipe `async`) ?
   - L'URL d'API utilise-t-elle `environment.apiUrl` ?
   - Les libellés visibles passent-ils par `translate` ?
   - Les routes sont-elles protégées par `authGuard` + `roleGuard` ?
   - Les états `loading` / `error` / `empty` sont-ils gérés dans les listes ?
   - Aucun `console.log` résiduel ?

7. **Audit comptable** (si module finance ou facturation) :
   - Numérotation séquentielle par hôtel + exercice ?
   - Lien facture → ligne → nuitée/produit/service correct ?
   - Conformité avec `plan_comptable_mauritanien.pdf` (vérifier les comptes utilisés) ?
   - Devise MRU partout ?

8. **Rapport** : structurer la sortie en 3 sections :
   - **🔴 Bloquants** : violations multi-tenant, sécurité, comptabilité.
   - **🟠 Importants** : patterns non respectés, dette technique élevée.
   - **🟡 Suggestions** : améliorations mineures.

   Pour chaque finding, donner : fichier:ligne, citation courte, correction proposée (en code).

## Sortie

Termine par une ligne de score : `Audit module <X> : N bloquants / M importants / K suggestions`.
