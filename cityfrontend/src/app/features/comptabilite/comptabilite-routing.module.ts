import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard } from '../../guards/role-guard.guard';
import { BalanceViewerComponent } from './components/balance/balance-viewer/balance-viewer.component';
import { BilanViewerComponent } from './components/bilan/bilan-viewer/bilan-viewer.component';
import { CompteMappingListComponent } from './components/compte-mapping/compte-mapping-list/compte-mapping-list.component';
import { CompteResultatViewerComponent } from './components/compte-resultat/compte-resultat-viewer/compte-resultat-viewer.component';
import { DeclarationTvaFormComponent } from './components/declarations-tva/declaration-tva-form/declaration-tva-form.component';
import { DeclarationsTvaListComponent } from './components/declarations-tva/declarations-tva-list/declarations-tva-list.component';
import { EcritureDetailComponent } from './components/ecritures/ecriture-detail/ecriture-detail.component';
import { EcritureFormComponent } from './components/ecritures/ecriture-form/ecriture-form.component';
import { EcrituresListComponent } from './components/ecritures/ecritures-list/ecritures-list.component';
import { ExercicesListComponent } from './components/exercices/exercices-list/exercices-list.component';
import { GrandLivreViewerComponent } from './components/grand-livre/grand-livre-viewer/grand-livre-viewer.component';
import { JournalFormComponent } from './components/journaux/journal-form/journal-form.component';
import { JournauxListComponent } from './components/journaux/journaux-list/journaux-list.component';
import { JournalViewerComponent } from './components/journal/journal-viewer/journal-viewer.component';
import { PlanComptableListComponent } from './components/plan-comptable/plan-comptable-list/plan-comptable-list.component';
import { TvaConfigListComponent } from './components/tva-config/tva-config-list/tva-config-list.component';

/**
 * Routes du module `comptabilite` (B7, 2026-05-08).
 *
 * `AuthGuard` est appliqué au niveau parent (`app-routing.module.ts`).
 * `RoleGuard` filtre finement par action :
 *  - lecture (toutes les listes / viewers) : SUPERADMIN, ADMIN, GERANT
 *  - écriture (formulaires, actions destructrices) : SUPERADMIN, ADMIN
 *    (`GERANT` peut lire / générer / exporter mais pas créer ni
 *    clôturer / contre-passer / valider — alignement back).
 */
const READ_ROLES = ['SUPERADMIN', 'ADMIN', 'GERANT'];
const WRITE_ROLES = ['SUPERADMIN', 'ADMIN'];

const routes: Routes = [
  { path: '', redirectTo: 'exercices', pathMatch: 'full' },

  // Exercices
  {
    path: 'exercices',
    component: ExercicesListComponent,
    canActivate: [RoleGuard],
    data: { roles: READ_ROLES },
  },

  // Plan comptable
  {
    path: 'plan-comptable',
    component: PlanComptableListComponent,
    canActivate: [RoleGuard],
    data: { roles: READ_ROLES },
  },

  // Mapping comptable
  {
    path: 'compte-mapping',
    component: CompteMappingListComponent,
    canActivate: [RoleGuard],
    data: { roles: WRITE_ROLES },
  },

  // Journaux
  {
    path: 'journaux',
    component: JournauxListComponent,
    canActivate: [RoleGuard],
    data: { roles: READ_ROLES },
  },
  {
    path: 'journaux/new',
    component: JournalFormComponent,
    canActivate: [RoleGuard],
    data: { roles: WRITE_ROLES },
  },
  {
    path: 'journaux/:id/edit',
    component: JournalFormComponent,
    canActivate: [RoleGuard],
    data: { roles: WRITE_ROLES },
  },

  // Écritures
  {
    path: 'ecritures',
    component: EcrituresListComponent,
    canActivate: [RoleGuard],
    data: { roles: READ_ROLES },
  },
  {
    path: 'ecritures/new',
    component: EcritureFormComponent,
    canActivate: [RoleGuard],
    data: { roles: WRITE_ROLES },
  },
  {
    path: 'ecritures/:id',
    component: EcritureDetailComponent,
    canActivate: [RoleGuard],
    data: { roles: READ_ROLES },
  },

  // TVA — config
  {
    path: 'tva/config',
    component: TvaConfigListComponent,
    canActivate: [RoleGuard],
    data: { roles: WRITE_ROLES },
  },

  // TVA — déclarations
  {
    path: 'tva/declarations',
    component: DeclarationsTvaListComponent,
    canActivate: [RoleGuard],
    data: { roles: READ_ROLES },
  },
  {
    path: 'tva/declarations/new',
    component: DeclarationTvaFormComponent,
    canActivate: [RoleGuard],
    data: { roles: WRITE_ROLES },
  },

  // États de synthèse
  {
    path: 'etats/balance',
    component: BalanceViewerComponent,
    canActivate: [RoleGuard],
    data: { roles: READ_ROLES },
  },
  {
    path: 'etats/grand-livre',
    component: GrandLivreViewerComponent,
    canActivate: [RoleGuard],
    data: { roles: READ_ROLES },
  },
  {
    path: 'etats/journal',
    component: JournalViewerComponent,
    canActivate: [RoleGuard],
    data: { roles: READ_ROLES },
  },
  {
    path: 'etats/bilan',
    component: BilanViewerComponent,
    canActivate: [RoleGuard],
    data: { roles: READ_ROLES },
  },
  {
    path: 'etats/compte-resultat',
    component: CompteResultatViewerComponent,
    canActivate: [RoleGuard],
    data: { roles: READ_ROLES },
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ComptabiliteRoutingModule {}
