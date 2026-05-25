import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { TranslateModule } from '@ngx-translate/core';

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
import { HebergementRoutingModule } from './hebergement-routing.module';

/**
 * Module feature `hebergement` — chargé en lazy depuis `app-routing.module.ts`.
 *
 * Convention NgModule (cf. cityfrontend/CLAUDE.md §1) : `standalone: false`
 * sur les composants déclarés.
 *
 * `TranslateModule.forChild()` rejoint la chaîne de loaders ngx-translate
 * mise en place dans `CoreModule`. Pas besoin de réinjecter le `TranslateLoader`.
 */
@NgModule({
  declarations: [
    ReservationsCalendarComponent,
    ReservationsListComponent,
    ReservationFormComponent,
    CheckInFormComponent,
    NightAuditPageComponent,
    TypesChambreListComponent,
    TypesChambreFormComponent,
    ChambresListComponent,
    ChambreFormComponent,
    TarifsChambreListComponent,
    TarifChambreFormComponent,
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    HebergementRoutingModule,
    TranslateModule.forChild(),
  ],
})
export class HebergementModule {}
