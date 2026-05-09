---
name: simplify
description: Revue de code city pour réutilisation, qualité et efficacité — détecte duplications, abstractions prématurées, fichiers trop gros (> 500 lignes), méthodes > 50 lignes. Adapté aux conventions citybackend (Spring) et cityfrontend (Angular). À invoquer après une grosse intégration de module ou avant un commit de refactor.
---

# simplify — Revue & simplification du code city

Skill local au projet (variante adaptée du skill stdlib `simplify`).
Centré sur les conventions city documentées dans `CLAUDE.md`, `citybackend/CLAUDE.md`, `cityfrontend/CLAUDE.md` et `ERREURS_AUDIT_A_EVITER.html`.

## Objectif

Repérer le code qui peut être :
- **Mutualisé** (DRY) — sans tomber dans l'abstraction prématurée.
- **Allégé** — fichiers > 500 lignes, méthodes > 50 lignes, conditions imbriquées > 3 niveaux.
- **Supprimé** — code mort, commentaires obsolètes, logs de debug, imports non utilisés.

## Démarche

### 1. Cibler le périmètre

```bash
# Backend volumineux
find citybackend/src/main/java -name "*.java" -size +20k -exec wc -l {} \;

# Frontend volumineux
find cityfrontend/src -name "*.ts" -o -name "*.html" -size +15k -exec wc -l {} \;

# Méthodes longues (Java)
grep -A 60 "public.*{" citybackend/src/main/java/.../X.java | head -70
```

### 2. Détections classiques

**Backend (Java / Spring)** :
- Code business répété dans plusieurs services → extraire un helper / un domain service.
- Mappings DTO → entité écrits à la main alors que MapStruct ferait l'affaire.
- Méthodes service > 50 lignes : split en sous-méthodes private (en restant dans le même `@Transactional`).
- Copies de boilerplate getter/setter → Lombok `@Getter @Setter`.
- Switches sur enum → polymorphisme ou `EnumMap`.
- `if (x != null && x.getY() != null && ...)` → `Optional.ofNullable(x).map(...).orElse(...)`.
- DTOs qui dupliquent l'entité : créer une seule **base record** + variantes Create/Update.
- Repository avec 10 méthodes custom → `Specification` ou `JpaSpecificationExecutor`.

**Frontend (Angular / TS)** :
- Composants > 300 lignes : split en sous-composants (`presentation/container`).
- Logique HTTP dupliquée → service feature unique.
- Templates avec >5 niveaux d'imbrication → composants enfants ou `*ngIf` extraits.
- Subscribe imbriqués → `switchMap`, `mergeMap`, `forkJoin`.
- Classes pour modèles → interfaces.
- Pipes maison qui doublonnent ngx-translate ou date-fns → supprimer.
- DataTables config dupliquée → factory dans `shared/components/data-table/`.

### 3. Anti-patterns city à corriger

Cf. `ERREURS_AUDIT_A_EVITER.html`. Les plus fréquents :
- `findAll()` sans filtre tenant → ajouter `findByHotelId()`.
- `@Transactional` sur méthode private → déplacer sur méthode publique appelante.
- `console.log` résiduels → supprimer.
- URL API en concat (`'http://localhost:8080/api/...'`) → `environment.apiUrl`.
- `.subscribe()` sans `unsubscribe` → `takeUntil(destroy$)` ou pipe `async`.

### 4. Règles de prudence

> **Don't add abstractions beyond what the task requires.** Trois lignes similaires < une abstraction prématurée.

- Ne pas extraire un helper utilisé une seule fois.
- Ne pas créer une interface si une classe suffit (sauf inversion de dépendance avérée).
- Ne pas factoriser si les deux blocs vont diverger dans la prochaine sprint.
- Ne pas refactorer du code multi-tenant sans `multitenant-check` derrière.
- **Avant de fusionner deux fichiers "qui se ressemblent"** détectés dans des modules différents, croiser avec `CARTOGRAPHIE_MODULES.md` : il est possible que l'un des deux soit en réalité **mal routé** (intégré depuis un dossier source `/MODULE/` qui ne correspondait pas à son domaine). Dans ce cas, la bonne action n'est PAS de fusionner mais de **déplacer** le fichier mal placé vers son module réel.

### 5. Sortie

```
═══════════════════════════════════════════════════════════════
  simplify — <périmètre>
═══════════════════════════════════════════════════════════════

🔵 DUPLICATIONS détectées : N
  - file1.java:120-145 ↔ file2.java:80-105
    ⏵ extraction proposée : <NomService>.<méthode>()

📏 FICHIERS / MÉTHODES TROP GROS : N
  - X.java (820 lignes) : split en X-base + X-helper
  - Y.component.ts:methodFoo (84 lignes) : extraire 3 sous-méthodes

🗑️  CODE MORT : N
  - imports inutilisés, méthodes non référencées, commentaires obsolètes

✂️  SIMPLIFICATIONS sûres : N
  - patron `Optional`, `switchMap`, MapStruct, etc.

⚠️  À NE PAS toucher :
  - <liste de "presque-doublons" qui devraient diverger sous peu>

═══════════════════════════════════════════════════════════════
```

### 6. Application

- Proposer les diffs **par paquet** (un paquet = un thème).
- Demander confirmation avant chaque paquet.
- Après chaque paquet : recompile + tests + `multitenant-check` si zone tenant touchée.
- Commit séparé par paquet (`refactor(<scope>): ...`).
