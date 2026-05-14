import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';
import { of } from 'rxjs';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import {
  BonCommande,
  FiltresBonsCommande,
  StatutBonCommande,
  STATUTS_BON_COMMANDE,
} from '../../models/bon-commande.model';
import { BonsCommandeService } from '../../services/bons-commande.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface BonsCommandePageRequest {
  page: number;
  size: number;
  sortBy: string;
  sortDir: 'asc' | 'desc';
  filtres: FiltresBonsCommande;
}

/**
 * Liste paginée des bons de commande avec recherche serveur (debounce 300 ms)
 * et filtre statut.
 */
@Component({
  selector: 'app-bons-commande-list',
  templateUrl: './bons-commande-list.component.html',
  styleUrls: ['./bons-commande-list.component.scss'],
  standalone: false,
})
export class BonsCommandeListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<BonCommande> | null = null;
  request: BonsCommandePageRequest = {
    page: 0,
    size: 10,
    sortBy: 'dateCreation',
    sortDir: 'desc',
    filtres: {},
  };
  searchTerm = '';
  selectedStatut: StatutBonCommande | '' = '';

  readonly statuts = STATUTS_BON_COMMANDE;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly bonsCommandeService: BonsCommandeService,
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
    this.bonsCommandeService
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
    this.selectedStatut = (value || '') as StatutBonCommande | '';
    this.request = {
      ...this.request,
      page: 0,
      filtres: {
        ...this.request.filtres,
        statut: this.selectedStatut || undefined,
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

  createNew(): void {
    this.router.navigate(['/inventory/bons-commande/new']);
  }

  edit(bon: BonCommande): void {
    if (bon.bonCommandeId == null) {
      return;
    }
    this.router.navigate(['/inventory/bons-commande', bon.bonCommandeId]);
  }

  view(bon: BonCommande): void {
    if (bon.bonCommandeId == null) {
      return;
    }
    this.router.navigate(['/inventory/bons-commande', bon.bonCommandeId, 'view']);
  }

  get pagesArray(): number[] {
    if (!this.page) {
      return [];
    }
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  /** Classe Bootstrap badge selon le statut. */
  badgeClass(statut: StatutBonCommande | undefined): string {
    switch (statut) {
      case 'brouillon':
        return 'text-bg-secondary';
      case 'envoye':
        return 'text-bg-primary';
      case 'confirme':
        return 'text-bg-info';
      case 'recu_partiel':
        return 'text-bg-warning';
      case 'recu_complet':
        return 'text-bg-success';
      case 'annule':
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }

  statutKey(statut: StatutBonCommande | undefined): string {
    if (!statut) {
      return 'inventory.bonCommande.statut.brouillon';
    }
    return 'inventory.bonCommande.statut.' + statut;
  }
}
