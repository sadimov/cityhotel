import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard } from '../../guards/role-guard.guard';
import { FactureDetailComponent } from './components/facture-detail/facture-detail.component';
import { FactureFormComponent } from './components/facture-form/facture-form.component';
import { FacturesListComponent } from './components/factures-list/factures-list.component';
import { PaiementFormComponent } from './components/paiement-form/paiement-form.component';
import { PaiementsListComponent } from './components/paiements-list/paiements-list.component';

/**
 * Routes du module `finance`.
 *
 * `AuthGuard` est appliqué au niveau parent (`app-routing.module.ts` →
 * route `finance`). `RoleGuard` filtre finement par action :
 *  - lecture (factures, paiements, détail) : RECEPTION, RESREC, GERANT,
 *    ADMIN, SUPERADMIN
 *  - écriture (création facture, encaissement, annulation) : RECEPTION,
 *    GERANT, ADMIN, SUPERADMIN (RESREC = lecture seule)
 *
 * Cf. `roles_utilisateurs.txt` racine + `Prompts_Backend_Frontend.txt` (sidebar).
 *
 * Note (post-audit B1+B2+B3 finance, 2026-05-08) : le back n'expose pas
 * d'endpoint `PUT /factures/{id}` (le cycle est BROUILLON → EMISE → AVOIR).
 * La route `/:id/edit` a donc été supprimée — toute correction passe par
 * la création d'un avoir + nouvelle facture (workflow non encore exposé
 * côté front).
 *
 * Routes futures (back à étendre) : `avoirs`, `comptes`, `releve`,
 * `dashboard-financier`.
 */
const routes: Routes = [
  { path: '', redirectTo: 'factures', pathMatch: 'full' },

  // Factures
  {
    path: 'factures',
    component: FacturesListComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC'] },
  },
  {
    path: 'factures/new',
    component: FactureFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION'] },
  },
  // Le chemin littéral `/paiement` doit venir AVANT `/:id` pour éviter les
  // conflits de routing (Angular matche au plus précis).
  {
    path: 'factures/:id/paiement',
    component: PaiementFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION'] },
  },
  {
    path: 'factures/:id',
    component: FactureDetailComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC'] },
  },

  // Paiements (historique)
  {
    path: 'paiements',
    component: PaiementsListComponent,
    canActivate: [RoleGuard],
    data: { roles: ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'RESREC'] },
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class FinanceRoutingModule {}
