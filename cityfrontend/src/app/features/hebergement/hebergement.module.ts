import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { TranslateModule } from '@ngx-translate/core';

import { CheckInFormComponent } from './components/check-in-form/check-in-form.component';
import { ReservationFormComponent } from './components/reservation-form/reservation-form.component';
import { ReservationsCalendarComponent } from './components/reservations-calendar/reservations-calendar.component';
import { ReservationsListComponent } from './components/reservations-list/reservations-list.component';
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
