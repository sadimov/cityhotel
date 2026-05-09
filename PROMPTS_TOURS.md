# Roadmap de prompts — du tour 1 jusqu'à 100 % complétion

> Document généré le 2026-05-05.
> Chaque section liste un **tour** (= un prompt à coller). Quand plusieurs tours sont **parallélisables**, ils sont regroupés sous une bannière `║ PARALLÈLE ║`.
> Les **agents** (`backend-spring`, etc.) sont délégués automatiquement par Claude Code dès que le prompt mentionne leur domaine — tu peux aussi les invoquer explicitement.
> Les **slash commands** (`/integrate-module`, etc.) sont définis dans `.claude/commands/`.
> Les **skills** (cf. liste de la session) sont invocables via `Skill` ou par leur nom.

---

## Légende

- `🤖 backend-spring` `🤖 frontend-angular` `🤖 db-postgres` `🤖 multitenant-guardian` `🤖 code-auditor` `🤖 hotel-business` `🤖 dolibarr-integrator` — sous-agents.
- `⚡ /xxx` — slash command.
- `🧩 skill:xxx` — skill à invoquer (souvent doublon d'une slash command).
- `║ PARALLÈLE ║` — peut être lancé dans une autre fenêtre Claude Code.
- `🔒 BLOQUANT` — tour qui doit terminer avant la suite.

---

# PHASE 0 — Préliminaires

## Tour 1 — Arbitrage stack 🔒 BLOQUANT
```
Arbitrage stack : on part sur l'Option A (Java 21 + Spring Boot 3.4.x +
Angular 21.2) ou l'Option B (Java 25 + Spring Boot 4.0.x + Angular 21.2) ?
Lance ⚡ /sync-tech en mode audit (sans --apply) pour me donner le diff
détaillé des deux scénarios : pom.xml, package.json, breaking changes
attendus, plugins Maven et schematics Angular impactés. Pas de
modification de fichier — juste le rapport.
```
Skill : `🧩 skill:sync-tech`. Agent secondaire : `🤖 backend-spring` + `🤖 frontend-angular`.

---

# PHASE 1 — Fondations (Vague 1)

> Une fois le tour 1 arbitré, **les phases 1A (backend) et 1B (frontend) sont parallélisables**.

## ║ PARALLÈLE ║ — Phase 1A backend  /  Phase 1B frontend

### Phase 1A — Backend

#### Tour 2A 🔒 — Migration stack backend
```
🤖 backend-spring : applique l'option arbitrée au tour 1.
- Bump Java 17 → 21 (ou 25) dans pom.xml properties + maven-compiler-plugin.
- Bump Spring Boot 3.2.0 → 3.4.x (ou 4.0.x) dans le parent.
- Bump MapStruct 1.5.5 → 1.6.x.
- Vérifie spring-boot-devtools, JDBC PostgreSQL.
Lance mvnw -DskipTests verify et rapporte les échecs un par un.
Pas encore de modules /MODULE/ — on stabilise d'abord la base.
```

#### Tour 3A 🔒 — Infrastructure multi-tenant
```
🤖 backend-spring : crée l'infrastructure multi-tenant.
1. Package com.cityprojects.citybackend.common.tenant
   - TenantContext (ThreadLocal Long hotelId) avec set/get/clear.
   - TenantAware (interface marqueur pour entités).
2. Package common.audit
   - AuditableEntity (@MappedSuperclass) : createdAt, updatedAt,
     createdBy, updatedBy via @EntityListeners(AuditingEntityListener.class).
   - Activer @EnableJpaAuditing.
3. Modifie security.JwtAuthenticationFilter :
   - Extrait hotelId et userId du token, alimente TenantContext +
     SecurityContextHolder + MDC ("hotel_id", "user_id").
   - Nettoie en finally.
4. Filtre Hibernate @FilterDef("tenantFilter") global.
Pas de toucher aux entités existantes (DBUser, Hotel, Role) — elles ne
portent pas de hotel_id par nature. Tests unitaires sur TenantContext.
```

#### Tour 4A — Dépendances métier manquantes
```
🤖 backend-spring : ajoute au pom.xml :
- Lombok 1.18.34+ (et annotation processor)
- Resilience4j 2.2+ (resilience4j-spring-boot3)
- JasperReports 7.0.x + OpenPDF 2.x
- Apache POI 5.3+ (poi-ooxml)
- Testcontainers 1.20+ (postgresql, junit-jupiter)
- WireMock 3.x (standalone, scope test)
- Spring Cloud BOM 2025.0.x (préparation Feign — pas encore d'usage).
Vérifie qu'aucune ne casse la compile (mvnw -DskipTests compile).
```

#### Tour 5A — Liquibase XML + activation
```
🤖 db-postgres : convertis les scripts SQL bruts du dossier
src/main/resources/db/changelog/changelog/ en changesets XML
conformes à la convention citybackend/CLAUDE.md §6 :
- db.changelog-master.xml référence sous-changesets numérotés
  001-init-core.xml ... 010-data.xml.
- Convertis 001 à 010 SQL → XML (preconditions, rollback).
- Active liquibase.enabled: true dans application.yml.
Puis lance ⚡ /db-validate pour confirmer la cohérence avec les entités JPA
existantes (DBUser, Hotel, Role, UserSession).
```

#### Tour 6A — NumerotationService
```
🤖 backend-spring : crée le service de numérotation comptable
(citybackend/CLAUDE.md §5).
- Table finance.numerotation_sequence (hotel_id, type, exercice,
  last_value) via changeset Liquibase XML.
- NumerotationService avec @Lock(PESSIMISTIC_WRITE) sur l'incrément.
- Enum TypeNumerotation (FACT, PAY, BC, BS, AVOIR…).
- Format : FACT-2026-MR-000123 (configurable par hôtel via code pays).
- Tests unitaires + tests de concurrence.
```

#### Tour 7A — Audit fondations
```
🤖 multitenant-guardian + 🤖 code-auditor : exécute ⚡ /multitenant-check
sur citybackend/src/main/java/com/cityprojects et ⚡ /audit-module common.
Rapporte uniquement findings 🔴 et 🟠. Pas de fix automatique — donne-moi
la liste prioritisée.
```

### Phase 1B — Frontend (peut tourner en parallèle de 1A)

#### Tour 2B 🔒 — Migration Angular 20 → 21
```
🤖 frontend-angular : migre Angular 20 → 21.2.x via ng update.
- @angular/* ^21.0.0
- @angular-devkit/build-angular ^21.0.0
- TypeScript 5.8 conservé
- zone.js 0.14 → 0.15
- Node engines ">=24.0.0"
- ESLint @angular-eslint ^19 ou la version compatible Angular 21.
Vérifie tsc --noEmit et ng build (mode dev). Garde NgModule architecture.
```

#### Tour 3B 🔒 — Tailwind v4 unifié
```
🤖 frontend-angular : unifie Tailwind sur v4.1.
- Remplace tailwindcss ^3.4.17 par ^4.1.x.
- Supprime postcss + @tailwindcss/postcss si redondant.
- Reconfigure tailwind.config + style global selon nouveau modèle v4
  (CSS-first config, @theme).
- Garde Bootstrap 5.3 (utilisé pour layout/grid).
```

#### Tour 4B — i18n via @ngx-translate
```
🤖 frontend-angular : installe et câble @ngx-translate.
- @ngx-translate/core ^16.x + http-loader ^16.x.
- Crée src/assets/i18n/{fr,ar,en}.json (clés minimales pour login,
  header, sidebar, common).
- Configure TranslateModule.forRoot dans CoreModule avec http-loader.
- Crée TranslationService wrappeur, gère le changement de langue + RTL
  (ajoute dir="rtl" sur <html> quand currentLang === 'ar').
- Branche dans le HeaderComponent (sélecteur de langue).
- Supprime le pipe maison src/app/pipes/translate-pipe.pipe.ts s'il
  doublonne ngx-translate.
```

#### Tour 5B — NgRx Store core
```
🤖 frontend-angular : installe NgRx 21.x.
- @ngrx/store, @ngrx/effects, @ngrx/entity, @ngrx/store-devtools.
- Crée store/auth (currentUser, currentHotel, roles) + store/ui
  (currentLang, sidebarCollapsed).
- Branche StoreModule + EffectsModule dans AppModule.
- Migre AuthService pour dispatcher login/logout via actions.
```

#### Tour 6B — Identité graphique header/sidebar/layout
```
🤖 frontend-angular : applique consignes_design_interface_graphique.txt.
- Variables CSS dans assets/styles/_theme.scss : couleur primaire
  bleu clair (sky-500 ≈ bg-info), fonds blanc/gris.
- HeaderComponent : logo hôtel + nom à gauche, sélecteur langue +
  notifications + profil + déconnexion à droite.
- SidebarComponent : pliable (collapsed/expanded), filtre items selon
  rôles via store.
- Boutons utilitaires .btn-primary-city avec transitions.
- Vérifie le rendu RTL en arabe.
```

#### Tour 7B — Audit fondations frontend
```
🤖 frontend-angular + 🤖 code-auditor : passe en revue le frontend
post-migration. Croise avec ERREURS_AUDIT_A_EVITER.html (souscriptions
non désinscrites, console.log, etc.). Rapporte les findings.
```

---

# PHASE 2 — Intégration des modules existants (Vague 2)

> **Ordre obligatoire** : clients → hebergement → inventory → finance → restaurant → menage.
> Pour chaque module, le rituel est : **cartographie du dossier source** → intégration back → intégration front → audit → multi-tenant check → db-validate → prep-commit.

## ⚠️ Vérité capitale sur les dossiers `/CLIENTS`, `/INVENTORY`, `/FINANCE`, `/HEBERGEMENT`, `/MENAGE`, `/RESTAURANT`

**Le nom du dossier n'est PAS une garantie de contenu.** Ces dossiers ont été produits par des sessions de génération hétérogènes (chatbot, ChatGPT, copies manuelles) et peuvent contenir :

- ✅ Le code du module attendu (cas idéal).
- 🟡 Du code qui appartient en réalité à **un autre module** (ex. une entité `Reservation` dans `/CLIENTS/`, ou un service de paiement dans `/RESTAURANT/`).
- 🟡 Du code transverse / technique (ex. `CitybackendApplication.java`, `SecurityConfig`, mappers partagés, intercepteurs HTTP, helpers i18n) qui doit aller dans `common/`, `core/`, `config/`, `shared/`.
- 🟡 Du code de **plusieurs modules mélangés** (ex. `/HEBERGEMENT/` contient aussi du finance car `Reservation` génère facture).
- 🟡 Du code de **modules from-scratch** (admin, profile, reporting) glissé là par erreur.
- ❌ Du code obsolète, du brouillon, du copier-coller doublonné de plusieurs versions.
- ❌ Des fichiers `.txt`, `.java`, `.ts` libres au niveau du dossier (`endpoints_module_*.txt`, `entities_services_module_*.java`) qui sont des **specs**, pas du code à intégrer tel quel.

**Conséquence opératoire** :
- ⛔ **Jamais** copier en bloc `/<MODULE>/files_back/` vers `citybackend/...<module>/`.
- ✅ **Toujours** classer fichier par fichier avant d'intégrer.
- ✅ Le rituel commence par un **tour de cartographie** (Tour 7.5) qui produit un mapping `fichier source → destination réelle`.
- ✅ Si un fichier appartient à un autre module, il sera intégré au tour de **ce** module (pas du dossier où il a été trouvé).
- ✅ Si un fichier est transverse, il rejoint `common/` (back) ou `shared/` / `core/` (front).
- ✅ Les `.txt` et `entities_services_*.java` libres servent de **spec de référence** pour la rédaction, pas de source à copier.

---

## Tour 7.5 🔒 BLOQUANT — Cartographie globale des 6 dossiers /MODULE/

> Ce tour précède **toute** intégration. Il produit un seul artefact : `CARTOGRAPHIE_MODULES.md` (à la racine).

```
🤖 Explore (ou general-purpose) : produis une cartographie exhaustive
des 6 dossiers /CLIENTS, /FINANCE, /HEBERGEMENT, /INVENTORY, /MENAGE,
/RESTAURANT.

Pour CHAQUE fichier de code (.java, .ts, .html, .scss) trouvé dans ces
dossiers (y compris les sous-dossiers files_back, files_front, partie_front,
COMPONENT_*, point-vente, resultat_chatgpt, etc.) :
1. Lis l'en-tête + premières lignes pour identifier la classe / le composant.
2. Détermine son DOMAINE RÉEL parmi : clients | hebergement | inventory |
   finance | restaurant | menage | admin | profile | reporting |
   notification | dolibarr | core | auth | shared | infra | spec.
3. Détermine sa DESTINATION FINALE :
   - back : citybackend/src/main/java/com/cityprojects/citybackend/<package>/<module>/
   - front : cityfrontend/src/app/features/<module>/ ou shared/ ou core/
   - autre : common/, config/, à supprimer (doublon obsolète), à ignorer (spec .txt).

Pour CHAQUE fichier .txt ou .pdf à la racine d'un dossier /MODULE/ :
- Marquer comme "SPEC" — sera lu en référence, jamais copié.

Format de sortie (CARTOGRAPHIE_MODULES.md) :

## /CLIENTS
| Fichier source | Type | Domaine réel | Destination | Notes |
|---|---|---|---|---|
| files_back/Client.java          | entity     | clients     | citybackend/.../entity/client/Client.java          | OK |
| files_back/ReservationDto.java  | dto        | hebergement | citybackend/.../dto/hebergement/ReservationDto.java | déplacé |
| files_back/CitybackendApplication.java | bootstrap | core   | DOUBLON — ignorer (existe déjà) | conflit |
| files_front/clients.module.ts   | module-ng  | clients     | cityfrontend/src/app/features/clients/             | OK |
| files_front/payment.service.ts  | service-ng | finance     | cityfrontend/src/app/features/finance/services/    | déplacé |
...

## /FINANCE
...

## Synthèse
- Total fichiers analysés : N
- À intégrer dans clients : X
- À intégrer dans hebergement : Y
- ...
- Doublons à ignorer : Z
- Specs (.txt) : W
- Conflits ou ambigus (à arbitrer) : K — listés en bas avec justification

NE TOUCHE À AUCUN fichier. Production READ-ONLY.
```

À la fin du Tour 7.5, l'utilisateur arbitre les fichiers ambigus avant de lancer le Tour 8.

---

## Module CLIENTS

### Tour 8 🔒
```
⚡ /integrate-module clients

Source de vérité du périmètre : section "## /CLIENTS" + ligne "à intégrer
dans clients" de CARTOGRAPHIE_MODULES.md (produit au Tour 7.5).

⚠️ N'intègre QUE les fichiers dont le "Domaine réel" = clients dans la
cartographie. Les fichiers du dossier /CLIENTS/ qui appartiennent à
d'autres modules (hebergement, finance, etc.) seront repris au tour
correspondant. Les fichiers transverses (Application, SecurityConfig,
intercepteurs) seront vérifiés contre l'existant et ignorés s'ils
doublonnent.

Compare avec ce qui existe déjà dans citybackend/ et cityfrontend/.
Détecte les doublons. Intègre dans la bonne arborescence
(entity/client, repository/client, service/client, controller/client
côté back ; features/clients/ côté front). Adapte aux conventions :
- AuditableEntity hérité.
- hotel_id NOT NULL via TenantContext.
- DTOs (record) + MapStruct.
- @PreAuthorize sur chaque endpoint.
- Front : feature module lazy + ngx-translate + DataTables.
Génère le changeset Liquibase XML pour le schéma clients.
```

### Tour 9 ║ PARALLÈLE ║
```
⚡ /audit-module clients   ⏵  🤖 code-auditor
⚡ /multitenant-check citybackend/src/main/java/com/cityprojects/citybackend/{entity,repository,service,controller}/client
⚡ /db-validate
```
(Trois sous-prompts indépendants, peuvent être trois fenêtres ou un seul prompt en série.)

### Tour 10 — Tests + commit
```
🤖 backend-spring : tests unitaires service + controller (Testcontainers
PostgreSQL + WireMock si appel externe).
🤖 frontend-angular : tests Karma/Vitest sur ClientsService + composant
liste.
Puis ⚡ /prep-commit pour le module clients.
```

## Module HEBERGEMENT

### Tour 11 🔒
```
⚡ /integrate-module hebergement

Source de vérité du périmètre : tous les fichiers dont "Domaine réel" =
hebergement dans CARTOGRAPHIE_MODULES.md, peu importe le dossier d'origine
(/HEBERGEMENT/ mais aussi /CLIENTS/, /FINANCE/, /RESTAURANT/ qui peuvent
contenir du code hebergement égaré).

Lis aussi /HEBERGEMENT/endpoints_module_hebergement.txt et
/HEBERGEMENT/entities_services_module_hebergement.java en SPEC seulement
(jamais en source à copier).

Composants prévus : files_back, files_front, COMPONENT_CALENDAR,
COMPONENT_PAY_RESERV — mais inclus aussi tout fichier hebergement
détecté dans d'autres dossiers /MODULE/.

Schéma hebergement : chambres, salles, types, prix, réservations,
nuitées. Côté front : intégrer Calendar + composant paiement réservation.
Changeset Liquibase XML pour schéma hebergement.
```

### Tour 12 — Validation métier
```
🤖 hotel-business : valide les règles métier réservation/nuitée :
- Génération automatique des nuitées entre check-in et check-out.
- Cas no-show (cf. règles_night_audit.txt).
- Tarification par type de chambre + saison + remise.
- Statuts : RESERVEE, CHECKED_IN, CHECKED_OUT, ANNULEE, NO_SHOW.
Liste les écarts entre le code intégré et les règles métier.
```

### Tour 13 — Night Audit scheduler
```
🤖 backend-spring : implémente le night audit (règles_night_audit.txt).
- @Scheduled à midi (Africa/Nouakchott).
- Pour chaque hôtel : repère check-in du jour non honorés → no-show,
  génère nuitées manquantes pour les check-in confirmés.
- Tâche idempotente, log MDC avec hotel_id.
- Endpoint manuel /api/night-audit/run (rôles ADMIN, GERANT).
- Tests unitaires avec mock clock.
```

### Tour 14 ║ PARALLÈLE ║
```
⚡ /audit-module hebergement   ⏵  🤖 code-auditor
⚡ /multitenant-check (sur entity/hebergement, controller/reservation,
                       service/reservation)
⚡ /db-validate
```

### Tour 15 — Tests + commit
```
Tests E2E sur le flux : créer client → réserver chambre → check-in →
nuitée → check-out. Puis ⚡ /prep-commit.
```

## Module INVENTORY

### Tour 16 🔒
```
⚡ /integrate-module inventory

Source de vérité du périmètre : tous les fichiers dont "Domaine réel" =
inventory dans CARTOGRAPHIE_MODULES.md, indépendamment du dossier source.

Lis /INVENTORY/controleurs_module_inventory.java,
endpoints_module_inventory.txt, entities_services_module_inventory.java
en SPEC seulement (jamais en source à copier).

Schéma inventory : produits, catégories, stocks, BC (bon de commande),
BS (bon de sortie), fournisseurs. Numérotation BC-2026-000001 via
NumerotationService. Front : DataTables + formulaires réactifs.
Changeset Liquibase XML.
```

### Tour 17 ║ PARALLÈLE ║
```
⚡ /audit-module inventory     ⏵  🤖 code-auditor
⚡ /multitenant-check
⚡ /db-validate
```

### Tour 18 — Tests + commit
```
Tests : créer fournisseur → BC → réception → BS. Vérifier mise à jour
stock + numérotation séquentielle sans trou. ⚡ /prep-commit.
```

## Module FINANCE 🔴 vigilance comptable max

### Tour 19 🔒
```
⚡ /integrate-module finance

Source de vérité du périmètre : tous les fichiers dont "Domaine réel" =
finance dans CARTOGRAPHIE_MODULES.md, indépendamment du dossier source.
🟠 Attention particulière : du code finance est probablement disséminé
dans /CLIENTS/ (paiements clients), /HEBERGEMENT/ (facturation
réservation, COMPONENT_PAY_RESERV) et /RESTAURANT/ (encaissement POS).

Schéma finance : factures, lignes, paiements, opérations comptables.
- Numérotation FACT-2026-MR-000001 via NumerotationService.
- Plan comptable conforme à plan_comptable_mauritanien.pdf.
- Lignes facture référencent : nuitée OU produit OU service OU menu.
- Paiement lié à facture + 1..n lignes. Modes : Espèces, Chèque,
  Bankily, Carte (modes_paiements.txt).
- Devise MRU. Pas de TVA (cf. prompt_restaurant_pos.txt).
Changeset Liquibase XML.
```

### Tour 20 — Validation métier comptable
```
🤖 hotel-business : valide la conformité au plan comptable mauritanien.
Vérifie qu'à chaque type de ligne (nuitée/produit/service/menu)
correspond le bon compte de produits, et qu'à chaque mode de paiement
correspond le bon compte de trésorerie. Produit un tableau récapitulatif
écart/code → écart/règle.
```

### Tour 21 ║ PARALLÈLE ║
```
⚡ /audit-module finance    ⏵  🤖 code-auditor (vigilance ++)
⚡ /multitenant-check (sur entity/finance, service/finance, controller/finance)
⚡ /db-validate
```

### Tour 22 — Tests + commit
```
Tests : facturation complète (nuitée + produit) → encaissement
multi-paiement → opérations comptables. Vérifier numérotation sans trou
sur 1000 factures concurrentes (Testcontainers). ⚡ /prep-commit.
```

## Module RESTAURANT (le plus volumineux)

### Tour 23 🔒 — Back + front catalogue
```
⚡ /integrate-module restaurant

Source de vérité du périmètre : tous les fichiers dont "Domaine réel" =
restaurant ET sous-domaine = catalogue dans CARTOGRAPHIE_MODULES.md.
N'inclut PAS encore les fichiers marqués sous-domaine = pos.

/RESTAURANT/resultat_chatgpt/ : SPEC uniquement (notes brouillon).
Vérifier que les fichiers libres au niveau du dossier (non rangés en
files_back/files_front) ont bien été classés.

Partie catalogue : menus, plats, catégories, prix.
Changeset Liquibase XML.
```

### Tour 24 🔒 — POS Restaurant
```
🤖 frontend-angular + 🤖 backend-spring : intègre la partie POS.

Source de vérité du périmètre : tous les fichiers dont "Domaine réel" =
restaurant ET sous-domaine = pos dans CARTOGRAPHIE_MODULES.md.
Ces fichiers sont essentiellement dans /RESTAURANT/point-vente/ mais
attention : du code pos peut traîner dans /RESTAURANT/files_front/ ou
/FINANCE/ (encaissement). Croise avec prompt_restaurant_pos.txt en SPEC.

- Front : feature module pos, NgRx Component Store local (état panier,
  table, ticket).
- Back : commande, ligne_commande, ticket, lien vers facture finance.
- Pas de TVA. Modes paiement : Espèces, Bankily, Carte, "à reporter
  sur la chambre" (lien réservation).
```

### Tour 25 ║ PARALLÈLE ║
```
⚡ /audit-module restaurant      ⏵  🤖 code-auditor
⚡ /multitenant-check
⚡ /db-validate
```

### Tour 26 — Tests + commit
```
Tests POS : ouvrir table → ajouter plats → split bill → encaisser →
clôture caisse. ⚡ /prep-commit.
```

## Module MENAGE

### Tour 27 🔒
```
⚡ /integrate-module menage

Source de vérité du périmètre : tous les fichiers dont "Domaine réel" =
menage dans CARTOGRAPHIE_MODULES.md, indépendamment du dossier source.
Le dossier /MENAGE/ contient files_back, files_front, partie_front
(possiblement redondant avec files_front — la cartographie a tranché).

Lis /MENAGE/endpoints_module_menage.txt et
/MENAGE/entities_dto_services_backend-menage.java en SPEC seulement
(jamais en source à copier).

Schéma menage : tâches, planning, personnel, historique. Lien vers
chambres (hebergement). Statuts tâche : PROPRE, SALE, EN_COURS, BLOQUEE.
Changeset Liquibase XML.
```

### Tour 28 — Compléter le front (si manquant)
```
🤖 frontend-angular : si /MENAGE/files_front/ ne couvre pas tout, scaffold
les composants manquants via ⚡ /new-component :
- planning-list (DataTables, filtre par étage/statut)
- tache-detail
- assignation-personnel
i18n via ngx-translate, rôles MENAGE + GERANT.
```

### Tour 29 ║ PARALLÈLE ║
```
⚡ /audit-module menage      ⏵  🤖 code-auditor
⚡ /multitenant-check
⚡ /db-validate
```

### Tour 30 — Tests + commit
```
Tests : générer planning du jour à partir des check-out, marquer
chambre PROPRE après tâche, blocage chambre. ⚡ /prep-commit.
```

---

# PHASE 3 — Modules from-scratch (Vague 3)

> **Tours 31, 32, 33 parallélisables** (modules indépendants).
> **Tour 34 (Dolibarr) doit attendre que finance soit stable.**

## ║ PARALLÈLE ║ — Modules indépendants

### Tour 31 — Module admin
```
🤖 backend-spring + 🤖 frontend-angular : module admin (gestion hôtels,
utilisateurs, rôles, paramètres globaux).
- Back : controller/admin (vide actuellement) à remplir. CRUD Hotel,
  CRUD DBUser, gestion rôles, paramètres app.
- Front : features/admin avec lazy loading. Rôle SUPERADMIN obligatoire.
- ⚡ /new-entity pour les entités manquantes (Parametre, etc.).
Changeset Liquibase XML.
```

### Tour 32 — Module profile (back uniquement)
```
🤖 backend-spring : back du module profile.
- Endpoints : GET /api/profile, PUT /api/profile, PUT /api/profile/password,
  POST /api/profile/avatar.
- Email + photo (chemin disque + URL relative, choix par défaut).
- Vérifie que le composant front profile/profile/profile.component.ts
  existant câble ces endpoints.
```

### Tour 33 — Module reporting
```
🤖 backend-spring + 🤖 frontend-angular : reporting.
- Back : ReportController avec endpoints exports (chiffre d'affaires
  par période, occupation chambres, top produits, etc.).
- Génération PDF via JasperReports (templates dans
  src/main/resources/templates/reports/), Excel via Apache POI.
- Front : features/reporting avec dashboards Chart.js + filtres date,
  bouton export PDF/Excel.
- NgRx pour le state des filtres et dashboards complexes.
- Liquibase XML si tables d'agrégat nécessaires.
```

## Tour 34 — Module notifications
```
🤖 backend-spring : notifications.
- Kafka producer (topic "city-events") via Spring Cloud Stream Kafka.
- Events : RESERVATION_CREATED, FACTURE_EMISE, NIGHT_AUDIT_DONE, etc.
- Email via spring-boot-starter-mail + templates Thymeleaf existants
  (src/main/resources/templates/emails/).
- Endpoint admin /api/notifications/test pour valider la conf.
- Décommissionner Kafka si l'arbitrage a écarté Kafka — sinon docker-compose
  pour les tests intégration.
```

## Tour 35 🔒 — Intégration Dolibarr
```
🤖 dolibarr-integrator : intégration Dolibarr via API REST UNIQUEMENT.
- Pattern <server>/api/index.php/<endpoint> + header DOLAPIKEY.
- Sync sortante : factures, clients, paiements depuis citybackend.
- Sync entrante : plan comptable Dolibarr.
- Resilience4j retry + circuit breaker.
- WireMock pour les tests d'intégration.
- AUCUN code PHP Dolibarr embarqué.
- Skill : 🧩 skill:dolibarr pour scaffolder le client REST.
```

---

# PHASE 4 — Polish & Production

## Tour 36 — Couverture tests intégration
```
🤖 backend-spring + 🤖 code-auditor : audit coverage. Cible CLAUDE.md §7 :
≥ 70 % services, ≥ 50 % controllers. Ajoute Testcontainers PostgreSQL
sur tous les controllers métier. WireMock sur tous les appels Dolibarr.
Rapport de coverage final.
```

## Tour 37 — Security review
```
🧩 skill:security-review
Sur tous les endpoints métier : @PreAuthorize, validation @Valid,
secrets en env var, JWT secret rotation, CORS, headers de sécurité.
Croise avec ERREURS_AUDIT_A_EVITER.html.
```

## Tour 38 — Multi-tenant final
```
🤖 multitenant-guardian : ⚡ /multitenant-check sur l'ensemble
citybackend/src/main/java. Aucune fuite tolérée. Rapport exhaustif.
```

## Tour 39 ║ PARALLÈLE ║ — DevOps
```
║ A ║ Dockerfile + Jib 3.4 (citybackend image) + nginx.conf (cityfrontend).
║ B ║ Jenkinsfile : build + test + sonar + docker push + deploy.
║ C ║ SonarQube : exécution locale + correction des issues bloquantes.
```

## Tour 40 — Optimisation
```
🧩 skill:simplify
Passe sur les modules les plus volumineux (restaurant, hebergement) pour
détecter les duplications, abstractions prématurées, et réduire les
fichiers à >500 lignes.
🧩 skill:fewer-permission-prompts pour réduire les prompts de permission
récurrents en CI.
```

## Tour 41 — Documentation finale
```
🧩 skill:init pour générer / mettre à jour les CLAUDE.md des sous-modules
les plus complexes. Mets à jour le CLAUDE.md racine §4 (état modules)
puisqu'il sera enfin exact.
```

## Tour 42 — Release
```
⚡ /prep-commit final.
Tag git v1.0.0.
🧩 skill:review pour préparer la PR de release.
```

---

# Tableau récapitulatif des parallélisations

| Bloc | Tours parallélisables | Justification |
|---|---|---|
| **Cartographie 7.5** | peut tourner en parallèle de Phase 1A / 1B | read-only sur /MODULE/, n'impacte ni le code back ni le front existant. Doit juste être terminée AVANT le Tour 8. |
| **Phase 1A vs 1B** | tours 2A-7A en parallèle de 2B-7B | back et front indépendants tant qu'aucun feature n'est intégré |
| **Audit par module** | les 3 sous-prompts du tour 9, 14, 17, 21, 25, 29 | audit-module / multitenant-check / db-validate sont read-only et indépendants |
| **Modules from-scratch** | tours 31, 32, 33 | admin, profile, reporting n'ont pas de dépendance directe entre eux |
| **DevOps final** | tour 39 (A, B, C) | Docker, Jenkins, Sonar indépendants |

# Tableau récapitulatif des agents/skills clés

| Étape | Outil principal | Outil secondaire |
|---|---|---|
| Audit stack | `🧩 skill:sync-tech` / `⚡ /sync-tech` | `🤖 backend-spring`, `🤖 frontend-angular` |
| Scaffolding entité | `⚡ /new-entity` | `🤖 backend-spring` |
| Scaffolding composant | `⚡ /new-component` | `🤖 frontend-angular` |
| Intégration code généré | `⚡ /integrate-module` | sous-agent du module |
| Audit qualité | `⚡ /audit-module` + `🧩 skill:audit-module` | `🤖 code-auditor` |
| Audit isolation | `⚡ /multitenant-check` | `🤖 multitenant-guardian` |
| Audit DB | `⚡ /db-validate` | `🤖 db-postgres` |
| Validation métier | `🤖 hotel-business` | — |
| Intégration Dolibarr | `🤖 dolibarr-integrator` + `🧩 skill:dolibarr` | — |
| Lancer back local | `⚡ /run-back` | — |
| Lancer front local | `⚡ /run-front` | — |
| Pré-commit | `⚡ /prep-commit` + `🧩 skill:prep-commit` | — |
| Revue sécurité | `🧩 skill:security-review` | — |
| Revue PR | `🧩 skill:review` | — |
| Refactor / DRY | `🧩 skill:simplify` | — |
| Réduire prompts perms | `🧩 skill:fewer-permission-prompts` | — |
| Init CLAUDE.md | `🧩 skill:init` | — |

---

# Estimation grossière

- **Phase 0** : 1 tour (arbitrage).
- **Phase 1** : ~12 tours (6 backend + 6 frontend, dont parallélisables).
- **Phase 1.5** : 1 tour (cartographie /MODULE/) — parallélisable avec Phase 1.
- **Phase 2** : ~23 tours (6 modules × ~4 tours).
- **Phase 3** : ~5 tours (4 modules + Dolibarr).
- **Phase 4** : ~7 tours (polish/prod).

**Total : ~49 tours** pour atteindre 100 % de complétion, dont **~13 parallélisables** (gain ≈ 26 %).

---

# Règles transverses à appliquer à CHAQUE tour

1. Lire d'abord `CLAUDE.md` racine + sous-CLAUDE.md du module touché.
2. Vérifier `ERREURS_AUDIT_A_EVITER.html` avant tout refactor.
3. `hotel_id` **toujours** depuis `TenantContext`, **jamais** depuis le payload.
4. `@PreAuthorize` obligatoire sur chaque endpoint public.
5. DTO en sortie controller, **jamais** d'entité JPA.
6. i18n côté front : tout libellé via `ngx-translate`.
7. Devise MRU partout, format `Africa/Nouakchott`.
8. `/sync-tech` avant tout commit qui touche pom.xml ou package.json.
9. Pas de big bang : un tour = une responsabilité claire.
10. **Pour tout tour Phase 2** : se référer à `CARTOGRAPHIE_MODULES.md` (produit au Tour 7.5) pour savoir QUELS fichiers du dossier source `/<MODULE>/` appartiennent vraiment au module en cours. Le nom du dossier est trompeur — du code finance dort dans `/CLIENTS/`, du code hebergement dans `/RESTAURANT/`, etc. **Ne JAMAIS copier en bloc un dossier `/MODULE/`** ; toujours sélectionner fichier par fichier selon la cartographie.
11. Les fichiers `.txt`, `endpoints_*.txt`, `entities_services_*.java` et `resultat_chatgpt/*` au niveau racine d'un dossier `/MODULE/` sont des **specs de référence**, jamais du code à copier. Les lire pour comprendre l'intention, pas pour les déplacer.
12. Si un fichier détecté pendant l'intégration n'est **pas** dans `CARTOGRAPHIE_MODULES.md` (apparu après coup, oublié), arrêter et mettre à jour la cartographie d'abord — ne jamais improviser un classement à chaud.
