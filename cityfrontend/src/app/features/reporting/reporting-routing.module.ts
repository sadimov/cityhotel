import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RoleGuard } from '../../guards/role-guard.guard';
import { ReportingHomeComponent } from './components/reporting-home/reporting-home.component';

const REPORTING_ROLES = ['SUPERADMIN', 'ADMIN', 'GERANT'];

const routes: Routes = [
  { path: '', redirectTo: 'hebergement', pathMatch: 'full' },
  { path: 'hebergement', component: ReportingHomeComponent, data: { module: 'hebergement', roles: REPORTING_ROLES }, canActivate: [RoleGuard] },
  { path: 'finance', component: ReportingHomeComponent, data: { module: 'finance', roles: REPORTING_ROLES }, canActivate: [RoleGuard] },
  { path: 'inventory', component: ReportingHomeComponent, data: { module: 'inventory', roles: REPORTING_ROLES }, canActivate: [RoleGuard] },
  { path: 'restaurant', component: ReportingHomeComponent, data: { module: 'restaurant', roles: REPORTING_ROLES }, canActivate: [RoleGuard] },
  { path: 'menage', component: ReportingHomeComponent, data: { module: 'menage', roles: REPORTING_ROLES }, canActivate: [RoleGuard] },
  { path: 'direction', component: ReportingHomeComponent, data: { module: 'direction', roles: REPORTING_ROLES }, canActivate: [RoleGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ReportingRoutingModule {}
