# CARTOGRAPHIE_MODULES.md

> Produit au Tour 6 (2026-05-05). Source de verite pour `/integrate-module <nom>`.
> Read-only — aucun fichier source n'a ete modifie.
> **Regle** : `/integrate-module <X>` n'integre QUE les fichiers dont `Domaine reel = <X>` dans cette cartographie, peu importe le dossier d'origine.

## Note de lecture (a lire avant tout)

Ce que l'audit a revele :

1. **Chaque dossier `<MODULE>/files_back/`** contient un **kit de scaffolding identique** copie depuis le projet de reference : `CitybackendApplication.java`, `SecurityConfig.java`, `RefreshTokenRequest.java`, `PasswordUtil.java`, `core_entities.java` (DBUser, Hotel, Role, UserSession), `AuthController_and_AuthService.java`, plus `pom.xml` et `application.yml`. Ces fichiers sont des **DOUBLONS** de ce qui est deja implemente dans `citybackend/src/main/java/com/cityprojects/citybackend/`. Ils ne doivent JAMAIS etre re-integres.

2. **Chaque dossier `<MODULE>/files_front/`** contient un kit Angular identique : `app.module.ts`, `app-routing.module.ts`, `app.component.ts`, `index.html`, `environment.ts`, interceptors, guards, `package.json`, `angular.json`. **Tous DOUBLONS** de `cityfrontend/src/app/`. Ne jamais re-integrer.

3. **La quasi-totalite du code metier "specifique"** est concentree dans **quelques fichiers monolithiques** (1 .java ou 1 .ts par module) qui contiennent **toutes les entites + tous les services + tous les DTO + tous les controllers** d'un module dans un seul fichier. Ce sont des **specifications/brouillons de generation** qui doivent etre **eclates** lors de l'integration en plusieurs fichiers Java/TS reels respectant les conventions du `CLAUDE.md`. Concretement : un `entities_services_module_inventory.java` doit etre eclate en `Fournisseur.java`, `Produit.java`, `BonCommande.java`, `FournisseurService.java`, etc., chacun dans son propre package/fichier.

4. Pour le module **restaurant** uniquement, le sous-dossier `resultat_chatgpt/restaurant-backend-module/` contient deja une vraie arborescence eclatee, fichier-par-fichier (controllers, entities, dtos, repositories, services, mapper). Idem cote front avec `cityfrontend_restaurant_module/` et `POS_avance_adapte/`.

5. Les fichiers `.txt` (`endpoints_*.txt`, `structure_*.txt`, `menu_user_par_role*.txt`), `.sql` (`structures_tables_schema_*.sql`), `.zip`, `.md` (`README.md`) sont des **specs**. Ils ne sont pas du code a integrer — ils servent de reference (les `.sql` notamment sont a comparer au snapshot `structure_cityprojectdb*.sql` a la racine et aux migrations Liquibase futures).

Convention destination : `<base>` = `citybackend/src/main/java/com/cityprojects/citybackend` (back) ou `cityfrontend/src/app` (front).

---

## /CLIENTS

| Fichier source (chemin relatif depuis /CLIENTS) | Type | Domaine reel | Destination | Notes |
|---|---|---|---|---|
| files_back/pom.xml | build | infra | — | DOUBLON du `citybackend/pom.xml`. Ignorer. |
| files_back/application.yml | config | infra | — | DOUBLON du `citybackend/src/main/resources/application.yml`. Ignorer. |
| files_back/CitybackendApplication.java | bootstrap | infra | — | DOUBLON. `citybackend/src/main/java/com/cityprojects/citybackend/CitybackendApplication.java` deja present. |
| files_back/SecurityConfig.java | config | infra | — | DOUBLON. `<base>/config/SecurityConfig.java` deja present. |
| files_back/RefreshTokenRequest.java | dto | auth | — | DOUBLON. `<base>/dto/auth/RefreshTokenRequest.java` deja present. |
| files_back/PasswordUtil.java | util | infra | — | DOUBLON. `<base>/util/PasswordUtil.java` deja present. |
| files_back/core_entities.java | entity (multi) | core | — | DOUBLON. Contient DBUser/Hotel/Role/UserSession deja presents dans `<base>/entity/core/`. |
| files_back/AuthController_and_AuthService.java | controller+service | auth | — | DOUBLON. `<base>/controller/auth/AuthController.java` + `<base>/service/auth/AuthService.java` deja presents. |
| files_back/structure_backend.txt | spec | spec | — | Arborescence cible — reference uniquement. |
| files_back/structures_tables_core.sql | spec | spec | — | Schema SQL core — reference. |
| files_back/structures_tables_schema_clients.sql | spec | spec | — | Schema SQL clients — a confronter aux entites Client/Societe + migrations Liquibase. |
| files_front/package.json | build | infra | — | DOUBLON. |
| files_front/angular.json | config | infra | — | DOUBLON. |
| files_front/index.html | bootstrap | infra | — | DOUBLON. |
| files_front/environment.ts | config | infra | — | DOUBLON. `cityfrontend/src/environments/environment.ts` deja present. |
| files_front/app.module.ts | module | infra | — | DOUBLON. |
| files_front/app-routing.module.ts | routing | infra | — | DOUBLON. |
| files_front/app.component.ts | component | infra | — | DOUBLON. |
| files_front/auth-interceptor.interceptor.ts | interceptor | infra | — | DOUBLON. `cityfrontend/src/app/interceptors/auth-interceptor.interceptor.ts` deja present. |
| files_front/error-interceptor.interceptor.ts | interceptor | infra | — | DOUBLON. |
| files_front/auth-guard.guard.ts | guard | auth | — | DOUBLON. `cityfrontend/src/app/guards/auth-guard.guard.ts` deja present. |
| files_front/role-guard.guard.ts | guard | auth | — | DOUBLON. |
| files_front/structure_frontend.txt | spec | spec | — | Arborescence cible — reference uniquement. |
| files_front/menu_user_par_roel.txt | spec | spec | — | Cartographie menus / roles — reference (sera utilisee pour configurer le sidebar). |

> **Manquant dans /CLIENTS** : les vraies entites/services Client/Societe ne sont PAS dans /CLIENTS. Le code source du module clients (Client.java, Societe.java, ClientService, etc.) est en realite cache dans **`HEBERGEMENT/files_back/Services_module_clients.java`** (cote back) et dans plusieurs `models_services_clients_frontend.ts` disperses (cote front). Voir lignes correspondantes.

---

## /FINANCE

| Fichier source (chemin relatif depuis /FINANCE) | Type | Domaine reel | Destination | Notes |
|---|---|---|---|---|
| files_back/pom.xml | build | infra | — | DOUBLON. |
| files_back/application.yml | config | infra | — | DOUBLON. |
| files_back/CitybackendApplication.java | bootstrap | infra | — | DOUBLON. |
| files_back/SecurityConfig.java | config | infra | — | DOUBLON. |
| files_back/RefreshTokenRequest.java | dto | auth | — | DOUBLON. |
| files_back/PasswordUtil.java | util | infra | — | DOUBLON. |
| files_back/core_entities.java | entity (multi) | core | — | DOUBLON. |
| files_back/AuthController_and_AuthService.java | controller+service | auth | — | DOUBLON. |
| files_back/structure_backend.txt | spec | spec | — | Reference. |
| files_back/structures_tables_core.sql | spec | spec | — | Reference. |
| files_back/structures_tables_schema_clients.sql | spec | spec | — | Reference. |
| files_back/structures_tables_schema_finance.sql | spec | spec | — | Schema SQL finance — reference de premiere importance pour creer entites + Liquibase. |
| files_back/structures_tables_schema_finance+index.sql | spec | spec | — | Index SQL finance — reference. |
| files_back/endpoints_module_client.txt | spec | spec | — | Reference. |
| files_back/endpoints_module_finance.txt | spec | spec | — | Reference cle pour controllers finance. |
| files_front/package.json | build | infra | — | DOUBLON. |
| files_front/angular.json | config | infra | — | DOUBLON. |
| files_front/index.html | bootstrap | infra | — | DOUBLON. |
| files_front/environment.ts | config | infra | — | DOUBLON. |
| files_front/app.module.ts | module | infra | — | DOUBLON. |
| files_front/app-routing.module.ts | routing | infra | — | DOUBLON. |
| files_front/app.component.ts | component | infra | — | DOUBLON. |
| files_front/auth-interceptor.interceptor.ts | interceptor | infra | — | DOUBLON. |
| files_front/error-interceptor.interceptor.ts | interceptor | infra | — | DOUBLON. |
| files_front/auth-guard.guard.ts | guard | auth | — | DOUBLON. |
| files_front/role-guard.guard.ts | guard | auth | — | DOUBLON. |
| files_front/structure_frontend.txt | spec | spec | — | Reference. |
| files_front/menu_user_par_role.txt | spec | spec | — | Reference. |
| files_front/models_services_clients_frontend.ts | model+service (multi) | clients | cityfrontend/src/app/features/clients/ (a eclater en `models/client.model.ts`, `models/societe.model.ts`, `services/client.service.ts`, `services/societe.service.ts`) | **MAL CLASSE** — c'est du code clients dans /FINANCE. A integrer quand on traite `clients`, pas `finance`. |

> **Manquant dans /FINANCE** : aucune entite/service finance dans ce dossier. Le code finance reel est dans **`HEBERGEMENT/files_back/Services_module_finance.java`** (cote back, monolithique) et dans **`HEBERGEMENT/files_front/models_services_finance_frontend.ts`** + **`HEBERGEMENT/COMPONENT_CALENDAR/models_services_finance_frontend.ts`** + **`RESTAURANT/point-vente/models_services_finance_frontend.ts`** (cote front). Voir lignes correspondantes.

---

## /HEBERGEMENT

| Fichier source (chemin relatif depuis /HEBERGEMENT) | Type | Domaine reel | Destination | Notes |
|---|---|---|---|---|
| files_back/pom.xml | build | infra | — | DOUBLON. |
| files_back/application.yml | config | infra | — | DOUBLON. |
| files_back/CitybackendApplication.java | bootstrap | infra | — | DOUBLON. |
| files_back/SecurityConfig.java | config | infra | — | DOUBLON. |
| files_back/RefreshTokenRequest.java | dto | auth | — | DOUBLON. |
| files_back/PasswordUtil.java | util | infra | — | DOUBLON. |
| files_back/core_entities.java | entity (multi) | core | — | DOUBLON. |
| files_back/AuthController_and_AuthService.java | controller+service | auth | — | DOUBLON. |
| files_back/Services_module_clients.java | service (multi) | clients | `<base>/service/client/` + `<base>/dto/client/` + `<base>/repository/client/` (a eclater en SocieteService.java, ClientService.java, leurs repositories, leurs DTOs Societe/StatistiquesClient) | **MAL CLASSE** — code clients dans /HEBERGEMENT. Source principale du module `clients`. A eclater. |
| files_back/Services_module_finance.java | service (multi) | finance | `<base>/service/finance/` + `<base>/repository/finance/` (a eclater en CompteService, OperationCompteService, etc.) | **MAL CLASSE** — code finance dans /HEBERGEMENT. Une des sources principales du module `finance` cote back. Refs Compte, OperationCompte, ReleveCompte. |
| files_back/structure_backend.txt | spec | spec | — | Reference. |
| files_back/structures_tables_core.sql | spec | spec | — | Reference. |
| files_back/structures_tables_schema_clients.sql | spec | spec | — | Reference. |
| files_back/structures_tables_schema_hebergement.sql | spec | spec | — | Schema SQL hebergement — reference cle. |
| files_back/structures_tables_schema_finance.sql | spec | spec | — | Reference. |
| files_back/endpoints_module_client.txt | spec | spec | — | Reference. |
| files_back/endpoints_module_finance.txt | spec | spec | — | Reference. |
| entities_services_module_hebergement.java | entity+service+dto+repo+controller (multi) | hebergement | `<base>/entity/hebergement/` + `<base>/repository/hebergement/` + `<base>/service/hebergement/` + `<base>/controller/hebergement/` + `<base>/dto/hebergement/` (a eclater : Reservation, Chambre, TypeChambre, Nuitee, ReservationChambre, ReservationClient, leurs repos/services/dto/controllers) | **Source principale du module `hebergement` cote back.** Fichier monolithique — eclater scrupuleusement par fichier (1 entite = 1 fichier). |
| endpoints_module_hebergement.txt | spec | spec | — | Reference cle pour controllers hebergement. |
| files_front/package.json | build | infra | — | DOUBLON. |
| files_front/angular.json | config | infra | — | DOUBLON. |
| files_front/index.html | bootstrap | infra | — | DOUBLON. |
| files_front/environment.ts | config | infra | — | DOUBLON. |
| files_front/app.module.ts | module | infra | — | DOUBLON. |
| files_front/app-routing.module.ts | routing | infra | — | DOUBLON. |
| files_front/app.component.ts | component | infra | — | DOUBLON. |
| files_front/auth-interceptor.interceptor.ts | interceptor | infra | — | DOUBLON. |
| files_front/error-interceptor.interceptor.ts | interceptor | infra | — | DOUBLON. |
| files_front/auth-guard.guard.ts | guard | auth | — | DOUBLON. |
| files_front/role-guard.guard.ts | guard | auth | — | DOUBLON. |
| files_front/structure_frontend.txt | spec | spec | — | Reference. |
| files_front/menu_user_par_role.txt | spec | spec | — | Reference. |
| files_front/models_services_clients_frontend.ts | model+service (multi) | clients | cityfrontend/src/app/features/clients/ | **MAL CLASSE** — code clients. DOUBLON probable de FINANCE/files_front/models_services_clients_frontend.ts (verifier diff lors integration). |
| files_front/models_services_finance_frontend.ts | model+service (multi) | finance | cityfrontend/src/app/features/finance/ (a eclater models/compte.model.ts, services/compte.service.ts, etc.) | Source principale du module `finance` cote front. |
| COMPONENT_CALENDAR/structures_tables_core.sql | spec | spec | — | Reference. |
| COMPONENT_CALENDAR/structures_tables_schema_clients.sql | spec | spec | — | Reference. |
| COMPONENT_CALENDAR/structures_tables_schema_hebergement.sql | spec | spec | — | Reference. |
| COMPONENT_CALENDAR/structures_tables_schema_finance.sql | spec | spec | — | Reference. |
| COMPONENT_CALENDAR/models_services_clients_frontend.ts | model+service (multi) | clients | cityfrontend/src/app/features/clients/ | DOUBLON probable (meme contenu repete depuis FINANCE/files_front/ et HEBERGEMENT/files_front/). A diff au moment de l'integration clients. |
| COMPONENT_CALENDAR/models_services_finance_frontend.ts | model+service (multi) | finance | cityfrontend/src/app/features/finance/ | DOUBLON probable de HEBERGEMENT/files_front/models_services_finance_frontend.ts. A diff. |
| COMPONENT_CALENDAR/models_services_hebergement_frontend.ts | model+service (multi) | hebergement | cityfrontend/src/app/features/hebergement/ (a eclater en models/{type-chambre,chambre,reservation,nuitee}.model.ts + services correspondants + composant calendrier) | **Source principale du module `hebergement` cote front + composant calendrier.** Fichier monolithique a eclater. |

---

## /INVENTORY

| Fichier source (chemin relatif depuis /INVENTORY) | Type | Domaine reel | Destination | Notes |
|---|---|---|---|---|
| files_back/pom.xml | build | infra | — | DOUBLON. |
| files_back/application.yml | config | infra | — | DOUBLON. |
| files_back/CitybackendApplication.java | bootstrap | infra | — | DOUBLON. |
| files_back/SecurityConfig.java | config | infra | — | DOUBLON. |
| files_back/RefreshTokenRequest.java | dto | auth | — | DOUBLON. |
| files_back/PasswordUtil.java | util | infra | — | DOUBLON. |
| files_back/core_entities.java | entity (multi) | core | — | DOUBLON. |
| files_back/AuthController_and_AuthService.java | controller+service | auth | — | DOUBLON. |
| files_back/structure_backend.txt | spec | spec | — | Reference. |
| files_back/structures_tables_core.sql | spec | spec | — | Reference. |
| files_back/structures_tables_schema_inventory.sql | spec | spec | — | Schema SQL inventory — reference cle. |
| entities_services_module_inventory.java | entity+service+dto+repo (multi) | inventory | `<base>/entity/inventory/` + `<base>/repository/inventory/` + `<base>/service/inventory/` + `<base>/dto/inventory/` (a eclater en Fournisseur, CategorieProduit, Produit, BonCommande, LigneBonCommande, BonSortie, LigneBonSortie, MouvementStock, AlerteStock, leurs repos/services/dto) | **Source principale du module `inventory` cote back.** Monolithique — eclater. |
| controleurs_module_inventory.java | controller (multi) | inventory | `<base>/controller/inventory/` (a eclater en FournisseurController, CategorieProduitController, ProduitController, BonCommandeController, BonSortieController, MouvementStockController, AlerteStockController) | **Source principale des controllers inventory.** Monolithique — eclater. |
| endpoints_module_inventory.txt | spec | spec | — | Reference des routes pour les controllers. |
| files_front/package.json | build | infra | — | DOUBLON. |
| files_front/angular.json | config | infra | — | DOUBLON. |
| files_front/index.html | bootstrap | infra | — | DOUBLON. |
| files_front/environment.ts | config | infra | — | DOUBLON. |
| files_front/app.module.ts | module | infra | — | DOUBLON. |
| files_front/app-routing.module.ts | routing | infra | — | DOUBLON. |
| files_front/app.component.ts | component | infra | — | DOUBLON. |
| files_front/auth-interceptor.interceptor.ts | interceptor | infra | — | DOUBLON. |
| files_front/error-interceptor.interceptor.ts | interceptor | infra | — | DOUBLON. |
| files_front/auth-guard.guard.ts | guard | auth | — | DOUBLON. |
| files_front/role-guard.guard.ts | guard | auth | — | DOUBLON. |
| files_front/structure_frontend.txt | spec | spec | — | Reference. |
| files_front/menu_user_par_role.txt | spec | spec | — | Reference. |
| files_front/module_routing_interfaces_models_services-frontend_module_inventory.ts | module+routing+models+services+components (multi) | inventory | cityfrontend/src/app/features/inventory/ (a eclater en `inventory.module.ts`, `inventory-routing.module.ts`, `models/{fournisseur,produit,categorie-produit,bon-commande,bon-sortie,mouvement-stock}.model.ts`, `services/*.service.ts`, plus tous les composants list/form/detail/dashboard) | **Source principale du module `inventory` cote front.** Fichier giga-monolithique — eclater rigoureusement. |

---

## /MENAGE

| Fichier source (chemin relatif depuis /MENAGE) | Type | Domaine reel | Destination | Notes |
|---|---|---|---|---|
| files_back/pom.xml | build | infra | — | DOUBLON. |
| files_back/application.yml | config | infra | — | DOUBLON. |
| files_back/CitybackendApplication.java | bootstrap | infra | — | DOUBLON. |
| files_back/SecurityConfig.java | config | infra | — | DOUBLON. |
| files_back/RefreshTokenRequest.java | dto | auth | — | DOUBLON. |
| files_back/PasswordUtil.java | util | infra | — | DOUBLON. |
| files_back/core_entities.java | entity (multi) | core | — | DOUBLON. |
| files_back/AuthController_and_AuthService.java | controller+service | auth | — | DOUBLON. |
| files_back/entities_services_module_hebergement.java | entity+service+dto+repo+controller (multi) | hebergement | — | DOUBLON probable de `HEBERGEMENT/entities_services_module_hebergement.java` (meme contenu Reservation/Chambre). A diff au moment integration `hebergement` ; ne PAS integrer ici. |
| files_back/structure_backend.txt | spec | spec | — | Reference. |
| files_back/structures_tables_schema_core_hebergement_menage.sql | spec | spec | — | Schema SQL menage (+ core + hebergement) — reference cle. |
| files_back/endpoints_module_hebergement.txt | spec | spec | — | DOUBLON probable de HEBERGEMENT/endpoints_module_hebergement.txt. Reference. |
| entities_dto_services_backend-menage.java | entity+dto+service+repo+controller (multi) | menage | `<base>/entity/menage/` + `<base>/repository/menage/` + `<base>/service/menage/` + `<base>/dto/menage/` + `<base>/controller/menage/` (a eclater : Personnel, Tache, Planning, HistoriqueMenage, leurs repos/services/dto/controllers) | **Source principale du module `menage` cote back.** Monolithique — eclater. |
| endpoints_module_menage.txt | spec | spec | — | Reference cle pour controllers menage. |
| files_front/package.json | build | infra | — | DOUBLON. |
| files_front/angular.json | config | infra | — | DOUBLON. |
| files_front/index.html | bootstrap | infra | — | DOUBLON. |
| files_front/environment.ts | config | infra | — | DOUBLON. |
| files_front/app.module.ts | module | infra | — | DOUBLON. |
| files_front/app-routing.module.ts | routing | infra | — | DOUBLON. |
| files_front/app.component.ts | component | infra | — | DOUBLON. |
| files_front/auth-interceptor.interceptor.ts | interceptor | infra | — | DOUBLON. |
| files_front/error-interceptor.interceptor.ts | interceptor | infra | — | DOUBLON. |
| files_front/auth-guard.guard.ts | guard | auth | — | DOUBLON. |
| files_front/role-guard.guard.ts | guard | auth | — | DOUBLON. |
| files_front/structure_frontend.txt | spec | spec | — | Reference. |
| files_front/menu_user_par_role.txt | spec | spec | — | Reference. |
| files_front/models_services_hebergement_frontend.ts | model+service (multi) | hebergement | cityfrontend/src/app/features/hebergement/ | **MAL CLASSE** — code hebergement dans /MENAGE. DOUBLON probable de HEBERGEMENT/COMPONENT_CALENDAR/models_services_hebergement_frontend.ts. A diff. |
| partie_front/text_prompt.txt | spec | spec | — | Brief originel du projet — reference pour comprendre l'intention. |

> **Manquant dans /MENAGE** : aucun code front dedie au menage (pas de `models_services_menage_frontend.ts`). **A creer entierement from-scratch** lors de l'integration `menage` cote front, en se basant sur `endpoints_module_menage.txt` et le DTO/entity vu dans `entities_dto_services_backend-menage.java`.

---

## /RESTAURANT

| Fichier source (chemin relatif depuis /RESTAURANT) | Type | Domaine reel | Destination | Notes |
|---|---|---|---|---|
| files_back/pom.xml | build | infra | — | DOUBLON. |
| files_back/application.yml | config | infra | — | DOUBLON. |
| files_back/CitybackendApplication.java | bootstrap | infra | — | DOUBLON. |
| files_back/SecurityConfig.java | config | infra | — | DOUBLON. |
| files_back/RefreshTokenRequest.java | dto | auth | — | DOUBLON. |
| files_back/PasswordUtil.java | util | infra | — | DOUBLON. |
| files_back/core_entities.java | entity (multi) | core | — | DOUBLON. |
| files_back/AuthController_and_AuthService.java | controller+service | auth | — | DOUBLON. |
| files_back/structure_backend.txt | spec | spec | — | Reference. |
| files_back/structures_tables_core.sql | spec | spec | — | Reference. |
| files_back/structures_tables_schema_restaurant.sql | spec | spec | — | Schema SQL restaurant — reference cle. |
| files_back/structures_tables_schema_core_restaurant.sql | spec | spec | — | Reference. |
| files_front/package.json | build | infra | — | DOUBLON. |
| files_front/angular.json | config | infra | — | DOUBLON. |
| files_front/index.html | bootstrap | infra | — | DOUBLON. |
| files_front/environment.ts | config | infra | — | DOUBLON. |
| files_front/app.module.ts | module | infra | — | DOUBLON. |
| files_front/app-routing.module.ts | routing | infra | — | DOUBLON. |
| files_front/app.component.ts | component | infra | — | DOUBLON. |
| files_front/auth-interceptor.interceptor.ts | interceptor | infra | — | DOUBLON. |
| files_front/error-interceptor.interceptor.ts | interceptor | infra | — | DOUBLON. |
| files_front/auth-guard.guard.ts | guard | auth | — | DOUBLON. |
| files_front/role-guard.guard.ts | guard | auth | — | DOUBLON. |
| files_front/structure_frontend.txt | spec | spec | — | Reference. |
| files_front/menu_user_par_role.txt | spec | spec | — | Reference. |
| models_services_hebergement_frontend.ts | model+service (multi) | hebergement | cityfrontend/src/app/features/hebergement/ | **MAL CLASSE** — code hebergement a la racine de /RESTAURANT. DOUBLON probable. A diff au moment integration `hebergement`. |
| point-vente/structures_tables_schema_clients.sql | spec | spec | — | Reference. |
| point-vente/structures_tables_schema_hebergement.sql | spec | spec | — | Reference. |
| point-vente/structures_tables_schema_finance.sql | spec | spec | — | Reference. |
| point-vente/structures_tables_schema_inventory.sql | spec | spec | — | Reference. |
| point-vente/models_services_clients_frontend.ts | model+service (multi) | clients | cityfrontend/src/app/features/clients/ | **MAL CLASSE** — code clients dans /RESTAURANT/point-vente. DOUBLON probable. |
| point-vente/models_services_finance_frontend.ts | model+service (multi) | finance | cityfrontend/src/app/features/finance/ | **MAL CLASSE** — DOUBLON probable de HEBERGEMENT/files_front/models_services_finance_frontend.ts. |
| point-vente/models_services_hebergement_frontend.ts | model+service (multi) | hebergement | cityfrontend/src/app/features/hebergement/ | **MAL CLASSE** — DOUBLON probable. |
| point-vente/models_services_inventory_frontend.ts | model+service (multi) | inventory | cityfrontend/src/app/features/inventory/ | **MAL CLASSE** — DOUBLON probable de INVENTORY/files_front/module_routing_interfaces_models_services-frontend_module_inventory.ts (segment models+services). A diff. |
| resultat_chatgpt/restaurant-backend-module.zip | archive | spec | — | Zip de la version eclatee (cf. arborescence ci-dessous). Ignorer le zip lui-meme. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../entity/restaurant/ArticleMenu.java | entity | restaurant | `<base>/entity/restaurant/ArticleMenu.java` | OK — deja correctement structure. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../entity/restaurant/CategorieMenu.java | entity | restaurant | `<base>/entity/restaurant/CategorieMenu.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../entity/restaurant/Commande.java | entity | restaurant | `<base>/entity/restaurant/Commande.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../entity/restaurant/LigneCommande.java | entity | restaurant | `<base>/entity/restaurant/LigneCommande.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../repository/restaurant/ArticleMenuRepository.java | repository | restaurant | `<base>/repository/restaurant/ArticleMenuRepository.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../repository/restaurant/CategorieMenuRepository.java | repository | restaurant | `<base>/repository/restaurant/CategorieMenuRepository.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../repository/restaurant/CommandeRepository.java | repository | restaurant | `<base>/repository/restaurant/CommandeRepository.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../repository/restaurant/LigneCommandeRepository.java | repository | restaurant | `<base>/repository/restaurant/LigneCommandeRepository.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../service/restaurant/CommandeService.java | service | restaurant | `<base>/service/restaurant/CommandeService.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../service/restaurant/MenuService.java | service | restaurant | `<base>/service/restaurant/MenuService.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../controller/restaurant/CommandeController.java | controller | restaurant | `<base>/controller/restaurant/CommandeController.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../controller/restaurant/MenuController.java | controller | restaurant | `<base>/controller/restaurant/MenuController.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../dto/restaurant/ArticleMenuDto.java | dto | restaurant | `<base>/dto/restaurant/ArticleMenuDto.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../dto/restaurant/CategorieMenuDto.java | dto | restaurant | `<base>/dto/restaurant/CategorieMenuDto.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../dto/restaurant/ChangeStatutRequest.java | dto | restaurant | `<base>/dto/restaurant/ChangeStatutRequest.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../dto/restaurant/CommandeDto.java | dto | restaurant | `<base>/dto/restaurant/CommandeDto.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../dto/restaurant/LigneCommandeDto.java | dto | restaurant | `<base>/dto/restaurant/LigneCommandeDto.java` | OK. |
| resultat_chatgpt/restaurant-backend-module/src/main/java/.../mapper/RestaurantMapper.java | mapper | restaurant | `<base>/mapper/RestaurantMapper.java` ou mieux `<base>/mapper/restaurant/RestaurantMapper.java` | OK — verifier que MapStruct 1.6.x est compatible. |
| resultat_chatgpt/cityfrontend_restaurant_module.zip | archive | spec | — | Zip — ignorer. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../restaurant.module.ts | module | restaurant | cityfrontend/src/app/features/restaurant/restaurant.module.ts | OK (ChatGPT a ecrit `modules/restaurant/`, on adapte vers `features/restaurant/` selon convention citybackend/CLAUDE.md). |
| resultat_chatgpt/cityfrontend_restaurant_module/.../restaurant-routing.module.ts | routing | restaurant | cityfrontend/src/app/features/restaurant/restaurant-routing.module.ts | OK. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../models/restaurant-models.ts | model | restaurant | cityfrontend/src/app/features/restaurant/models/restaurant-models.ts (ou eclater en `categorie-menu.model.ts`, `article-menu.model.ts`, `commande.model.ts`) | **RESOLU (arbitrage Tour 6) : source UNIQUE des DTOs restaurant.** Le `POS_avance_adapte/menu.model.ts` est abandonne. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../services/restaurant-api.service.ts | service | restaurant | cityfrontend/src/app/features/restaurant/services/restaurant-api.service.ts | OK. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../directives/datatable.directive.ts | directive | shared | cityfrontend/src/app/shared/directives/datatable.directive.ts | **RESOLU (arbitrage Tour 6) : confirme `shared/directives/`.** Directive DataTables generique reutilisable hors restaurant. Verifier qu'il n'existe pas deja dans `cityfrontend/src/app/directives/`. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/article-form/article-form.component.ts | component | restaurant | cityfrontend/src/app/features/restaurant/components/article-form/ | OK (+ .html + .scss correspondants). |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/article-form/article-form.component.html | template | restaurant | idem ci-dessus | OK. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/article-form/article-form.component.scss | style | restaurant | idem ci-dessus | OK. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/articles-list/articles-list.component.ts | component | restaurant | cityfrontend/src/app/features/restaurant/components/articles-list/ | OK. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/articles-list/articles-list.component.html | template | restaurant | idem | OK. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/articles-list/articles-list.component.scss | style | restaurant | idem | OK. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/categories-list/categories-list.component.ts | component | restaurant | cityfrontend/src/app/features/restaurant/components/categories-list/ | OK. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/categories-list/categories-list.component.html | template | restaurant | idem | OK. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/categories-list/categories-list.component.scss | style | restaurant | idem | OK. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/category-form/category-form.component.ts | component | restaurant | cityfrontend/src/app/features/restaurant/components/category-form/ | OK. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/category-form/category-form.component.html | template | restaurant | idem | OK. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/category-form/category-form.component.scss | style | restaurant | idem | OK. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/pos/pos.component.ts | component | restaurant | — | **RESOLU (arbitrage Tour 6) : NE PAS INTEGRER.** Version basique abandonnee, remplacee par `POS_avance_adapte/pos.component.ts`. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/pos/pos.component.html | template | restaurant | — | **RESOLU (arbitrage Tour 6) : NE PAS INTEGRER.** Idem. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../components/pos/pos.component.scss | style | restaurant | — | **RESOLU (arbitrage Tour 6) : NE PAS INTEGRER.** Idem. |
| resultat_chatgpt/cityfrontend_restaurant_module/.../README.md | doc | spec | — | Doc d'integration ChatGPT — reference. |
| resultat_chatgpt/POS_avance_adapte/pos.component.ts | component | restaurant | cityfrontend/src/app/features/restaurant/components/pos/ | **RESOLU (arbitrage Tour 6) : version retenue** — POS avance avec liaisons hebergement/clients/inventory/finance. |
| resultat_chatgpt/POS_avance_adapte/pos.component.html | template | restaurant | idem | **RESOLU (arbitrage Tour 6) : version retenue.** |
| resultat_chatgpt/POS_avance_adapte/pos.component.scss | style | restaurant | idem | **RESOLU (arbitrage Tour 6) : version retenue.** |
| resultat_chatgpt/POS_avance_adapte/menu.model.ts | model | restaurant | — | **RESOLU (arbitrage Tour 6) : NE PAS INTEGRER.** Doublon logique de `restaurant-models.ts` qui devient la source unique des DTOs. |
| resultat_chatgpt/POS_avance_adapte/menu.service.ts | service | restaurant | cityfrontend/src/app/features/restaurant/services/menu.service.ts | A INTEGRER — perimetre POS-specifique (panier, calcul total, lien chambre/facture). Distinct de `restaurant-api.service.ts` (CRUD admin menu/categorie). Les deux coexistent. |
| resultat_chatgpt/POS_avance_adapte/pos.models.ts | model | restaurant | cityfrontend/src/app/features/restaurant/models/pos.models.ts | OK. |
| resultat_chatgpt/POS_avance_adapte/POS_README.md | doc | spec | — | Doc — reference. |
| resultat_chatgpt/POS_avance_adapte/restaurant-role-guard-and-snippets.zip | archive | spec | — | Snippets role-guard — ignorer (deja en place dans `cityfrontend/src/app/guards/`). |
| resultat_chatgpt/POS_avance_adapte/recommandations_et_consignes.txt | spec | spec | — | Recommandations ChatGPT — reference. |

---

## Synthese

- **Total fichiers analyses** : ~130 fichiers (code + spec + scaffolding) repartis sur les 6 dossiers.
- **Fichiers de code unique a integrer** : ~25 (les fichiers monolithiques + l'arborescence eclatee restaurant).
- **Fichiers DOUBLONS de citybackend/cityfrontend** : ~85 (scaffolding repete 6x : Application, Security, Auth, App.module, etc.).
- **Fichiers SPEC** (txt, sql, md, zip) : ~35.

### Repartition par domaine reel (top 5)

| Domaine | Sources principales | Nb fichiers source unique |
|---|---|---|
| `restaurant` | `RESTAURANT/resultat_chatgpt/restaurant-backend-module/**` (16 fichiers eclates) + `RESTAURANT/resultat_chatgpt/cityfrontend_restaurant_module/**` (17 fichiers) + `RESTAURANT/resultat_chatgpt/POS_avance_adapte/**` (3 fichiers code) | ~36 |
| `hebergement` | `HEBERGEMENT/entities_services_module_hebergement.java` (mono) + `HEBERGEMENT/COMPONENT_CALENDAR/models_services_hebergement_frontend.ts` (mono) + 3 doublons `models_services_hebergement_frontend.ts` | 5 fichiers source (dont 2 vrais + 3 doublons) |
| `inventory` | `INVENTORY/entities_services_module_inventory.java` (mono) + `INVENTORY/controleurs_module_inventory.java` (mono) + `INVENTORY/files_front/module_routing_interfaces_models_services-frontend_module_inventory.ts` (mono) + 1 doublon `RESTAURANT/point-vente/models_services_inventory_frontend.ts` | 4 fichiers source |
| `finance` | `HEBERGEMENT/files_back/Services_module_finance.java` (mono) + `HEBERGEMENT/files_front/models_services_finance_frontend.ts` (mono) + 2 doublons | 4 fichiers source |
| `clients` | `HEBERGEMENT/files_back/Services_module_clients.java` (mono) + 4 instances de `models_services_clients_frontend.ts` (FINANCE, HEBERGEMENT/files_front, HEBERGEMENT/COMPONENT_CALENDAR, RESTAURANT/point-vente — toutes a diff) | 5 fichiers source (1 back + 4 front a deduplique) |
| `menage` | `MENAGE/entities_dto_services_backend-menage.java` (mono) | 1 fichier source back ; **0 fichier source front** (a creer from-scratch) |

### Doublons internes au stock (entre dossiers source)

- `models_services_hebergement_frontend.ts` apparait dans : `HEBERGEMENT/COMPONENT_CALENDAR/`, `MENAGE/files_front/`, `RESTAURANT/`, `RESTAURANT/point-vente/`. **A diff ; integrer une seule fois (la version la plus complete) cote `hebergement`.**
- `models_services_clients_frontend.ts` apparait dans : `FINANCE/files_front/`, `HEBERGEMENT/files_front/`, `HEBERGEMENT/COMPONENT_CALENDAR/`, `RESTAURANT/point-vente/`. **Idem.**
- `models_services_finance_frontend.ts` apparait dans : `HEBERGEMENT/files_front/`, `HEBERGEMENT/COMPONENT_CALENDAR/`, `RESTAURANT/point-vente/`. **Idem.**
- `entities_services_module_hebergement.java` apparait dans : `HEBERGEMENT/` (racine) et `MENAGE/files_back/`. **A diff ; integrer une seule fois cote `hebergement`.**
- `endpoints_module_hebergement.txt` : `HEBERGEMENT/` + `MENAGE/files_back/`. Doublon spec.
- `endpoints_module_client.txt`, `endpoints_module_finance.txt` : `FINANCE/files_back/` + `HEBERGEMENT/files_back/`. Doublons spec.
- `structures_tables_schema_*.sql` : largement dupliques entre `HEBERGEMENT/files_back/`, `HEBERGEMENT/COMPONENT_CALENDAR/`, `RESTAURANT/point-vente/`, etc. Tous specs.

### Doublons avec citybackend/ ou cityfrontend/ deja implementes

| Fichier source | Fichier deja present dans le projet | Statut |
|---|---|---|
| `<MODULE>/files_back/CitybackendApplication.java` (6 copies) | `citybackend/src/main/java/com/cityprojects/citybackend/CitybackendApplication.java` | DOUBLON — ignorer les 6. |
| `<MODULE>/files_back/SecurityConfig.java` (6 copies) | `citybackend/src/main/java/com/cityprojects/citybackend/config/SecurityConfig.java` | DOUBLON — ignorer. |
| `<MODULE>/files_back/PasswordUtil.java` (6 copies) | `citybackend/src/main/java/com/cityprojects/citybackend/util/PasswordUtil.java` | DOUBLON — ignorer. |
| `<MODULE>/files_back/RefreshTokenRequest.java` (6 copies) | `citybackend/src/main/java/com/cityprojects/citybackend/dto/auth/RefreshTokenRequest.java` | DOUBLON — ignorer. |
| `<MODULE>/files_back/core_entities.java` (6 copies, multi-classes) | `citybackend/src/main/java/com/cityprojects/citybackend/entity/core/{DBUser,Hotel,Role,UserSession}.java` | DOUBLON — ignorer. |
| `<MODULE>/files_back/AuthController_and_AuthService.java` (6 copies) | `citybackend/src/main/java/com/cityprojects/citybackend/controller/auth/AuthController.java` + `service/auth/AuthService.java` | DOUBLON — ignorer. |
| `<MODULE>/files_back/pom.xml` (6 copies) | `citybackend/pom.xml` | DOUBLON — ignorer. |
| `<MODULE>/files_back/application.yml` (6 copies) | `citybackend/src/main/resources/application.yml` | DOUBLON — ignorer. |
| `<MODULE>/files_front/app.module.ts` (6 copies) | `cityfrontend/src/app/app.module.ts` | DOUBLON — ignorer. |
| `<MODULE>/files_front/app-routing.module.ts` (6 copies) | `cityfrontend/src/app/app-routing.module.ts` | DOUBLON — ignorer. |
| `<MODULE>/files_front/app.component.ts` (6 copies) | `cityfrontend/src/app/app.component.ts` | DOUBLON — ignorer. |
| `<MODULE>/files_front/index.html` (6 copies) | `cityfrontend/src/index.html` | DOUBLON — ignorer. |
| `<MODULE>/files_front/environment.ts` (6 copies) | `cityfrontend/src/environments/environment.ts` | DOUBLON — ignorer. |
| `<MODULE>/files_front/auth-interceptor.interceptor.ts` (6 copies) | `cityfrontend/src/app/interceptors/auth-interceptor.interceptor.ts` | DOUBLON — ignorer. |
| `<MODULE>/files_front/error-interceptor.interceptor.ts` (6 copies) | `cityfrontend/src/app/interceptors/error-interceptor.interceptor.ts` | DOUBLON — ignorer. |
| `<MODULE>/files_front/auth-guard.guard.ts` (6 copies) | `cityfrontend/src/app/guards/auth-guard.guard.ts` | DOUBLON — ignorer. |
| `<MODULE>/files_front/role-guard.guard.ts` (6 copies) | `cityfrontend/src/app/guards/role-guard.guard.ts` | DOUBLON — ignorer. |
| `<MODULE>/files_front/package.json` (6 copies) | `cityfrontend/package.json` | DOUBLON — ignorer. |
| `<MODULE>/files_front/angular.json` (6 copies) | `cityfrontend/angular.json` | DOUBLON — ignorer. |
| `RESTAURANT/resultat_chatgpt/cityfrontend_restaurant_module/.../directives/datatable.directive.ts` | (a verifier) `cityfrontend/src/app/directives/has-role.directive.ts` existe mais pas de datatable.directive.ts | NON DOUBLON — a integrer dans `shared/directives/`. |

**Total doublons scaffolding** : 19 fichiers types x 6 modules = 114 lignes a ignorer (dont quelques-unes manquent dans certains modules : ex. `package.json` n'est que dans certains).

---

## Conflits / ambigus — Arbitrages Tour 6 (2026-05-06)

### ✅ RESOLUS (arbitrage utilisateur)

| # | Sujet | Decision retenue |
|---|---|---|
| 1 | POS basique (`cityfrontend_restaurant_module/.../components/pos/`) **vs** POS avance (`POS_avance_adapte/`) | ⭐ **POS avance** retenu (liaisons hebergement/clients/inventory/finance, plus aligne metier). POS basique abandonne — destination "—" dans le tableau. |
| 3 | `POS_avance_adapte/menu.model.ts` **vs** `restaurant-models.ts` (ArticleMenuDto) | ⭐ **`restaurant-models.ts` source UNIQUE** des DTOs restaurant. `menu.model.ts` abandonne. |
| 4 | `datatable.directive.ts` destination `shared` ou `restaurant` ? | ⭐ **`shared/directives/`** confirme (directive DataTables generique reutilisable hors restaurant). |
| 10 | `MENAGE` cote front (aucun source) | ⭐ **From-scratch** lors de `/integrate-module menage`, sur base `endpoints_module_menage.txt` + entity Personnel/Tache du back. |

### ⏸️ REPORTES — a arbitrer au moment de chaque `/integrate-module`

| # | Fichier | Hypothese 1 | Hypothese 2 | Justification report |
|---|---|---|---|---|
| 2 | `POS_avance_adapte/menu.service.ts` **vs** `cityfrontend_restaurant_module/.../services/restaurant-api.service.ts` | Garder les 2 : `menu.service.ts` (specialise POS) + `restaurant-api.service.ts` (CRUD complet) | Fusionner | Decision lors de `/integrate-module restaurant`. **Recommandation tour 6** : garder les 2, perimetres distincts. |
| 5 | Les 4 instances de `models_services_clients_frontend.ts` | Identiques → integrer la version la plus complete | Versions divergentes → diff trois-voies | A trancher au moment de `/integrate-module clients` via diff. |
| 6 | Les 4 instances de `models_services_hebergement_frontend.ts` | Idem | Idem | A trancher au moment de `/integrate-module hebergement`. |
| 7 | Les 3 instances de `models_services_finance_frontend.ts` | Idem | Idem | A trancher au moment de `/integrate-module finance`. |
| 8 | `MENAGE/files_back/entities_services_module_hebergement.java` **vs** `HEBERGEMENT/entities_services_module_hebergement.java` | Identiques → integrer une seule fois (cote `hebergement`) | Divergents → privilegier la version `/HEBERGEMENT/` | A diff au moment integration `hebergement`. |
| 9 | `RestaurantMapper.java` regroupe plusieurs entites (ArticleMenu, CategorieMenu, Commande, LigneCommande) | Conserver le mapper unique | Scinder en 4 mappers (`ArticleMenuMapper`, etc.) | A trancher lors de `/integrate-module restaurant` — convention CLAUDE.md §7 : 1 mapper par entite. |

---

## Modules sans code source (a developper from-scratch)

| Module | Statut |
|---|---|
| `admin` | Aucun fichier source dedie. A concevoir entierement (gestion superadmin Hotels/Roles/Users globaux). Reference partielle dans `core_entities.java` + `roles_utilisateurs.txt` racine. |
| `profile` | Composant frontend deja present dans `cityfrontend/src/app/profile/` ; backend a creer (endpoints `/users/me`, `/users/me/password`, `/users/me/avatar`). |
| `reporting` | Aucun fichier source. A concevoir (dashboards, JasperReports, exports financiers/occupations). |
| `notification` | Aucun fichier source. A concevoir (Mail, Kafka events). |
| `dolibarr` | Aucun fichier source. A scaffolder avec le skill `dolibarr` (Feign clients, sync factures/clients/paiements). |

---

## Regles d'integration (rappel a chaque `/integrate-module`)

1. **Lire cette cartographie en entree** ; n'integrer QUE les lignes dont `Domaine reel = <module-cible>`.
2. **Ne JAMAIS copier en bloc** `/<MODULE>/files_back/` ou `/<MODULE>/files_front/` — toutes les lignes `<MODULE>/files_*/` portant `Domaine reel = infra/auth/core` sont des doublons et **doivent rester intouchees**.
3. **Eclater les fichiers monolithiques** systematiquement en respectant les conventions §7 du CLAUDE.md racine : 1 entite = 1 fichier, 1 service = 1 fichier, 1 controller = 1 fichier, dans le bon package `<base>/<layer>/<module>/`.
4. **Pour chaque doublon entre dossiers source** (mentionne en colonne "Notes"), faire un diff trois-voies avant fusion ; ne pas integrer en double.
5. **Apres integration** : `/audit-module <module>` + `/multitenant-check` obligatoires avant commit.
6. Si un fichier source decouvert n'apparait pas ici → **arreter**, mettre a jour cette cartographie d'abord (Tour 6 a relancer en mode patch).
