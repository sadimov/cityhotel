import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import {
  FiltresProduits,
  Produit,
  StatutStock,
} from '../../models/produit.model';
import { ProduitsService } from '../../services/produits.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface ProduitsPageRequest {
  page: number;
  size: number;
  sortBy: string;
  sortDir: 'asc' | 'desc';
  filtres: FiltresProduits;
}

/**
 * Liste paginée des produits avec recherche serveur (debounce 300 ms).
 *
 * NB Tour 16 : DataTables n'est pas câblé directement ici (le wrapper
 * `<app-data-table>` n'existe pas encore dans `shared/`). On garde le même
 * pattern que `clients-list` / `reservations-list` (Tours 8 et 11) — table
 * Bootstrap + pagination maison + états loading/error/empty/ready. Quand
 * le wrapper DataTables sera disponible, ce composant pourra être migré
 * sans casser l'API publique.
 */
@Component({
  selector: 'app-produits-list',
  templateUrl: './produits-list.component.html',
  styleUrls: ['./produits-list.component.scss'],
  standalone: false,
})
export class ProduitsListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<Produit> | null = null;
  request: ProduitsPageRequest = {
    page: 0,
    size: 10,
    sortBy: 'nomProduit',
    sortDir: 'asc',
    filtres: {},
  };
  searchTerm = '';
  deleting = false;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly produitsService: ProduitsService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
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

  goToPage(page: number): void {
    if (!this.page || page < 0 || page >= this.page.totalPages) {
      return;
    }
    this.request = { ...this.request, page };
    this.load();
  }

  createNew(): void {
    this.router.navigate(['/inventory/produits/new']);
  }

  edit(produit: Produit): void {
    if (produit.produitId == null) {
      return;
    }
    this.router.navigate(['/inventory/produits', produit.produitId]);
  }

  view(produit: Produit): void {
    if (produit.produitId == null) {
      return;
    }
    this.router.navigate(['/inventory/produits', produit.produitId, 'view']);
  }

  remove(produit: Produit): void {
    if (produit.produitId == null) {
      return;
    }
    const id = produit.produitId;
    Swal.fire({
      title: this.i18n.translate('inventory.produit.messages.deleteConfirm'),
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('inventory.produit.actions.delete'),
      cancelButtonText: this.i18n.translate('inventory.actions.close'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.deleting = true;
      this.produitsService
        .delete(id)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.deleting = false;
          }),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('inventory.produit.messages.deleteSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('inventory.produit.messages.deleteError'),
            });
          },
        });
    });
  }

  get pagesArray(): number[] {
    if (!this.page) {
      return [];
    }
    return Array.from({ length: this.page.totalPages }, (_, i) => i);
  }

  /** Classe Bootstrap badge selon le statut de stock. */
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

  statutStockKey(statut: StatutStock | undefined): string {
    if (!statut) {
      return 'inventory.stock.statut.unknown';
    }
    return 'inventory.stock.statut.' + statut.toLowerCase();
  }
}
