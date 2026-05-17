import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard } from '../../guards/role-guard.guard';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { PersonnelFormComponent } from './components/personnel-form/personnel-form.component';
import { PersonnelsListComponent } from './components/personnels-list/personnels-list.component';
import { PlanningFormComponent } from './components/planning-form/planning-form.component';
import { PlanningListComponent } from './components/planning-list/planning-list.component';
import { TacheDetailComponent } from './components/tache-detail/tache-detail.component';
import { TacheFormComponent } from './components/tache-form/tache-form.component';
import { TachesListComponent } from './components/taches-list/taches-list.component';

/**
 * Routes du module `menage`.
 *
 * `AuthGuard` est appliqué au niveau parent (`app-routing.module.ts`).
 * `RoleGuard` filtre finement par action, conformément aux rôles
 * documentés dans `MENAGE/endpoints_module_menage.txt` :
 *
 *  - **lecture (dashboard, listes, détails)** : SUPERADMIN + ADMIN +
 *    GERANT + RECEPTION (cf. spec : RECEPTION peut consulter le
 *    dashboard et les tâches du jour pour ses échanges avec le service
 *    ménage). MENAGE ajouté car le personnel ménage doit voir ses tâches.
 *  - **CRUD personnel + tâches + assignation** : SUPERADMIN + ADMIN +
 *    GERANT (gestion managériale). RECEPTION et MENAGE n'écrivent pas.
 *  - **workflow tâche (commencer/terminer)** : SUPERADMIN + ADMIN +
 *    GERANT + RECEPTION (RECEPTION peut clôturer une tâche pour le
 *    compte d'un agent — cf. spec §Workflow).
 *
 * Le rôle MENAGE n'est pas listé dans la spec d'origine mais on le
 * fait passer en lecture car c'est l'utilisateur final ; le filtrage
 * fin (« voir uniquement mes tâches ») est délégué au backend (par
 * personnel.userId, hors scope ce tour).
 *
 * Périmètre Tour 27 (cf. consigne) :
 *  - personnel : list + form
 *  - taches    : list + form (CRUD + workflow assigner/commencer/terminer)
 *  - dashboard : KPI texte
 *
 * Ajouts Tour 28 :
 *  - planning  : list (DataTables direct)
 *  - taches/:id/detail : vue détail + workflow
 *  - assignation-personnel : pas de route (modal embarquée)
 *
 * Différé (Tour 29+) :
 *  - planning : create/edit form (composant `planning-form`)
 *  - statistiques : graphiques Chart.js
 *  - historique : audit trail tâches
 */
const ROLES_LECTURE = ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION', 'MENAGE'];
const ROLES_ECRITURE = ['SUPERADMIN', 'ADMIN', 'GERANT'];
const ROLES_WORKFLOW = ['SUPERADMIN', 'ADMIN', 'GERANT', 'RECEPTION'];

const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },

  // Dashboard ménage
  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [RoleGuard],
    data: { roles: ROLES_LECTURE },
  },

  // Personnel
  {
    path: 'personnel',
    component: PersonnelsListComponent,
    canActivate: [RoleGuard],
    data: { roles: ROLES_LECTURE },
  },
  {
    path: 'personnel/new',
    component: PersonnelFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ROLES_ECRITURE },
  },
  {
    path: 'personnel/:id',
    component: PersonnelFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ROLES_ECRITURE },
  },

  // Tâches
  {
    path: 'taches',
    component: TachesListComponent,
    canActivate: [RoleGuard],
    // Lecture + workflow : on autorise RECEPTION pour qu'elle accède
    // aux boutons commencer/terminer ; les boutons "supprimer/éditer"
    // restent visibles mais le backend rejettera (403) si rôle insuffisant.
    data: { roles: ROLES_WORKFLOW.concat(['MENAGE']) },
  },
  {
    path: 'taches/new',
    component: TacheFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ROLES_ECRITURE },
  },
  // Détail (lecture + actions workflow) — DOIT être déclaré AVANT
  // `taches/:id` pour que le router résolve d'abord le segment
  // `:id/detail` (Angular n'a pas de précédence par "spécificité",
  // c'est l'ordre de déclaration qui prime).
  {
    path: 'taches/:id/detail',
    component: TacheDetailComponent,
    canActivate: [RoleGuard],
    data: { roles: ROLES_LECTURE },
  },
  {
    path: 'taches/:id',
    component: TacheFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ROLES_ECRITURE },
  },

  // Planning — Tour 28 (liste) + Tour 56 (form création/édition).
  // Lecture : MENAGE + GERANT + ADMIN + RECEPTION (pour vue partagée).
  // Création / édition : ADMIN + GERANT (planification = responsabilité
  // hiérarchique, cf. roles_utilisateurs.txt + PlanningController.@PreAuthorize).
  {
    path: 'planning',
    component: PlanningListComponent,
    canActivate: [RoleGuard],
    data: { roles: ROLES_LECTURE },
  },
  {
    path: 'planning/new',
    component: PlanningFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ROLES_ECRITURE },
  },
  {
    path: 'planning/:id',
    component: PlanningFormComponent,
    canActivate: [RoleGuard],
    data: { roles: ROLES_ECRITURE },
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class MenageRoutingModule {}
