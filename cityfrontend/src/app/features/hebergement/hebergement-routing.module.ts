import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard } from '../../guards/role-guard.guard';
import { CheckInFormComponent } from './components/check-in-form/check-in-form.component';
import { NightAuditPageComponent } from './components/night-audit-page/night-audit-page.component';
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
  // Tour 44 Phase 2 — entrée du module = calendrier directement (page d'accueil
  // de la feature hebergement, cf. cahier des charges `correction-calendar/`).
  { path: '', redirectTo: 'calendar', pathMatch: 'full' },
  {
    path: 'calendar',
    component: ReservationsCalendarComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC'] },
  },
  {
    // Alias historique — conservé pour ne pas casser les liens externes.
    path: 'reservations',
    redirectTo: 'calendar',
    pathMatch: 'full',
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
    // Tour 48 — Page Night Audit (préparation de la clôture)
    path: 'night-audit',
    component: NightAuditPageComponent,
    canActivate: [RoleGuard],
    data: {
      roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'NIGHTAUDIT'],
    },
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
