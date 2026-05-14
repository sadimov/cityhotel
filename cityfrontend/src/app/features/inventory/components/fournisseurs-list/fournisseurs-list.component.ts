import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import { Fournisseur } from '../../models/fournisseur.model';
import { FournisseursService } from '../../services/fournisseurs.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste paginée des fournisseurs (Tour 51).
 */
@Component({
  selector: 'app-fournisseurs-list',
  templateUrl: './fournisseurs-list.component.html',
  standalone: false,
})
export class FournisseursListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<Fournisseur> | null = null;
  request = {
    page: 0,
    size: 10,
    sortBy: 'nomFournisseur',
    sortDir: 'asc' as 'asc' | 'desc',
    search: '' as string | undefined,
  };
  searchTerm = '';
  deleting = false;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly fournisseursService: FournisseursService,
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
    this.fournisseursService
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
    this.router.navigate(['/inventory/fournisseurs/new']);
  }

  edit(f: Fournisseur): void {
    if (f.fournisseurId == null) {
      return;
    }
    this.router.navigate(['/inventory/fournisseurs', f.fournisseurId]);
  }

  view(f: Fournisseur): void {
    if (f.fournisseurId == null) {
      return;
    }
    this.router.navigate(['/inventory/fournisseurs', f.fournisseurId, 'view']);
  }

  remove(f: Fournisseur): void {
    if (f.fournisseurId == null) {
      return;
    }
    const id = f.fournisseurId;
    Swal.fire({
      title: this.i18n.translate('inventory.fournisseurs.messages.deleteConfirm'),
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
      this.fournisseursService
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
              title: this.i18n.translate('inventory.fournisseurs.messages.deleteSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('inventory.fournisseurs.messages.deleteError'),
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
