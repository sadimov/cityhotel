import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { UserFormComponent } from './components/user-form/user-form.component';
import { UsersListComponent } from './components/users-list/users-list.component';

/**
 * Routes du module `hotel-admin` (zone ADMIN tenant-scoped).
 *
 * L'accès est filtré côté parent dans `app-routing.module.ts` via
 * `AuthGuard + RoleGuard` (data: { roles: ['ADMIN'] }). Le backend
 * applique la même restriction côté serveur via
 * `@PreAuthorize("hasRole('ADMIN')")` sur le controller `/api/hotel/users/**`.
 *
 * Hiérarchie :
 *   /hotel-admin
 *     ├── ''             → redirect 'users'
 *     ├── users          → UsersListComponent
 *     ├── users/new      → UserFormComponent (création)
 *     └── users/:id      → UserFormComponent (édition)
 */
const routes: Routes = [
  { path: '', redirectTo: 'users', pathMatch: 'full' },
  { path: 'users', component: UsersListComponent },
  { path: 'users/new', component: UserFormComponent },
  { path: 'users/:id', component: UserFormComponent },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class HotelAdminRoutingModule {}
