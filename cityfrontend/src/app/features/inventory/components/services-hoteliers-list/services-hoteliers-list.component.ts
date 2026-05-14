import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import {
  FiltresServicesHoteliers,
  ServiceHotelier,
} from '../../models/service-hotelier.model';
import { TypeServiceHotelier } from '../../models/type-service-hotelier.model';
import { ServicesHoteliersService } from '../../services/services-hoteliers.service';
import { TypesServicesHoteliersService } from '../../services/types-services-hoteliers.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

/**
 * Liste paginée des services hôteliers (Tour 51 Phase A).
 */
@Component({
  selector: 'app-services-hoteliers-list',
  templateUrl: './services-hoteliers-list.component.html',
  standalone: false,
})
export class ServicesHoteliersListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<ServiceHotelier> | null = null;
  request = {
    page: 0,
    size: 10,
    sortBy: 'nomService',
    sortDir: 'asc' as 'asc' | 'desc',
    filtres: {} as FiltresServicesHoteliers,
  };
  searchTerm = '';
  selectedTypeId: number | '' = '';
  types: TypeServiceHotelier[] = [];
  deleting = false;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly servicesService: ServicesHoteliersService,
    private readonly typesService: TypesServicesHoteliersService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.loadTypes();
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
    this.servicesService
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

  onTypeChange(value: string): void {
    const id = value ? Number(value) : '';
    this.selectedTypeId = id;
    this.request = {
      ...this.request,
      page: 0,
      filtres: {
        ...this.request.filtres,
        typeServiceId: typeof id === 'number' && Number.isFinite(id) ? id : undefined,
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
    this.router.navigate(['/inventory/services-hoteliers/new']);
  }

  edit(s: ServiceHotelier): void {
    if (s.serviceId == null) {
      return;
    }
    this.router.navigate(['/inventory/services-hoteliers', s.serviceId]);
  }

  view(s: ServiceHotelier): void {
    if (s.serviceId == null) {
      return;
    }
    this.router.navigate(['/inventory/services-hoteliers', s.serviceId, 'view']);
  }

  goToBridge(): void {
    this.router.navigate(['/inventory/services/ajouter-a-facture']);
  }

  remove(s: ServiceHotelier): void {
    if (s.serviceId == null) {
      return;
    }
    const id = s.serviceId;
    Swal.fire({
      title: this.i18n.translate('inventory.services.messages.deleteConfirm'),
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
      this.servicesService
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
              title: this.i18n.translate('inventory.services.messages.deleteSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('inventory.services.messages.deleteError'),
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

  private loadTypes(): void {
    this.typesService
      .findActifs()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of([] as TypeServiceHotelier[])),
      )
      .subscribe((types) => {
        this.types = types;
      });
  }
}
