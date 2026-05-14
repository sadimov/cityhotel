import { Component, OnDestroy, OnInit } from '@angular/core';
import { of, Subject } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import {
  FiltresMouvements,
  MouvementStock,
  TYPES_MOUVEMENT,
  TypeMouvement,
} from '../../models/stock.model';
import { StocksService } from '../../services/stocks.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface MvtsPageRequest {
  page: number;
  size: number;
  sortBy: string;
  sortDir: 'asc' | 'desc';
  filtres: FiltresMouvements;
}

/**
 * Liste paginée des mouvements de stock (lecture seule, Tour 51).
 */
@Component({
  selector: 'app-mouvements-stock-list',
  templateUrl: './mouvements-stock-list.component.html',
  standalone: false,
})
export class MouvementsStockListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<MouvementStock> | null = null;
  request: MvtsPageRequest = {
    page: 0,
    size: 20,
    sortBy: 'dateMouvement',
    sortDir: 'desc',
    filtres: {},
  };
  selectedType: TypeMouvement | '' = '';
  readonly types = TYPES_MOUVEMENT;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly stocksService: StocksService,
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
    this.stocksService
      .pageMouvements(this.request.filtres, this.request.page, this.request.size, this.request.sortBy, this.request.sortDir)
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

  onTypeChange(value: string): void {
    this.selectedType = (value || '') as TypeMouvement | '';
    this.request = {
      ...this.request,
      page: 0,
      filtres: { ...this.request.filtres, typeMouvement: this.selectedType || undefined },
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

  badgeClass(type: TypeMouvement | undefined): string {
    switch (type) {
      case 'entree':
        return 'text-bg-success';
      case 'sortie':
        return 'text-bg-warning';
      case 'ajustement':
        return 'text-bg-info';
      case 'perte':
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }
}
