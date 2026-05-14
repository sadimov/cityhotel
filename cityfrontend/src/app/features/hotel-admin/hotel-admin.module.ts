import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { TranslateModule } from '@ngx-translate/core';

import { UserFormComponent } from './components/user-form/user-form.component';
import { UsersListComponent } from './components/users-list/users-list.component';
import { HotelAdminRoutingModule } from './hotel-admin-routing.module';

/**
 * Module feature `hotel-admin` — gestion des utilisateurs par un ADMIN
 * d'hôtel (périmètre du tenant courant). Chargé en lazy depuis
 * `app-routing.module.ts`.
 *
 * Décision de packaging : module séparé de `features/admin/` (SUPERADMIN
 * cross-tenant) pour clarifier le routing et les responsabilités :
 *  - `/admin/...`        → SUPERADMIN (cross-hotel via `SuperAdminGuard`)
 *  - `/hotel-admin/...`  → ADMIN (tenant courant via `RoleGuard`)
 *
 * Le module ré-utilise `RolesAdminService` du module `admin` (référentiel
 * de rôles) — réutilisation transversale légitime puisque ce service est
 * `providedIn: 'root'` et accessible à TOUS les rôles authentifiés sur
 * le backend (cf. `RoleController` GET `/api/admin/roles`).
 *
 * Conventions :
 *  - NgModule (`standalone: false`)
 *  - `TranslateModule.forChild()` rejoint la chaîne ngx-translate du CoreModule.
 */
@NgModule({
  declarations: [UsersListComponent, UserFormComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    RouterModule,
    HotelAdminRoutingModule,
    TranslateModule.forChild(),
  ],
})
export class HotelAdminModule {}
