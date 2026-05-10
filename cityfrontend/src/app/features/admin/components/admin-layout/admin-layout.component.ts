import { Component } from '@angular/core';

/**
 * Wrapper de la zone administration (SUPERADMIN).
 *
 * Affiche un sub-header + une sidebar interne à 4 onglets
 * (hôtels, utilisateurs, rôles, paramètres) puis un `<router-outlet>`
 * pour les écrans enfants.
 *
 * La protection d'accès est portée par `SuperAdminGuard` au niveau
 * de la route parente déclarée dans `admin-routing.module.ts`.
 */
@Component({
  selector: 'app-admin-layout',
  templateUrl: './admin-layout.component.html',
  styleUrls: ['./admin-layout.component.scss'],
  standalone: false,
})
export class AdminLayoutComponent {
  readonly tabs: ReadonlyArray<{ id: string; route: string; icon: string; label: string }> = [
    { id: 'hotels', route: '/admin/hotels', icon: 'bi bi-building', label: 'admin.tabs.hotels' },
    { id: 'users', route: '/admin/users', icon: 'bi bi-people', label: 'admin.tabs.users' },
    { id: 'roles', route: '/admin/roles', icon: 'bi bi-shield-lock', label: 'admin.tabs.roles' },
    {
      id: 'parametres',
      route: '/admin/parametres',
      icon: 'bi bi-sliders',
      label: 'admin.tabs.parametres',
    },
  ];
}
