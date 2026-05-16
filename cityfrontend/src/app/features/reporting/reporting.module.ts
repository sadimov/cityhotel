import { CommonModule } from '@angular/common';
import { HttpClientModule } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

import { ReportingHomeComponent } from './components/reporting-home/reporting-home.component';
import { ReportingRoutingModule } from './reporting-routing.module';

/**
 * Module feature `reporting` — landing pages par domaine pour les 20 rapports
 * backend exposés sous `/api/reports/{hebergement,finance,inventory,restaurant,menage,direction}`.
 *
 * Tour 41 backend : 20 rapports R-HEB/R-FIN/R-INV/R-RES/R-MEN/R-DIR livrés
 * en read-only (JPQL + projections + cache). Le module front fournit ici
 * une UI minimale pour les exposer ; à enrichir avec graphiques/exports.
 */
@NgModule({
  declarations: [ReportingHomeComponent],
  imports: [CommonModule, HttpClientModule, TranslateModule.forChild(), ReportingRoutingModule],
})
export class ReportingModule {}
