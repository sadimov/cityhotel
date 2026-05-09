import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, of } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';

import { DashboardMenage, StatistiquesMenage } from '../../models/statistiques-menage.model';
import { DashboardMenageService } from '../../services/dashboard-menage.service';

type DashboardState = 'loading' | 'ready' | 'error';

interface RepartitionEntry {
  key: string;
  value: number;
}

/**
 * Dashboard ménage — vue synthèse texte (KPI + répartitions).
 *
 * Périmètre Tour 27 (cf. consigne) : KPI texte uniquement, pas de
 * graphiques Chart.js (différés au tour suivant).
 *
 * Lit `GET /api/menage/dashboard` (agrégat) et expose les chiffres clés :
 *  - Personnel actif
 *  - Tâches du jour (total / en cours / terminées / en retard)
 *  - Tâches urgentes
 *  - Taux de réalisation
 *  - Temps moyen de réalisation
 *  - Répartitions par statut, type, priorité (sous forme de listes)
 */
@Component({
  selector: 'app-menage-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
  standalone: false,
})
export class DashboardComponent implements OnInit, OnDestroy {
  state: DashboardState = 'loading';
  dashboard: DashboardMenage | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(private readonly dashboardService: DashboardMenageService) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.state = 'loading';
    this.dashboardService
      .getDashboard()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of<DashboardMenage | null>(null);
        }),
      )
      .subscribe((d) => {
        if (!d) {
          return;
        }
        this.dashboard = d;
        this.state = 'ready';
      });
  }

  get stats(): StatistiquesMenage {
    return this.dashboard?.statistiques ?? {};
  }

  /** Convertit une `Record<string, number>` en liste triée pour l'affichage. */
  toEntries(rec?: Record<string, number>): RepartitionEntry[] {
    if (!rec) {
      return [];
    }
    return Object.entries(rec)
      .map(([key, value]) => ({ key, value }))
      .sort((a, b) => b.value - a.value);
  }
}
