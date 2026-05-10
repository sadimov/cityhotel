# cityfrontend — SPA Angular 21.2

> Voir aussi le `CLAUDE.md` à la racine pour le contexte produit, les règles métier et l'i18n.

## 1. Stack (mai 2026)

> **🎯 Stratégie en deux paliers** (arbitrage Tour 1, 2026-05-05) :
> - **Palier 1 (actuel — effectif depuis Tour 2B 2026-05-06)** : **Angular 21.2.x + TypeScript 5.9.x** + Node 24 LTS. La peerDep d'Angular 21 a forcé l'amendement TS 5.8 → 5.9 (option arbitrée user, Tour 2B).
> - **Palier 2 (en même temps que back palier 2)** : Angular 22 + Vitest si stables.
>
> **Bloquants pré-Vague 1 — TOUS RÉSOLUS** :
> 1. ~~Tailwind v4.1 unifié~~ — **fait** (`tailwindcss@4.1.x` + `@tailwindcss/postcss@4.1.x` ensemble en `dependencies`, plus de `tailwindcss@3.4.x` cohabitant).
> 2. ~~Ajouter `@ngx-translate/core@^16.0.4` + `@ngx-translate/http-loader@^16.0.1`~~ — **fait au Tour 4B (2026-05-06)**. `CoreModule` câble `TranslateModule.forRoot` + `TranslateHttpLoader` (`assets/i18n/{fr,ar,en}.json`). `TranslationService` est devenu un wrappeur synchrone autour de `TranslateService` (API publique conservée, signature `translate(key, fallback)` à 2 args supportée pour compat HeaderComponent). Pipe maison `TranslatePipe` supprimé — `| translate` est désormais résolu par ngx-translate.
> 3. ~~Supprimer `@types/chart.js@^2.9.41`~~ — **fait** (obsolète, conflit avec types natifs Chart.js v4).
> 4. ~~Migration Angular 20 → 21.2~~ — **fait au Tour 2B (2026-05-06)**. `tsc --noEmit` vert, `ng build --configuration development` vert (10.7 s, 4.39 MB initial). ESLint legacy `.eslintrc.json` remplacé par `eslint.config.js` (flat config, requis par `@typescript-eslint` v8 + ESLint v9).

### Palier 1 — détail (cible atteinte)
- **Angular 21.2.x** (TypeScript **5.9.x**). **Pas Angular 20 ou inférieur** comme cible long-terme.
- Architecture **NgModule** (non standalone) pour cohérence avec l'existant. Schematic `ng update` a ajouté `standalone: false` explicite partout.
- **No SSR** (cf. exigence d'usage de `localStorage` pour le JWT).
- **RxJS 7.8+** (reste sur 7.8, pas de bump majeur), **zone.js 0.15+** (peerDep obligatoire Angular 21). **Ne PAS activer le zoneless** — cassera SweetAlert2, jQuery/DataTables triggers.
- UI : **Tailwind v4.1 unifié** (config CSS-first via `@theme {}` dans le CSS principal — plus de `tailwind.config.js`), **Bootstrap 5.3.6** (layout/grid), **jQuery 3.7** + **DataTables 2.3+** (tables avancées via wrapper maison `<app-data-table>`, pas `angular-datatables`), **SweetAlert2 11.17+**, **Chart.js 4.5+** (sans `@types/chart.js`). **jsPDF/jspdf-autotable** : restent en v2/v3 actuel, bump v3/v5 différé Vague 2 (breaking signatures). **date-fns 3.x** : bump v4 différé Vague 2.
- État : **NgRx 21.x** (`store`, `effects`, `entity`, `component-store`) pour le state complexe (POS, reporting, dashboards). Pour les états locaux feature → services + RxJS.
- i18n : **@ngx-translate/core 16.0.4** + **@ngx-translate/http-loader 16.0.1** — fr / ar (RTL) / en. (Pas de 16.1.x stable ; passage à 17.x reporté car récent.)
- Build : Angular CLI **21.2.x**, **Node.js 24 LTS** (Krypton), npm ≥ 10.9. `engines.node: ">=24.0.0"`, `engines.npm: ">=10.9.0"`.
- Lint : **@angular-eslint 21.3.x** (flat config `eslint.config.js`, le legacy `.eslintrc.json` n'est plus supporté avec `@typescript-eslint` v8 + ESLint v9). Le fichier `eslint.config.js` reproduit la matrice de règles historique + ajoute les `globals` browser/jasmine. La règle `@angular-eslint/prefer-standalone` est désactivée (architecture NgModule assumée).
- Tests : **Karma + Jasmine maintenus en palier 1** (toujours supportés Angular 21.2). Migration vers **Vitest** différée au palier 2.
- Port dev : `4200`. Backend cible : `http://localhost:8080/citybackend`.

### Historique — migration Angular 20 → 21.2 (Tour 2B, 2026-05-06)
Commande utilisée : `ng update @angular/core@21 @angular/cli@21 --allow-dirty`, puis recovery manuelle de `package.json` (régression `^20.0.0` à corriger), `rm -rf node_modules package-lock.json && npm install`.
- **Schematics auto appliqués (à conserver)** : `standalone: false` explicite, `provideZoneChangeDetection({ eventCoalescing: true })` injecté dans `src/main.ts`, `tsconfig.json` `lib` actualisé, 6 templates HTML migrés vers control flow `@if/@for` (sidebar, header, profile, dashboard, login, main-layout).
- **Manuel Tour 2B** : `package.json` figé sur `^21.2.x` Angular, TS `~5.9.0`, `engines.node ">=24.0.0"`, ESLint flat config (`eslint.config.js`) — voir bloc Lint ci-dessus.
- **Reportés Vague 2** : `date-fns` v3 → v4, `jspdf` v2 → v3, `jspdf-autotable` v3 → v5 (breaking signatures, hors scope migration framework).

### Historique — installation NgRx 21 + migration AuthService (Tour 5B, 2026-05-06)
Versions installées : `@ngrx/store@21.1.0`, `@ngrx/effects@21.1.0`, `@ngrx/entity@21.1.0`, `@ngrx/store-devtools@21.1.0` (dernière 21.x stable, peerDep `@angular/core ^21.0.0` validée).
- **Root feature stores** : `auth` (token + user + hôtel + roles + loading + error) et `ui` (langue + sidebarCollapsed + theme), câblés dans `AppModule` via `StoreModule.forRoot` + `EffectsModule.forRoot([AuthEffects, UiEffects])` + `StoreDevtoolsModule.instrument`. **Tous les `runtimeChecks` sont activés** (immutability + serializability + actionWithinNgZone + actionTypeUniqueness).
- **AuthService** conservé en façade publique : la signature reste compatible (login/logout/refreshToken/getCurrentUser/isAuthenticated/hasRole/getToken). L'implémentation interne dispatch désormais des actions et lit le store via `take(1)` ; le JWT reste persisté en `localStorage` (clés `city_hotel_token` + `city_hotel_user`) — c'est la source de vérité au démarrage avant que l'effect `bootstrap$` ne réhydrate le store.
- **Intercepteur JWT** : continue de lire `localStorage` directement (un sélecteur NgRx synchrone serait overkill pour ce cas) ; documenté dans `auth.storage.ts`.
- **Persistance UI** opt-in dans `UiEffects` (langue + sidebar) — pas de meta-reducer global.
- **i18n** : 9 nouvelles clés `error.auth.*` / `error.network.*` / `error.server.*` ajoutées en fr/ar/en. Aucun texte affiché en clair dans les effects.

> Voir `/sync-tech` pour les versions cibles de chaque lib. Toute rétrogradation sous le palier 1 est refusée.

## 2. Arborescence

```
src/app/
├── core/                            # singletons (services HTTP, intercepteurs, guards)
│   ├── auth/
│   │   ├── auth.service.ts
│   │   ├── auth.guard.ts
│   │   ├── role.guard.ts
│   │   └── jwt.interceptor.ts
│   ├── http/
│   │   ├── error.interceptor.ts
│   │   └── api-base.service.ts
│   ├── i18n/translate-loader.factory.ts
│   └── core.module.ts
├── shared/                          # composants/pipes/directives réutilisables
│   ├── components/
│   │   ├── header/
│   │   ├── sidebar/
│   │   ├── footer/
│   │   ├── data-table/              # wrapper DataTables.net
│   │   ├── confirm-modal/
│   │   └── loader/
│   ├── pipes/
│   ├── directives/
│   └── shared.module.ts
├── layouts/
│   ├── auth-layout/                 # login, mdp oublié
│   └── app-layout/                  # header + sidebar + router-outlet
├── features/
│   ├── auth/
│   ├── clients/
│   ├── inventory/
│   ├── finance/
│   ├── hebergement/
│   ├── restaurant/
│   ├── menage/
│   ├── reporting/
│   ├── admin/
│   └── profile/
├── store/                            # NgRx root feature stores (Tour 5B, 2026-05-06)
│   ├── auth/                         # token + currentUser + currentHotel + roles + loading + error
│   │   ├── auth.actions.ts           # createActionGroup → AuthActions.{login, loginSuccess, ...}
│   │   ├── auth.reducer.ts
│   │   ├── auth.effects.ts           # login$ / logout$ / refreshToken$ / bootstrap$ / loadCurrentUser$
│   │   ├── auth.selectors.ts         # selectCurrentUser, selectCurrentHotel, selectRoles, selectIsAuthenticated, selectHasRole(...)
│   │   ├── auth.state.ts
│   │   ├── auth.api.ts               # couche HTTP pure pour les effects
│   │   ├── auth.storage.ts           # helpers localStorage (JWT + LoginResponse sérialisé)
│   │   └── index.ts                  # barrel
│   └── ui/                           # currentLang + sidebarCollapsed + theme
│       ├── ui.actions.ts
│       ├── ui.reducer.ts
│       ├── ui.effects.ts             # persistance localStorage opt-in (langue + sidebar)
│       ├── ui.selectors.ts           # selectCurrentLang, selectSidebarCollapsed, selectIsRtl
│       ├── ui.state.ts
│       └── index.ts
├── app-routing.module.ts
├── app.module.ts
└── app.component.ts

src/assets/
├── i18n/  fr.json  ar.json  en.json
└── images/
```

## 3. Conventions

| Élément              | Convention                              | Exemple                                  |
|----------------------|-----------------------------------------|------------------------------------------|
| Module feature       | `<feature>.module.ts`, lazy loaded      | `inventory.module.ts`                    |
| Routing feature      | `<feature>-routing.module.ts`           | `inventory-routing.module.ts`            |
| Component            | dossier kebab-case, suffixe `.component`| `bon-commande-list/bon-commande-list.component.ts` |
| Service              | `<name>.service.ts` dans `services/`    | `bon-commande.service.ts`                |
| Modèle/Interface     | `<name>.model.ts` ou `.interface.ts`    | `bon-commande.model.ts`                  |
| Guard                | `<name>.guard.ts`                       | `auth.guard.ts`                          |
| Pipe                 | `<name>.pipe.ts`                        | `currency-mru.pipe.ts`                   |
| Constantes           | `SCREAMING_SNAKE_CASE` exportées        | `API_BASE_URL`                           |

**Pas de préfixe `I` sur les interfaces.** Pas de classes pour les modèles — interfaces ou types.

## 4. Patterns

### 4.1 Service HTTP

```typescript
@Injectable({ providedIn: 'root' })
export class BonCommandeService {
  private readonly base = `${environment.apiUrl}/api/bons-commande`;

  constructor(private http: HttpClient) {}

  findById(id: number): Observable<BonCommande> {
    return this.http.get<BonCommande>(`${this.base}/${id}`);
  }

  page(req: PageRequest): Observable<Page<BonCommande>> {
    return this.http.get<Page<BonCommande>>(this.base, { params: toParams(req) });
  }

  create(dto: BonCommandeCreate): Observable<BonCommande> {
    return this.http.post<BonCommande>(this.base, dto);
  }
}
```

- Pas de `Promise` — toujours `Observable`. Désinscrire avec `takeUntil(this.destroy$)` ou pipe `async`.
- Le `hotelId` n'est **jamais** envoyé par le client — le backend le lit du JWT.

### 4.2 Component liste avec DataTables

Utiliser le wrapper `<app-data-table>` du `shared/`. Si DataTables direct :

```typescript
ngOnInit() {
  this.dtOptions = {
    pagingType: 'full_numbers',
    pageLength: 10,
    serverSide: true,
    processing: true,
    language: this.dtLang(this.translate.currentLang),  // i18n
    ajax: (params, callback) => {
      this.service.page(toPageRequest(params)).subscribe(p =>
        callback({ recordsTotal: p.totalElements, recordsFiltered: p.totalElements, data: p.content })
      );
    },
    columns: [...],
  };
}

ngOnDestroy() { this.dtTrigger.complete(); }
```

### 4.3 i18n

```html
<button class="btn btn-primary">{{ 'inventory.bonCommande.create' | translate }}</button>
<input [placeholder]="'common.search' | translate" />
```

```typescript
this.translate.get('error.client.notFound').subscribe(msg => Swal.fire({ icon: 'error', text: msg }));
```

- Clé : `<feature>.<entité>.<action>` (ex. `inventory.bonCommande.delete.confirm`).
- RTL pour l'arabe : ajouter `dir="rtl"` sur `<html>` quand `currentLang === 'ar'`.

### 4.4 Guards

```typescript
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const expected = route.data?.['roles'] as string[] | undefined;
  if (!expected || auth.hasAnyRole(expected)) return true;
  return router.createUrlTree(['/forbidden']);
};
```

Routes :
```typescript
{
  path: 'bons-commande',
  loadChildren: () => import('./features/inventory/inventory.module').then(m => m.InventoryModule),
  canActivate: [authGuard, roleGuard],
  data: { roles: ['SUPERADMIN', 'GERANT', 'MAGASIN'] }
}
```

### 4.5 Intercepteur JWT

```typescript
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).getToken();
  return next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req);
};
```

 ### 4.6 Models partagés (`shared/models/`, Tour 40ter)

  `shared/models/api.model.ts` centralise les interfaces communes (superset des 5 variantes historiques par feature) :

  ```ts
  import { ApiResponse, PageResponse, PageRequest } from '../../shared/models/api.model';
  ```

  Les 5 `features/<module>/models/api.model.ts` font désormais des `re-exports` (compat sans casser les imports). Migration progressive : à terme, supprimer les locaux +
  corriger les imports.

  ### 4.7 Lookup maps statuts (Tour 40ter)

  Au lieu d'un `switch` dupliqué entre composants, exporter les maps lookup depuis le model :

  ```ts
  // features/hebergement/models/reservation.model.ts
  export const STATUT_RESERVATION_BADGE_MAP: Record<StatutReservation, string> = {
    EN_ATTENTE: 'text-bg-warning',
    CONFIRMEE: 'text-bg-info',
    ARRIVEE: 'text-bg-success',
    PARTIE: 'text-bg-secondary',
    ANNULEE: 'text-bg-danger',
  };
  export function statutReservationKey(s: StatutReservation): string {
    return `hebergement.statut.${s.toLowerCase()}`;
  }
  ```

  Composants utilisent : `STATUT_RESERVATION_BADGE_MAP[statut]` (lookup O(1) au lieu de switch).
  Composants utilisent : `STATUT_RESERVATION_BADGE_MAP[statut]` (lookup O(1) au lieu de switch).

  ### 4.8 SuperAdminGuard (Tour 31)

  `guards/super-admin.guard.ts` — guard `canActivate` + `canActivateChild` qui :
  - Lit le JWT via `AuthService.getCurrentUser()`
  - Redirect `/login` si non auth, `/dashboard` si auth non-SUPERADMIN
  - Combiné avec `AuthGuard` parent dans `app-routing.module.ts` pour defense-in-depth

  ```ts
  {
    path: 'admin',
    canActivate: [AuthGuard, SuperAdminGuard],
    loadChildren: () => import('./features/admin/admin.module').then(m => m.AdminModule)
  }
  ```
  
## 5. Identité graphique

Source : `consignes_design_interface_graphique.txt`.

- Couleur primaire : bleu clair (≈ Bootstrap `bg-info` / Tailwind `sky-400`/`sky-500`).
- Fonds : blanc / gris clair.
- Header : logo hôtel + nom à gauche, profil + notifications + déconnexion à droite.
- Sidebar pliable (icônes seules / collapsed / expanded).
- Boutons : ombrés en bleu clair, transitions douces (`transition-colors duration-200`).

Définir un fichier `src/assets/styles/_theme.scss` qui fixe les variables CSS et les classes utilitaires (`btn-primary-city`, etc.).

## 6. Sidebar

Voir le détail des entrées dans `Prompts_Backend_Frontend.txt`. Construit à partir d'une config typée :

```typescript
export const MENU_ITEMS: MenuItem[] = [
  { label: 'inventory.products', icon: '🛒', children: [...], roles: ['ADMIN','GERANT','SUPERADMIN'] },
  { label: 'reservation', icon: '🗓️', children: [...], roles: ['RECEPTION','GERANT','RESREC','SUPERADMIN'] },
  // ...
];
```

Le composant `<app-sidebar>` filtre les items selon les rôles du user.

## 7. State management

Stratégie graduée :
- **Composant local** : `signal()` + `computed()` (Angular 21 stable) ou simple `BehaviorSubject` dans un service feature.
- **Feature complexe** (ex. POS Restaurant, Reporting dashboard) : **NgRx Component Store** local au feature.
- **Global** (auth user courant, hôtel courant, préférences) : **NgRx Store 21.x** + Effects + Entity.

**État de l'installation (Tour 5B, 2026-05-06)** :
- NgRx 21.1.0 installé (`@ngrx/store`, `@ngrx/effects`, `@ngrx/entity`, `@ngrx/store-devtools`).
- **Deux feature stores root déjà câblés** dans `AppModule` :
  - `auth` (`store/auth/`) — flow login/logout/refreshToken/bootstrap. AuthService est désormais une façade qui dispatch des actions ; les guards et l'intercepteur conservent leur API.
  - `ui` (`store/ui/`) — langue + sidebarCollapsed + theme. Persistance localStorage opt-in via les effects (clés `city_hotel_lang`, `city_hotel_sidebar_collapsed`).
- **Convention pour les feature modules métier** (clients, inventory, finance, ...) : chacun ouvrira son propre dossier `store/<feature>/` câblé dans le feature module via `StoreModule.forFeature(...)` + `EffectsModule.forFeature([...])`. **Pas** de feature store déclaré dans le root — réservé à `auth` + `ui`.
- Tous les `runtimeChecks` strict sont activés (immutability + serializability + actionWithinNgZone + actionTypeUniqueness). Toute action / state non-sérialisable lèvera une erreur en dev.

Ne pas mettre toute l'app dans un store global — réserver NgRx aux flux complexes ou partagés entre features.

## 8. Erreurs récurrentes à éviter

Voir `ERREURS_AUDIT_A_EVITER.html`. Quelques classiques côté front :
- Souscriptions sans `unsubscribe` (fuites mémoire).
- Manipuler le DOM avec jQuery quand un binding Angular existe.
- Mettre du business dans le template (utiliser un getter ou un pipe pur).
- Recharger la page au lieu d'invalider une observable.
- Construire l'URL d'API en concaténation au lieu de `environment.apiUrl`.
- Laisser des `console.log` en production.
- Ne pas gérer les états `loading` / `empty` / `error` dans une vue de liste.

## 9. Lancer un nouveau composant

```bash
ng generate module features/menage --route menage --module app
ng generate component features/menage/components/tache-list
ng generate service features/menage/services/tache
```

Ou utiliser `/new-component <nom> <feature>` (Claude Code scaffolding).

## 10. Build & déploiement

```bash
npm run build              # dist/cityfrontend
npm run build -- --configuration=production
```

Servir via Nginx (config à fournir dans `Tech_DevOPS/`) ou intégrer au JAR backend (option Spring Boot static resources).
