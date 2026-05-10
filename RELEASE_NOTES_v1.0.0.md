# Release Notes — city hotel v1.0.0

**Date** : 2026-05-10
**Tag git** : `v1.0.0`
**Commit** : `c5bbd55`
**Repo** : https://github.com/sadimov/cityhotel

Première release stable du SaaS de gestion hôtelière multi-tenant. 8 modules métier livrés, 209 tests verts, sécurité durcie post-audits.

---

## 1. Contexte produit

city hotel est une application web SaaS de gestion hôtelière **multi-tenant** ciblant le marché mauritanien. Le client initial s'appelle "City Hotel" mais le produit est commercialisé à d'autres hôtels qui s'abonnent comme **membres**. Chaque hôtel dispose de son **propre espace** isolé : ses utilisateurs, ses chambres, ses clients, ses factures, ses stocks.

**Devise** : MRU (ouguiya). **Timezone** : Africa/Nouakchott. **Langues** : français (par défaut), arabe (RTL), anglais.

**Règle d'or** : un utilisateur de l'hôtel A ne peut JAMAIS voir, lire ou modifier la moindre donnée de l'hôtel B. L'isolation multi-tenant est garantie par Hibernate `@TenantId` (audit Tour 38 final : 0 critique, 0 haute).

---

## 2. Périmètre fonctionnel — 8 modules livrés

| Module | Description | Tour intégration |
|---|---|---|
| **core / auth** | JWT (jjwt 0.12.6), refresh token rotation 7j, lockout après 5 échecs, rate limit Resilience4j 10/60s/IP sur `/auth/login` + `/auth/refresh`, sessions max 80 / 3 par user | Tours 1-7, 38 |
| **clients** | Client (personne physique) + Société (personne morale, prise en charge facturation), NIF unique par hôtel, recherche full-text | Tours 8, 9bis |
| **hebergement** | Chambres + machine d'état (DISPONIBLE/OCCUPEE/NETTOYAGE/MAINTENANCE/HORS_SERVICE) + Réservations multi-chambres + Nuitées idempotentes + NumerotationService (lock pessimiste, zéro trou comptable) + NightAuditScheduler (cron midi Africa/Nouakchott) | Tours 11-15 |
| **inventory** | Produits + Stock + Bons de commande + Bons de sortie + Fournisseurs + alertes seuil rupture | Tours 16-18 |
| **finance** | Factures (BROUILLON→EMISE→PARTIELLEMENT_PAYEE→PAYEE/ANNULEE) + Paiements + Comptes auxiliaires CLIENT/SOCIETE + 12 modes paiement (Espèces, Chèque, Bankily, Carte bancaire, MASRIVI, SEDAD, CLICK, AMANETY, BFI Cash, MOOV Money, GazaPay, Virement). **Doctrine v1.0.0** : comptabilité auxiliaire client uniquement, comptabilité générale SYSCOHADA externalisée Dolibarr (bridge Feign à venir Vague 3) | Tours 19-22 |
| **restaurant** | Catalogue (CategorieMenu + ArticleMenu) + POS (Caisse, Commande, Ticket) avec NgRx Component Store + Recettes auto-décrément stock à la transition `PRETE → SERVIE` (génère un BS automatique) + commandes reportées sur facture chambre client | Tours 23-26 |
| **menage** | Personnel + Tâches (workflow PLANIFIEE→EN_COURS→TERMINEE/ANNULEE avec `@Version` optimistic locking) + Planning + Historique audit (AOP `@AuditAction`) + workflows event-driven Spring Events : auto-génération planning à `checkOut` Reservation, transitions chambre auto sur tâche terminée, blocage chambre lors tâche MAINTENANCE | Tours 27-31 |
| **admin** | SUPERADMIN-only. CRUD Hotel + DBUser (mode ROOT via `TenantScope.runAs`) + Role read-only + Paramètres globaux (table `core.parametres`, modifiable=false pour système) | Tour 31 |

---

## 3. Stack technique — palier 1 (mai 2026)

### Backend
- **Java 21 LTS** (Temurin) — palier 2 visera Java 25 LTS Q4 2026
- **Spring Boot 3.4.5** (Spring 6.2, Security 6.4, Hibernate 6.6, Tomcat 10.1, Jakarta EE 10)
- **Spring Cloud 2024.0.1** (Moorgate) — OpenFeign + Stream Kafka prêts pour Vague 3
- **PostgreSQL 18.3** + Liquibase 4.30+ (changesets XML, jamais modification d'un changeset appliqué)
- **MapStruct 1.6.3**, **jjwt 0.12.6**, **Resilience4j 2.2.0**, **JasperReports 6.21.3**, **OpenPDF 2.0.3**, **Apache POI 5.3.0**
- **Testcontainers 1.20.3**, **WireMock 3.9.1**
- Build : Maven wrapper (`./mvnw`), `maven-compiler-plugin 3.13.0` `<release>21</release>`
- Image Docker : **Jib 3.4.4** (`eclipse-temurin:21-jre-alpine`, port 8080, user nonroot, format OCI)

### Frontend
- **Angular 21.2.x** (NgModule non standalone), **TypeScript 5.9.3**, **Node.js 24 LTS** (Krypton)
- **NgRx 21** (Store + Effects + Entity + Component Store pour POS)
- **@ngx-translate/core 16.0.4** + http-loader 16.0.1 (FR/AR/EN, Arabic complète RTL)
- UI : **Tailwind v4.1 unifié** + **Bootstrap 5.3.6** + **jQuery 3.7** + **DataTables 2.3+** + **SweetAlert2 11.17+** + **Chart.js 4.5+**
- Build Angular CLI 21.2, lazy chunks par feature
- Tests : Karma + Jasmine (palier 1, migration Vitest différée palier 2)

### DevOps
- **Jenkinsfile** déclaratif (parameters + tools jdk-21 + withCredentials secrets + stages parallèles + Sonar conditionnel)
- **GitHub Actions** (`.github/workflows`-style fichier dans `deploiement/ressources/ci/`) avec setup-java@v4 + setup-node@v4 + Jib + docker/build-push-action@v6
- **docker-compose.prod.yml** : 5 services (Traefik, frontend nginx, backend, postgres 18.3, backup nocturne 02h00 NKC)
- Secrets injection : `JWT_SECRET`, `DB_USERNAME`, `DB_PASSWORD`, `MAIL_PASSWORD` requis (boot fail-fast si absents)

---

## 4. Métriques

| Indicateur | Valeur |
|---|---|
| Modules métier livrés | 8 / 12 |
| Commits sur main | 19 (Tours 1 → 41) |
| Backend LOC (.java production) | ~15 800 (355 classes) |
| Frontend LOC (.ts production hors specs) | ~20 200 (185 fichiers) |
| Tests Surefire (unitaires) | **147 verts** |
| Tests Failsafe (intégration H2) | **62 verts** |
| BUILD SUCCESS | ✅ |
| Couverture audit multi-tenant | 0 critique, 0 haute (Tour 38 final) |
| Couverture audit sécurité | 10 critiques + 10 hautes corrigés (Tour 38 hardening) |

---

## 5. Breaking changes

**Aucun** — première release stable.

---

## 6. Installation / migration

### Pré-requis
- Java 21 LTS (Temurin recommandé) — `JAVA_HOME` configuré
- Node.js 24 LTS + npm ≥ 10.9
- PostgreSQL 18.3 local (ou container)
- Git

### Variables d'environnement obligatoires
```bash
export JWT_SECRET="<min 64 chars random — utiliser openssl rand -base64 64>"
export DB_USERNAME="<user PG>"
export DB_PASSWORD="<password PG>"
export MAIL_PASSWORD="<password SMTP — optionnel en dev>"
```

⚠️ Le boot Spring Boot **fail-fast** si `JWT_SECRET` est absent ou commence par `mySecretKey` (anti-leak).

### Étapes
```bash
# 1. Clone
git clone https://github.com/sadimov/cityhotel.git
cd cityhotel
git checkout v1.0.0

# 2. Base de données (création schéma initial)
psql -U postgres -c "CREATE DATABASE cityprojectdb;"

# 3. Backend
cd citybackend
./mvnw clean verify         # 147 Surefire + 62 Failsafe verts attendus
./mvnw spring-boot:run      # démarre sur http://localhost:8080/citybackend

# 4. Frontend (autre terminal)
cd cityfrontend
npm ci
npm start                   # démarre sur http://localhost:4200
```

### Premier login
- Username : `superadmin`
- Mot de passe initial : `SuperAdmin123!` (hashé dans le seed Liquibase)
- ⚠️ **Rotation OBLIGATOIRE** : flag `mot_passe_temporaire=true` posé sur ce compte. Endpoint `/auth/change-password` à câbler en Vague 3 (pour l'instant, rotation manuelle via admin DB).

### Build production
```bash
cd citybackend && ./mvnw clean package -DskipTests       # JAR exécutable
cd citybackend && ./mvnw jib:build                       # image OCI vers registry
cd cityfrontend && npm run build -- --configuration=production
```

---

## 7. Sécurité

Audits Tours 37 + 38 (10 critiques + 10 hautes identifiés et corrigés).

### Authentification
- JWT HS512 (jjwt 0.12.6), secret ≥ 64 chars validé au boot via `JwtTokenProvider.@PostConstruct`
- Access token expiration **1h** (réduit de 24h)
- Refresh token séparé, expiration **7j**, **rotation à chaque usage** (détection réutilisation cross-device → revoke all + 401)
- Purge nocturne refresh tokens expirés (`RefreshTokenPurgeScheduler` cron 03:00)
- Cross-device logout : `AuthService.logout` revoke tous les refresh tokens du user
- Lockout après 5 échecs login

### Rate limiting
- `RateLimitFilter` Resilience4j sur `/auth/login` + `/auth/refresh` : 10 req/60s/IP

### Multi-tenant (NON NÉGOCIABLE)
- Hibernate 6 multi-tenancy DISCRIMINATOR via `@TenantId` natif
- Sentinel `ROOT = 0L` pour mode global (admin/scheduler/batch)
- `@RequireTenant` AOP guard sur tous services métier (`error.tenant.missing` si TenantContext vide)
- Triggers PL/pgSQL pivot tenant coherence (Postgres-only, modules hebergement/finance/restaurant/menage)
- Audit Tour 38 final : **0 critique, 0 haute**, isolation étanche

### Headers HTTP
- HSTS 1 an + `includeSubDomains`
- CSP `default-src 'none'; frame-ancestors 'none'`
- Referrer-Policy `NO_REFERRER`
- X-Content-Type-Options nosniff (Spring Security default)

### Secrets
- Aucun default `application-prod.yml` — boot fail-fast si env var absente
- Aucun mot de passe en clair dans Liquibase seeds (compte `demoadmin` supprimé, `RAISE NOTICE` retirés)

### CORS
- Whitelist explicite `Authorization, Content-Type, X-Requested-With, Accept-Language` (plus de fallback `*`)
- `app.cors.allowed-origins` configurable

---

## 8. Limitations connues

### Vague 3 non livrée
- **reporting** — Chart.js + JasperReports + KPIs métier
- **profile** — back from-scratch (changement mdp + photo + préférences) ; le composant front existe partiellement
- **notification** — Mail Thymeleaf + Kafka mode dégradé Spring Events
- **dolibarr** — bridge Feign + Resilience4j (déjà au pom) pour comptabilité générale SYSCOHADA

### Dettes techniques résiduelles (signalées, non bloquantes)
1. `AuthController.login/refreshToken/logout` catchent encore `Exception` et renvoient `ex.getMessage()` brut — court-circuite `GlobalExceptionHandler` durci. À retirer (10 min).
2. Endpoint `/auth/change-password` à câbler pour exploiter `mot_passe_temporaire=true` (forcing rotation au premier login).
3. 8 `console.log/warn/error` non gardés `environment.production` dans 5 fichiers frontend (Tour 39 audit C2-1).
4. `DBUser.role @ManyToOne(fetch=EAGER)` à passer `LAZY` + `@EntityGraph` sur les requêtes ad hoc.
5. 4 `catch(Exception e)` silencieux dans `AuthService` (lignes 106, 443, 480, 533) — préciser type ou re-throw.
6. `ReservationServiceImpl` (542 LOC, 12 deps) : God service à scinder Command/Query (Tour 40bis2 dédié).
7. `pos.store.ts` 685 LOC : NgRx Component Store à scinder cart/client/checkout.
8. `PaginatedListBase` + `EntityFormBase` shared front : refactor reporté Tour 40ter2 dédié (3 listes + 3 forms à harmoniser).
9. Sonar local non exécuté (Docker indisponible sandbox au moment de la release) — substitut audit code statique manuel Tour 39 C2 ; à exécuter sur Sonar Cloud ou local Docker quand disponible.

### Tests
- Tous les ITs tournent sur **H2** (palier 1) — migration Testcontainers PostgreSQL différée palier 2 (Tour 2C, requiert Docker en CI).
- Pas de tests E2E Cypress/Playwright — couverture front via Karma uniquement.

---

## 9. Roadmap Vague 3

Ordre recommandé :

1. **Bridge Dolibarr** — `DolibarrFeignClient` + `DolibarrSyncService` + `ParametrageComptable` (mapping `TypeLigneFacture → compte 70x` + `ModePaiement → compte 51x/53x`) + idempotence retry. **Doctrine** : 1 instance Dolibarr par hôtel client OU 1 instance partagée avec ventilation analytique (à arbitrer produit).
2. **Notification** — Mail Thymeleaf (welcome / account-locked / password-reset / facture-emise / night-audit-done / reservation-created / stock-low-alert) + Kafka mode dégradé Spring Events.
3. **Profile** — back from-scratch (changement mdp avec règles `app.security.password.*`, upload photo avec `MultipartFile` + stockage filesystem ou S3, préférences langue UI).
4. **Reporting** — Chart.js dashboards + JasperReports exports PDF (occupation chambres, CA par module, top clients, alertes stock, balance auxiliaire client).
5. **Migration palier 2** (Q4 2026 si écosystème prêt) — Java 25 LTS, Spring Boot 4.0.x, Spring Cloud 2025.0.x. **Avant** intégration finance/restaurant POS production massive.

---

## 10. Crédits

**Auteur** : sadimov <sasidimed@gmail.com>
**Développement** : Claude Code (Anthropic) avec délégation à agents spécialisés (backend-spring, frontend-angular, code-auditor, multitenant-guardian, db-postgres, hotel-business)
**Période** : 2026-05-05 à 2026-05-10 (39 tours, 19 commits)

## 11. Licence

**Proprietary — All rights reserved.**

Code propriétaire. Aucune licence open source n'a été apposée à ce stade. Le code source contient des spécifications métier sensibles (plan comptable mauritanien, modes de paiement locaux Bankily/MASRIVI/SEDAD, identité graphique client). Toute redistribution ou usage tiers requiert l'autorisation explicite du propriétaire.

Si une licence open source est envisagée pour Vague 3 (ex. AGPL-3.0 pour aligner sur Dolibarr) : à arbitrer + ajouter `LICENSE` à la racine + headers de fichiers.

---

**Fin Release Notes v1.0.0**
