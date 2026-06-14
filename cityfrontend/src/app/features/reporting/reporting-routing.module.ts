import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard } from '../../guards/role-guard.guard';
import { FinanceReportsComponent } from './components/finance-reports/finance-reports.component';
import { HebergementReportsComponent } from './components/hebergement-reports/hebergement-reports.component';
import { InventoryReportsComponent } from './components/inventory-reports/inventory-reports.component';
import { DirectionReportsComponent } from './components/direction-reports/direction-reports.component';
import { MenageReportsComponent } from './components/menage-reports/menage-reports.component';
import { RestaurantReportsComponent } from './components/restaurant-reports/restaurant-reports.component';

const REPORTING_ROLES = ['SUPERADMIN', 'ADMIN', 'GERANT'];
// NIGHTAUDIT autorise sur les rapports hebergement + finance (consultation
// dans le cadre du night audit — endpoints back deja ouverts cf.
// HebergementReportController + FinanceReportController.ROLES_FIN).
const REPORTING_ROLES_WITH_NIGHTAUDIT = [...REPORTING_ROLES, 'NIGHTAUDIT'];

const routes: Routes = [
  { path: '', redirectTo: 'hebergement', pathMatch: 'full' },
  // Refonte Tour 51ter terminée : tous les modules reporting passent par
  // un composant dédié de consultation + exports XLSX/DOCX/PDF (bordures).
  // L'ancien ReportingHomeComponent (catalogue + download brut) n'est plus
  // référencé — peut être supprimé lors d'un prochain cleanup.
  { path: 'hebergement', component: HebergementReportsComponent, data: { roles: REPORTING_ROLES_WITH_NIGHTAUDIT }, canActivate: [RoleGuard] },
  { path: 'finance', component: FinanceReportsComponent, data: { roles: REPORTING_ROLES_WITH_NIGHTAUDIT }, canActivate: [RoleGuard] },
  { path: 'inventory', component: InventoryReportsComponent, data: { roles: [...REPORTING_ROLES, 'MAGASIN'] }, canActivate: [RoleGuard] },
  { path: 'restaurant', component: RestaurantReportsComponent, data: { roles: [...REPORTING_ROLES, 'RESTAURANT'] }, canActivate: [RoleGuard] },
  { path: 'menage', component: MenageReportsComponent, data: { roles: [...REPORTING_ROLES, 'MENAGE'] }, canActivate: [RoleGuard] },
  { path: 'direction', component: DirectionReportsComponent, data: { roles: REPORTING_ROLES }, canActivate: [RoleGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ReportingRoutingModule {}
