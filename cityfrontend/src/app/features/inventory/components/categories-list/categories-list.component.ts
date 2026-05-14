import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import { CategorieProduit } from '../../models/categorie.model';
import { CategoriesService } from '../../services/categories.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste paginée des catégories de produits (Tour 51).
 *
 * Pattern aligné sur `produits-list` : DataTables-friendly (table Bootstrap
 * + pagination maison + search debounced 300 ms + états loading/error/empty/ready).
 */
@Component({
  selector: 'app-categories-list',
  templateUrl: './categories-list.component.html',
  standalone: false,
})
export class CategoriesListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<CategorieProduit> | null = null;
  request = {
    page: 0,
    size: 10,
    sortBy: 'nomCategorie',
    sortDir: 'asc' as 'asc' | 'desc',
    search: '' as string | undefined,
  };
  searchTerm = '';
  deleting = false;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly categoriesService: CategoriesService,
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
    this.categoriesService
      .page(this.request.search, this.request.page, this.request.size, this.request.sortBy, this.request.sortDir)
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
      this.request = { ...this.request, page: 0, search: value.trim() || undefined };
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
    this.router.navigate(['/inventory/categories/new']);
  }

  edit(c: CategorieProduit): void {
    if (c.categorieId == null) {
      return;
    }
    this.router.navigate(['/inventory/categories', c.categorieId]);
  }

  view(c: CategorieProduit): void {
    if (c.categorieId == null) {
      return;
    }
    this.router.navigate(['/inventory/categories', c.categorieId, 'view']);
  }

  remove(c: CategorieProduit): void {
    if (c.categorieId == null) {
      return;
    }
    const id = c.categorieId;
    Swal.fire({
      title: this.i18n.translate('inventory.categories.messages.deleteConfirm'),
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('inventory.actions.delete'),
      cancelButtonText: this.i18n.translate('inventory.actions.close'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.deleting = true;
      this.categoriesService
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
              title: this.i18n.translate('inventory.categories.messages.deleteSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('inventory.categories.messages.deleteError'),
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
}
