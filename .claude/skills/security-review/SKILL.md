---
name: security-review
description: Revue de sécurité complète du projet city — multi-tenant, JWT, secrets, CORS, OWASP top 10, validation entrée, comptabilité, intégration Dolibarr. Croise systématiquement avec ERREURS_AUDIT_A_EVITER.html. À lancer avant chaque release et après une grosse intégration de module.
---

# security-review — Revue sécurité du projet city

Skill local au projet city, variante de `security-review` adaptée au domaine SaaS multi-tenant hôtelier.

Délègue généralement à `multitenant-guardian` (isolation hôtel) et `code-auditor` (autres aspects).

## Périmètre

1. **Multi-tenant** (priorité absolue cf. CLAUDE.md §6.1).
2. **Authentification & autorisation** (JWT, rôles, sessions).
3. **Validation d'entrée** (DTO, query params, headers).
4. **Secrets & configuration** (env vars, JWT secret, clés Dolibarr, Bankily).
5. **CORS & headers HTTP**.
6. **OWASP Top 10** (injection SQL, XSS, CSRF, IDOR, deserialization, etc.).
7. **Audit logs** (MDC `hotel_id` + `user_id`).
8. **Comptabilité** (numérotation sans trou, intégrité financière).
9. **Intégration Dolibarr** (clé chiffrée, idempotence, retry).
10. **Frontend** (XSS DOM, stockage JWT, ngx-translate échappement).
11. **Cohérence du routage post-intégration** : croiser le code intégré avec `CARTOGRAPHIE_MODULES.md`. Un fichier mal classé (ex. `PaymentService.java` placé dans `service/client/` au lieu de `service/finance/`) peut interroger la mauvaise table sans le filtre tenant attendu — finding **🟠 HAUTE**.

## Checklist détaillée

### 1. Multi-tenant
- [ ] Toute entité métier porte `hotel_id NOT NULL`.
- [ ] Toute requête JPA filtre par `hotelId` (ou est globale référentielle justifiée — rôles, types_chambre).
- [ ] `hotelId` extrait du JWT (`TenantContext`), jamais du payload, query, header.
- [ ] Endpoint de lecture : 403 si `hotelId(entité) != TenantContext.get()`.
- [ ] Endpoint de création : `hotelId` posé côté serveur, ignoré s'il vient du DTO.
- [ ] Tests d'intégration vérifient explicitement la 403 cross-hôtel.

### 2. JWT & sessions
- [ ] `JwtTokenProvider` : algo HS256+ ou RS256, **pas** none.
- [ ] Secret ≥ 256 bits, lu depuis `JWT_SECRET` env, **jamais** committed.
- [ ] Expiration ≤ 24h.
- [ ] Refresh token rotation activée.
- [ ] `JwtAuthenticationFilter` valide signature + expiration + tenant + révocation.
- [ ] `TenantContext.clear()` et `MDC.clear()` en `finally`.
- [ ] Limite de 80 sessions concurrentes / 3 par user (cf. application.yml) appliquée.
- [ ] `application.yml` : pas de `mySecretKey...` en clair en prod.

### 3. Autorisation
- [ ] `@PreAuthorize("hasAnyRole(...)")` sur **chaque** endpoint public.
- [ ] Vérifier la cohérence avec `roles_utilisateurs.txt` : SUPERADMIN, ADMIN, GERANT, RECEPTION, RESTAURANT, RESREC, MAGASIN, MENAGE.
- [ ] Pas de `permitAll` hors `/auth/login`, `/auth/refresh`, `/actuator/health`.
- [ ] Endpoints sensibles (admin, finance) → SUPERADMIN/ADMIN/GERANT seulement.

### 4. Validation d'entrée
- [ ] DTOs annotés `@Valid` au controller.
- [ ] Contraintes Jakarta : `@NotNull`, `@NotBlank`, `@Size`, `@Email`, `@Pattern`, `@PastOrPresent`, `@PositiveOrZero`.
- [ ] Pas de `@RequestParam String` non validé (taille, regex).
- [ ] Pagination : `Pageable` borné par `@PageableDefault(size=20)` et `size` max via `setMaxPageSize`.

### 5. Secrets
- [ ] Aucun secret en clair dans git :
  ```bash
  grep -rE "(password|secret|api.?key|DOLAPIKEY|JWT_SECRET)\s*[:=]\s*['\"][^'\"]{6,}" \
    citybackend/src cityfrontend/src
  ```
- [ ] `application.yml` utilise `${ENV_VAR:default}` (default ok en dev seulement).
- [ ] `.gitignore` exclut `.env`, `application-local.yml`, `*.key`, `*.pem`.
- [ ] Clés Dolibarr et Bankily chiffrées en base (Jasypt).

### 6. CORS & headers
- [ ] `app.cors.allowed-origins` whitelist, pas de `*` en prod.
- [ ] Headers de sécurité Spring Security : HSTS, X-Content-Type-Options, X-Frame-Options DENY, Referrer-Policy, Content-Security-Policy.
- [ ] Pas de `allow-credentials: true` avec un `*` (incompatible et dangereux).

### 7. OWASP Top 10
- [ ] **A01 IDOR** : tout `findById` exposé est en réalité `findByIdAndHotelId`.
- [ ] **A02 Crypto** : pas de MD5/SHA1 sur mots de passe, BCrypt ou Argon2.
- [ ] **A03 Injection** : 100% requêtes via JPA/JPQL ou `Specification` ; aucun `EntityManager.createNativeQuery` avec concat string.
- [ ] **A04 Insecure Design** : revue des flows finance (numérotation, paiements concurrents).
- [ ] **A05 Misconfiguration** : `ddl-auto: validate` en prod ; logs INFO et pas DEBUG en prod.
- [ ] **A06 Vulnerable Components** : `npm audit` et OWASP Dependency Check propres.
- [ ] **A07 Auth failures** : rate limiting login, lockout après 5 échecs (cf. `account-locked.html` template).
- [ ] **A08 Data Integrity** : signatures JWT, hashes mots de passe, intégrité comptable (no gaps).
- [ ] **A09 Logging** : MDC `hotel_id` + `user_id` sur tous les logs métier.
- [ ] **A10 SSRF** : pas de fetch de ressource depuis URL utilisateur (sauf upload contrôlé).

### 8. Comptabilité
- [ ] Numérotation factures sans trou (verrou pessimiste sur `numerotation_sequence`).
- [ ] Paiements ne dépassent pas le total facture (contrainte vérifiée côté service).
- [ ] Comptes du plan comptable mauritanien tous référencés et valides.
- [ ] Devise MRU partout (pas d'hétérogénéité EUR/USD).

### 9. Dolibarr
- [ ] `DOLAPIKEY` chiffré (Jasypt) en base.
- [ ] Aucun log de la clé (filter Logback masquant `DOLAPIKEY`).
- [ ] Idempotence : `numero` city = `ref_ext` Dolibarr → pas de doublon en cas de retry.
- [ ] Resilience4j retry + circuit breaker en place.

### 10. Frontend
- [ ] JWT en `localStorage` (pas SSR) — accepté car application sensible.
- [ ] Pas d'`innerHTML` sans `DomSanitizer`.
- [ ] `[innerHTML]` Angular → toujours via `DomSanitizer.bypassSecurityTrustHtml(...)` justifié.
- [ ] Routes : `authGuard` + `roleGuard` partout sauf `/auth/*`.
- [ ] Pas de `eval`, pas de `Function('...')`.
- [ ] Aucun secret en `environment.ts` (clé publique acceptable, pas plus).
- [ ] Téléchargements PDF/Excel : token rafraîchi au moment de l'appel.

## Outils

```bash
# Backend
cd citybackend && ./mvnw org.owasp:dependency-check-maven:check
cd citybackend && grep -rn "permitAll\|@PreAuthorize" src/main/java

# Frontend
cd cityfrontend && npm audit --audit-level=high
cd cityfrontend && grep -rn "innerHTML\|eval\|Function(" src/app
```

## Format de rapport

```
═══════════════════════════════════════════════════════════════
  security-review — city — <date>
═══════════════════════════════════════════════════════════════

🔴 CRITIQUES (bloque release)
  - <fichier:ligne> : <description> — OWASP A0X
    Correction : <patch>

🟠 HAUTES (à corriger sprint courant)
  - ...

🟡 MOYENNES (à corriger mois courant)
  - ...

🟢 INFOS (durcissements optionnels)
  - ...

═══════════════════════════════════════════════════════════════
  Score : N critiques / M hautes / K moyennes
  Verdict : ✅ release autorisée / 🔴 release bloquée
═══════════════════════════════════════════════════════════════
```

## Action en cas de critique

Bloquer toute release tant qu'au moins un finding 🔴 n'est pas corrigé. Proposer un plan de remédiation avant tout autre travail.
