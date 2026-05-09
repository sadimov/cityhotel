import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard } from '../../guards/role-guard.guard';
import { ClientFormComponent } from './components/client-form/client-form.component';
import { ClientsListComponent } from './components/clients-list/clients-list.component';

/**
 * Routes du module `clients`.
 *
 * `AuthGuard` est appliqué au niveau parent (`app-routing.module.ts` →
 * route `clients`), inutile de le redéclarer ici. `RoleGuard` filtre
 * finement par action : la création est interdite à RESREC (lecture seule
 * réception/restaurant), conformément à `roles_utilisateurs.txt`.
 */
const routes: Routes = [
  {
    path: '',
    component: ClientsListComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC'] },
  },
  {
    path: 'new',
    component: ClientFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION'] },
  },
  {
    path: ':id',
    component: ClientFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC'] },
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ClientsRoutingModule {}
