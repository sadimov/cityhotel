import { Component, HostBinding, OnInit, OnDestroy } from '@angular/core';
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

  // Le <nav class="sidebar"> à l'intérieur du composant est en position fixed,
  // donc le sélecteur `.sidebar.minimized ~ .main-content` ne match jamais
  // (sidebar et main-content ne sont pas frères dans le DOM). On fait remonter
  // l'état sur l'élément host <app-sidebar> qui, lui, est frère de <main>.
  @HostBinding('class.is-minimized') get hostMinimized(): boolean { return this.isMinimized; }
  @HostBinding('class.is-collapsed') get hostCollapsed(): boolean { return this.isCollapsed; }

  /**
   * Routes qui exigent un sidebar minimisé (icônes seules) pour libérer
   * l'espace écran. Le calendrier des réservations occupe toute la largeur
   * (cf. `correction-calendar/Cahier de charges définitif.txt`).
   */
  private static readonly FULLSCREEN_ROUTES: readonly string[] = [
    '/hebergement/calendar',
    '/hebergement/reservations',
    '/hebergement',
  ];

  /**
   * Snapshot de la préférence utilisateur avant un forçage par route
   * fullscreen. `null` = pas de forçage en cours.
   */
  private preferredMinimizeBeforeForce: boolean | null = null;

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
        },
        {
          // Tour 48 — page Night Audit (préparation de la clôture)
          id: 'night-audit',
          label: 'Night audit',
          icon: 'fas fa-moon',
          route: '/hebergement/night-audit',
          roles: ['ADMIN', 'GERANT', 'NIGHTAUDIT', 'SUPERADMIN']
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
          id: 'categories-produits',
          label: 'Catégories produits',
          icon: 'fas fa-tags',
          route: '/inventory/categories',
          roles: ['ADMIN', 'GERANT', 'MAGASIN', 'SUPERADMIN']
        },
        {
          id: 'fournisseurs',
          label: 'Fournisseurs',
          icon: 'fas fa-truck',
          route: '/inventory/fournisseurs',
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
          id: 'bons-sortie',
          label: 'Bons de sortie',
          icon: 'fas fa-sign-out-alt',
          route: '/inventory/bons-sortie',
          roles: ['ADMIN', 'GERANT', 'MAGASIN', 'SUPERADMIN']
        },
        {
          id: 'stocks',
          label: 'Niveaux de stock',
          icon: 'fas fa-layer-group',
          route: '/inventory/stocks',
          roles: ['ADMIN', 'GERANT', 'MAGASIN', 'SUPERADMIN']
        },
        {
          id: 'mouvements-stock',
          label: 'Mouvements stock',
          icon: 'fas fa-exchange-alt',
          route: '/inventory/mouvements-stock',
          roles: ['ADMIN', 'GERANT', 'MAGASIN', 'SUPERADMIN']
        },
        {
          id: 'services-hoteliers',
          label: 'Services hôteliers',
          icon: 'fas fa-concierge-bell',
          route: '/inventory/services-hoteliers',
          roles: ['ADMIN', 'GERANT', 'MAGASIN', 'SUPERADMIN']
        },
        {
          id: 'types-services-hoteliers',
          label: 'Types de services',
          icon: 'fas fa-list-ul',
          route: '/inventory/types-services-hoteliers',
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
    // Module Comptabilité — feature lazy (Bloc B7, 2026-05-08). Périmètre
    // restreint SUPERADMIN/ADMIN/GERANT — alignement back. Les écritures
    // automatiques générées par finance (paiements/factures) sont gérées
    // côté serveur ; ce menu expose uniquement l'analyse / configuration.
    {
      id: 'comptabilite',
      label: 'sidebar.comptabilite',
      icon: 'fas fa-calculator',
      roles: ['ADMIN', 'GERANT', 'SUPERADMIN'],
      children: [
        {
          id: 'comptabilite-exercices',
          label: 'sidebar.comptabilite.exercices',
          icon: 'fas fa-calendar-day',
          route: '/comptabilite/exercices',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'comptabilite-plan',
          label: 'sidebar.comptabilite.planComptable',
          icon: 'fas fa-list-ol',
          route: '/comptabilite/plan-comptable',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'comptabilite-mapping',
          label: 'sidebar.comptabilite.mapping',
          icon: 'fas fa-link',
          route: '/comptabilite/compte-mapping',
          roles: ['ADMIN', 'SUPERADMIN']
        },
        {
          id: 'comptabilite-journaux',
          label: 'sidebar.comptabilite.journaux',
          icon: 'fas fa-book',
          route: '/comptabilite/journaux',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'comptabilite-ecritures',
          label: 'sidebar.comptabilite.ecritures',
          icon: 'fas fa-pen-fancy',
          route: '/comptabilite/ecritures',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'comptabilite-tva-config',
          label: 'sidebar.comptabilite.tvaConfig',
          icon: 'fas fa-percentage',
          route: '/comptabilite/tva/config',
          roles: ['ADMIN', 'SUPERADMIN']
        },
        {
          id: 'comptabilite-tva-declarations',
          label: 'sidebar.comptabilite.tvaDeclarations',
          icon: 'fas fa-file-invoice-dollar',
          route: '/comptabilite/tva/declarations',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'comptabilite-balance',
          label: 'sidebar.comptabilite.balance',
          icon: 'fas fa-balance-scale',
          route: '/comptabilite/etats/balance',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'comptabilite-grand-livre',
          label: 'sidebar.comptabilite.grandLivre',
          icon: 'fas fa-book-open',
          route: '/comptabilite/etats/grand-livre',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'comptabilite-journal-edition',
          label: 'sidebar.comptabilite.journal',
          icon: 'fas fa-newspaper',
          route: '/comptabilite/etats/journal',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'comptabilite-bilan',
          label: 'sidebar.comptabilite.bilan',
          icon: 'fas fa-chart-pie',
          route: '/comptabilite/etats/bilan',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
        },
        {
          id: 'comptabilite-compte-resultat',
          label: 'sidebar.comptabilite.compteResultat',
          icon: 'fas fa-chart-line',
          route: '/comptabilite/etats/compte-resultat',
          roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
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
    // Administration tenant-scoped (ADMIN d'hôtel uniquement) — Tour B
    // (2026-05-13). Sépare de l'item 'admin' SUPERADMIN ci-dessous : un ADMIN
    // gère les users de SON hôtel via /hotel-admin/..., un SUPERADMIN gère
    // tous les hôtels via /admin/...
    {
      id: 'hotel-admin',
      label: 'menu.hotelAdmin',
      icon: 'fas fa-user-cog',
      roles: ['ADMIN'],
      children: [
        {
          id: 'hotel-admin-users',
          label: 'submenu.hotelAdmin.users',
          icon: 'fas fa-users',
          route: '/hotel-admin/users',
          roles: ['ADMIN']
        }
      ]
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
        this.applyFullscreenRouteMinimize();
      });

    // Charger l'état du sidebar depuis localStorage
    this.loadSidebarState();

    // Si on démarre déjà sur une route fullscreen (refresh F5, deep-link),
    // appliquer la minimisation sans attendre un NavigationEnd.
    this.currentRoute = this.router.url;
    this.applyFullscreenRouteMinimize();
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
   * Basculer l'état minimisé (icônes seulement). Un toggle manuel sur une
   * route fullscreen libère le snapshot (l'utilisateur reprend le contrôle).
   */
  toggleMinimize(): void {
    this.isMinimized = !this.isMinimized;
    this.preferredMinimizeBeforeForce = null;
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
   * Sauvegarder l'état du sidebar. Quand un forçage est actif (route
   * fullscreen comme `/hebergement/calendar`), on persiste la préférence
   * utilisateur initiale plutôt que la valeur forcée, pour ne pas écraser
   * son choix au prochain démarrage.
   */
  private saveSidebarState(): void {
    if (typeof localStorage !== 'undefined') {
      const minimizedToPersist = this.preferredMinimizeBeforeForce !== null
        ? this.preferredMinimizeBeforeForce
        : this.isMinimized;
      const state = {
        isCollapsed: this.isCollapsed,
        isMinimized: minimizedToPersist
      };
      localStorage.setItem('sidebar_state', JSON.stringify(state));
    }
  }

  /**
   * Sur une route fullscreen, force `isMinimized = true` et mémorise la
   * préférence utilisateur précédente. À la sortie, restaure la préférence.
   */
  private applyFullscreenRouteMinimize(): void {
    const onFullscreen = SidebarComponent.FULLSCREEN_ROUTES.some(prefix =>
      this.currentRoute === prefix || this.currentRoute.startsWith(prefix + '/') || this.currentRoute.startsWith(prefix + '?')
    );

    if (onFullscreen) {
      if (this.preferredMinimizeBeforeForce === null) {
        this.preferredMinimizeBeforeForce = this.isMinimized;
        this.isMinimized = true;
      }
    } else if (this.preferredMinimizeBeforeForce !== null) {
      this.isMinimized = this.preferredMinimizeBeforeForce;
      this.preferredMinimizeBeforeForce = null;
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