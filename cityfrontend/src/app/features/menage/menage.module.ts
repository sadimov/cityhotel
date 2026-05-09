import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { TranslateModule } from '@ngx-translate/core';

import { AssignationPersonnelComponent } from './components/assignation-personnel/assignation-personnel.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { PersonnelFormComponent } from './components/personnel-form/personnel-form.component';
import { PersonnelsListComponent } from './components/personnels-list/personnels-list.component';
import { PlanningListComponent } from './components/planning-list/planning-list.component';
import { TacheDetailComponent } from './components/tache-detail/tache-detail.component';
import { TacheFormComponent } from './components/tache-form/tache-form.component';
import { TachesListComponent } from './components/taches-list/taches-list.component';
import { MenageRoutingModule } from './menage-routing.module';

/**
 * Module feature `menage` — chargé en lazy depuis `app-routing.module.ts`.
 *
 * Convention NgModule (cf. cityfrontend/CLAUDE.md §1) : `standalone: false`
 * sur les composants déclarés. Architecture from-scratch (Tour 27,
 * 2026-05-05) — aucun fichier source frontend pré-existant pour ce
 * module (cf. CARTOGRAPHIE_MODULES.md §menage).
 *
 * `TranslateModule.forChild()` rejoint la chaîne de loaders ngx-translate
 * mise en place dans `CoreModule`. Pas besoin de réinjecter le `TranslateLoader`.
 *
 * Périmètre Tour 27 :
 *  - personnels-list + personnel-form
 *  - taches-list + tache-form
 *  - dashboard (vue synthèse texte)
 *
 * Ajouts Tour 28 :
 *  - planning-list (DataTables direct + filtres étage/disponibilité)
 *  - tache-detail (vue lecture + actions workflow)
 *  - assignation-personnel (modal maison, pattern payment-modal Tour 24)
 *  - service planning.service.ts + model planning.model.ts
 *
 * Périmètre différé :
 *  - planning-form (création/édition d'un créneau) — différé Tour 29
 *  - statistiques (graphiques Chart.js)
 *  - historique (audit trail des actions sur les tâches)
 */
@NgModule({
  declarations: [
    PersonnelsListComponent,
    PersonnelFormComponent,
    TachesListComponent,
    TacheFormComponent,
    TacheDetailComponent,
    DashboardComponent,
    PlanningListComponent,
    AssignationPersonnelComponent,
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    MenageRoutingModule,
    TranslateModule.forChild(),
  ],
})
export class MenageModule {}
