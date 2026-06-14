import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard } from '../../guards/role-guard.guard';
import { FinanceReportsComponent } from './components/finance-reports/finance-reports.component';
import { HebergementReportsComponent } from './components/hebergement-reports/hebergement-reports.component';
import { InventoryReportsComponent } from './components/inventory-reports/inventory-reports.component';
import { MenageReportsComponent } from './components/menage-reports/menage-reports.component';
import { RestaurantReportsComponent } from './components/restaurant-reports/restaurant-reports.component';
import { ReportingHomeComponent } from './components/reporting-home/reporting-home.component';

const REPORTING_ROLES = ['SUPERADMIN', 'ADMIN', 'GERANT'];
// NIGHTAUDIT autorise sur les rapports hebergement + finance (consultation
// dans le cadre du night audit — endpoints back deja ouverts cf.
// HebergementReportController + FinanceReportController.ROLES_FIN).
const REPORTING_ROLES_WITH_NIGHTAUDIT = [...REPORTING_ROLES, 'NIGHTAUDIT'];

const routes: Routes = [
  { path: '', redirectTo: 'hebergement', pathMatch: 'full' },
  // Refonte Tour 51ter : consultation directe + exports (pilote pour les
  // autres modules reporting). Les autres routes ci-dessous restent pour
  // l'instant sur l'ancien ReportingHomeComponent (catalogue + download).
  { path: 'hebergement', component: HebergementReportsComponent, data: { roles: REPORTING_ROLES_WITH_NIGHTAUDIT }, canActivate: [RoleGuard] },
  { path: 'finance', component: FinanceReportsComponent, data: { roles: REPORTING_ROLES_WITH_NIGHTAUDIT }, canActivate: [RoleGuard] },
  { path: 'inventory', component: InventoryReportsComponent, data: { roles: [...REPORTING_ROLES, 'MAGASIN'] }, canActivate: [RoleGuard] },
  { path: 'restaurant', component: RestaurantReportsComponent, data: { roles: [...REPORTING_ROLES, 'RESTAURANT'] }, canActivate: [RoleGuard] },
  { path: 'menage', component: MenageReportsComponent, data: { roles: [...REPORTING_ROLES, 'MENAGE'] }, canActivate: [RoleGuard] },
  { path: 'direction', component: ReportingHomeComponent, data: { module: 'direction', roles: REPORTING_ROLES }, canActivate: [RoleGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ReportingRoutingModule {}
