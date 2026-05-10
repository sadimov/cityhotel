import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { Subject, takeUntil, filter } from 'rxjs';
import { AuthService, UserInfo } from '../../services/auth.service';
import { TranslationService } from '../../services/translation.service';

interface MenuItem {
  id: string;
  label: string;
  icon: string;
  route?: string;
  roles: string[];
  children?: MenuItem[];
  expanded?: boolean;
  active?: boolean;
}

@Component({
  selector: 'app-sidebar',
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
  standalone: false
})
export class SidebarComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  currentUser: UserInfo | null = null;
  currentRoute = '';
  isCollapsed = false;
  isMinimized = false;

  menuItems: MenuItem[] = [
    {
      id: 'products',
      label: 'menu.products',
      icon: 'fas fa-shopping-cart',
      roles: ['ADMIN', 'GERANT', 'SUPERADMIN'],
      children: [
        {
          id: 'products-list',
          label: 'submenu.products.products',
          icon: 'fas fa-box',
          route: '/products/list',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'products-categories',
          label: 'submenu.products.categories',
          icon: 'fas fa-tags',
          route: '/products/categories',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        }
      ]
    },
    {
      id: 'orders',
      label: 'menu.orders',
      icon: 'fas fa-clipboard-list',
      roles: ['MAGASIN', 'GERANT', 'RESTAURANT', 'RESREC', 'SUPERADMIN'],
      children: [
        {
          id: 'purchase-orders',
          label: 'submenu.orders.purchase_orders',
          icon: 'fas fa-shopping-bag',
          route: '/orders/purchase',
          roles: ['MAGASIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'delivery-orders',
          label: 'submenu.orders.delivery_orders',
          icon: 'fas fa-truck',
          route: '/orders/delivery',
          roles: ['MAGASIN', 'GERANT', 'RESTAURANT', 'RESREC', 'SUPERADMIN']
        }
      ]
    },
    {
      id: 'reservations',
      label: 'menu.reservations',
      icon: 'fas fa-calendar-alt',
      roles: ['RECEPTION', 'GERANT', 'RESREC', 'SUPERADMIN'],
      children: [
        {
          id: 'reservations-list',
          label: 'submenu.reservations.reservations',
          icon: 'fas fa-bed',
          route: '/reservations/list',
          roles: ['RECEPTION', 'GERANT', 'RESREC', 'SUPERADMIN']
        },
        {
          id: 'night-audit',
          label: 'submenu.reservations.night_audit',
          icon: 'fas fa-moon',
          route: '/reservations/night-audit',
          roles: ['RECEPTION', 'GERANT', 'RESREC', 'SUPERADMIN']
        }
      ]
    },
    {
      id: 'payments',
      label: 'menu.payments',
      icon: 'fas fa-credit-card',
      roles: ['RECEPTION', 'GERANT', 'RESREC', 'SUPERADMIN'],
      children: [
        {
          id: 'account-statement',
          label: 'Relevé de compte',
          icon: 'fas fa-file-invoice',
          route: '/payments/account-statement',
          roles: ['RECEPTION', 'GERANT', 'RESREC', 'SUPERADMIN']
        }
      ]
    },
    {
      id: 'hotels',
      label: 'menu.hotels',
      icon: 'fas fa-hotel',
      roles: ['ADMIN', 'GERANT', 'SUPERADMIN'],
      children: [
        {
          id: 'add-hotel',
          label: 'Ajout hôtel',
          icon: 'fas fa-plus-circle',
          route: '/hotels/add',
          roles: ['ADMIN', 'SUPERADMIN']
        },
        {
          id: 'hotels-list',
          label: 'Hôtels',
          icon: 'fas fa-building',
          route: '/hotels/list',
          roles: ['ADMIN', 'SUPERADMIN']
        },
        {
          id: 'add-room',
          label: 'Ajout chambre',
          icon: 'fas fa-plus',
          route: '/hotels/rooms/add',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'rooms-list',
          label: 'Chambres',
          icon: 'fas fa-door-open',
          route: '/hotels/rooms',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'room-types',
          label: 'Types chambre',
          icon: 'fas fa-th-large',
          route: '/hotels/room-types',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'room-prices',
          label: 'Prix chambre',
          icon: 'fas fa-euro-sign',
          route: '/hotels/room-prices',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        }
      ]
    },
    {
      id: 'clients',
      label: 'menu.clients',
      icon: 'fas fa-users',
      roles: ['GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN'],
      children: [
        {
          id: 'add-client',
          label: 'Ajout client',
          icon: 'fas fa-user-plus',
          route: '/clients/add',
          roles: ['GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN']
        },
        {
          id: 'clients-list',
          label: 'Clients',
          icon: 'fas fa-user-friends',
          route: '/clients/list',
          roles: ['GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN']
        },
        {
          id: 'add-company',
          label: 'Ajout société',
          icon: 'fas fa-building',
          route: '/clients/companies/add',
          roles: ['GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN']
        },
        {
          id: 'companies-list',
          label: 'Sociétés',
          icon: 'fas fa-industry',
          route: '/clients/companies',
          roles: ['GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN']
        }
      ]
    },
    {
      id: 'restaurant',
      label: 'menu.restaurant',
      icon: 'fas fa-utensils',
      roles: ['RESTAURANT', 'RESREC', 'SUPERADMIN'],
      children: [
        {
          id: 'menus',
          label: 'Menus',
          icon: 'fas fa-list-alt',
          route: '/restaurant/menus',
          roles: ['RESTAURANT', 'RESREC', 'SUPERADMIN']
        },
        {
          id: 'pos',
          label: 'Point vente',
          icon: 'fas fa-cash-register',
          route: '/restaurant/pos',
          roles: ['RESTAURANT', 'RESREC', 'SUPERADMIN']
        }
      ]
    },
    {
      id: 'housekeeping',
      label: 'menu.housekeeping',
      icon: 'fas fa-broom',
      roles: ['MENAGE', 'GERANT', 'SUPERADMIN'],
      children: [
        {
          id: 'housekeeping-dashboard',
          label: 'Dashboard',
          icon: 'fas fa-tachometer-alt',
          route: '/housekeeping/dashboard',
          roles: ['MENAGE', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'tasks',
          label: 'Tâches',
          icon: 'fas fa-tasks',
          route: '/housekeeping/tasks',
          roles: ['MENAGE', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'staff',
          label: 'Personnel ménage',
          icon: 'fas fa-user-cog',
          route: '/housekeeping/staff',
          roles: ['MENAGE', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'planning',
          label: 'Planning & historique',
          icon: 'fas fa-calendar-check',
          route: '/housekeeping/planning',
          roles: ['MENAGE', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'history',
          label: 'Historique',
          icon: 'fas fa-history',
          route: '/housekeeping/history',
          roles: ['MENAGE', 'GERANT', 'SUPERADMIN']
        }
      ]
    },
    {
      id: 'reporting',
      label: 'menu.reporting',
      icon: 'fas fa-chart-bar',
      roles: ['ADMIN', 'GERANT', 'SUPERADMIN'],
      children: [
        {
          id: 'reporting-dashboard',
          label: 'Dashboard',
          icon: 'fas fa-tachometer-alt',
          route: '/reporting/dashboard',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'finances',
          label: 'Finances',
          icon: 'fas fa-chart-line',
          route: '/reporting/finances',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'reservations-reports',
          label: 'Rapports Réservations',
          icon: 'fas fa-bed',
          route: '/reporting/reservations',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'clients-reports',
          label: 'Rapports Clients',
          icon: 'fas fa-users',
          route: '/reporting/clients',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'stock-alerts',
          label: 'Alerts des Stocks',
          icon: 'fas fa-exclamation-triangle',
          route: '/reporting/stock-alerts',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        }
      ]
    },
    {
      id: 'profile',
      label: 'menu.profile',
      icon: 'fas fa-user-circle',
      route: '/profile',
      roles: ['ADMIN', 'GERANT', 'SUPERADMIN', 'RECEPTION', 'RESTAURANT', 'RESREC', 'MENAGE', 'MAGASIN']
    },
    // Administration cross-tenant (SUPERADMIN uniquement) — Tour 31.
    // Le SuperAdminGuard interne au module admin double cette restriction
    // côté routing ; ici la liste `roles` filtre uniquement l'affichage
    // dans le sidebar via `filterMenuByRole`.
    {
      id: 'admin',
      label: 'menu.admin',
      icon: 'fas fa-gear',
      roles: ['SUPERADMIN'],
      children: [
        {
          id: 'admin-hotels',
          label: 'submenu.admin.hotels',
          icon: 'fas fa-building',
          route: '/admin/hotels',
          roles: ['SUPERADMIN']
        },
        {
          id: 'admin-users',
          label: 'submenu.admin.users',
          icon: 'fas fa-users-cog',
          route: '/admin/users',
          roles: ['SUPERADMIN']
        },
        {
          id: 'admin-roles',
          label: 'submenu.admin.roles',
          icon: 'fas fa-user-shield',
          route: '/admin/roles',
          roles: ['SUPERADMIN']
        },
        {
          id: 'admin-parametres',
          label: 'submenu.admin.parametres',
          icon: 'fas fa-sliders-h',
          route: '/admin/parametres',
          roles: ['SUPERADMIN']
        }
      ]
    }
  ];

  constructor(
    private authService: AuthService,
    private router: Router,
    public translationService: TranslationService
  ) {}

  ngOnInit(): void {
    // S'abonner aux changements d'utilisateur
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => {
        this.currentUser = user;
        this.filterMenuByRole();
      });

    // S'abonner aux changements de route
    this.router.events
      .pipe(
        filter(event => event instanceof NavigationEnd),
        takeUntil(this.destroy$)
      )
      .subscribe((event: NavigationEnd) => {
        this.currentRoute = event.url;
        this.updateActiveItems();
      });

    // Charger l'état du sidebar depuis localStorage
    this.loadSidebarState();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Basculer l'état réduit du sidebar
   */
  toggleCollapse(): void {
    this.isCollapsed = !this.isCollapsed;
    this.saveSidebarState();
  }

  /**
   * Basculer l'état minimisé (icônes seulement)
   */
  toggleMinimize(): void {
    this.isMinimized = !this.isMinimized;
    this.saveSidebarState();
  }

  /**
   * Basculer l'expansion d'un menu
   */
  toggleMenu(item: MenuItem): void {
    if (item.children && item.children.length > 0) {
      item.expanded = !item.expanded;
      
      // Fermer les autres menus si nécessaire
      if (item.expanded) {
        this.menuItems.forEach(menu => {
          if (menu.id !== item.id && menu.children) {
            menu.expanded = false;
          }
        });
      }
    } else if (item.route) {
      this.navigateTo(item.route);
    }
  }

  /**
   * Navigation vers une route
   */
  navigateTo(route: string): void {
    this.router.navigate([route]);
  }

  /**
   * Vérifier si un item est visible selon le rôle
   */
  isItemVisible(item: MenuItem): boolean {
    if (!this.currentUser) return false;
    return item.roles.includes(this.currentUser.roleCode);
  }

  /**
   * Vérifier si un item est actif
   */
  isItemActive(item: MenuItem): boolean {
    if (item.route) {
      return this.currentRoute.startsWith(item.route);
    }
    
    if (item.children) {
      return item.children.some(child => 
        child.route && this.currentRoute.startsWith(child.route)
      );
    }
    
    return false;
  }

  /**
   * Vérifier si un parent a un enfant actif
   */
  hasActiveChild(item: MenuItem): boolean {
    if (!item.children) return false;
    
    return item.children.some(child => 
      child.route && this.currentRoute.startsWith(child.route)
    );
  }

  /**
   * Filtrer le menu selon le rôle de l'utilisateur
   */
  private filterMenuByRole(): void {
    if (!this.currentUser) return;
    
    this.menuItems.forEach(item => {
      if (item.children) {
        item.children = item.children.filter(child => 
          child.roles.includes(this.currentUser!.roleCode)
        );
      }
    });
    
    this.updateActiveItems();
  }

  /**
   * Mettre à jour les items actifs
   */
  private updateActiveItems(): void {
    this.menuItems.forEach(item => {
      // Marquer l'item comme actif
      item.active = this.isItemActive(item);
      
      // Expendre automatiquement si un enfant est actif
      if (item.children && this.hasActiveChild(item)) {
        item.expanded = true;
        
        // Marquer les enfants actifs
        item.children.forEach(child => {
          child.active = child.route ? this.currentRoute.startsWith(child.route) : false;
        });
      }
    });
  }

  /**
   * Sauvegarder l'état du sidebar
   */
  private saveSidebarState(): void {
    if (typeof localStorage !== 'undefined') {
      const state = {
        isCollapsed: this.isCollapsed,
        isMinimized: this.isMinimized
      };
      localStorage.setItem('sidebar_state', JSON.stringify(state));
    }
  }

  /**
   * Charger l'état du sidebar
   */
  private loadSidebarState(): void {
    if (typeof localStorage !== 'undefined') {
      const storedState = localStorage.getItem('sidebar_state');
      if (storedState) {
        try {
          const state = JSON.parse(storedState);
          this.isCollapsed = state.isCollapsed || false;
          this.isMinimized = state.isMinimized || false;
        } catch (error) {
          console.error('Erreur lors du chargement de l\'état du sidebar:', error);
        }
      }
    }
  }

  /**
   * Obtenir le nombre d'items visibles
   */
  getVisibleItemsCount(): number {
    return this.menuItems.filter(item => this.isItemVisible(item)).length;
  }

  /**
   * Obtenir les items visibles
   */
  getVisibleItems(): MenuItem[] {
    return this.menuItems.filter(item => this.isItemVisible(item));
  }

  /**
   * Réinitialiser l'état du sidebar
   */
  resetSidebarState(): void {
    this.isCollapsed = false;
    this.isMinimized = false;
    this.menuItems.forEach(item => {
      item.expanded = false;
    });
    this.saveSidebarState();
  }

  /**
   * Fonction de tracking pour ngFor
   */
  trackByFn(index: number, item: MenuItem): string {
    return item.id;
  }

  /**
   * Fonction de tracking pour les enfants
   */
  trackByChildFn(index: number, child: MenuItem): string {
    return child.id;
  }
}