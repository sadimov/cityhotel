# Plan d'action — 3 vagues

> Document généré le 2026-05-05 à partir de l'état des lieux initial.
> Voir `PROMPTS_TOURS.md` pour la liste détaillée des prompts à enchaîner.

---

## Vague 1 — Fondations (avant tout feature work)

### 1.1 Bloquants compilation
- Aucun bloquant immédiat : `mvnw compile` (backend) et `tsc --noEmit` (frontend) passent en exit 0.
- En revanche, l'app est essentiellement *vide* côté métier (27 fichiers Java, 42 fichiers TS — quasi tout dans `auth` / `core` / techniques).

### 1.2 Bloquants multi-tenant
- 🔴 **Aucune infrastructure tenant n'existe** :
  - Pas de `TenantContext` (ThreadLocal `hotelId`).
  - Pas de `TenantFilter` Hibernate ni d'`@Filter`.
  - Pas d'`AuditableEntity` (createdAt/updatedAt/createdBy/updatedBy).
  - Pas de `NumerotationService` ni de table `numerotation_sequence`.
  - `JwtAuthenticationFilter` ne propage pas `hotelId` dans un `ThreadLocal` réutilisable par les services.
- Aucun endpoint métier ne fuit aujourd'hui (puisqu'aucun n'existe), mais **toute intégration de `/MODULE/` produirait immédiatement des fuites** sans cette infra.

### 1.3 Upgrades de stack jugés critiques (pas de big bang)
| Item | Actuel | Cible | Priorité |
|---|---|---|---|
| Java | 17 | **21 LTS** (palier) ou 25 (doctrine stricte) | 🔴 |
| Spring Boot | 3.2.0 | **3.4.x** (palier) ou 4.0.x (doctrine stricte) | 🔴 |
| Angular | 20 | **21.2.x** | 🔴 |
| Tailwind | mix v3 + postcss-v4 | **v4.1 unifié** | 🟠 |
| Lombok | absent | 1.18.34+ | 🔴 (boilerplate CLAUDE.md) |
| MapStruct | 1.5.5 | 1.6.x | 🟡 |
| Resilience4j | absent | 2.2+ | 🟠 (Dolibarr) |
| JasperReports / OpenPDF / POI | absents | 7.0.x / 2.x / 5.3+ | 🟠 (finance/reporting) |
| Testcontainers + WireMock | absents | 1.20+ / 3.x | 🟠 (tests intégration) |
| **@ngx-translate/core** | absent | 16.x | 🔴 (i18n est règle métier 6.6) |
| **NgRx 21.x** | absent | 21.x | 🟠 (POS, reporting) |
| zone.js | 0.14 | 0.15+ | 🟡 |
| Node engines | `>=22` | 24 LTS | 🟡 |
| Liquibase | présent mais `enabled: false` + scripts SQL bruts | activé + changesets XML | 🟠 |

### 1.4 Arbitrages à acter avant de coder
1. **Stack option A vs B** :
   - **Option A** (pragmatique) — Java 21 LTS + Spring Boot 3.4.x + Angular 21.2.
   - **Option B** (doctrine stricte CLAUDE.md) — Java 25 + Spring Boot 4.0.x + Angular 21.2.
2. **Auth** : on garde `jjwt` 0.12.x (déjà en place) ou on bascule sur Spring Security OAuth2 Resource Server ?
3. **Build front** : Karma+Jasmine maintenu ou migration Vitest dès Angular 21 ?

---

## Vague 2 — Intégration des modules existants

> **Ordre justifié par les dépendances inter-modules.** Les tableaux du CLAUDE.md §4 disent que ces modules sont "✅ fait" côté backend — ce n'est pas vrai : les dossiers `entity/client`, `controller/finance`, etc. existent **vides**. Ce qui est "fait", c'est la couche `auth` + `core`. Le code à intégrer dort dans `/CLIENTS`, `/FINANCE`, `/HEBERGEMENT`, `/INVENTORY`, `/MENAGE`, `/RESTAURANT`.

| Ordre | Module | Dépend de | Volume à intégrer | Risque |
|---|---|---|---|---|
| 1 | **CLIENTS** | core | 6 .java + 8 .ts (213 KB) | faible — base référentielle |
| 2 | **HEBERGEMENT** | clients | 9 .java + 13 .ts + Calendar + PayReserv (653 KB) | moyen — gros périmètre, night audit |
| 3 | **INVENTORY** | core | 8 .java + 9 .ts (385 KB) | faible — pré-requis restaurant/finance |
| 4 | **FINANCE** | clients, hebergement, inventory | 6 .java + 9 .ts (265 KB) | 🔴 **vigilance comptable max** — numérotation, plan comptable, MRU, Dolibarr |
| 5 | **RESTAURANT** | inventory, clients, finance | 24 .java + 27 .ts + 7 html POS (726 KB) | élevé — POS le plus volumineux |
| 6 | **MENAGE** | hebergement | 8 .java + 9 .ts (501 KB) | faible — front à compléter |

Rituel par module :
1. `/integrate-module <nom>` (sous-agent vérifie doublons et merge propre).
2. `/audit-module <nom>` (croise avec `ERREURS_AUDIT_A_EVITER.html`).
3. `/multitenant-check <chemin>` (déclenche `multitenant-guardian`).
4. `/db-validate` (cohérence schéma/JPA après changeset Liquibase).
5. Tests verts → `/prep-commit`.

---

## Vague 3 — Modules à concevoir from scratch

| Module | Dépend de | Notes |
|---|---|---|
| **admin** | core, hotel | Gestion hôtels, utilisateurs, rôles superadmin. Le dossier `controller/admin` existe vide. |
| **profile** | core | Le composant front `profile/profile` existe ; back inexistant. |
| **reporting** | tous modules métier | Dashboard + exports PDF/Excel via JasperReports + POI. |
| **notification** | core | Kafka producers + emails (templates Thymeleaf déjà présents). |
| **intégration Dolibarr** | finance stable | Feign + Resilience4j (retry/circuit breaker). REST uniquement, jamais de PHP embarqué. |

---

## Action proposée pour le prochain tour

> **Au prochain tour, je suggère que tu me demandes :**
>
> ```
> Arbitre la stack — Option A (Java 21 + Spring Boot 3.4 + Angular 21.2)
> ou Option B (Java 25 + Spring Boot 4.0 + Angular 21.2) ?
> Puis lance la Vague 1 étape 1.4 : crée l'infrastructure multi-tenant
> (TenantContext, AuditableEntity, câblage JwtFilter, MDC) sans toucher
> aux modules /MODULE/.
> ```

Pas d'initiative — c'est toi qui arbitres.
