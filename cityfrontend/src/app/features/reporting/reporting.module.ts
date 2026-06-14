import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';

import { FinanceReportsComponent } from './components/finance-reports/finance-reports.component';
import { HebergementReportsComponent } from './components/hebergement-reports/hebergement-reports.component';
import { ReportingHomeComponent } from './components/reporting-home/reporting-home.component';
import { ReportingRoutingModule } from './reporting-routing.module';

/**
 * Module feature `reporting` — landing pages par domaine pour les 20 rapports
 * backend exposés sous `/api/reports/{hebergement,finance,inventory,restaurant,menage,direction}`.
 *
 * Tour 41 backend : 20 rapports R-HEB/R-FIN/R-INV/R-RES/R-MEN/R-DIR livrés
 * en read-only (JPQL + projections + cache).
 *
 * Tour 51ter : refonte UX consultation directe + exports.
 *  - HebergementReportsComponent : pilote (5 rapports R-HEB-001..005)
 *  - ReportingHomeComponent : catalogue + download pour les autres modules
 *    (à migrer progressivement vers le pattern HebergementReports).
 */
@NgModule({
  declarations: [FinanceReportsComponent, HebergementReportsComponent, ReportingHomeComponent],
  imports: [CommonModule, FormsModule, HttpClientModule, TranslateModule.forChild(), ReportingRoutingModule],
})
export class ReportingModule {}
