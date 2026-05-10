import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { TranslateModule } from '@ngx-translate/core';

import { AdminRoutingModule } from './admin-routing.module';
import { AdminLayoutComponent } from './components/admin-layout/admin-layout.component';
import { HotelFormComponent } from './components/hotel-form/hotel-form.component';
import { HotelsListComponent } from './components/hotels-list/hotels-list.component';
import { ParametreFormComponent } from './components/parametre-form/parametre-form.component';
import { ParametresListComponent } from './components/parametres-list/parametres-list.component';
import { RolesListComponent } from './components/roles-list/roles-list.component';
import { UserFormComponent } from './components/user-form/user-form.component';
import { UsersListComponent } from './components/users-list/users-list.component';

/**
 * Module feature `admin` — chargé en lazy depuis `app-routing.module.ts`.
 *
 * Convention NgModule (cf. cityfrontend/CLAUDE.md §1) : `standalone: false`
 * sur les composants déclarés. Architecture from-scratch (Tour 31,
 * 2026-05-09) — aucun fichier source frontend pré-existant pour ce
 * module.
 *
 * `TranslateModule.forChild()` rejoint la chaîne de loaders ngx-translate
 * mise en place dans `CoreModule`. Pas besoin de réinjecter le `TranslateLoader`.
 *
 * Périmètre Tour 31 :
 *  - admin-layout : wrapper + sidebar interne 4 onglets + router-outlet
 *  - hotels-list / hotel-form : CRUD hôtels + soft delete (désactiver/réactiver)
 *  - users-list / user-form   : CRUD users cross-hotel + verrouillage / reset password
 *  - roles-list               : lecture seule du référentiel rôles
 *  - parametres-list / parametre-form : CRUD paramètres globaux
 *    (lock UI si modifiable=false)
 *
 * Single source of truth d'accès : `SuperAdminGuard` câblé dans
 * `admin-routing.module.ts` (canActivate + canActivateChild).
 */
@NgModule({
  declarations: [
    AdminLayoutComponent,
    HotelsListComponent,
    HotelFormComponent,
    UsersListComponent,
    UserFormComponent,
    RolesListComponent,
    ParametresListComponent,
    ParametreFormComponent,
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    RouterModule,
    AdminRoutingModule,
    TranslateModule.forChild(),
  ],
})
export class AdminModule {}
