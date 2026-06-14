import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';

import { DirectionReportsComponent } from './components/direction-reports/direction-reports.component';
import { FinanceReportsComponent } from './components/finance-reports/finance-reports.component';
import { HebergementReportsComponent } from './components/hebergement-reports/hebergement-reports.component';
import { InventoryReportsComponent } from './components/inventory-reports/inventory-reports.component';
import { MenageReportsComponent } from './components/menage-reports/menage-reports.component';
import { RestaurantReportsComponent } from './components/restaurant-reports/restaurant-reports.component';
import { ReportingRoutingModule } from './reporting-routing.module';

/**
 * Module feature `reporting` — pages de consultation par domaine pour les
 * 20 rapports backend exposés sous {@code /api/reports/{module}}.
 *
 * Tour 51ter : refonte UX terminée — consultation directe + exports
 * (XLSX/DOCX/PDF) avec bordures pour tous les modules :
 *  - Hebergement / Finance / Inventory / Restaurant / Menage / Direction
 */
@NgModule({
  declarations: [
    DirectionReportsComponent,
    FinanceReportsComponent,
    HebergementReportsComponent,
    InventoryReportsComponent,
    MenageReportsComponent,
    RestaurantReportsComponent,
  ],
  imports: [CommonModule, FormsModule, HttpClientModule, TranslateModule.forChild(), ReportingRoutingModule],
})
export class ReportingModule {}
