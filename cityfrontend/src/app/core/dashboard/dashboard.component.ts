import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subject, takeUntil } from 'rxjs';
import { AuthService, UserInfo } from '../../services/auth.service';
import { TranslationService } from '../../services/translation.service';

interface DashboardCard {
  title: string;
  value: string | number;
  icon: string;
  color: string;
  trend?: {
    value: number;
    isPositive: boolean;
  };
  route?: string;
  roles: string[];
}

interface QuickAction {
  title: string;
  description: string;
  icon: string;
  color: string;
  route: string;
  roles: string[];
}

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
  standalone: false
})
export class DashboardComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  currentUser: UserInfo | null = null;
  isLoading = true;

  // Cartes de statistiques
  dashboardCards: DashboardCard[] = [
    {
      title: 'Réservations Aujourd\'hui',
      value: 12,
      icon: 'fas fa-calendar-check',
      color: 'primary',
      trend: { value: 8, isPositive: true },
      route: '/reservations',
      roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN']
    },
    {
      title: 'Chambres Occupées',
      value: '85%',
      icon: 'fas fa-bed',
      color: 'success',
      trend: { value: 5, isPositive: true },
      route: '/hotels/rooms',
      roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN']
    },
    {
      title: 'Revenus du Jour',
      value: '2,450 MRU',
      icon: 'fas fa-coins',
      color: 'warning',
      trend: { value: 12, isPositive: true },
      route: '/payments',
      roles: ['ADMIN', 'GERANT', 'SUPERADMIN']
    },
    {
      title: 'Clients Nouveaux',
      value: 8,
      icon: 'fas fa-user-plus',
      color: 'info',
      trend: { value: 3, isPositive: false },
      route: '/clients',
      roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN']
    },
    {
      title: 'Commandes Restaurant',
      value: 24,
      icon: 'fas fa-utensils',
      color: 'danger',
      trend: { value: 15, isPositive: true },
      route: '/restaurant',
      roles: ['RESTAURANT', 'RESREC', 'SUPERADMIN']
    },
    {
      title: 'Tâches Ménage',
      value: 6,
      icon: 'fas fa-broom',
      color: 'secondary',
      trend: { value: 2, isPositive: false },
      route: '/housekeeping',
      roles: ['MENAGE', 'GERANT', 'SUPERADMIN']
    },
    {
      title: 'Produits en Stock',
      value: 156,
      icon: 'fas fa-boxes',
      color: 'primary',
      trend: { value: 4, isPositive: false },
      route: '/products',
      roles: ['ADMIN', 'GERANT', 'MAGASIN', 'SUPERADMIN']
    },
    {
      title: 'Alertes Stock',
      value: 3,
      icon: 'fas fa-exclamation-triangle',
      color: 'warning',
      route: '/reporting/stock-alerts',
      roles: ['ADMIN', 'GERANT', 'MAGASIN', 'SUPERADMIN']
    }
  ];

  // Actions rapides
  quickActions: QuickAction[] = [
    {
      title: 'Nouvelle Réservation',
      description: 'Créer une nouvelle réservation de chambre',
      icon: 'fas fa-plus-circle',
      color: 'primary',
      route: '/reservations/add',
      roles: ['RECEPTION', 'GERANT', 'RESREC', 'SUPERADMIN']
    },
    {
      title: 'Check-in Client',
      description: 'Enregistrer l\'arrivée d\'un client',
      icon: 'fas fa-sign-in-alt',
      color: 'success',
      route: '/reservations/checkin',
      roles: ['RECEPTION', 'GERANT', 'RESREC', 'SUPERADMIN']
    },
    {
      title: 'Nouveau Client',
      description: 'Ajouter un nouveau client',
      icon: 'fas fa-user-plus',
      color: 'info',
      route: '/clients/add',
      roles: ['RECEPTION', 'GERANT', 'RESREC', 'SUPERADMIN']
    },
    {
      title: 'Commande Restaurant',
      description: 'Prendre une commande au restaurant',
      icon: 'fas fa-utensils',
      color: 'warning',
      route: '/restaurant/pos',
      roles: ['RESTAURANT', 'RESREC', 'SUPERADMIN']
    },
    {
      title: 'Bon de Commande',
      description: 'Créer un bon de commande',
      icon: 'fas fa-shopping-cart',
      color: 'secondary',
      route: '/orders/purchase/add',
      roles: ['MAGASIN', 'GERANT', 'SUPERADMIN']
    },
    {
      title: 'Tâche Ménage',
      description: 'Assigner une tâche de ménage',
      icon: 'fas fa-tasks',
      color: 'primary',
      route: '/housekeeping/tasks/add',
      roles: ['MENAGE', 'GERANT', 'SUPERADMIN']
    }
  ];

  constructor(
    private authService: AuthService,
    public translationService: TranslationService
  ) {}

  ngOnInit(): void {
    // S'abonner aux changements d'utilisateur
    this.authService.currentUser$
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => {
        this.currentUser = user;
        this.loadDashboardData();
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Charger les données du dashboard
   */
  private loadDashboardData(): void {
    this.isLoading = true;
    
    // Simuler le chargement des données
    setTimeout(() => {
      // Ici, vous pouvez appeler vos services pour récupérer les vraies données
      this.isLoading = false;
    }, 1000);
  }

  /**
   * Vérifier si une carte est visible selon le rôle
   */
  isCardVisible(card: DashboardCard): boolean {
    if (!this.currentUser) return false;
    return card.roles.includes(this.currentUser.roleCode);
  }

  /**
   * Vérifier si une action est visible selon le rôle
   */
  isActionVisible(action: QuickAction): boolean {
    if (!this.currentUser) return false;
    return action.roles.includes(this.currentUser.roleCode);
  }

  /**
   * Obtenir les cartes visibles
   */
  getVisibleCards(): DashboardCard[] {
    return this.dashboardCards.filter(card => this.isCardVisible(card));
  }

  /**
   * Obtenir les actions visibles
   */
  getVisibleActions(): QuickAction[] {
    return this.quickActions.filter(action => this.isActionVisible(action));
  }

  /**
   * Obtenir le message de bienvenue selon l'heure
   */
  getWelcomeMessage(): string {
    const hour = new Date().getHours();
    const name = this.currentUser?.prenom || 'Utilisateur';
    
    if (hour < 12) {
      return `Bonjour ${name}`;
    } else if (hour < 17) {
      return `Bon après-midi ${name}`;
    } else {
      return `Bonsoir ${name}`;
    }
  }

  /**
   * Obtenir la classe CSS pour la couleur
   */
  getColorClass(color: string, type: 'bg' | 'text' | 'border' = 'bg'): string {
    return `${type}-${color}`;
  }

  /**
   * Rafraîchir les données
   */
  refreshData(): void {
    this.loadDashboardData();
  }

  /**
   * Fonction de tracking pour ngFor des cartes
   */
  trackByCardFn(index: number, card: DashboardCard): string {
    return card.title;
  }

  /**
   * Fonction de tracking pour ngFor des actions
   */
  trackByActionFn(index: number, action: QuickAction): string {
    return action.title;
  }
}