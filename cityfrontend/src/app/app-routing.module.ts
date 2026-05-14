import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { AuthLayoutComponent } from './auth/auth-layout/auth-layout.component';
import { MainLayoutComponent } from './core/main-layout/main-layout.component';
import { LoginComponent } from './auth/login/login.component';
import { DashboardComponent } from './core/dashboard/dashboard.component';
import { ProfileComponent } from './profile/profile/profile.component';

import { AuthGuard } from './guards/auth-guard.guard';
import { RoleGuard } from './guards/role-guard.guard';
import { SuperAdminGuard } from './guards/super-admin.guard';

const routes: Routes = [
  // Routes publiques (sans authentification)
  {
    path: '',
    component: AuthLayoutComponent,
    children: [
      { path: '', redirectTo: '/login', pathMatch: 'full' },
      { path: 'login', component: LoginComponent },
    ]
  },
  
  // Routes privées (avec authentification)
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [AuthGuard],
    children: [
      { path: 'dashboard', component: DashboardComponent },
      { path: 'profile', component: ProfileComponent },

      // Module Clients & Sociétés — feature lazy (Tour 8, 2026-05-05)
      // RoleGuard est appliqué finement à l'intérieur du module pour
      // distinguer lecture (incl. RESREC) et écriture.
      {
        path: 'clients',
        loadChildren: () =>
          import('./features/clients/clients.module').then(m => m.ClientsModule),
        canActivate: [RoleGuard],
        data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC'] }
      },

      // Module Hébergement (Réservations, calendrier, check-in) — feature lazy
      // (Tour 11, 2026-05-05). RoleGuard fin appliqué dans le routing du module.
      {
        path: 'hebergement',
        loadChildren: () =>
          import('./features/hebergement/hebergement.module').then(m => m.HebergementModule),
        canActivate: [RoleGuard],
        data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC'] }
      },

      // Module Inventory (Produits, bons de commande, stocks) — feature lazy
      // (Tour 16, 2026-05-05). RoleGuard fin appliqué dans le routing du module.
      {
        path: 'inventory',
        loadChildren: () =>
          import('./features/inventory/inventory.module').then(m => m.InventoryModule),
        canActivate: [RoleGuard],
        data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'MAGASIN'] }
      },

      // Module Finance (Factures, paiements, avoirs, comptes) — feature lazy
      // (Tour 19, 2026-05-05). RoleGuard fin appliqué dans le routing du module
      // pour distinguer lecture (incl. RESREC) et écriture.
      {
        path: 'finance',
        loadChildren: () =>
          import('./features/finance/finance.module').then(m => m.FinanceModule),
        canActivate: [RoleGuard],
        data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC'] }
      },

      // Module Comptabilité (Exercices, PCG, mapping, journaux, écritures,
      // TVA, états de synthèse) — feature lazy (Tour B7, 2026-05-08).
      // RoleGuard fin appliqué dans le routing du module : lecture
      // SUPERADMIN/ADMIN/GERANT, écriture SUPERADMIN/ADMIN.
      {
        path: 'comptabilite',
        loadChildren: () =>
          import('./features/comptabilite/comptabilite.module').then(m => m.ComptabiliteModule),
        canActivate: [RoleGuard],
        data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT'] }
      },

      // Module Restaurant (catalogue articles + catégories) — feature lazy
      // (Tour 23, 2026-05-05). Périmètre catalogue uniquement ; le POS et
      // la gestion des commandes seront ajoutés au Tour 24+ (cf. CARTOGRAPHIE_MODULES.md).
      // RoleGuard fin appliqué dans le routing du module pour distinguer lecture
      // (incl. RECEPTION/RESREC pour consultation menu) et écriture (RESTAURANT).
      {
        path: 'restaurant',
        loadChildren: () =>
          import('./features/restaurant/restaurant.module').then(m => m.RestaurantModule),
        canActivate: [RoleGuard],
        data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'RESTAURANT'] }
      },

      // Module Ménage (Personnel, Tâches, Dashboard) — feature lazy from-scratch
      // (Tour 27, 2026-05-05). Pas de code source frontend pré-existant
      // (cf. CARTOGRAPHIE_MODULES.md §menage). RoleGuard fin appliqué
      // dans le routing du module pour distinguer lecture (incl. RECEPTION
      // et MENAGE pour consultation) et écriture managériale (ADMIN/GERANT).
      // Périmètre Tour 27 : CRUD personnel + tâches + workflow + dashboard KPI.
      // Différé Tour 28+ : planning (vue calendaire), statistiques (Chart.js),
      // historique.
      {
        path: 'menage',
        loadChildren: () =>
          import('./features/menage/menage.module').then(m => m.MenageModule),
        canActivate: [RoleGuard],
        data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'MENAGE'] }
      },

      // Module Administration (hôtels, utilisateurs, rôles, paramètres) —
      // feature lazy SUPERADMIN-only from-scratch (Tour 31, 2026-05-09).
      // Tour 38 (H7) — defense-in-depth : `AuthGuard` + `SuperAdminGuard` sont
      // désormais appliqués AUSSI au niveau parent du chargement lazy, EN PLUS
      // du `SuperAdminGuard` interne au module (`canActivate` + `canActivateChild`
      // sur la route racine du layout admin, cf. `admin-routing.module.ts`).
      // Cela bloque tout accès non-SUPERADMIN avant même le téléchargement du
      // chunk lazy (et garantit qu'une régression interne au module ne suffirait
      // pas à exposer la surface admin). La double vérification est volontairement
      // assumée comme ceinture + bretelles sur ce périmètre sensible.
      {
        path: 'admin',
        canActivate: [AuthGuard, SuperAdminGuard],
        loadChildren: () =>
          import('./features/admin/admin.module').then(m => m.AdminModule),
      },

      // Module Hotel-Admin (gestion users par ADMIN du tenant courant) — Tour B
      // (2026-05-13). Périmètre : `/hotel-admin/users` (liste + CRUD + actions
      // verrou/reset/désactiver). Le tenant est résolu côté serveur via JWT
      // (`@PreAuthorize("hasRole('ADMIN')")` sur `HotelUserController`).
      // Pas de SUPERADMIN ici — SUPERADMIN passe par `/admin/users` (cross-tenant).
      {
        path: 'hotel-admin',
        loadChildren: () =>
          import('./features/hotel-admin/hotel-admin.module').then(m => m.HotelAdminModule),
        canActivate: [RoleGuard],
        data: { roles: ['ADMIN'] }
      },

      // Module Produits & Catégories (ADMIN, GERANT, SUPERADMIN)
      /*{
        path: 'products',
        loadChildren: () => import('./modules/products/products.module').then(m => m.ProductsModule),
        canActivate: [RoleGuard],
        data: { roles: ['ADMIN', 'GERANT', 'SUPERADMIN'] }
      },
      
      // Module Commandes & Sorties (MAGASIN, GERANT, RESTAURANT, RESREC, SUPERADMIN)
      {
        path: 'orders',
        loadChildren: () => import('./modules/orders/orders.module').then(m => m.OrdersModule),
        canActivate: [RoleGuard],
        data: { roles: ['MAGASIN', 'GERANT', 'RESTAURANT', 'RESREC', 'SUPERADMIN'] }
      },
      
      // Module Réservations (RECEPTION, GERANT, RESREC, SUPERADMIN)
      {
        path: 'reservations',
        loadChildren: () => import('./modules/reservations/reservations.module').then(m => m.ReservationsModule),
        canActivate: [RoleGuard],
        data: { roles: ['RECEPTION', 'GERANT', 'RESREC', 'SUPERADMIN'] }
      },
      
      // Module Paiements (RECEPTION, GERANT, RESREC, SUPERADMIN)
      {
        path: 'payments',
        loadChildren: () => import('./modules/payments/payments.module').then(m => m.PaymentsModule),
        canActivate: [RoleGuard],
        data: { roles: ['RECEPTION', 'GERANT', 'RESREC', 'SUPERADMIN'] }
      },
      
      // Module Hôtels & Chambres (ADMIN, GERANT, SUPERADMIN)
      {
        path: 'hotels',
        loadChildren: () => import('./modules/hotels/hotels.module').then(m => m.HotelsModule),
        canActivate: [RoleGuard],
        data: { roles: ['ADMIN', 'GERANT', 'SUPERADMIN'] }
      },
      
      // Module Clients & Sociétés — déclaré ci-dessus en route active (Tour 8).
      // Module Restaurant — déclaré ci-dessus en route active (Tour 23).
      // Module Ménage — déclaré ci-dessus en route active (Tour 27, /menage).
      
      // Module Reporting (ADMIN, GERANT, SUPERADMIN)
      {
        path: 'reporting',
        loadChildren: () => import('./modules/reporting/reporting.module').then(m => m.ReportingModule),
        canActivate: [RoleGuard],
        data: { roles: ['ADMIN', 'GERANT', 'SUPERADMIN'] }
      }*/
    ]
  },
  
  // Route de fallback — redirige vers /dashboard. L'AuthGuard se chargera de
  // rediriger vers /login si l'utilisateur n'est pas authentifié. Évite la
  // boucle login → dashboard pour les utilisateurs déjà connectés qui suivent
  // un lien cassé.
  { path: '**', redirectTo: '/dashboard' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes, {
    // Configuration pour Angular 20
    initialNavigation: 'enabledBlocking',
    enableTracing: false,
    bindToComponentInputs: true // Nouvelle fonctionnalité Angular 16+
  })],
  exports: [RouterModule]
})
export class AppRoutingModule { }