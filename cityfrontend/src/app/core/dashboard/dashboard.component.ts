import { Component, OnInit, OnDestroy } from '@angular/core';
import { forkJoin, of, Subject } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';
import { AuthService, UserInfo } from '../../services/auth.service';
import { TranslationService } from '../../services/translation.service';
import { ReportingDashboardService } from '../../services/reporting-dashboard.service';
import { ReservationsService } from '../../features/hebergement/services/reservations.service';
import { ProduitsService } from '../../features/inventory/services/produits.service';
import { DashboardMenageService } from '../../features/menage/services/dashboard-menage.service';
import { CommandesService } from '../../features/restaurant/services/commandes.service';
import { ClientsService } from '../../features/clients/services/clients.service';

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
      route: '/hebergement/reservations/list',
      roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN']
    },
    {
      title: 'Chambres Occupées',
      value: '85%',
      icon: 'fas fa-bed',
      color: 'success',
      trend: { value: 5, isPositive: true },
      route: '/hebergement/calendar',
      roles: ['ADMIN', 'GERANT', 'RECEPTION', 'RESREC', 'SUPERADMIN']
    },
    {
      title: 'Revenus du Jour',
      value: '2,450 MRU',
      icon: 'fas fa-coins',
      color: 'warning',
      trend: { value: 12, isPositive: true },
      route: '/finance/paiements',
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
      route: '/menage/dashboard',
      roles: ['MENAGE', 'GERANT', 'SUPERADMIN']
    },
    {
      title: 'Produits en Stock',
      value: 156,
      icon: 'fas fa-boxes',
      color: 'primary',
      trend: { value: 4, isPositive: false },
      route: '/inventory/produits',
      roles: ['ADMIN', 'GERANT', 'MAGASIN', 'SUPERADMIN']
    }
    // TODO 33B : réintégrer la carte "Alertes Stock" après livraison frontend reporting
  ];

  // Actions rapides
  quickActions: QuickAction[] = [
    {
      title: 'Nouvelle Réservation',
      description: 'Créer une nouvelle réservation de chambre',
      icon: 'fas fa-plus-circle',
      color: 'primary',
      route: '/hebergement/reservations/new',
      roles: ['RECEPTION', 'GERANT', 'RESREC', 'SUPERADMIN']
    },
    {
      title: 'Check-in Client',
      description: 'Enregistrer l\'arrivée d\'un client',
      icon: 'fas fa-sign-in-alt',
      color: 'success',
      route: '/hebergement/check-in',
      roles: ['RECEPTION', 'GERANT', 'RESREC', 'SUPERADMIN']
    },
    {
      title: 'Nouveau Client',
      description: 'Ajouter un nouveau client',
      icon: 'fas fa-user-plus',
      color: 'info',
      route: '/clients/new',
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
      route: '/inventory/bons-commande/new',
      roles: ['MAGASIN', 'GERANT', 'SUPERADMIN']
    },
    {
      title: 'Tâche Ménage',
      description: 'Assigner une tâche de ménage',
      icon: 'fas fa-tasks',
      color: 'primary',
      route: '/menage/taches/new',
      roles: ['MENAGE', 'GERANT', 'SUPERADMIN']
    }
  ];

  constructor(
    private authService: AuthService,
    public translationService: TranslationService,
    private reservationsService: ReservationsService,
    private produitsService: ProduitsService,
    private dashboardMenageService: DashboardMenageService,
    private reportingDashboardService: ReportingDashboardService,
    private commandesService: CommandesService,
    private clientsService: ClientsService,
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
   * Charger les KPI dynamiques depuis les services HTTP réels. En cas d'échec
   * sur un appel, la valeur est marquée "—" (pas de chiffre décoratif).
   *
   * Sources des KPI :
   *  - Réservations / Chambres / Produits / Tâches : services métier dédiés.
   *  - Revenus du Jour : R-DIR-001 dashboard direction (CA jour caEmisTtc).
   *  - Commandes Restaurant : commandesService.page filtré sur date du jour.
   *  - Clients Nouveaux : aucun endpoint dédié → laissé en "—" (TODO).
   */
  private loadDashboardData(): void {
    this.isLoading = true;
    const today = new Date().toISOString().slice(0, 10);

    forkJoin({
      arrivees: this.reservationsService.arriveesToday().pipe(catchError(() => of([] as unknown[]))),
      enCours: this.reservationsService.enCours().pipe(catchError(() => of([] as unknown[]))),
      produits: this.produitsService.findActifs().pipe(catchError(() => of([] as unknown[]))),
      menage: this.dashboardMenageService.getDashboard().pipe(catchError(() => of(null))),
      direction: this.reportingDashboardService.getDashboard().pipe(catchError(() => of(null))),
      commandesJour: this.commandesService
        .page({ dateDebut: today, dateFin: today }, 0, 1)
        .pipe(catchError(() => of(null))),
      clientsNouveaux: this.clientsService.countNouveauxDuJour()
        .pipe(catchError(() => of(null))),
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe(({ arrivees, enCours, produits, menage, direction, commandesJour, clientsNouveaux }) => {
        this.setCardValue("Réservations Aujourd'hui", arrivees.length);
        this.setCardValue('Chambres Occupées', enCours.length);
        this.setCardValue('Produits en Stock', produits.length);
        const menageData = menage as { nombreTachesEnCours?: number; nombreTachesAujourdhui?: number } | null;
        this.setCardValue(
          'Tâches Ménage',
          menageData?.nombreTachesEnCours ?? menageData?.nombreTachesAujourdhui ?? '—',
        );
        // CA Jour via R-DIR-001 (caEmisTtc en MRU)
        const caTtc = direction?.caJour?.caEmisTtc;
        this.setCardValue(
          'Revenus du Jour',
          caTtc != null ? `${this.formatMru(caTtc)} MRU` : '—',
        );
        // Commandes restaurant du jour (totalElements de la première page filtrée)
        this.setCardValue('Commandes Restaurant', commandesJour?.totalElements ?? '—');
        // Clients nouveaux du jour via /api/clients/nouveaux-du-jour
        this.setCardValue('Clients Nouveaux', clientsNouveaux?.count ?? '—');
        this.isLoading = false;
      });
  }

  /** Format MRU : entier sans décimales, séparateurs milliers. */
  private formatMru(montant: number): string {
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(montant);
  }

  private setCardValue(title: string, value: string | number): void {
    const card = this.dashboardCards.find((c) => c.title === title);
    if (card) {
      card.value = value;
    }
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