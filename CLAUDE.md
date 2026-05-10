# City Hotel — Application multi-hôtels (SaaS)

> **Document mémoire de Claude Code** — lu automatiquement à chaque session.
> Ne pas supprimer. Mettre à jour quand l'architecture évolue.

---

## 1. Contexte produit

City Hotel est une application web SaaS de gestion hôtelière **multi-tenant**. Le client initial s'appelle "City Hotel" mais le produit est commercialisé à d'autres hôtels qui s'abonnent comme **membres**. Chaque hôtel dispose de son **propre espace** isolé : ses utilisateurs, ses chambres, ses clients, ses factures, ses stocks.

**Règle d'or absolue** : un utilisateur de l'hôtel A ne doit JAMAIS pouvoir voir, lire ou modifier la moindre donnée de l'hôtel B. L'isolation multi-tenant est la responsabilité numéro 1 de toute nouvelle fonctionnalité.

## 2. Architecture du dépôt

```
/                                       # racine du workspace
├── citybackend/                        # API Spring Boot — voir citybackend/CLAUDE.md
├── cityfrontend/                       # SPA Angular 20 — voir cityfrontend/CLAUDE.md
│
├── CLIENTS/                            # code source BRUT et MÉLANGÉ — voir §2bis
├── FINANCE/                            # idem
├── HEBERGEMENT/                        # idem
├── INVENTORY/                          # idem
├── MENAGE/                             # idem
├── RESTAURANT/                         # idem
│
├── CARTOGRAPHIE_MODULES.md             # mapping fichier source → domaine réel (à produire au Tour 7.5)
│
├── Tech_DevOPS/                        # spécifications techniques et DevOps cibles
│   └── TECHNOLOGIES_DEVOPS_A_UTILISER.md
│
├── PROMPTS/                            # prompts originaux ayant servi à générer le code
│   ├── prompt_inventory.txt
│   ├── prompt_module_clients.txt
│   ├── prompt_module_finance.txt
│   ├── prompt_module_hebergement.txt
│   ├── prompt_menage.txt
│   ├── prompt_restaurant.txt
│   └── prompt_restaurant_pos.txt
│
├── consignes_design_interface_graphique.txt
├── modes_paiements.txt
├── règles_night_audit.txt
├── roles_utilisateurs.txt
├── plan_comptable_mauritanien.pdf
├── ERREURS_AUDIT_A_EVITER.html        # erreurs récurrentes à NE PAS reproduire
├── structure_cityprojectdb*.sql        # snapshot SQL de référence
│
└── .claude/                            # configuration Claude Code (commands, agents)
```

### Sous-fichiers `CLAUDE.md`
- `citybackend/CLAUDE.md` — conventions Spring Boot
- `cityfrontend/CLAUDE.md` — conventions Angular

Claude Code charge automatiquement le `CLAUDE.md` du répertoire courant + parents. Quand tu travailles dans `citybackend/`, les deux fichiers sont actifs.

## 2bis. ⚠️ Vérité capitale sur les dossiers `/CLIENTS`, `/FINANCE`, `/HEBERGEMENT`, `/INVENTORY`, `/MENAGE`, `/RESTAURANT`

**Le nom du dossier n'est PAS une garantie de contenu.** Ces dossiers ont été produits par des sessions de génération hétérogènes (chatbot, ChatGPT, copies manuelles). Le contenu est **brut et mélangé** :

- ✅ Du code du module attendu (cas idéal).
- 🟡 Du code qui appartient en réalité à **un autre module** (ex. une entité `Reservation` dans `/CLIENTS/`, un service de paiement dans `/RESTAURANT/`, un calendrier dans `/CLIENTS/`).
- 🟡 Du code **transverse / technique** (Application, SecurityConfig, intercepteurs HTTP, mappers partagés, helpers i18n) à router vers `common/`, `core/`, `config/`, `shared/`.
- 🟡 Des fragments de **modules from-scratch** (admin, profile, reporting) glissés là par erreur.
- 🟡 Des fichiers **specs** : `endpoints_*.txt`, `entities_services_*.java`, `models_services_*.ts`, `resultat_chatgpt/*` — à lire en référence, **jamais** à copier comme code.
- ❌ Du code obsolète, des doublons de plusieurs versions, du brouillon.

### Conséquences opératoires (NON NÉGOCIABLES)

- ⛔ **Ne JAMAIS** copier en bloc `/<MODULE>/files_back/` → `citybackend/.../<module>/`, ni `/<MODULE>/files_front/` → `cityfrontend/.../<module>/`.
- ✅ **Toujours** classer fichier par fichier avant d'intégrer.
- ✅ Avant la première intégration, produire `CARTOGRAPHIE_MODULES.md` (Tour 7.5 du `PROMPTS_TOURS.md`) qui mappe chaque fichier source → **domaine réel** + **destination finale**.
- ✅ Lors d'un `/integrate-module <X>` : n'intégrer **que** les fichiers dont `Domaine réel = <X>` dans la cartographie, **peu importe** le dossier d'origine. Du code finance trouvé dans `/CLIENTS/` sera intégré quand on traitera `finance`, pas avant.
- ✅ Le seul code "implémenté" du projet est celui qui se trouve dans `citybackend/` et `cityfrontend/`. Les dossiers `/MODULE/` à la racine sont du **stock de matière première**, pas le projet lui-même.
- ✅ Si un fichier détecté pendant une intégration n'apparaît pas dans la cartographie, **arrêter** et mettre à jour la cartographie d'abord — jamais d'improvisation à chaud.

## 3. Stack technique cible (mai 2026 — dernières stables)

> **Doctrine "pas de rétrogradation"** : `Tech_DevOPS/TECHNOLOGIES_DEVOPS_A_UTILISER.md` est issu d'un autre projet et liste des versions anciennes (Angular 12, Spring Boot 2, Java 8, Gradle 5). Ce document sert de **catalogue de catégories** uniquement (quelles libs utiliser pour quel besoin). Pour les **versions**, on s'aligne **toujours sur la dernière stable** documentée ici. La slash command `/sync-tech` détaille l'audit et applique cette doctrine.

> **🎯 Stratégie en deux paliers** (arbitrage Tour 1, 2026-05-05) :
> - **Palier 1 — actuel** : Java 21 LTS + Spring Boot 3.4.5 + Spring Cloud 2024.0.1 + Angular 21.2. Stack stable, écosystème city certifié (jjwt, MapStruct, Resilience4j, Liquibase, PG JDBC). Boot 3.4 supporté OSS jusqu'à fin 2026.
> - **Palier 2 — Q4 2026** : Java 25 LTS + Spring Boot 4.0.x + Spring Cloud 2025.0.x. À déclencher quand Resilience4j publiera l'artifact `spring-boot4`, JasperReports 7 sera stabilisé et MapStruct 1.7 sera GA. Migration prévue **avant** intégration finance/restaurant POS.
>
> ⚠️ Le palier 1 n'autorise **aucune** rétrogradation sous Java 21, Spring Boot 3.4, Spring Cloud 2024.0.1, Angular 21.2, Node 22 LTS, PostgreSQL 16. Les valeurs ci-dessous sont les **planchers** ; toute lib doit viser sa dernière patch compatible.

### Backend (`citybackend/`) — palier 1
- **Java 21** LTS (sept 2023) — palier 2 visera Java 25 LTS. **Jamais** Java 17 ou inférieur.
- **Spring Boot 3.4.5** — Spring Framework 6.2, Spring Security 6.4, Hibernate 6.6, Tomcat 10.1, Jakarta EE 10. Starters : Web, Data JPA, Security, Mail, Thymeleaf, Validation, Actuator.
- Spring Cloud **2024.0.1** ("Moorgate") BOM pour OpenFeign + Stream Kafka.
- **PostgreSQL 18.3** (JDBC `42.7.x`).
- **Liquibase 4.30+** pour les migrations XML.
- Auth stateless : Spring Security OAuth2 Resource Server (préféré) **ou** `jjwt 0.12.x`.
- **MapStruct 1.6.3** pour les mappers, **Lombok 1.18.34** pour le boilerplate, **jjwt 0.12.6**.
- **JasperReports 6.21.3** (v7 reportée palier 2) + **OpenPDF 2.0.3** + **Apache POI 5.3.0** pour exports.
- **Resilience4j 2.2.0** (artifact `resilience4j-spring-boot3`) — retry/circuit breaker pour Dolibarr.
- **WireMock 3.9.1** (groupId `org.wiremock`) + **Testcontainers 1.20.3** pour les tests d'intégration.
- Build : **Maven 3.9.x ou 4.0.x** (`./mvnw`), `maven-compiler-plugin 3.13.0` avec `<release>21</release>`. Image Docker via **Jib 3.4.4**.

### Frontend (`cityfrontend/`) — palier 1
- **Angular 21.2.x** (TypeScript **5.8**, ou 5.9 avec Angular 22 dès release stable). **Pas Angular 20 ou inférieur.**
- **NgModule** (non standalone) pour cohérence avec l'existant. Schematic `ng update` ajoute `standalone: false` explicite.
- **Node.js 24 LTS** (Krypton, Active LTS). Acceptable : 22 LTS (Maintenance).
- **RxJS 7.8+**, **zone.js 0.15+** (peerDep obligatoire Angular 21 ; préparer le passage zoneless mais ne pas l'activer — casserait SweetAlert2 + jQuery/DataTables).
- UI : **Tailwind v4.1 unifié** (`tailwindcss@4.1.x` + `@tailwindcss/postcss@4.1.x` ensemble en `dependencies` ; pas de mix v3/v4) + **Bootstrap 5.3** + **jQuery 3.7** + **DataTables 2.1+**.
- **NgRx 21.x** pour le state complexe (POS, reporting).
- **@ngx-translate/core 16.0.x** (16.0.4 stable) + http-loader 16.0.x (16.0.1 stable — la 16.1.x n'est qu'en RC) — i18n **arabe / français / anglais**. Compat Angular 20 → peut être ajouté **avant** la migration vers 21.
- SweetAlert2 11.14+, Chart.js 4.5+ (sans `@types/chart.js` qui est obsolète), jsPDF 3.x + jspdf-autotable 5.x, **date-fns 4.x** (pas moment.js).
- Tests : Karma + Jasmine **maintenus** en palier 1 (toujours supportés Angular 21.2). Migration vers **Vitest** différable au palier 2.

> **Migration front Angular 20 → 21** : recommandée **après** intégration de la Vague 2 (clients + inventory + finance) pour éviter de cumuler régressions UI et bugs métier. Bloquants pré-Vague 1 (compatibles Angular 20) : (1) résoudre l'incohérence Tailwind v3/v4, (2) ajouter ngx-translate, (3) supprimer `@types/chart.js`.

### Infra & DevOps
- **Keycloak 26.x** (Quarkus, pas Wildfly) si Keycloak retenu — sinon auth maison JWT.
- **Docker** + **Jib 3.4+** (build d'images sans démon).
- **Apache Kafka 3.8+** pour les events asynchrones.
- **Jenkins LTS** (`Jenkinsfile`) pour la CI/CD.
- **SonarQube** pour la qualité.

> Pour **toute** introduction ou montée de version, lancer `/sync-tech` avant le commit. Les régressions (proposer Java 17, Spring Boot 3.3, Angular 19, etc.) sont **refusées par défaut**.

## 4. État des modules

> ⚠️ **Distinction importante** :
> - **"Code source disponible"** = des fichiers existent dans `/CLIENTS/`, `/FINANCE/`, etc. à la racine. Cela ne signifie **pas** que le module est implémenté (cf. §2bis : ces dossiers contiennent du code mélangé).
> - **"Implémenté"** = le code est intégré dans `citybackend/` et `cityfrontend/` selon les conventions, après cartographie et aiguillage.

État réel — **mis à jour Tour 41 (documentation finale, 2026-05-10)**. Tableau initial (démarrage 2026-05-05) conservé dans l'historique git pour référence ; ci-dessous =
   état post-Vague 1 et Vague 2 livrées.

  | Module       | Backend | Frontend | Tour intégration | État | Notes |
  |--------------|---------|----------|------------------|------|-------|
  | core / auth  | ✅      | ✅       | Tours 1-7        | livré | JWT jjwt 0.12.6, refresh token rotation (Tour 38), `RateLimitFilter` Resilience4j, multi-tenant Hibernate
   `@TenantId` |
  | clients      | ✅      | ✅       | Tour 8 + 9bis    | livré | Client + Societe, NIF unique, audit Tour 9 |
  | hebergement  | ✅      | ✅       | Tours 11/12bis/12ter/13/15 | livré | Chambres + Reservations + Nuitees + `NumerotationService` (lock pessimiste) +
  `NightAuditScheduler` cron midi + triggers PL/pgSQL pivot tenant |
  | inventory    | ✅      | ✅       | Tours 16/18      | livré | Produits + Stock + BC + BS + Fournisseurs + alertes seuil |
  | finance      | ✅      | ✅       | Tours 19/20/22   | livré | Facture + Paiement + Compte auxiliaire client + 12 modes paiement (Bankily/MASRIVI/SEDAD/etc.).
  **Doctrine Tour 20** : auxiliaire client uniquement, comptabilité générale externalisée Dolibarr (TODO bridge Feign) |
  | restaurant   | ✅      | ✅       | Tours 23/24/25/25bis/26 | livré | Catalogue + POS NgRx Component Store + Recettes auto-décrément stock + 4 triggers PL/pgSQL
  coherence cross-tenant |
  | menage       | ✅      | ✅       | Tours 27/28/29/30/30events/31 | livré | Personnel + Tache (workflow PLANIFIEE→EN_COURS→TERMINEE/ANNULEE + `@Version` optimistic
  locking) + Planning + Historique + AOP `@AuditAction` (Tour 30 hardening) + workflows event-driven Spring Events (Tour 30 events) + `MenagePlanningScheduler` cron 12:05 |
  | admin        | ✅      | ✅       | Tour 31          | livré | SUPERADMIN-only. CRUD Hotel + DBUser (mode ROOT via `TenantScope.runAs`) + Role read-only + Parametre
  globaux. Endpoint exception : `POST /api/admin/hotels/{hotelId}/users` (hotelId path-positioned, seule exception au principe §10) |
  | reporting    | ❌      | ❌       | —                | Vague 3 | from-scratch — Chart.js + JasperReports + KPIs métier |
  | profile      | ❌      | 🟡 composant front seul | — | Vague 3 | back from-scratch (changement mdp + photo + préférences) |
  | notification | ❌      | ❌       | —                | Vague 3 | from-scratch — Mail Thymeleaf + Kafka mode dégradé Spring Events |
  | dolibarr     | ❌      | —        | —                | Vague 3 | scaffolder via skill `dolibarr` — Feign + Resilience4j (déjà au pom) + bridge Facture/Paiement →
  Dolibarr |

  **Tests** : 147 Surefire + 62 Failsafe verts (BUILD SUCCESS) — couvre 8 modules + multi-tenant + sécurité Tour 38.

  **Refactor Tour 40bis** : `ReservationServiceImpl.create` 130→17 lignes (5 helpers private), `ChambreServiceImpl.checkTransition` switch→Map<>,
  `CommandeServiceImpl.onTransitionToServie` 53→20 lignes, helpers `SecurityUtils.currentUserId*` partagés.

  **Centralisation Tour 40ter** : `cityfrontend/src/app/shared/models/api.model.ts` (superset ApiResponse/PageResponse/PageRequest), `STATUT_RESERVATION_BADGE_MAP` +
  `_CHIP_MAP` lookup au lieu de switch.

  > **Cartographie historique** : `CARTOGRAPHIE_MODULES.md` à la racine — référence pour comprendre quel code source brut a alimenté quel module. Plus utile maintenant que
  tous les modules sont intégrés.

  > **Pour ajouter un nouveau module** :
  > 1. Lancer `/new-entity <nom> <module>` (scaffold) ou `/integrate-module <nom>` si stock brut.
  > 2. Auditer (`/audit-module`, `/multitenant-check`, `/db-validate`) avant tout commit.
  > 3. Tests Surefire + Failsafe doivent rester verts.

## 5. Schémas PostgreSQL

```
core         — DBUsers, Roles, Hotels, paramètres globaux
clients      — clients, sociétés, comptes
hebergement  — chambres, salles, types, prix, réservations, nuitées
inventory    — produits, catégories, stocks, BC, BS, fournisseurs
restaurant   — menus, plats, commandes POS
finance      — factures, lignes, paiements, opérations comptables
menage       — tâches, planning, personnel, historique
```

Table utilisateurs = **`DBUsers`** (jamais `users` — conflit mot-clé SQL).

## 6. Règles métier critiques

### 6.1 Multi-tenant (NON NÉGOCIABLE) — architecture en place depuis Tour 3B (2026-05-06)

**Pattern retenu** : multi-tenancy DISCRIMINATOR natif Hibernate 6 via `@TenantId` + `CurrentTenantIdentifierResolver` (PAS `@Filter`/`@FilterDef`, abandonné au Tour 3B Alt-NEW-6).

**Composants en place** (`com.cityprojects.citybackend.common.tenant` + `common.audit`) :
- `TenantContext` — ThreadLocal<Long> set/get/clear avec contrats stricts. Alimenté par `JwtAuthenticationFilter` à partir du JWT, **jamais** depuis le payload de la requête. Nettoyé en `finally` à chaque requête.
- `TenantAware` — interface marqueur (`getHotelId/setHotelId`) sur les futures entités tenant.
- `CityTenantIdentifierResolver` — résout le tenant via `TenantContext.getOrNull()`. **Sentinel `ROOT = 0L`** retourné si pas de tenant : Hibernate bypass alors le filtre (mode global pour boot/admin/scheduler/batch).
- `TenantHibernatePropertiesCustomizer` — active `hibernate.multiTenancy = DISCRIMINATOR` + injecte le resolver.
- `@RequireTenant` + `RequireTenantAspect` — **garde** AOP qui lève `IllegalStateException("error.tenant.missing")` si `TenantContext` vide. À annoter sur tous les services/controllers métier (hors auth, admin, hotel-management, scheduler, batch).
- `AuditableEntity` (`@MappedSuperclass`) + `JpaAuditingConfig` (`@EnableJpaAuditing` + `AuditorAware<String>` username Spring Security ou `"system"`).

**Convention pour TOUTE nouvelle entité tenant** :

```java
@Entity
@Table(name = "bon_commande", schema = "inventory")
public class BonCommande extends AuditableEntity implements TenantAware {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "hotel_id", nullable = false, updatable = false)
    private Long hotelId;
    // ... reste de l'entité
}
```

**Règles d'or NON NÉGOCIABLES** :
- `hotel_id` est **populé automatiquement par Hibernate** au moment de l'INSERT depuis le resolver — ne **jamais** le `setHotelId(...)` manuellement dans un service métier.
- Liquibase : pour chaque table tenant, ajouter `CHECK (hotel_id > 0)` — `0` est réservé au sentinel `ROOT`, jamais une valeur métier.
- Tout service / controller métier porte `@RequireTenant` au niveau classe (sauf services techniques : auth, admin, schedulers, batchs).
- Tout endpoint qui retourne une entité hôtel-scopée DOIT vérifier l'appartenance avant de répondre (sinon 403).
- Pas d'`@Filter`/`@FilterDef` dans les nouvelles entités — utiliser `@TenantId` exclusivement.
- L'agent `multitenant-guardian` audite ce point — `/multitenant-check` à exécuter sur chaque PR.

### 6.2 Comptabilité & finance

> **🎯 Doctrine actée Tour 20 (2026-05-07)** : **City Hotel = comptabilité auxiliaire client uniquement**. La **comptabilité générale** (partie double SYSCOHADA, classes 1-9, balances, bilan, compte de résultat, journaux, FEC, exercices clôturés) est **externalisée vers Dolibarr** via bridge Feign REST. Le code backend `entity/finance/Compte` et `entity/finance/OperationCompte` représente **uniquement** des comptes auxiliaires CLIENT/SOCIETE (suivi de la dette client par tenant) — **PAS** des comptes du Plan Comptable Général. Marqués `@Deprecated` (forRemoval=false) le 2026-05-07 pour signaler l'ambiguïté du nom ; à renommer en `CompteClient`/`MouvementCompteClient` lors d'un tour de cleanup ultérieur.

#### Périmètre City Hotel (auxiliaire client)
- Numérotation des factures : séquentielle **par hôtel et par exercice**, formatée (ex. `FACT-2026-MR-000123`). Jamais de trou. Lock pessimiste sur `NumerotationSequence` (Tour 6A).
- Une **facture** a des **lignes** ; chaque ligne référence soit une nuitée, soit un produit, soit un service, soit une commande POS restaurant (TypeLigneFacture : `NUITEE, PRODUIT, COMMANDE, SERVICE, DIVERS`).
- Un **paiement** est lié à une facture **et** à une (ou plusieurs) ligne(s) via `AffectationPaiement`. Modes : `ESPECES, CHEQUE, BANKILY, CARTE_BANCAIRE, MASRIVI, SEDAD, CLICK, AMANETY, BFI_CASH, MOOV_MONEY, GAZAPAY, VIREMENT` (cf. `modes_paiements.txt`).
- Statuts facture : `BROUILLON → EMISE → PARTIELLEMENT_PAYEE → PAYEE` ou `ANNULEE`. Pas de delete physique.
- Devise : **MRU (ouguiya)**. Pas de TVA dans la version POS actuelle (cf. `prompt_restaurant_pos.txt`) — colonne `taux_tva` à 0 par défaut, prête pour évolution future.

#### Périmètre Dolibarr (comptabilité générale — bridge à venir)
- **Intégration Dolibarr** : exclusivement via API REST (pattern `<server>/api/index.php/<endpoint>` + header `DOLAPIKEY`). Aucun code PHP Dolibarr embarqué. Voir agent `dolibarr-integrator`.
- Mapping attendu (à implémenter dans un tour bridge dédié) :
  - `Facture` City Hotel → écriture Dolibarr (Débit `4111x` Client / Crédit `7062x` Hébergement, `7074x` Restauration, etc.)
  - `Paiement` City Hotel → écriture Dolibarr (Débit `571` Caisse / `521` Banque / `518x` Mobile Money / Crédit `4111x` Client)
  - `Avoir` City Hotel → écriture inverse
- **Plan comptable** : géré côté Dolibarr (paramétrage SYSCOHADA / `plan_comptable_mauritanien.pdf`). City Hotel ne tient PAS le PCG en local.
- **Balance, bilan, compte de résultat, FEC** : produits par Dolibarr.
- **Tenue continue conforme Article 14 OHADA** : assurée par Dolibarr.
- **Multi-tenant Dolibarr** : 1 instance Dolibarr par hôtel client OU 1 instance partagée avec ventilation analytique par hôtel — décision produit à acter au tour bridge.

#### TODO bridge Dolibarr (Tour ultérieur)
- Implémentation `DolibarrFeignClient` (Spring Cloud OpenFeign + Resilience4j déjà au pom Tour 4A)
- `DolibarrSyncService` qui pousse Facture/Paiement vers Dolibarr de façon idempotente
- Table `ParametrageComptable(hotel_id, type_ligne, mode_paiement, compte_pcg)` pour mapping configurable
- Statut sync (`PENDING`, `SYNCED`, `FAILED`) sur Facture/Paiement avec retry
- Voir audit Tour 20 (5 🔴 + 6 🟠 + 5 💡) pour le détail des écarts identifiés et le plan de mitigation

### 6.3 Rôles
Voir `roles_utilisateurs.txt`. Rôles connus : `SUPERADMIN`, `ADMIN`, `GERANT`, `RECEPTION`, `RESTAURANT`, `RESREC`, `MAGASIN`, `MENAGE`, `NIGHTAUDIT`. Sécuriser chaque endpoint avec `@PreAuthorize("hasAnyRole(...)")`.

### 6.4 Night Audit
Voir `règles_night_audit.txt`. Tâche planifiée à **midi** qui contrôle les check-in du jour, identifie les no-show, génère les nuitées manquantes.

### 6.5 Modes de paiement
Voir `modes_paiements.txt`. Au minimum : Espèces, Chèque, **Bankily** (mobile money), Carte bancaire.

### 6.6 i18n
Trois langues : `fr` (par défaut), `ar` (RTL), `en`. Tout libellé visible passe par `@ngx-translate`. Côté backend, les messages d'erreur retournent une **clé** (`error.client.notFound`) que le front traduit.

## 7. Conventions de nommage

| Type                | Convention                                    | Exemple                              |
|---------------------|-----------------------------------------------|--------------------------------------|
| Package Java        | `com.cityprojects.<module>.<layer>`           | `com.cityprojects.inventory.service` |
| Entité JPA          | PascalCase, nom métier                        | `BonCommande`                        |
| Table SQL           | snake_case, schéma préfixé                    | `inventory.bon_commande`             |
| Repository          | `<Entity>Repository`                          | `BonCommandeRepository`              |
| Service             | `<Entity>Service` + `<Entity>ServiceImpl`     | `BonCommandeService`                 |
| Controller          | `<Entity>Controller`, route `/api/<plural>`   | `/api/bons-commande`                 |
| DTO                 | `<Entity>Dto`, `<Entity>CreateDto`            | `BonCommandeDto`                     |
| Mapper MapStruct    | `<Entity>Mapper`                              | `BonCommandeMapper`                  |
| Component Angular   | kebab-case, dossier par feature               | `bon-commande-list/`                 |
| Service Angular     | `<Feature>Service`                            | `BonCommandeService`                 |
| Interface TS        | PascalCase, **pas** de préfixe `I`            | `BonCommande`                        |

## 8. Commandes courantes

```bash
# Backend
cd citybackend && ./mvnw spring-boot:run                 # démarrer (port 8080, ctx /citybackend)
cd citybackend && ./mvnw test                            # tests
cd citybackend && ./mvnw clean package -DskipTests       # build jar

# Frontend
cd cityfrontend && npm install                           # installer
cd cityfrontend && npm start                             # ng serve port 4200
cd cityfrontend && npm run build                         # build prod
cd cityfrontend && npm test                              # karma

# Base de données
psql -U postgres -d cityprojectdb -f structure_cityprojectdb*.sql

# Slash commands Claude Code (voir .claude/commands/)
/integrate-module <nom>      # intégrer le code de /<MODULE>/
/audit-module <nom>          # audit selon ERREURS_AUDIT_A_EVITER.html
/multitenant-check <fichier> # vérifier l'isolation hôtel
/new-entity <nom> <module>   # scaffolder entité + repo + service + controller + DTO
/new-component <nom> <mod>   # scaffolder component Angular
/sync-tech                   # vérifier conformité avec Tech_DevOPS/
```

## 9. Workflow recommandé

1. **Toujours lire** ce `CLAUDE.md` + le `CLAUDE.md` du sous-projet concerné avant de coder. Lire en particulier le §2bis sur les dossiers `/MODULE/`.
2. **Avant la toute première intégration** d'un module métier : produire `CARTOGRAPHIE_MODULES.md` (Tour 7.5 du `PROMPTS_TOURS.md`). Sans cette cartographie, aucune intégration n'est autorisée.
3. Pour intégrer un module : lancer `/integrate-module <nom>` qui consulte la cartographie et intègre **uniquement** les fichiers dont `Domaine réel = <nom>`, peu importe leur dossier source. **Ne jamais copier en bloc `/<MODULE>/files_back/` ou `/<MODULE>/files_front/`.**
4. Lire `ERREURS_AUDIT_A_EVITER.html` au début d'une session de refactoring — y revenir avant chaque commit.
5. Pour toute nouvelle fonctionnalité : déléguer au sous-agent approprié (`backend-spring`, `frontend-angular`, `db-postgres`, etc.) via Claude Code.
6. Avant commit : `/audit-module <nom>` + `/multitenant-check` + tests verts + `/prep-commit`.

## 10. Anti-patterns interdits

- ❌ Récupérer le `hotel_id` depuis un paramètre client (query, body, header). Toujours depuis le JWT.
- ❌ Endpoint sans `@PreAuthorize`.
- ❌ Reproduire du code business dans plusieurs services au lieu d'extraire une dépendance.
- ❌ Renvoyer une entité JPA directement depuis un controller (toujours via DTO).
- ❌ `findAll()` sans filtre tenant.
- ❌ Stocker des secrets en clair dans `application.yml` (utiliser `${ENV_VAR}`).
- ❌ Manipuler le DOM avec jQuery quand un mécanisme Angular natif fait l'affaire (le mix Angular + jQuery doit rester limité aux libs externes : DataTables, etc.).
- ❌ Désactiver `ddl-auto: validate` en prod ou utiliser `create-drop` ailleurs qu'en dev/test.
- ❌ Cumuler plusieurs versions majeures d'une même lib (Bootstrap, FontAwesome, etc.).
- ❌ **Copier en bloc `/<MODULE>/files_back/` vers `citybackend/.../<module>/`** ou `/<MODULE>/files_front/` vers `cityfrontend/...features/<module>/`. Le dossier source n'est PAS la source de vérité du périmètre — la cartographie l'est.
- ❌ **Considérer un dossier `/MODULE/` à la racine comme une "implémentation"**. C'est du stock brut. L'implémentation vit dans `citybackend/` et `cityfrontend/`.
- ❌ **Copier comme du code** un fichier `endpoints_*.txt`, `entities_services_*.java`, `models_services_*.ts` ou `resultat_chatgpt/*` — ce sont des specs, pas du code.
- ❌ **Improviser un classement** quand un fichier source n'apparaît pas dans `CARTOGRAPHIE_MODULES.md`. Mettre à jour la cartographie d'abord.

## 11. Notes spécifiques

- Identité graphique : couleur principale = **bleu clair** (proche de `bg-info` Bootstrap), fonds **blancs/gris**. Header avec logo + nom hôtel à gauche, profil à droite. Voir `consignes_design_interface_graphique.txt`.
- Logos d'hôtel et avatars utilisateurs : stockés en base ou sur disque ? À trancher — par défaut chemin disque + URL relative.
- Sessions : 80 simultanées max (cf. `application.yml`), 3 sessions/user.
- Timezone serveur : `Africa/Nouakchott` (déjà config).

---

**Quand tu introduis une nouveauté structurelle (lib, pattern, table, module), mets ce fichier à jour dans le même commit.**
