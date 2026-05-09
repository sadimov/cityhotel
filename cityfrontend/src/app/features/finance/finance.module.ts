import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { TranslateModule } from '@ngx-translate/core';

import { FactureDetailComponent } from './components/facture-detail/facture-detail.component';
import { FactureFormComponent } from './components/facture-form/facture-form.component';
import { FacturesListComponent } from './components/factures-list/factures-list.component';
import { PaiementFormComponent } from './components/paiement-form/paiement-form.component';
import { PaiementsListComponent } from './components/paiements-list/paiements-list.component';
import { FinanceRoutingModule } from './finance-routing.module';

/**
 * Module feature `finance` — chargé en lazy depuis `app-routing.module.ts`.
 *
 * Convention NgModule (cf. cityfrontend/CLAUDE.md §1) : `standalone: false`
 * sur les composants déclarés.
 *
 * `TranslateModule.forChild()` rejoint la chaîne de loaders ngx-translate
 * mise en place dans `CoreModule`. Pas besoin de réinjecter le `TranslateLoader`.
 *
 * Périmètre actuel (post-audit B1+B2+B3 finance, 2026-05-08) :
 *  - factures-list / facture-form (création) / facture-detail (lecture
 *    + transitions emettre / annuler / encaisser)
 *  - paiement-form (encaissement direct sur facture) + paiements-list
 *
 * Endpoints back disponibles : `FactureController` (findById, findAll,
 * create, emettre, annuler, fromReservation), `PaiementController`
 * (findById, findAll, create, affecter, annuler).
 *
 * Composants futurs à concevoir from-scratch quand le back exposera les
 * endpoints associés : avoirs (workflow AVOIR), comptes-comptables
 * (CompteController inexistant), releve-compte, dashboard-financier,
 * rapport-chiffre-affaires.
 */
@NgModule({
  declarations: [
    FacturesListComponent,
    FactureFormComponent,
    FactureDetailComponent,
    PaiementsListComponent,
    PaiementFormComponent,
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    FinanceRoutingModule,
    TranslateModule.forChild(),
  ],
})
export class FinanceModule {}
