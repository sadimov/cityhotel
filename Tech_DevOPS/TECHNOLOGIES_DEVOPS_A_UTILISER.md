# Technologies & Outils DevOps — Projet STT

Inventaire des technologies, frameworks, bibliothèques et outils DevOps utilisés pour la réalisation des deux projets :

- **`stt-backend`** — API Java / Spring Boot
- **`stt-frontend`** — Application web Angular

---

## 1. Langages de programmation

| Langage | Version | Projet | Usage |
|---------|---------|--------|-------|
| Java | 8 (1.8) | backend | Langage principal de l'API |
| TypeScript | 4.3.5 | frontend | Langage principal du SPA |
| SQL (PostgreSQL) | — | backend | Migrations Liquibase, scripts d'init |
| HTML5 | — | frontend | Templates Angular |
| SCSS / CSS3 | — | frontend | Styles globaux et styles de composants |
| GraphQL (SDL) | — | frontend | Définition de requêtes (`.graphql`) |
| XML | — | backend | Liquibase changelogs, templates JasperReports (JRXML) |
| Properties | — | backend | Configuration Spring Boot (`application.properties`) |
| Bash / Shell | — | backend & frontend | Scripts `backend.sh`, `build.sh` |

---

## 2. Frameworks & plateformes

### Backend

| Framework | Version | Rôle |
|-----------|---------|------|
| Spring Boot | 2.x (compatible Java 8) | Framework applicatif principal (IoC, auto-configuration) |
| Spring MVC / Spring Web | inclus Spring Boot | Contrôleurs REST (`@RestController`, `@RequestMapping`) |
| Spring Data JPA | inclus Spring Boot | Persistance ORM via Hibernate |
| Spring Security | inclus Spring Boot | Sécurisation des endpoints, intégration Keycloak |
| Spring Boot Mail | inclus Spring Boot | Envoi d'e-mails SMTP |
| Spring Boot Thymeleaf | inclus Spring Boot | Moteur de templates (templates d'e-mail) |
| Spring Cloud OpenFeign | 3.1.3 | Client HTTP déclaratif inter-services |
| Spring Cloud Stream Kafka | inclus Spring Cloud | Messaging asynchrone via Kafka |
| Hibernate ORM | inclus Spring Data JPA | Mapping objet-relationnel |
| Tomcat (embedded) | inclus Spring Boot | Conteneur servlet embarqué |

### Frontend

| Framework / Lib | Version | Rôle |
|-----------------|---------|------|
| Angular | 12.2.x | Framework SPA principal (architecture NgModule, non standalone) |
| Angular CDK | 12.2.x | Composants utilitaires (overlay, drag-drop, a11y…) |
| Angular Elements | 8.0.1 | Web Components Angular |
| RxJS | 6.6.x | Programmation réactive |
| zone.js | 0.11.x | Détection de changement Angular |

---

## 3. UI / UX

| Bibliothèque | Version | Rôle |
|--------------|---------|------|
| PrimeNG | 12.2.3 | Bibliothèque de composants UI (thème `saga-green`) |
| PrimeIcons | 5.0.0 | Icônes PrimeNG |
| Bootstrap | 5.1.3 | Framework CSS |
| ng-bootstrap | 10.0.0 | Composants Angular pour Bootstrap |
| ngx-bootstrap | 7.1.2 | Composants Bootstrap alternatifs |
| FontAwesome | 6.0.0 + 5.x | Icônes (Free Solid / Regular / Brands) |
| @fortawesome/angular-fontawesome | 0.9.0 | Intégration Angular FontAwesome |
| AG Grid Community / Angular | 23.2.1 | Tableaux de données avancés |
| ngx-toastr | 14.3.0 | Notifications toast |
| ngx-spinners | 1.1.1 | Indicateurs de chargement |
| ngx-editor | 14.2.0 | Éditeur WYSIWYG |
| mydatepicker | 2.6.6 | Sélecteur de dates |
| ng-drag-drop | 5.0.0 | Drag & drop |
| ng-dynamic-component | 9.0.0 | Composants dynamiques |
| @yellowspot/ng-truncate | 1.5.1 | Pipe de troncature de texte |

---

## 4. State management & data

| Bibliothèque | Version | Rôle |
|--------------|---------|------|
| @ngrx/store | 12.5.1 | Store Redux-like |
| @ngrx/effects | 12.5.1 | Side-effects pour NgRx |
| @ngrx/entity | 8.3.0 | Gestion d'entités normalisées |
| @ngrx/router-store | 8.3.0 | Synchronisation router ↔ store |
| @ngrx/store-devtools | 12.5.1 | Outils de debug Redux |
| Apollo Client | 2.4.7 | Client GraphQL |
| apollo-angular | 1.5.0 | Intégration Apollo dans Angular |
| graphql | 14.0.2 | Implémentation GraphQL |
| graphql-tag / graphql-tools | 2.10.0 / 4.0.5 | Tooling GraphQL |

---

## 5. Authentification & sécurité

| Composant | Version | Rôle |
|-----------|---------|------|
| Keycloak (serveur) | 19.x (Legacy / Wildfly) | Serveur d'identité SSO (realm `stt`, port 8088) |
| Keycloak Admin Client (Java) | 15.0.2 | Administration programmatique de Keycloak depuis le backend |
| keycloak-angular | 9.3.0 | Adapter Keycloak pour Angular |
| keycloak-js | 19.0.2 | SDK JavaScript Keycloak |
| Auth0 java-jwt | 3.4.1 | Génération / vérification de JWT côté backend |
| @auth0/angular-jwt | 2.1.0 | Helper JWT côté Angular |

---

## 6. Persistance & base de données

| Outil | Version | Rôle |
|-------|---------|------|
| PostgreSQL | 14+ | SGBDR principal (base `stt_db`) |
| PostgreSQL JDBC Driver | 42.5.4 | Pilote JDBC backend |
| H2 Database | 1.4.191 | Base embarquée (profil local / tests) |
| Liquibase | inclus Spring Boot | Migrations versionnées (`db.changelog-master.xml`) |

---

## 7. Génération de documents & fichiers

| Bibliothèque | Version | Rôle |
|--------------|---------|------|
| JasperReports | 6.19.1 | Moteur de reporting (templates JRXML : reçus de paiement) |
| JasperReports Fonts | 6.19.1 | Polices intégrées |
| iText PDF | 5.5.13.1 | Génération de PDF |
| iText (Lowagie) | 2.1.7 | Compatibilité legacy iText |
| Apache POI (poi-ooxml) | 4.0.0 | Lecture / écriture de fichiers Excel (`.xlsx`) |
| Apache Tika (tika-parsers) | 1.11 | Détection de type MIME, extraction de contenu |
| Apache Batik | 1.14 | Manipulation SVG |
| Google ZXing (core / javase) | 3.4.1 | Génération / lecture de codes-barres et QR codes |
| Apache Commons IO | 2.6 | Utilitaires de manipulation de fichiers |

---

## 8. Mapping & utilitaires Java

| Bibliothèque | Version | Rôle |
|--------------|---------|------|
| MapStruct | 1.4.2.Final | Mapper objet-objet (DTO ↔ entités) basé annotation processor |
| Project Lombok | 1.18.22 | Réduction de boilerplate (`@Data`, `@Builder`, etc.) |
| Jackson Module JAXB Annotations | 2.10.0 | Sérialisation JSON avec annotations JAXB |

---

## 9. Utilitaires frontend

| Bibliothèque | Version | Rôle |
|--------------|---------|------|
| Lodash | 4.17.21 | Utilitaires fonctionnels JavaScript |
| Moment.js | 2.29.3 | Manipulation de dates |
| Moment Timezone | 0.5.31 | Gestion des fuseaux horaires |
| file-saver | 2.0.2 | Téléchargement côté client |
| expr-eval | 2.0.2 | Évaluation d'expressions mathématiques |
| glob-promise | 3.4.0 | Glob asynchrone |
| ngx-take-until-destroy | 5.4.0 | Helper de désinscription RxJS |
| @ngx-translate/core | 13.0.0 | Internationalisation (i18n) |
| @ngx-translate/http-loader | 6.0.0 | Chargement HTTP des fichiers de traduction |
| ngx-build-plus | 8.1.1 | Extensions du builder Angular |

---

## 10. Build & gestion de dépendances

| Outil | Version | Projet | Rôle |
|-------|---------|--------|------|
| Gradle | 5.6.4 (wrapper) | backend & frontend | Outil de build principal |
| Gradle Wrapper | 5.6.4 | backend & frontend | Build reproductible (`gradlew`) |
| npm | 6.14.8 (cible) | frontend | Gestionnaire de paquets Node |
| Node.js | 14.15.1 (cible) / 16+ (dev) | frontend | Runtime JavaScript |
| Angular CLI | 12.x | frontend | CLI Angular (`ng serve`, `ng build`) |
| @angular-devkit/build-angular | 12.2.17 | frontend | Builder Webpack pour Angular |

---

## 11. Tests

### Backend
| Outil | Rôle |
|-------|------|
| Spring Boot Starter Test | Stack de test Spring Boot (JUnit, Mockito, AssertJ, Spring Test) |
| JUnit Platform (Jupiter) | Framework de tests unitaires |

### Frontend
| Outil | Version | Rôle |
|-------|---------|------|
| Karma | ~5.0.0 | Test runner |
| Jasmine Core | ~3.5.0 | Framework de tests unitaires |
| karma-chrome-launcher | ~3.1.0 | Lanceur Chrome pour Karma |
| karma-jasmine | ~3.0.1 | Adapter Karma ↔ Jasmine |
| karma-jasmine-html-reporter | ^1.4.2 | Reporter HTML |
| karma-coverage-istanbul-reporter | ~2.1.0 | Couverture de code (Istanbul / lcov) |
| Protractor | ~7.0.0 | Tests end-to-end |
| jasmine-spec-reporter | ~4.2.1 | Reporter console pour Jasmine |

---

## 12. Qualité de code & linting

| Outil | Version | Projet | Rôle |
|-------|---------|--------|------|
| ESLint | 7.26.0 | frontend | Linter TypeScript / JavaScript |
| @angular-eslint/* | 12.7.0 | frontend | Règles ESLint spécifiques Angular |
| @typescript-eslint/parser | 4.28.2 | frontend | Parser ESLint pour TypeScript |
| @typescript-eslint/eslint-plugin | 4.28.2 | frontend | Règles ESLint TypeScript |
| Prettier | 2.6.2 | frontend | Formatage automatique du code |
| eslint-config-prettier | 8.5.0 | frontend | Désactive les règles ESLint conflictuelles avec Prettier |
| Husky | 8.0.1 | frontend | Git hooks (pre-commit, etc.) |
| lint-staged | 13.0.0 | frontend | Lint des fichiers stagés uniquement |
| SonarQube | — | frontend | Analyse statique de qualité (config dans `build.gradle`) |

---

## 13. DevOps & CI/CD

| Outil | Rôle |
|-------|------|
| Jenkins | Serveur d'intégration continue (`Jenkinsfile` à la racine de chaque projet) |
| Pipeline Jenkins déclarative | Définition des étapes de build/test/deploy |
| Gradle Jib (`jib`) | Construction d'images Docker sans démon Docker (publication d'images) |
| Docker | Conteneurisation (cible des builds Jib) |
| Artifactory (repository Maven privé) | Distribution interne d'artefacts Gradle/Maven |
| Maven Central | Dépôt public de dépendances Java |
| Git | Gestion de versions du code source |
| Husky + Git Hooks | Vérifications pré-commit côté frontend |

---

## 14. Configuration & infrastructure d'exécution

| Composant | Détails |
|-----------|---------|
| Apache Tomcat (embedded) | Serveur web embarqué dans le JAR Spring Boot (port `8888`) |
| Angular Dev Server | Serveur de développement (port `4200`, proxy via `proxy.conf.json`) |
| Apache Kafka | Broker de messages (`localhost:29092`) — topic `notificationTopic` |
| SMTP | Envoi d'e-mails (configurable via `spring.mail.*`) |
| Profils Spring | `dev`, `local`, `test` (`application-{profil}.properties`) |

---

## 15. Communication & API

| Technologie | Rôle |
|-------------|------|
| REST (JSON) | Style principal d'API exposé par le backend |
| GraphQL (Apollo) | Requêtes côté frontend pour certaines ressources |
| OpenFeign | Clients HTTP déclaratifs côté backend |
| Spring Cloud Stream + Kafka | Messaging asynchrone (notifications) |

---

## 16. Récapitulatif des versions clés

| Composant | Version |
|-----------|---------|
| Java JDK | 8 |
| Spring Boot | 2.x |
| Gradle | 5.6.4 |
| Node.js | 14.15.1 (cible) / 16+ (dev) |
| npm | 6.14.8 (cible) |
| Angular | 12.2 |
| TypeScript | 4.3.5 |
| PostgreSQL | 14+ |
| Keycloak | 19.x |
| Apache Kafka | — (broker externe) |
