import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { TranslateModule } from '@ngx-translate/core';

import { ClientsRoutingModule } from './clients-routing.module';
import { ClientFormComponent } from './components/client-form/client-form.component';
import { ClientsListComponent } from './components/clients-list/clients-list.component';

/**
 * Module feature `clients` — chargé en lazy depuis `app-routing.module.ts`.
 *
 * Convention NgModule (cf. cityfrontend/CLAUDE.md §1) : `standalone: false`
 * sur les composants déclarés.
 *
 * `TranslateModule.forChild()` rejoint la chaîne de loaders ngx-translate
 * mise en place dans `CoreModule`. Pas besoin de réinjecter le `TranslateLoader`.
 */
@NgModule({
  declarations: [ClientsListComponent, ClientFormComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    ClientsRoutingModule,
    TranslateModule.forChild(),
  ],
})
export class ClientsModule {}
