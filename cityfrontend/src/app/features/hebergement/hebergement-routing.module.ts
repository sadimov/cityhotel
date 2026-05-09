import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard } from '../../guards/role-guard.guard';
import { CheckInFormComponent } from './components/check-in-form/check-in-form.component';
import { ReservationFormComponent } from './components/reservation-form/reservation-form.component';
import { ReservationsCalendarComponent } from './components/reservations-calendar/reservations-calendar.component';
import { ReservationsListComponent } from './components/reservations-list/reservations-list.component';

/**
 * Routes du module `hebergement`.
 *
 * `AuthGuard` est appliqué au niveau parent (`app-routing.module.ts` →
 * route `hebergement`). `RoleGuard` filtre finement par action :
 *  - calendrier + liste : ouverts à RECEPTION + RESREC (lecture)
 *  - création / édition / check-in : RECEPTION + ADMIN + GERANT (écriture)
 *
 * Cf. `roles_utilisateurs.txt` racine.
 */
const routes: Routes = [
  { path: '', redirectTo: 'reservations', pathMatch: 'full' },
  {
    path: 'reservations',
    component: ReservationsCalendarComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC'] },
  },
  {
    path: 'reservations/list',
    component: ReservationsListComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC'] },
  },
  {
    path: 'reservations/new',
    component: ReservationFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION'] },
  },
  {
    path: 'check-in',
    component: CheckInFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION'] },
  },
  {
    path: 'reservations/:id',
    component: ReservationFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC'] },
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class HebergementRoutingModule {}
