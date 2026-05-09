---
name: audit-module
description: Audite le code d'un module city contre ERREURS_AUDIT_A_EVITER.html, les CLAUDE.md et les règles métier (multi-tenant, comptabilité, i18n, sécurité). À déclencher après l'intégration d'un /MODULE/, après un refactor majeur, ou avant tout commit qui touche un module métier.
---

# audit-module — Audit qualité d'un module city

Skill jumeau de `/audit-module <module>`. À utiliser sur : `clients`, `hebergement`, `inventory`, `finance`, `restaurant`, `menage`, `admin`, `profile`, `reporting`.

Délègue généralement à l'agent `code-auditor` pour la passe finale.

## Pré-requis à charger

1. `ERREURS_AUDIT_A_EVITER.html` (racine) — référentiel des anti-patterns.
2. `CLAUDE.md` racine + `citybackend/CLAUDE.md` + `cityfrontend/CLAUDE.md`.
3. Prompt d'origine : `PROMPTS/prompt_<module>*.txt`.
4. Pour finance/restaurant/hebergement : `plan_comptable_mauritanien.pdf`, `modes_paiements.txt`, `règles_night_audit.txt`.
5. `CARTOGRAPHIE_MODULES.md` (racine, si elle existe) — pour détecter les fichiers mal routés lors de l'intégration.

## ⚠️ Périmètre — important

L'audit porte sur le **module intégré** (`citybackend/.../service/<module>/`, `cityfrontend/src/app/features/<module>/`), pas sur le dossier source `/<MODULE>/` à la racine.

Les dossiers source `/CLIENTS/`, `/INVENTORY/`, `/FINANCE/`, `/HEBERGEMENT/`, `/MENAGE/`, `/RESTAURANT/` mélangent du code de plusieurs modules. Si un fichier a été mal aiguillé pendant l'intégration (ex. un service `PaymentService.java` placé dans `service/client/` alors qu'il appartient à `service/finance/`), c'est un **finding 🟠 Important** à signaler — la correction sera de le déplacer.

## Checklist backend (`citybackend/src/main/java/com/cityprojects/citybackend/{entity,repository,service,controller,dto,mapper}/<module>/`)

### Multi-tenant
- [ ] Toute requête JPA filtre par `hotel_id` (ou est globale référentielle justifiée).
- [ ] Chaque méthode repository custom intègre `hotelId`.
- [ ] Le `hotelId` est extrait de `TenantContext`, **jamais** d'un DTO ou d'un `@RequestParam`.
- [ ] Les `findById` exposés au public sont remplacés par `findByIdAndHotelId`.

### Sécurité
- [ ] Chaque endpoint public a `@PreAuthorize("hasAnyRole(...)")`.
- [ ] Aucun endpoint sensible n'utilise `permitAll`.
- [ ] Aucun secret en clair dans le code (mots-clés `apiKey`, `password`, `secret`).

### Patterns Spring
- [ ] Le controller renvoie un **DTO** (pas une entité JPA).
- [ ] `@Transactional(readOnly=true)` au niveau classe + override en écriture.
- [ ] `@Transactional` jamais sur méthode `private` (no-op).
- [ ] `@OneToMany` / `@ManyToOne` en `LAZY`.
- [ ] Pas de `@Data` Lombok sur entité (equals/hashCode dangereux).
- [ ] `AuditableEntity` hérité.

### Validation & erreurs
- [ ] DTO d'entrée annotés `@Valid` + contraintes Jakarta.
- [ ] Exceptions métier = clés i18n (`error.<module>.notFound`).
- [ ] Pas de `catch (Exception e)` muet.

### Comptabilité (si finance / facturation / paiements)
- [ ] Numérotation séquentielle par hôtel + exercice via `NumerotationService`.
- [ ] Aucun trou de numéro autorisé (verrou pessimiste).
- [ ] Lignes facture référencent **exactement un** parmi : nuitée, produit, service, menu.
- [ ] Comptes utilisés conformes à `plan_comptable_mauritanien.pdf`.
- [ ] Devise = MRU (ouguiya).
- [ ] Modes de paiement = liste de `modes_paiements.txt` (Espèces, Chèque, Bankily, Carte).

## Checklist frontend (`cityfrontend/src/app/features/<module>/`)

### Architecture
- [ ] Module feature lazy-loaded.
- [ ] Routes protégées par `authGuard` + `roleGuard` (data.roles renseigné).
- [ ] Pas de cross-import entre features (passer par `core/` ou `shared/`).

### RxJS
- [ ] Chaque souscription a `takeUntil(destroy$)` ou pipe `async`.
- [ ] Pas de `.subscribe()` sans `unsubscribe`.

### HTTP
- [ ] URL via `environment.apiUrl`, **jamais** concaténée en dur.
- [ ] `hotelId` jamais envoyé par le client (le back lit le JWT).

### i18n
- [ ] Tout libellé visible passe par `| translate` ou `translate.get()`.
- [ ] Clés au format `<feature>.<entité>.<action>`.
- [ ] Clés présentes dans `assets/i18n/{fr,ar,en}.json`.
- [ ] RTL (arabe) : pas de styles `text-left` / `margin-left` qui cassent.

### UI/UX
- [ ] Listes : états `loading` / `empty` / `error` gérés.
- [ ] DataTables : pagination serveur si > 50 lignes attendues.
- [ ] Aucun `console.log` résiduel.
- [ ] Pas de manipulation jQuery quand un binding Angular suffit.

## Format de rapport

```
═══════════════════════════════════════════════════════════════
  Audit module <X> — <date>
═══════════════════════════════════════════════════════════════

🔴 BLOQUANTS (multi-tenant, sécurité, comptabilité)
  - file:line — citation courte
    Correction proposée :
    ```diff
    - ancien
    + nouveau
    ```

🟠 IMPORTANTS (patterns non respectés, dette élevée)
  - ...

🟡 SUGGESTIONS (mineurs, qualité)
  - ...

═══════════════════════════════════════════════════════════════
  Audit module <X> : N bloquants / M importants / K suggestions
═══════════════════════════════════════════════════════════════
```

## Bonus

Si possible, à la fin proposer la commande exacte pour appliquer un sous-ensemble des fixes :
```
git checkout -b fix/audit-<module>-bloquants
```

Ne **jamais** appliquer automatiquement les bloquants — l'utilisateur arbitre.
