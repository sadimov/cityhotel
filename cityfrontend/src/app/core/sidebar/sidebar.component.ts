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
      id: 'clients',
      label: 'Clients & Sociétés',
      icon: 'fas fa-users',
      roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN'],
      children: [
        {
          id: 'clients-list',
          label: 'Clients',
          icon: 'fas fa-user-friends',
          route: '/clients',
          roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN']
        },
        {
          id: 'clients-add',
          label: 'Nouveau client',
          icon: 'fas fa-user-plus',
          route: '/clients/new',
          roles: ['ADMIN', 'GERANT', 'RECEPTION', 'SUPERADMIN']
        }
      ]
    },
    {
      id: 'hebergement',
      label: 'Hébergement',
      icon: 'fas fa-bed',
      roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN'],
      children: [
        {
          id: 'reservations',
          label: 'Réservations',
          icon: 'fas fa-calendar-alt',
          route: '/hebergement/reservations',
          roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN']
        },
        {
          id: 'reservation-new',
          label: 'Nouvelle réservation',
          icon: 'fas fa-calendar-plus',
          route: '/hebergement/reservations/new',
          roles: ['ADMIN', 'GERANT', 'RECEPTION', 'SUPERADMIN']
        },
        {
          id: 'check-in',
          label: 'Check-in / Check-out',
          icon: 'fas fa-key',
          route: '/hebergement/check-in',
          roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN']
        }
      ]
    },
    {
      id: 'inventory',
      label: 'Stocks & Magasin',
      icon: 'fas fa-boxes',
      roles: ['ADMIN', 'GERANT', 'MAGASIN', 'SUPERADMIN'],
      children: [
        {
          id: 'produits',
          label: 'Produits',
          icon: 'fas fa-box',
          route: '/inventory/produits',
          roles: ['ADMIN', 'GERANT', 'MAGASIN', 'SUPERADMIN']
        },
        {
          id: 'bons-commande',
          label: 'Bons de commande',
          icon: 'fas fa-shopping-bag',
          route: '/inventory/bons-commande',
          roles: ['ADMIN', 'GERANT', 'MAGASIN', 'SUPERADMIN']
        },
        {
          id: 'stocks',
          label: 'Niveaux de stock',
          icon: 'fas fa-layer-group',
          route: '/inventory/stocks',
          roles: ['ADMIN', 'GERANT', 'MAGASIN', 'SUPERADMIN']
        }
      ]
    },
    {
      id: 'finance',
      label: 'Finance',
      icon: 'fas fa-credit-card',
      roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN'],
      children: [
        {
          id: 'factures',
          label: 'Factures',
          icon: 'fas fa-file-invoice',
          route: '/finance/factures',
          roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN']
        },
        {
          id: 'paiements',
          label: 'Paiements',
          icon: 'fas fa-money-bill-wave',
          route: '/finance/paiements',
          roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN']
        }
      ]
    },
    {
      id: 'restaurant',
      label: 'Restaurant',
      icon: 'fas fa-utensils',
      roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'RESTAURANT', 'SUPERADMIN'],
      children: [
        {
          id: 'pos',
          label: 'Point de vente',
          icon: 'fas fa-cash-register',
          route: '/restaurant/pos',
          roles: ['ADMIN', 'GERANT', 'RESREC', 'RESTAURANT', 'SUPERADMIN']
        },
        {
          id: 'articles-menu',
          label: 'Articles menu',
          icon: 'fas fa-list-alt',
          route: '/restaurant/articles',
          roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'RESTAURANT', 'SUPERADMIN']
        },
        {
          id: 'categories-menu',
          label: 'Catégories menu',
          icon: 'fas fa-tags',
          route: '/restaurant/categories',
          roles: ['ADMIN', 'GERANT', 'RESTAURANT', 'SUPERADMIN']
        }
      ]
    },
    {
      id: 'menage',
      label: 'Ménage',
      icon: 'fas fa-broom',
      roles: ['ADMIN', 'GERANT', 'RECEPTION', 'MENAGE', 'SUPERADMIN'],
      children: [
        {
          id: 'menage-dashboard',
          label: 'Dashboard',
          icon: 'fas fa-tachometer-alt',
          route: '/menage/dashboard',
          roles: ['ADMIN', 'GERANT', 'RECEPTION', 'MENAGE', 'SUPERADMIN']
        },
        {
          id: 'taches',
          label: 'Tâches',
          icon: 'fas fa-tasks',
          route: '/menage/taches',
          roles: ['ADMIN', 'GERANT', 'RECEPTION', 'MENAGE', 'SUPERADMIN']
        },
        {
          id: 'personnel',
          label: 'Personnel',
          icon: 'fas fa-user-cog',
          route: '/menage/personnel',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'planning',
          label: 'Planning',
          icon: 'fas fa-calendar-check',
          route: '/menage/planning',
          roles: ['ADMIN', 'GERANT', 'MENAGE', 'SUPERADMIN']
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