import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import {
  BonSortie,
  FiltresBonsSortie,
  StatutBonSortie,
  STATUTS_BON_SORTIE,
} from '../../models/bon-sortie.model';
import { BonsSortieService } from '../../services/bons-sortie.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface BonsSortiePageRequest {
  page: number;
  size: number;
  sortBy: string;
  sortDir: 'asc' | 'desc';
  filtres: FiltresBonsSortie;
}

/**
 * Liste paginée des bons de sortie (Tour 51).
 */
@Component({
  selector: 'app-bons-sortie-list',
  templateUrl: './bons-sortie-list.component.html',
  standalone: false,
})
export class BonsSortieListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<BonSortie> | null = null;
  request: BonsSortiePageRequest = {
    page: 0,
    size: 10,
    sortBy: 'dateCreation',
    sortDir: 'desc',
    filtres: {},
  };
  searchTerm = '';
  selectedStatut: StatutBonSortie | '' = '';

  readonly statuts = STATUTS_BON_SORTIE;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly bonsSortieService: BonsSortieService,
    private readonly router: Router,
    private readonly _i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(): void {
    this.state = 'loading';
    this.bonsSortieService
      .page(this.request.filtres, this.request.page, this.request.size, this.request.sortBy, this.request.sortDir)
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

  onSearchChange(value: string): void {
    this.searchTerm = value;
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.searchTimer = setTimeout(() => {
      this.request = {
        ...this.request,
        page: 0,
        filtres: { ...this.request.filtres, search: value.trim() || undefined },
      };
      this.load();
    }, 300);
  }

  onStatutChange(value: string): void {
    this.selectedStatut = (value || '') as StatutBonSortie | '';
    this.request = {
      ...this.request,
      page: 0,
      filtres: { ...this.request.filtres, statut: this.selectedStatut || undefined },
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

  createNew(): void {
    this.router.navigate(['/inventory/bons-sortie/new']);
  }

  edit(bon: BonSortie): void {
    if (bon.bonSortieId == null) {
      return;
    }
    this.router.navigate(['/inventory/bons-sortie', bon.bonSortieId]);
  }

  view(bon: BonSortie): void {
    if (bon.bonSortieId == null) {
      return;
    }
    this.router.navigate(['/inventory/bons-sortie', bon.bonSortieId, 'view']);
  }

  get pagesArray(): number[] {
    if (!this.page) {
      return [];
    }
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  badgeClass(statut: StatutBonSortie | undefined): string {
    switch (statut) {
      case 'brouillon':
        return 'text-bg-secondary';
      case 'valide':
        return 'text-bg-info';
      case 'livre':
        return 'text-bg-success';
      case 'annule':
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }

  statutKey(statut: StatutBonSortie | undefined): string {
    if (!statut) {
      return 'inventory.bonsSortie.statut.brouillon';
    }
    return 'inventory.bonsSortie.statut.' + statut;
  }
}
