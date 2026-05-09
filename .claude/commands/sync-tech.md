---
description: Aligne les dépendances sur les DERNIÈRES versions stables (mai 2026). Refuse toute rétrogradation suggérée par Tech_DevOPS/.
argument-hint: [--apply]   (sans flag : rapport ; avec --apply : modifie pom.xml et package.json après confirmation)
allowed-tools: Read, Edit, Bash(./mvnw:*), Bash(npm:*), Bash(grep:*), WebFetch
---

Vérifie et aligne la stack du projet **city** sur les **dernières versions stables**.

## ⚠️ Règle d'or — pas de rétrogradation

`Tech_DevOPS/TECHNOLOGIES_DEVOPS_A_UTILISER.md` provient du projet **STT** et liste des versions **anciennes** (Angular 12, Spring Boot 2, Java 8, Gradle 5, etc.). Ce document sert de **catalogue d'outils et de catégories** — pas de référence de version.

**Règle absolue** :
- ✅ Utiliser ce doc pour identifier les **types de libs nécessaires** (reporting, mapping, messaging, etc.).
- ❌ **JAMAIS** rétrograder le projet pour s'aligner sur les versions qu'il cite.
- ❌ **JAMAIS** suggérer Angular 12, Spring Boot 2, Java 8 ou Gradle 5.

Si l'utilisateur insiste pour rétrograder, **refuser** et expliquer pourquoi (sécurité CVE, perte de fonctionnalités, dette technique, fin de support).

## 🎯 Versions cibles (mai 2026)

### Backend — Java / Spring

| Composant | Version cible | Notes |
|-----------|---------------|-------|
| **Java** | **25** (LTS, sept 2025) | LTS, support jusqu'à sept 2028. Java 21 LTS acceptable si contrainte. **Pas Java 17 ou inférieur**. |
| **Spring Boot** | **4.0.x** (4.0.5+) | Spring Framework 7, Java 17 min, Java 25 first-class, JSpecify, API versioning. |
| Spring Cloud | **2025.0.x** | Aligné Spring Boot 4.0 |
| Spring Cloud OpenFeign | géré par Spring Cloud BOM | |
| Spring Cloud Stream Kafka | géré par Spring Cloud BOM | events asynchrones |
| Spring Security | 7.x (auto via Spring Boot 4.0) | |
| Spring Data | 2025.0 (auto) | |
| Hibernate | 7.x (auto) | |
| Tomcat | 11 (auto) | |
| **Maven** | **3.9.x** ou **4.0.x** | Wrapper `./mvnw` |
| Liquibase | **4.30+** | XML changelogs |
| PostgreSQL JDBC | **42.7.x** | |
| H2 (test) | 2.3.x | |
| **MapStruct** | **1.6.x** | |
| **Lombok** | **1.18.34+** | |
| jjwt | **0.12.x** | (préférer Spring Security OAuth2 Resource Server) |
| jasypt-spring-boot | **3.0.x** | chiffrement secrets (clés Dolibarr) |
| Resilience4j | **2.2+** | retry, circuit breaker (Dolibarr) |
| Testcontainers | **1.20+** | tests d'intégration |
| WireMock | **3.x** | mock Dolibarr |
| **JasperReports** | **7.0.x** | reporting PDF |
| **iText core** | **9.x** OU **OpenPDF 2.x** | iText 5/7 = AGPL coûteuses au-delà |
| Apache POI | **5.3+** | Excel |
| Apache Tika | **3.x** | détection MIME |
| Apache Batik | **1.18+** | SVG |
| ZXing | **3.5.x** | QR/codes-barres |
| Apache Commons IO | **2.16+** | |
| Keycloak Admin Client | **26.x** | si intégration Keycloak retenue |
| Jackson | **2.18+** (auto) | |

### Frontend — Angular ecosystem

| Composant | Version cible | Notes |
|-----------|---------------|-------|
| **Angular** | **21.2.x** | Angular 22 attendu mai 2026 (à passer dès release stable). **Pas Angular 20 ou inférieur**. |
| **TypeScript** | **5.9** (Angular 22) **ou 5.8** (Angular 21) | |
| **Node.js** | **24 LTS** (Krypton, Active LTS jusqu'à avril 2028) | Acceptable : 22 LTS (Maintenance) |
| npm | **10.9+** | (ou pnpm 9+) |
| Angular CLI | aligné Angular | |
| RxJS | **7.8+** | |
| zone.js | **0.15+** | (ou zoneless en preview Angular 21) |
| **Tailwind CSS** | **4.1.x** | déjà OK |
| Bootstrap | **5.3.x** | déjà OK |
| jQuery | **3.7.x** | déjà OK |
| DataTables | **2.1+** | déjà OK |
| **PrimeNG** | **19.x** (aligné Angular major) | si retenu |
| PrimeIcons | **8.x** | |
| ng-bootstrap | **18+** (aligné Angular 21) | |
| FontAwesome | **6.5+** ou **7.x** | |
| **NgRx** (store, effects, entity, router-store, store-devtools) | **21.x** (aligné Angular major) | |
| Apollo Angular | **11.x** | si GraphQL retenu |
| @apollo/client | **4.x** | |
| graphql | **16.x** | |
| **@ngx-translate/core** | **16.x** | i18n trilingue obligatoire |
| @ngx-translate/http-loader | **16.x** | |
| keycloak-angular | dernière compatible Angular 21 | si Keycloak retenu |
| keycloak-js | **26.x** | |
| @auth0/angular-jwt | **5.2+** | |
| **date-fns** | **4.x** | (préférer à moment.js, déprécié) |
| Chart.js | **4.5+** | déjà OK |
| jsPDF | **2.5+** | déjà OK |
| jspdf-autotable | **3.8+** | déjà OK |
| SweetAlert2 | **11.14+** | déjà OK |
| ngx-toastr | **19+** | si ajouté |

### Tests & Qualité

| Composant | Version cible |
|-----------|---------------|
| JUnit Jupiter | 5.11+ (auto via Spring Boot 4.0) |
| Mockito | 5.14+ (auto) |
| AssertJ | 3.26+ (auto) |
| **Vitest** (Angular 21+) | dernière | nouveau runner Angular par défaut |
| Karma + Jasmine | Karma toujours supporté ; viser migration Vitest |
| ESLint | 9.x |
| @typescript-eslint/* | 8.x |
| Prettier | 3.4+ |
| Husky | 9.x |
| lint-staged | 16+ |
| SonarQube Scanner | dernière |

### DevOps

| Composant | Version |
|-----------|---------|
| Docker | dernière stable |
| Jib (Maven plugin) | **3.4+** |
| Jenkins | LTS (2.452+) |
| Keycloak (serveur) | **26.x** (Quarkus, pas Wildfly) si retenu |
| Apache Kafka | **3.8+** |
| Nginx | mainline 1.27+ |

## 📋 Démarche d'audit

1. **Lire** `citybackend/pom.xml` (ou `build.gradle` si migration en cours).
2. **Lire** `cityfrontend/package.json`.
3. **Comparer** chaque dépendance présente avec la version cible ci-dessus.
4. **Identifier** :
   - 🔴 **Régressions** : version installée < cible (à mettre à jour).
   - 🟢 **Conformes** : version installée ≥ cible.
   - ⚪ **Manquantes** : lib citée dans Tech_DevOPS catégorie utile mais absente du projet.
   - 🟡 **À déprécier** : libs obsolètes (moment.js → date-fns ; iText 5 → OpenPDF/iText 9 ; Karma → Vitest).
5. **Vérifier la cohérence Spring Boot** : si on monte Spring Boot, monter Spring Cloud à la version compatible (BOM Spring Cloud 2025.0 pour Spring Boot 4.0).
6. **Vérifier la cohérence Angular** : NgRx, Apollo, ngx-translate, ng-bootstrap, PrimeNG doivent suivre la version majeure Angular (≥ 21).

## 📊 Format de rapport

```
═══════════════════════════════════════════════════════════════
  city — Audit stack vs versions stables (mai 2026)
═══════════════════════════════════════════════════════════════

▶ BACKEND — citybackend/pom.xml

  🔴 Spring Boot         3.3.4    →  cible 4.0.5      [UPGRADE majeur recommandé]
  🔴 Java                21       →  cible 25         [montée mineure non bloquante]
  🟢 Hibernate           via Spring Boot 4.0 → 7.x (auto)
  🔴 PostgreSQL JDBC     42.6.0   →  cible 42.7.4     [CVE possibles]
  ⚪ MapStruct           ABSENT    →  ajouter 1.6.3
  ⚪ Lombok              ABSENT    →  ajouter 1.18.34
  ⚪ Resilience4j        ABSENT    →  ajouter 2.2.0 (Dolibarr)
  ⚪ JasperReports       ABSENT    →  ajouter 7.0.0 (reporting PDF)
  ⚪ Apache POI          ABSENT    →  ajouter 5.3.0 (export Excel)
  🟡 iText 5             5.5.13   →  remplacer par OpenPDF 2.0 ou iText core 9
  ⚪ jjwt                ABSENT    →  ajouter 0.12.6 (ou OAuth2 Resource Server)
  ⚪ Spring Cloud BOM    ABSENT    →  ajouter 2025.0.0 (OpenFeign + Stream Kafka)

▶ FRONTEND — cityfrontend/package.json

  🔴 Angular             20.0.0   →  cible 21.2.x     [UPGRADE majeur recommandé]
  🟢 TypeScript          5.8      →  reste 5.8 (5.9 avec Angular 22)
  🟡 Node engines        ≥22      →  cible ≥24 LTS    [bumper en CI]
  🟢 RxJS                7.8      →  OK
  🟢 Tailwind            4.1.12   →  OK
  ⚪ @ngx-translate/core ABSENT    →  ajouter 16.x (i18n trilingue obligatoire)
  ⚪ NgRx (store...)     ABSENT    →  ajouter 21.x (POS, reporting)
  ⚪ keycloak-angular    ABSENT    →  ajouter (compat Angular 21) SI Keycloak retenu
  🟡 Karma + Jasmine     présents  →  envisager Vitest pour Angular 21+
  ⚪ Husky + lint-staged ABSENT    →  ajouter (qualité code)

═══════════════════════════════════════════════════════════════
  TOTAL : 4 régressions, 11 manquantes, 3 dépréciations
═══════════════════════════════════════════════════════════════

⚠️ Avertissement Tech_DevOPS :
  Le document Tech_DevOPS/TECHNOLOGIES_DEVOPS_A_UTILISER.md mentionne :
    - Java 8                    → IGNORÉ (sécurité, EOL OSS)
    - Spring Boot 2.x           → IGNORÉ (EOL nov 2023)
    - Angular 12                → IGNORÉ (EOL nov 2022, CVE non patchés)
    - Gradle 5.6.4              → si migration Gradle, viser Gradle 8.10+
    - Keycloak 19 (Wildfly)     → si retenu, viser Keycloak 26 (Quarkus)
  Ces versions sont conservées dans le doc à titre de catalogue de catégories
  uniquement. Le projet city reste sur Java 25 / Spring Boot 4 / Angular 21+.
```

## 🛠️ Mode `--apply`

Si l'utilisateur passe `--apply` :

1. Pour chaque entrée 🔴 ou ⚪ :
   - Présenter le diff exact à appliquer dans `pom.xml` / `package.json`.
   - Demander confirmation **par groupes** (jamais tout d'un coup) avant édition.
2. Après chaque salve d'édits :
   - Lancer `cd citybackend && ./mvnw -q -DskipTests dependency:resolve` pour vérifier.
   - Lancer `cd cityfrontend && npm install --no-fund --no-audit` pour vérifier les peer-deps.
3. Si conflit de peer-dep côté npm :
   - **Ne jamais** utiliser `--legacy-peer-deps` ou `--force` pour masquer un vrai conflit.
   - Préférer aligner la version de la lib problématique sur celle compatible Angular 21.
4. Si Spring Boot est monté de 3.x à 4.0 :
   - **Avertir** que c'est un upgrade majeur (Spring Framework 7, JSpecify, modules splittés).
   - Renvoyer au [migration guide Spring Boot 4.0](https://github.com/spring-projects/spring-boot/wiki).
   - **Proposer** une branche dédiée `chore/upgrade-spring-boot-4` avant de toucher `main`.
5. Si Angular est monté (ex. 20 → 21) :
   - Privilégier `ng update @angular/core@21 @angular/cli@21` plutôt qu'une édition manuelle de `package.json`.
   - Vérifier les schematics de migration (NgRx 21, ngx-translate 16, etc.).

## 🚫 Refus catégoriques de rétrogradation

Si la lecture de `Tech_DevOPS/` ou une demande utilisateur suggère de **descendre** :
- À Java < 21 → refuser, citer EOL OSS et CVE non patchées.
- À Spring Boot < 3.5 → refuser, citer EOL OSS (3.4 EOL fin 2025).
- À Angular < 21 → refuser, citer EOL et risques d'accessibilité.
- À Node < 22 LTS → refuser, citer EOL.
- À PostgreSQL < 16 → refuser, conseiller 18.

Réponse type au refus :
> "❌ Régression refusée. <Lib> <X> est en fin de vie OSS depuis <date> et expose à des CVE non corrigées. Le projet reste sur <version cible>. Si une dépendance tierce ne supporte que <X>, ouvrons une issue pour la remplacer plutôt que rétrograder le socle."

## 📚 Sources de version (à fetch si l'utilisateur doute)

- Spring Boot : `https://endoflife.date/spring-boot`
- Angular : `https://endoflife.date/angular` ou `https://angular.dev/reference/releases`
- Java : `https://endoflife.date/oracle-jdk`
- Node.js : `https://endoflife.date/nodejs`
- PostgreSQL : `https://endoflife.date/postgresql`
- npm packages : `https://www.npmjs.com/package/<nom>`
- Maven artifacts : `https://central.sonatype.com/`

Lors d'un audit, utiliser `WebFetch` sur ces sources si l'utilisateur demande une vérification fraîche, ou si la date du dernier audit dépasse 30 jours.

---

**Sortie finale** : conclure par une **recommandation hiérarchisée** (priorité 1 = sécurité, priorité 2 = manquants critiques pour le métier, priorité 3 = nice-to-have) plutôt qu'une longue liste indifférenciée.
