import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard } from '../../guards/role-guard.guard';
import { ChambreFormComponent } from './components/chambre-form/chambre-form.component';
import { ChambresListComponent } from './components/chambres-list/chambres-list.component';
import { CheckInFormComponent } from './components/check-in-form/check-in-form.component';
import { NightAuditPageComponent } from './components/night-audit-page/night-audit-page.component';
import { ReservationFormComponent } from './components/reservation-form/reservation-form.component';
import { ReservationsCalendarComponent } from './components/reservations-calendar/reservations-calendar.component';
import { ReservationsListComponent } from './components/reservations-list/reservations-list.component';
import { TarifChambreFormComponent } from './components/tarif-chambre-form/tarif-chambre-form.component';
import { TarifsChambreListComponent } from './components/tarifs-chambre-list/tarifs-chambre-list.component';
import { TypesChambreFormComponent } from './components/types-chambre-form/types-chambre-form.component';
import { TypesChambreListComponent } from './components/types-chambre-list/types-chambre-list.component';

/**
 * Routes du module `hebergement`.
 *
 * `AuthGuard` est appliqué au niveau parent (`app-routing.module.ts` →
 * route `hebergement`). `RoleGuard` filtre finement par action :
 *  - calendrier + liste : ouverts à RECEPTION + RESREC (lecture)
 *  - création / édition / check-in : RECEPTION + ADMIN + GERANT (écriture)
 *  - configuration (types / chambres / tarifs) : SUPERADMIN + ADMIN + GERANT
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
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'NIGHTAUDIT'] },
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
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'NIGHTAUDIT'] },
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
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'NIGHTAUDIT'] },
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
  // CRUD configuration hébergement (Tour CRUD 2026-05-25) ─────────────────
  {
    path: 'types-chambre',
    component: TypesChambreListComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT'] },
  },
  {
    path: 'types-chambre/new',
    component: TypesChambreFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT'] },
  },
  {
    path: 'types-chambre/:id',
    component: TypesChambreFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT'] },
  },
  {
    path: 'chambres',
    component: ChambresListComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT'] },
  },
  {
    path: 'chambres/new',
    component: ChambreFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT'] },
  },
  {
    path: 'chambres/:id',
    component: ChambreFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT'] },
  },
  {
    path: 'tarifs-chambre',
    component: TarifsChambreListComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT'] },
  },
  {
    path: 'tarifs-chambre/new',
    component: TarifChambreFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT'] },
  },
  {
    path: 'tarifs-chambre/:id',
    component: TarifChambreFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT'] },
  },
  // ─── Doit rester APRÈS les routes statiques (sinon conflit) ───
  {
    path: 'reservations/:id',
    component: ReservationFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'NIGHTAUDIT'] },
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class HebergementRoutingModule {}
