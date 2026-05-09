---
name: sync-tech
description: Aligne les dépendances du projet city sur les dernières versions stables (mai 2026) et refuse toute rétrogradation suggérée par Tech_DevOPS/. À utiliser dès qu'un pom.xml ou package.json est modifié, ou avant tout commit qui touche aux dépendances.
---

# sync-tech — Audit & alignement de la stack city

Skill jumeau de la slash command `/sync-tech`. Invoquer ce skill quand :
- Un fichier `pom.xml` ou `package.json` change.
- Un agent (`backend-spring`, `frontend-angular`) introduit une nouvelle dépendance.
- L'utilisateur demande une montée de version.
- Un audit régulier est lancé (cf. `PROMPTS_TOURS.md` Tour 1).

## Règle d'or — pas de rétrogradation

`Tech_DevOPS/TECHNOLOGIES_DEVOPS_A_UTILISER.md` provient d'un **autre projet** (STT) et liste des versions anciennes (Java 8, Spring Boot 2, Angular 12, Gradle 5). Ce document sert de **catalogue de catégories** uniquement.

- ✅ S'en servir pour identifier les **types** de libs nécessaires (reporting, mapping, messaging, etc.).
- ❌ **JAMAIS** rétrograder pour s'aligner sur ses versions.
- ❌ Refus catégoriques : Java < 21, Spring Boot < 3.5, Angular < 21, Node < 22 LTS, PostgreSQL < 16.

## Versions cibles (mai 2026)

### Backend
| Composant | Cible |
|---|---|
| Java | **25 LTS** (21 LTS acceptable) |
| Spring Boot | **4.0.x** |
| Spring Cloud BOM | **2025.0.x** |
| Spring Security | **7.x** (auto) |
| Hibernate | **7.x** (auto) |
| Maven | **3.9.x ou 4.0.x** (`./mvnw`) |
| Liquibase | **4.30+** (changesets XML) |
| PostgreSQL JDBC | **42.7.x** |
| MapStruct | **1.6.x** |
| Lombok | **1.18.34+** |
| jjwt | **0.12.x** (ou OAuth2 Resource Server) |
| Resilience4j | **2.2+** |
| Testcontainers | **1.20+** |
| WireMock | **3.x** |
| JasperReports | **7.0.x** |
| OpenPDF | **2.x** (ou iText core 9 — pas iText 5/7 AGPL) |
| Apache POI | **5.3+** |
| Spring Boot starter mail | auto |

### Frontend
| Composant | Cible |
|---|---|
| Angular | **21.2.x** (22 dès release stable) |
| TypeScript | **5.8** (5.9 avec Angular 22) |
| Node | **24 LTS** (22 LTS acceptable) |
| RxJS | **7.8+** |
| zone.js | **0.15+** |
| Tailwind CSS | **4.1.x** unifié (pas de mix v3+v4) |
| Bootstrap | **5.3.x** |
| jQuery | **3.7.x** |
| DataTables | **2.1+** |
| NgRx | **21.x** |
| @ngx-translate/core + http-loader | **16.x** |
| date-fns | **4.x** |
| Chart.js | **4.5+** |
| jsPDF | **2.5+** |
| SweetAlert2 | **11.14+** |

### DevOps
| Composant | Cible |
|---|---|
| Docker + Jib | dernière / **3.4+** |
| Apache Kafka | **3.8+** |
| Keycloak (si retenu) | **26.x** Quarkus |
| Jenkins LTS | dernière |
| SonarQube | dernière |

## Démarche d'audit

1. Lire `citybackend/pom.xml` et `cityfrontend/package.json`.
2. Comparer chaque dépendance présente avec la cible ci-dessus.
3. Classer :
   - 🔴 **Régression** : version installée < cible → upgrade requis.
   - 🟢 **Conforme** : version installée ≥ cible.
   - ⚪ **Manquante** : utile pour le métier, absente du projet.
   - 🟡 **À déprécier** : moment.js → date-fns, iText 5 → OpenPDF, Karma → Vitest.
4. Vérifier les **cohérences transitives** : si Spring Boot monte, Spring Cloud BOM doit suivre. Si Angular monte, NgRx / ngx-translate / Apollo / ng-bootstrap / PrimeNG doivent suivre la majeure.

## Format de rapport

```
═══════════════════════════════════════════════════════════════
  city — Audit stack vs versions stables (mai 2026)
═══════════════════════════════════════════════════════════════

▶ BACKEND — citybackend/pom.xml
  🔴 Spring Boot   3.2.0  → cible 4.0.x   [UPGRADE majeur]
  🔴 Java          17     → cible 25 LTS
  🟢 MapStruct     1.5.5  → 1.6.x (mineur)
  ⚪ Lombok        ABSENT → 1.18.34+
  ⚪ Resilience4j  ABSENT → 2.2+ (Dolibarr)
  ...

▶ FRONTEND — cityfrontend/package.json
  🔴 Angular       20     → cible 21.2.x
  ⚪ ngx-translate ABSENT → 16.x (i18n trilingue)
  ⚪ NgRx          ABSENT → 21.x
  ...

═══════════════════════════════════════════════════════════════
  TOTAL : N régressions, M manquantes, K dépréciations
═══════════════════════════════════════════════════════════════
```

## Mode `--apply`

Si l'utilisateur demande l'application :
1. Présenter le diff exact pom.xml / package.json.
2. Demander confirmation **par groupes** (jamais tout d'un coup).
3. Après chaque salve : `mvnw -q -DskipTests dependency:resolve` et `npm install --no-fund --no-audit`.
4. Si conflit peer-dep npm : **ne jamais** utiliser `--legacy-peer-deps` ou `--force` pour masquer un conflit. Aligner la version compatible.
5. Spring Boot 3 → 4 : ouvrir branche `chore/upgrade-spring-boot-4`, jamais directement sur main.
6. Angular : préférer `ng update @angular/core@21 @angular/cli@21` à l'édition manuelle.

## Refus type

> ❌ Régression refusée. <Lib> <X> est en fin de vie OSS depuis <date> et expose à des CVE non corrigées. Le projet reste sur <version cible>. Si une dépendance tierce ne supporte que <X>, ouvrons une issue pour la remplacer plutôt que rétrograder le socle.

## Sources de vérification

- Spring Boot : https://endoflife.date/spring-boot
- Angular : https://endoflife.date/angular
- Java : https://endoflife.date/oracle-jdk
- Node.js : https://endoflife.date/nodejs
- PostgreSQL : https://endoflife.date/postgresql
- npm : https://www.npmjs.com/package/<nom>
- Maven Central : https://central.sonatype.com/

Utiliser `WebFetch` si l'audit a plus de 30 jours.

## Sortie finale

Conclure par une **recommandation hiérarchisée** :
- Priorité 1 : sécurité / EOL.
- Priorité 2 : manquants critiques pour le métier (i18n, multi-tenant, Dolibarr).
- Priorité 3 : nice-to-have.
