import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import { TypeServiceHotelier } from '../../models/type-service-hotelier.model';
import { TypesServicesHoteliersService } from '../../services/types-services-hoteliers.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste paginée des types de services hôteliers (Tour 51 Phase A).
 */
@Component({
  selector: 'app-types-services-hoteliers-list',
  templateUrl: './types-services-hoteliers-list.component.html',
  standalone: false,
})
export class TypesServicesHoteliersListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<TypeServiceHotelier> | null = null;
  request = {
    page: 0,
    size: 10,
    sortBy: 'nomType',
    sortDir: 'asc' as 'asc' | 'desc',
  };
  searchTerm = '';
  deleting = false;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly typesService: TypesServicesHoteliersService,
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
    this.typesService
      .page({ search: this.searchTerm.trim() || undefined }, this.request.page, this.request.size, this.request.sortBy, this.request.sortDir)
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
      this.request = { ...this.request, page: 0 };
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
    this.router.navigate(['/inventory/types-services-hoteliers/new']);
  }

  edit(t: TypeServiceHotelier): void {
    if (t.typeServiceId == null) {
      return;
    }
    this.router.navigate(['/inventory/types-services-hoteliers', t.typeServiceId]);
  }

  view(t: TypeServiceHotelier): void {
    if (t.typeServiceId == null) {
      return;
    }
    this.router.navigate(['/inventory/types-services-hoteliers', t.typeServiceId, 'view']);
  }

  remove(t: TypeServiceHotelier): void {
    if (t.typeServiceId == null) {
      return;
    }
    const id = t.typeServiceId;
    Swal.fire({
      title: this.i18n.translate('inventory.typesServices.messages.deleteConfirm'),
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
      this.typesService
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
              title: this.i18n.translate('inventory.typesServices.messages.deleteSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('inventory.typesServices.messages.deleteError'),
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
