import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, of } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import {
  FiltresProduits,
  Produit,
  StatutStock,
} from '../../models/produit.model';
import { ProduitsService } from '../../services/produits.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface StocksPageRequest {
  page: number;
  size: number;
  sortBy: string;
  sortDir: 'asc' | 'desc';
  filtres: FiltresProduits;
}

/**
 * Liste paginée — état des stocks (lecture seule).
 *
 * S'appuie sur `ProduitsService.page()` qui inclut, pour chaque produit,
 * la photo instantanée du stock + le statut calculé serveur (NORMAL /
 * ALERTE / CRITIQUE). Filtre dédié sur `statutStock` pour cibler les
 * produits en alerte ou en rupture.
 */
@Component({
  selector: 'app-stocks-list',
  templateUrl: './stocks-list.component.html',
  styleUrls: ['./stocks-list.component.scss'],
  standalone: false,
})
export class StocksListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<Produit> | null = null;
  request: StocksPageRequest = {
    page: 0,
    size: 20,
    sortBy: 'stockActuel',
    sortDir: 'asc',
    filtres: {},
  };
  selectedStatut: StatutStock | '' = '';

  readonly statutsStock: ReadonlyArray<StatutStock> = ['CRITIQUE', 'ALERTE', 'NORMAL'];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly produitsService: ProduitsService,
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
    this.produitsService
      .page(
        this.request.filtres,
        this.request.page,
        this.request.size,
        this.request.sortBy,
        this.request.sortDir,
      )
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of(null);
        }),
      )
      .subscribe((p) => {
        if (!p) {
          return;
        }
        this.page = p;
        this.state = p.numberOfElements === 0 ? 'empty' : 'ready';
      });
  }

  onStatutChange(value: string): void {
    this.selectedStatut = (value || '') as StatutStock | '';
    this.request = {
      ...this.request,
      page: 0,
      filtres: {
        ...this.request.filtres,
        statutStock: this.selectedStatut || undefined,
      },
    };
    this.load();
  }

  goToPage(page: number): void {
    if (!this.page || page < 0 || page >= this.page.totalPages) {
      return;
    }
    this.request = { ...this.request, page };
    this.load();
  }

  get pagesArray(): number[] {
    if (!this.page) {
      return [];
    }
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  badgeClass(statut: StatutStock | undefined): string {
    switch (statut) {
      case 'CRITIQUE':
        return 'text-bg-danger';
      case 'ALERTE':
        return 'text-bg-warning';
      case 'NORMAL':
        return 'text-bg-success';
      default:
        return 'text-bg-secondary';
    }
  }

  statutKey(statut: StatutStock | undefined): string {
    if (!statut) {
      return 'inventory.stock.statut.unknown';
    }
    return 'inventory.stock.statut.' + statut.toLowerCase();
  }
}
