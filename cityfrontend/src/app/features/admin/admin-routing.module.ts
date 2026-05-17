import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { SuperAdminGuard } from '../../guards/super-admin.guard';
import { AdminLayoutComponent } from './components/admin-layout/admin-layout.component';
import { HotelFormComponent } from './components/hotel-form/hotel-form.component';
import { HotelsListComponent } from './components/hotels-list/hotels-list.component';
import { ParametreFormComponent } from './components/parametre-form/parametre-form.component';
import { ParametresListComponent } from './components/parametres-list/parametres-list.component';
import { RolesListComponent } from './components/roles-list/roles-list.component';
import { UserFormComponent } from './components/user-form/user-form.component';
import { UsersListComponent } from './components/users-list/users-list.component';

/**
 * Routes du module `admin` (zone SUPERADMIN cross-tenant).
 *
 * Single source of truth pour le contrôle d'accès :
 *  - `SuperAdminGuard` est appliqué au niveau de la route racine
 *    (parent layout) avec `canActivate` + `canActivateChild`. Pas de
 *    `RoleGuard` cumulé côté `app-routing.module.ts` (cf. consigne
 *    Tour 31 — éviter la double vérification).
 *
 * Hiérarchie :
 *   /admin                                            (AdminLayoutComponent + sub-sidebar)
 *     ├── ''                  → redirect 'hotels'
 *     ├── hotels              → HotelsListComponent
 *     ├── hotels/new          → HotelFormComponent
 *     ├── hotels/:id          → HotelFormComponent
 *     ├── users               → UsersListComponent (cross-hotel, ?hotelId= optionnel)
 *     ├── hotels/:hotelId/users/new       → UserFormComponent (création scoped hôtel)
 *     ├── hotels/:hotelId/users/:userId   → UserFormComponent (édition scoped hôtel)
 *     ├── roles               → RolesListComponent (read-only)
 *     ├── parametres          → ParametresListComponent
 *     ├── parametres/new      → ParametreFormComponent
 *     └── parametres/:id      → ParametreFormComponent
 */
const routes: Routes = [
  {
    path: '',
    component: AdminLayoutComponent,
    canActivate: [SuperAdminGuard],
    canActivateChild: [SuperAdminGuard],
    children: [
      { path: '', redirectTo: 'hotels', pathMatch: 'full' },

      // Hotels
      { path: 'hotels', component: HotelsListComponent },
      { path: 'hotels/new', component: HotelFormComponent },

      // Users — création / édition scoped par hôtel (déclarées AVANT
      // `hotels/:id` pour que le router résolve d'abord le segment plus
      // spécifique. Angular ne fait pas d'ordre par "spécificité" — c'est
      // l'ordre de déclaration qui prime, cf. menage-routing).
      { path: 'hotels/:hotelId/users/new', component: UserFormComponent },
      { path: 'hotels/:hotelId/users/:userId', component: UserFormComponent },
      { path: 'hotels/:id', component: HotelFormComponent },

      // Users (vue cross-hotel) + création/édition standalone.
      // L'hôtel est choisi via un champ du formulaire (pas en path param)
      // — cf. consigne user 2026-05-17 : « le formulaire de création doit
      // s'ouvrir normalement, ensuite on choisit un hôtel via un champ ».
      // Les routes path-positioned `hotels/:hotelId/users/...` restent pour
      // rétro-compat (entrée depuis le détail d'un hôtel).
      { path: 'users', component: UsersListComponent },
      { path: 'users/new', component: UserFormComponent },
      { path: 'users/:userId', component: UserFormComponent },

      // Roles (read-only)
      { path: 'roles', component: RolesListComponent },

      // Parametres
      { path: 'parametres', component: ParametresListComponent },
      { path: 'parametres/new', component: ParametreFormComponent },
      { path: 'parametres/:id', component: ParametreFormComponent },
    ],
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class AdminRoutingModule {}
