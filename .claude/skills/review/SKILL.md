---
name: review
description: Revue de pull request city — vérifie multi-tenant, conventions Spring/Angular, i18n, comptabilité, tests, conformité aux CLAUDE.md et à ERREURS_AUDIT_A_EVITER.html. À utiliser sur une PR GitHub (gh pr view) ou sur une branche locale avant ouverture de PR.
---

# review — Revue de PR / branche city

Skill local au projet city, variante de `review` adaptée aux conventions city.

## Démarche

### 1. Récupérer le diff

#### PR GitHub
```bash
gh pr view <num> --json title,body,files,commits,author
gh pr diff <num>
```

#### Branche locale
```bash
git diff main...HEAD --stat
git log main..HEAD --oneline
git diff main...HEAD
```

### 2. Identifier les modules touchés

Chaque fichier modifié appartient à un module (`auth`, `clients`, `hebergement`, `inventory`, `finance`, `restaurant`, `menage`, `admin`, `profile`, `reporting`, `dolibarr`, `core`, `infra`).

### 3. Lire les conventions

- `CLAUDE.md` racine — règles métier transverses.
- `citybackend/CLAUDE.md` — conventions Spring.
- `cityfrontend/CLAUDE.md` — conventions Angular.
- `ERREURS_AUDIT_A_EVITER.html` — anti-patterns à détecter.
- Pour finance : `plan_comptable_mauritanien.pdf`, `modes_paiements.txt`.
- Pour hebergement : `règles_night_audit.txt`.

### 4. Checklist par catégorie

#### Architecture
- [ ] Le code respecte l'arborescence packages (citybackend/CLAUDE.md §2) et features (cityfrontend/CLAUDE.md §2).
- [ ] Pas de cross-import entre features Angular.
- [ ] Pas de référence directe à une entité d'un autre module backend (passer par services).
- [ ] Conventions de nommage respectées (CLAUDE.md §7).

#### Multi-tenant 🔴
- [ ] Chaque nouvelle entité métier porte `hotel_id NOT NULL`.
- [ ] Chaque nouvelle requête repository filtre par `hotelId`.
- [ ] `hotelId` lu depuis `TenantContext`, jamais depuis le payload.
- [ ] Tests vérifient la 403 cross-hôtel.

#### Routage des fichiers (PR d'intégration depuis `/CLIENTS/`, `/INVENTORY/`, `/FINANCE/`, `/HEBERGEMENT/`, `/MENAGE/`, `/RESTAURANT/`)
- [ ] Si la PR intègre du code depuis un dossier `/MODULE/`, croiser chaque fichier ajouté avec `CARTOGRAPHIE_MODULES.md`. Un fichier placé sous `service/<X>/` alors que sa cartographie indique `Domaine réel = <Y>` est un **bloquant à corriger** (le déplacer vers le bon module).
- [ ] Aucun fichier `endpoints_*.txt`, `entities_services_*.java`, `resultat_chatgpt/*` ne doit avoir été copié comme code (ce sont des specs).
- [ ] Aucun fichier transverse (Application, SecurityConfig, intercepteurs) ne doit avoir été dupliqué — vérifier qu'il n'écrase pas l'existant.

#### Sécurité 🔴
- [ ] `@PreAuthorize` sur chaque nouvel endpoint.
- [ ] `@Valid` sur DTOs entrants.
- [ ] DTO en sortie controller, jamais d'entité.
- [ ] Pas de secret en clair dans le diff.

#### Comptabilité (si finance/factures/paiements)
- [ ] Numérotation via `NumerotationService`.
- [ ] Plan comptable conforme.
- [ ] Devise MRU.
- [ ] Modes paiement = liste autorisée.

#### Patterns Spring
- [ ] `@Transactional(readOnly=true)` au niveau classe + override en écriture.
- [ ] `LAZY` sur tous les `@ManyToOne`/`@OneToMany`.
- [ ] Pas de `@Data` Lombok sur entité.
- [ ] `AuditableEntity` hérité.
- [ ] Mappers MapStruct (pas de mapping manuel).

#### Patterns Angular
- [ ] Souscriptions désinscrites (`takeUntil`, pipe `async`).
- [ ] URL via `environment.apiUrl`.
- [ ] `hotelId` jamais envoyé par le client.
- [ ] Routes protégées par `authGuard` + `roleGuard`.
- [ ] Lazy loading pour le module feature.

#### i18n
- [ ] Tout libellé visible passe par `translate`.
- [ ] Clés présentes dans `assets/i18n/{fr,ar,en}.json`.
- [ ] Côté backend : exceptions retournent une **clé** i18n (`error.<module>.<cas>`).

#### Tests
- [ ] Tests unitaires services + mappers (Mockito).
- [ ] Tests intégration controllers (`@SpringBootTest` + Testcontainers).
- [ ] Tests sécurité multi-tenant : `@WithMockUser` ou JWT factice avec mauvais `hotelId`.
- [ ] Tests Karma/Vitest sur composants critiques.
- [ ] Coverage suffisante (≥ 70 % services, ≥ 50 % controllers).

#### Liquibase / DB
- [ ] Nouveau changeset XML, jamais modification d'un changeset existant.
- [ ] Rollback déclaré.
- [ ] Cohérence avec entité JPA (lancer `db-validate`).

#### DevOps
- [ ] Pas de `application-local.yml` ni `.env` committés.
- [ ] Pas de `--no-verify` dans les commits.
- [ ] Versions de dépendances cohérentes (`sync-tech` clean).

### 5. Évaluer la PR

#### Verdict
- ✅ **APPROVE** — tous les critères passent.
- 💬 **COMMENT** — questions à clarifier mais pas de bloquant.
- 🔴 **REQUEST CHANGES** — au moins un bloquant (multi-tenant, sécurité, comptabilité).

#### Comment poster
```bash
gh pr review <num> --approve --body "..."
gh pr review <num> --comment --body "..."
gh pr review <num> --request-changes --body "..."
```

⚠️ **Ne jamais poster un review sans demander confirmation à l'utilisateur**.

### 6. Format du review

```markdown
## Verdict : 🔴 REQUEST CHANGES

### Bloquants
- `BonCommandeService.java:42` — `findById(id)` sans `hotelId`. Risque IDOR cross-hôtel.
  ```diff
  - return repo.findById(id).orElseThrow(...)
  + return repo.findByIdAndHotelId(id, TenantContext.getCurrentHotelId()).orElseThrow(...)
  ```
- `bon-commande.component.ts:18` — souscription sans `unsubscribe`. Fuite mémoire.

### Importants
- `BonCommandeController.java:30` — DTO renvoyé directement OK ; ajouter `@PreAuthorize` sur `delete`.
- Manque tests de coverage sur `BonCommandeService.create`.

### Suggestions
- Extraire la validation des dates en pipe Angular dédié.
- i18n : la clé `bonCommande.title` n'est pas en `ar.json`.

### À saluer
- Bon usage de `Specification` pour les filtres.
- Tests Testcontainers solides.
```

### 7. Sortie

À la fin, résumer :
```
═══════════════════════════════════════════════════════════════
  review — PR #<num> / branche <X>
═══════════════════════════════════════════════════════════════
  Fichiers modifiés     : N
  Modules touchés       : <liste>
  Bloquants             : N (multi-tenant: X, sécu: Y, compta: Z)
  Importants            : M
  Suggestions           : K

  Verdict : <APPROVE / COMMENT / REQUEST CHANGES>

  ⏵ Confirmer pour poster le review.
═══════════════════════════════════════════════════════════════
```
