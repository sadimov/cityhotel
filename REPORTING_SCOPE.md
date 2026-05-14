# REPORTING_SCOPE.md — Catalogue des rapports city et roadmap

> Document produit au Tour 40 (2026-05-13). Source de vérité pour le périmètre
> du module `reporting` côté citybackend. **Read-only** : tout rapport est
> recalculé à la volée via des projections JPA sur les entités existantes —
> pas d'entités JPA persistées dans le schéma `reporting`.

---

## 1. Résumé exécutif

- **20 rapports** au total, répartis sur **7 domaines métier** (hebergement, finance, clients, inventory, restaurant POS, ménage, cross-module).
- Priorisation : **5 P0** (MVP Tour 40, ~6.5 j) + **10 P1** (Vague 3 finition, ~12 j) + **5 P2** (Vague 4 et au-delà, ~5 j).
- **Effort total estimé** : ~23.5 jours-homme côté backend, hors développement frontend Angular (Tour 47+).
- Stack : **JasperReports 6.21.3** (PDF) + **Apache POI 5.3.0** (XLSX) + **Spring Cache** (`@Cacheable`, ConcurrentMapCacheManager en palier 1).
- **Aucun changeset Liquibase** ajouté : le module est read-only via projections sur entités existantes.

---

## 2. Architecture cible

### 2.1 Doctrine read-only

- **Pas d'entités JPA `reporting.*`** persistées. Les tables `reporting.alertes` et `reporting.dashboard_financier` du snapshot historique sont **abandonnées** — recalcul à la volée à chaque appel.
- Les services reporting s'appuient sur des **projections Spring Data** (interface-based) + `@Query` JPQL sur les entités métier (`Facture`, `Reservation`, `Nuitee`, `Produit`, `Paiement`, etc.).
- **Hibernate applique automatiquement le filtre `@TenantId`** sur les entités sous-jacentes → aucun `WHERE hotel_id = ?` explicite à ajouter, aucun `hotelId` en paramètre des `@Query`.
- Les services portent `@RequireTenant` au niveau classe + `@Transactional(readOnly = true)`.

### 2.2 Pas de vues SQL en palier 1

Les vues SQL (`CREATE VIEW reporting.xxx`) sont **interdites en palier 1** car elles bypassent le filtre `@TenantId` Hibernate. Risque de fuite cross-tenant grave.

Évaluation possible en palier 2 via **PostgreSQL Row Level Security (RLS)** + `current_setting('app.current_hotel_id')` — décision à prendre si les agrégats sur très gros volumes deviennent un goulet de performance.

### 2.3 Cache

`spring-boot-starter-cache` déjà au pom. À activer au Tour 41 :

```java
@Cacheable(value = "ca-recap", key = "T(...TenantContext).get() + '-' + #from + '-' + #to")
```

- TTL court (5 minutes) via ConcurrentMapCacheManager. Pas de Redis externe en palier 1.
- La clé inclut **systématiquement** `TenantContext.get()` pour éviter le cache poisoning cross-tenant.

### 2.4 Exports binaires

- **PDF** via JasperReports : templates `.jrxml` sous `src/main/resources/reports/`. Service `PdfExportServiceImpl` cache les templates compilés via `ConcurrentHashMap`.
- **XLSX** via POI : service `XlsxExportServiceImpl` générique acceptant `List<T>` + métadonnées `ColumnSpec<T>` (titre, accesseur, format).
- Le `ApiResponseBodyAdvice` global wrap les responses JSON en `{success, message, data: ...}` — il a été modifié au Tour 40 pour **bypasser les `byte[]`/`Resource`/content-types non-JSON** afin de retourner les flux binaires bruts.

### 2.5 Contrôleur unique

`ReportController` sous `/api/reports` expose **2 endpoints par rapport** : un endpoint JSON pour le dashboard front, un endpoint export PDF ou XLSX. Pas de duplication par module — la sécurité est portée par `@PreAuthorize` granulaire par méthode.

### 2.6 Pagination & filtres

- Toute liste paginable expose `?page=&size=&sort=` (Spring Data `Pageable`).
- Les rapports d'agrégat (`CARecapDto`, `OccupationDto`) retournent une structure dénormalisée sans pagination — la période sert de filtre suffisant.
- Validation `@Valid` sur les DTOs `DateRangeRequest` (et 400 explicite si `from > to`).

---

## 3. Catalogue détaillé des 20 rapports

### 3.1 Hebergement (5 rapports)

| Code | Titre | Question métier | Sources | Périodicité | Sortie | Prio | Effort |
|---|---|---|---|---|---|---|---|
| **R-HEB-001** | Occupation chambres | Combien de chambres occupées, par type, pour la période choisie ? | `nuitees + chambres + types_chambres` | jour/sem/mois/an | JSON+PDF | **P0** | 2 j |
| R-HEB-002 | Durée moyenne séjour (ALOS) | Quelle est la durée moyenne de séjour par type chambre / source / mois ? | `reservations + nuitees` | mois | JSON | P1 | 1 j |
| R-HEB-003 | No-show rate | Quel % de réservations confirmées ne se présentent pas ? | `reservations + nuitees` | mois | JSON | P1 | 0.5 j |
| R-HEB-004 | Source de réservation | Quelle est la répartition des résa par canal (direct, OTA, agence) ? | `reservations.source` (col à ajouter) | mois | JSON+XLSX | P2 | 1 j |
| R-HEB-005 | KPI réception | Nb check-in, check-out, walk-in, durée moyenne, taux d'occupation jour | `reservations + nuitees` | jour | JSON | P1 | 1 j |

### 3.2 Finance (4 rapports)

| Code | Titre | Question métier | Sources | Périodicité | Sortie | Prio | Effort |
|---|---|---|---|---|---|---|---|
| **R-FIN-001** | CA agrégé | Quel CA (factures émises et paiements reçus) sur la période ? | `factures + paiements` | sem/mois | JSON+XLSX | **P0** | 1.5 j |
| R-FIN-002 | Encours clients (dettes) | Qui doit combien sur quelle ancienneté (0-30, 30-60, 60-90, 90+) ? | `factures + clients` | temps réel | JSON+XLSX | P1 | 1 j |
| R-FIN-003 | TVA collectée | Quelle TVA collectée sur la période ? (placeholder — réactivation TVA POS) | `lignes_factures` | mois | JSON+XLSX | P2 | 0.5 j |
| R-FIN-004 | Top sociétés par CA | Quelles sociétés génèrent le plus de revenus ? | `factures + societes` | mois/an | JSON+XLSX | P1 | 1 j |

### 3.3 Clients (1 rapport)

| Code | Titre | Question métier | Sources | Périodicité | Sortie | Prio | Effort |
|---|---|---|---|---|---|---|---|
| **R-CLI-001** | Top 10 clients | Quels sont les 10 clients qui ont le plus dépensé sur la période ? | `factures + lignes_factures + clients` | mois/an | JSON+XLSX | **P0** | 1 j |

### 3.4 Inventory (3 rapports)

| Code | Titre | Question métier | Sources | Périodicité | Sortie | Prio | Effort |
|---|---|---|---|---|---|---|---|
| **R-INV-001** | Alertes stock | Quels produits sont sous le seuil d'alerte ? | `produits` | temps réel | JSON+XLSX | **P0** | 1 j |
| R-INV-002 | Mouvements valorisés | Quelle est la valeur (entrées/sorties) des mouvements sur la période ? | `mouvements_stock + produits` | mois | JSON+XLSX | P1 | 1 j |
| R-INV-003 | BC pendants + rotation | Quels BC non livrés, et quelle rotation produit ? | `bons_commande + lignes` | temps réel | JSON+XLSX | P1 | 1.5 j |

### 3.5 Restaurant POS (3 rapports)

| Code | Titre | Question métier | Sources | Périodicité | Sortie | Prio | Effort |
|---|---|---|---|---|---|---|---|
| R-RES-001 | Journal caisse jour | Toutes les opérations caisse de la journée | `commandes + paiements POS` | jour | JSON+PDF | P1 | 1 j |
| R-RES-002 | Top articles vendus | Quels articles génèrent le plus de revenus ? | `lignes_commandes + articles_menu` | mois | JSON+XLSX | P1 | 1 j |
| R-RES-003 | Ticket moyen + marge | Quel ticket moyen ? Quelle marge par article ? | `commandes + recettes_articles` | mois | JSON+XLSX | P2 | 1.5 j |

### 3.6 Ménage (2 rapports)

| Code | Titre | Question métier | Sources | Périodicité | Sortie | Prio | Effort |
|---|---|---|---|---|---|---|---|
| R-MEN-001 | Récap tâches | Combien de tâches planifiées vs réalisées, retards ? | `taches + planning` | jour/sem | JSON+XLSX | P1 | 1 j |
| R-MEN-002 | Charge par femme de chambre | Combien de chambres nettoyées par agent, sur quelle durée moyenne ? | `taches + personnel` | mois | JSON+XLSX | P2 | 1 j |

### 3.7 Night audit + Cross-module (2 rapports)

| Code | Titre | Question métier | Sources | Périodicité | Sortie | Prio | Effort |
|---|---|---|---|---|---|---|---|
| **R-NA-001** | Récap night audit | Combien de no-show, nuitées générées, écarts détectés ? | `reservations + nuitees` | jour | JSON+PDF | **P0** | 1.5 j |
| R-DIR-001 | Dashboard direction | Vue agrégée occupation + CA + alertes + tâches en 1 écran | toutes | temps réel | JSON | P1 | 2 j |

---

## 4. Matrice sécurité × rôles

Cf. `roles_utilisateurs.txt` racine. Lecture only (aucun rapport n'écrit en base).

| Code | SUPERADMIN | ADMIN | GERANT | RECEPTION | RESREC | RESTAURANT | MAGASIN | MENAGE | NIGHTAUDIT |
|---|---|---|---|---|---|---|---|---|---|
| R-HEB-001 occupation | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |
| R-HEB-002..005 | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |
| R-FIN-001 CA | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| R-FIN-002..004 | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| R-CLI-001 top clients | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| R-INV-001 alertes | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| R-INV-002..003 | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| R-RES-001..003 | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| R-MEN-001..002 | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| R-NA-001 night audit | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| R-DIR-001 dashboard direction | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

---

## 5. Roadmap d'implémentation

### Tour 40 — MVP P0 ✅ (2026-05-13)

Livré : R-HEB-001, R-FIN-001, R-INV-001, R-NA-001, R-CLI-001 (5 rapports, 22 unit tests + 14 IT tests verts).

### Tour 41 — Finance & Cache (~3 j)

- R-FIN-002 Encours clients (avec aging buckets 0-30/30-60/60-90/90+)
- R-FIN-004 Top sociétés par CA
- Activer `@Cacheable` sur les 5 rapports P0 + nouveaux (clé incluant `TenantContext.get()`)

### Tour 42 — Hebergement avancé (~2.5 j)

- R-HEB-002 ALOS (durée moyenne séjour)
- R-HEB-003 No-show rate
- R-HEB-005 KPI réception

### Tour 43 — Inventory avancé (~2.5 j)

- R-INV-002 Mouvements valorisés
- R-INV-003 BC pendants + rotation

### Tour 44 — Restaurant POS (~2 j, dépend stabilisation POS)

- R-RES-001 Journal caisse jour
- R-RES-002 Top articles vendus

### Tour 45 — Ménage (~1 j)

- R-MEN-001 Récap tâches

### Tour 46 — Direction (~2 j)

- R-DIR-001 Dashboard direction (agrégat multi-domaine)

### Tour 47 — Frontend reporting (~3-5 j)

- Composants Angular consommant les 10 endpoints MVP + P1
- Sélecteur période, viewer tableau, téléchargement PDF/XLSX
- Charts Chart.js sur le dashboard direction

### Tour 48 — Scheduler & Archivage (~2 j, Vague 4)

- Génération automatique PDF/XLSX hebdomadaires (cron via Spring `@Scheduled`)
- Archivage disque sous `/srv/city/data/reports/<hotel-code>/<YYYY-MM>/`
- Endpoint download archive

### Tour 49 — P2 & spécialisations (~5 j, Vague 4)

- R-HEB-004 Source de réservation (nécessite colonne `reservations.source_canal`)
- R-FIN-003 TVA collectée (nécessite réactivation TVA POS)
- R-RES-003 Ticket moyen + marge
- R-MEN-002 Charge par femme de chambre

---

## 6. Risques et dépendances

| Risque | Impact | Mitigation |
|---|---|---|
| Vues SQL bypass `@TenantId` | 🔴 fuite cross-tenant | Interdites en palier 1 ; évaluer RLS PG en palier 2 |
| Cache mal scopé (clé sans tenant) | 🔴 cache poisoning cross-hotel | Clé `@Cacheable` inclut **toujours** `TenantContext.get()` |
| Agrégats lents sur grosses bases | 🟡 perf dégradée | Index couvrants sur `(hotel_id, date_*)`, ajouter MV PG en palier 2 si besoin |
| POS pas encore complètement Vague 2 | 🟡 R-RES-001/002 incertain | Tour 44 conditionnel à stabilisation POS |
| Colonne `reservations.source_canal` absente | 🟡 R-HEB-004 bloqué | Changeset additif au Tour 49 avant impl |
| Réactivation TVA POS reportée | 🟡 R-FIN-003 placeholder | Stub côté API en attendant |
| Tests Testcontainers (Docker requis) | 🟢 skippés localement | Validés en CI Jenkins/GitHub Actions |

---

## 7. Conventions de code

- **DTOs** : records Java 21 immutables, dans `dto/reporting/`.
- **Projections** : interfaces Spring Data avec getters, dans `dto/reporting/projection/`.
- **Services** : interface + Impl séparés, dans `service/reporting/`. Annotations `@Service @RequireTenant @Transactional(readOnly = true)` au niveau classe.
- **Controller** : un seul `ReportController` sous `/api/reports`. `@PreAuthorize` granulaire par méthode.
- **Exports** : services génériques `PdfExportService` + `XlsxExportService` réutilisables.
- **Templates Jasper** : `.jrxml` sous `src/main/resources/reports/<code-rapport>.jrxml`.
- **Tests** : `*Tests.java` unitaires Mockito (Surefire), `*IT.java` intégration MockMvc (Failsafe).
- **i18n** : titres et entêtes des PDF/XLSX traduisibles via `MessageSource` + clés `reporting.<code>.title|column.<n>`.

---

## 8. État au 2026-05-14 (Tour 41)

- ✅ MVP 5 rapports P0 livré au Tour 40 (22 unit + 14 IT verts).
- ✅ 15 rapports P1/P2 livrés Tour 41 — voir détail par ligne ci-dessous.
- ✅ Cache activé via `ReportingCacheConfiguration` (ConcurrentMapCacheManager,
  21 caches nommés, clé impérative incluant `TenantContext.get()`). Annoté
  `@Cacheable` sur tous les services P0 + P1 + P2.
- ✅ `ApiResponseBodyAdvice` adapté pour bypasser les byte[]/Resource (exports binaires).
- ✅ `GlobalExceptionHandler` enrichi (400 sur params manquants/types invalides).
- ✅ Controllers scindés par module métier sous `/api/reports/{hebergement,
  finance,inventory,restaurant,menage,direction}` (ReportController générique
  conserve les 5 P0 originaux).
- ✅ Changeset Liquibase 044 ajoute `hebergement.reservations.source_canal`
  (VARCHAR(50) nullable + index) — nécessaire R-HEB-004.
- ✅ Tests : 67 unit Surefire + 37 IT Failsafe verts sur le périmètre reporting
  (P0 + P1 + P2 confondus).
- ⏸️ Frontend Angular reporting : 0% (Tour 47).
- ⏸️ Scheduler + archivage : 0% (Tour 48).

### 8.1 Détail catalogue Tour 41

- R-HEB-002 ALOS — ✅ livré Tour 41 — 2026-05-14
- R-HEB-003 No-show rate — ✅ livré Tour 41 — 2026-05-14
- R-HEB-004 Source de réservation — ✅ livré Tour 41 — 2026-05-14
- R-HEB-005 KPI réception — ✅ livré Tour 41 — 2026-05-14
- R-FIN-002 Encours clients (aging 0-30/30-60/60-90/90+) — ✅ livré Tour 41 — 2026-05-14
- R-FIN-003 TVA collectée — ✅ livré Tour 41 — 2026-05-14 (calcul réel
  `SUM(lignes_factures.montantTva)`, groupage MOIS ou TAUX)
- R-FIN-004 Top sociétés — ✅ livré Tour 41 — 2026-05-14
- R-INV-002 Mouvements valorisés — ✅ livré Tour 41 — 2026-05-14 (fallback
  `Produit.prixUnitaire` car `prixUnitaireMoyen` non implémenté palier 1)
- R-INV-003 BC pendants + rotation — ✅ livré Tour 41 — 2026-05-14
- R-RES-001 Journal caisse — ✅ livré Tour 41 — 2026-05-14
- R-RES-002 Top articles — ✅ livré Tour 41 — 2026-05-14
- R-RES-003 Ticket moyen + marge — ✅ livré Tour 41 — 2026-05-14 (marge réelle
  via `RecetteArticle × Produit.prixUnitaire`)
- R-MEN-001 Récap tâches — ✅ livré Tour 41 — 2026-05-14
- R-MEN-002 Charge personnel — ✅ livré Tour 41 — 2026-05-14
- R-DIR-001 Dashboard direction — ✅ livré Tour 41 — 2026-05-14 (orchestration
  pure : pas de duplication de logique métier)

**Couverture finale Tour 41 : 20/20 rapports = 100%.**
