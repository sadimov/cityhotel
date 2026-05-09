import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';

import { TranslationService } from '../../../../services/translation.service';
import { Page } from '../../models/page.model';
import {
  ModePaiement,
  MODES_PAIEMENT,
  PaiementDto,
  StatutPaiement,
  STATUTS_PAIEMENT,
} from '../../models/paiement.model';
import { PaiementsService } from '../../services/paiements.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface PaiementsPageRequest {
  page: number;
  size: number;
  sort: string;
}

/**
 * Liste paginée des paiements (historique).
 *
 * Le backend `PaiementController.findAll(Pageable)` n'expose pas (encore)
 * de filtres serveur ; les filtres mode + statut sont appliqués côté client
 * sur la page courante. Pattern aligné sur `factures-list`.
 */
@Component({
  selector: 'app-paiements-list',
  templateUrl: './paiements-list.component.html',
  styleUrls: ['./paiements-list.component.scss'],
  standalone: false,
})
export class PaiementsListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: Page<PaiementDto> | null = null;
  request: PaiementsPageRequest = {
    page: 0,
    size: 10,
    sort: 'datePaiement,desc',
  };
  selectedMode: ModePaiement | '' = '';
  selectedStatut: StatutPaiement | '' = '';

  readonly modes = MODES_PAIEMENT;
  readonly statuts = STATUTS_PAIEMENT;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly paiementsService: PaiementsService,
    private readonly _router: Router,
    private readonly _i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.state = 'loading';
    this.paiementsService
      .page({
        page: this.request.page,
        size: this.request.size,
        sort: this.request.sort,
      })
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of(null);
        }),
      )
      .subscribe((p) => {
        if (!p) return;
        this.page = p;
        this.state = p.numberOfElements === 0 ? 'empty' : 'ready';
      });
  }

  onModeChange(value: string): void {
    this.selectedMode = (value || '') as ModePaiement | '';
  }

  onStatutChange(value: string): void {
    this.selectedStatut = (value || '') as StatutPaiement | '';
  }

  goToPage(page: number): void {
    if (!this.page || page < 0 || page >= this.page.totalPages) {
      return;
    }
    this.request = { ...this.request, page };
    this.load();
  }

  /**
   * Paiements affichés (filtres mode + statut client-side).
   */
  get paiements(): PaiementDto[] {
    const all = this.page?.content ?? [];
    return all.filter((p) => {
      if (this.selectedMode && p.modePaiement !== this.selectedMode) return false;
      if (this.selectedStatut && p.statut !== this.selectedStatut) return false;
      return true;
    });
  }

  get pagesArray(): number[] {
    if (!this.page) return [];
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  badgeStatutClass(statut: StatutPaiement | undefined): string {
    switch (statut) {
      case StatutPaiement.VALIDE:
        return 'text-bg-success';
      case StatutPaiement.EN_ATTENTE:
        return 'text-bg-warning';
      case StatutPaiement.REFUSE:
        return 'text-bg-danger';
      case StatutPaiement.ANNULE:
        return 'text-bg-secondary';
      default:
        return 'text-bg-secondary';
    }
  }

  statutKey(statut: StatutPaiement | undefined): string {
    if (!statut) return 'finance.paiement.statut.VALIDE';
    return 'finance.paiement.statut.' + statut;
  }

  modeKey(mode: ModePaiement | undefined): string {
    if (!mode) return 'finance.paiement.mode.ESPECES';
    return 'finance.paiement.mode.' + mode;
  }
}
