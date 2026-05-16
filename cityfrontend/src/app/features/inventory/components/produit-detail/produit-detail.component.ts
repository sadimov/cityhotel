import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';

import { PageResponse } from '../../models/api.model';
import { Produit, StatutStock } from '../../models/produit.model';
import { MouvementStock, TypeMouvement } from '../../models/stock.model';
import { ProduitsService } from '../../services/produits.service';
import { StocksService } from '../../services/stocks.service';

type DetailState = 'loading' | 'ready' | 'error';

/**
 * Vue détail (read-only) d'un produit avec historique des mouvements de
 * stock embarqué (lecture seule, paginé Tour 51bis).
 */
@Component({
  selector: 'app-produit-detail',
  templateUrl: './produit-detail.component.html',
  standalone: false,
})
export class ProduitDetailComponent implements OnInit, OnDestroy {
  state: DetailState = 'loading';
  entity: Produit | null = null;
  mouvementsPage: PageResponse<MouvementStock> | null = null;
  mouvementsLoading = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly produitsService: ProduitsService,
    private readonly stocksService: StocksService,
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = Number(idParam);
    if (!Number.isFinite(id) || id <= 0) {
      this.state = 'error';
      return;
    }
    this.produitsService
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (p) => {
          this.entity = p;
          this.state = 'ready';
          this.loadMouvements(id, 0);
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  edit(): void {
    if (this.entity?.produitId != null) {
      this.router.navigate(['/inventory/produits', this.entity.produitId]);
    }
  }

  back(): void {
    this.router.navigate(['/inventory/produits']);
  }

  loadMouvementsPage(page: number): void {
    if (this.entity?.produitId == null) {
      return;
    }
    this.loadMouvements(this.entity.produitId, page);
  }

  private loadMouvements(produitId: number, page: number): void {
    this.mouvementsLoading = true;
    this.stocksService
      .pageMouvementsProduit(produitId, page, 10)
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.mouvementsLoading = false;
          return of(null);
        }),
      )
      .subscribe((p) => {
        this.mouvementsLoading = false;
        if (p) {
          this.mouvementsPage = p;
        }
      });
  }

  badgeClassStock(s: StatutStock | undefined): string {
    switch (s) {
      case 'NORMAL':
        return 'text-bg-success';
      case 'ALERTE':
        return 'text-bg-warning';
      case 'CRITIQUE':
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }

  statutStockKey(statut: StatutStock | undefined): string {
    if (!statut) {
      return 'inventory.stock.statut.unknown';
    }
    return 'inventory.stock.statut.' + statut.toLowerCase();
  }

  badgeClassMouvement(t: TypeMouvement | undefined): string {
    switch (t) {
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

  get mouvementsPagesArray(): number[] {
    if (!this.mouvementsPage) {
      return [];
    }
    return Array.from({ length: this.mouvementsPage.totalPages }, (_, i) => i);
  }
}
