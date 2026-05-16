import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard } from '../../guards/role-guard.guard';
import { ClientFormComponent } from './components/client-form/client-form.component';
import { ClientsListComponent } from './components/clients-list/clients-list.component';
import { SocieteFormComponent } from './components/societe-form/societe-form.component';
import { SocietesListComponent } from './components/societes-list/societes-list.component';

const READ_ROLES = ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC'];
const WRITE_ROLES = ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION'];

/**
 * Routes du module `clients` — clients individuels + sociétés.
 *
 * `AuthGuard` est appliqué au niveau parent (`app-routing.module.ts` →
 * route `clients`). `RoleGuard` filtre finement par action.
 *
 * Ordre des routes important : `societes` AVANT `:id` sinon Angular matchera
 * `societes` comme un client avec id="societes".
 */
const routes: Routes = [
  { path: '', component: ClientsListComponent, canActivate: [RoleGuard], data: { roles: READ_ROLES } },
  { path: 'new', component: ClientFormComponent, canActivate: [RoleGuard], data: { roles: WRITE_ROLES } },

  // Sociétés (déclarées AVANT :id pour ne pas être interprétées comme un ID)
  { path: 'societes', component: SocietesListComponent, canActivate: [RoleGuard], data: { roles: READ_ROLES } },
  { path: 'societes/new', component: SocieteFormComponent, canActivate: [RoleGuard], data: { roles: WRITE_ROLES } },
  { path: 'societes/:id', component: SocieteFormComponent, canActivate: [RoleGuard], data: { roles: READ_ROLES } },

  { path: ':id', component: ClientFormComponent, canActivate: [RoleGuard], data: { roles: READ_ROLES } },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ClientsRoutingModule {}
