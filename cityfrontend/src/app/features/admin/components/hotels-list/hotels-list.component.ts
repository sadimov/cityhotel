import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import { FiltresHotels, Hotel } from '../../models/hotel.admin.model';
import { HotelsAdminService } from '../../services/hotels.admin.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface HotelsPageRequest {
  page: number;
  size: number;
  sortBy: string;
  sortDir: 'asc' | 'desc';
  filtres: FiltresHotels;
}

/**
 * Liste paginée des hôtels du SaaS (vue SUPERADMIN).
 *
 * Pattern aligné sur `menage/personnels-list` (Tour 27/28) :
 *  - table Bootstrap + pagination maison + états loading/error/empty/ready
 *  - recherche serveur avec debounce 300 ms
 *  - filtre statut (actif / inactif / tous)
 *  - actions ligne : édition, désactivation/réactivation, voir-utilisateurs
 *
 * Pas de DELETE physique : un hôtel se désactive (`actif=false`) puis
 * peut être réactivé. L'historique des données associées est préservé.
 */
@Component({
  selector: 'app-admin-hotels-list',
  templateUrl: './hotels-list.component.html',
  styleUrls: ['./hotels-list.component.scss'],
  standalone: false,
})
export class HotelsListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<Hotel> | null = null;
  request: HotelsPageRequest = {
    page: 0,
    size: 10,
    sortBy: 'nom',
    sortDir: 'asc',
    filtres: {},
  };
  searchTerm = '';
  togglingId: number | null = null;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly hotelsService: HotelsAdminService,
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
    this.hotelsService
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

  onActifFilterChange(value: string): void {
    let actif: boolean | undefined;
    if (value === 'true') {
      actif = true;
    } else if (value === 'false') {
      actif = false;
    } else {
      actif = undefined;
    }
    this.request = {
      ...this.request,
      page: 0,
      filtres: { ...this.request.filtres, actif },
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
    this.router.navigate(['/admin/hotels/new']);
  }

  edit(hotel: Hotel): void {
    if (hotel.hotelId == null) {
      return;
    }
    this.router.navigate(['/admin/hotels', hotel.hotelId]);
  }

  /**
   * Navigue vers la liste des utilisateurs en pré-filtrant par hôtel.
   * Le composant `users-list` lit le query param `hotelId`.
   */
  viewUsers(hotel: Hotel): void {
    if (hotel.hotelId == null) {
      return;
    }
    this.router.navigate(['/admin/users'], {
      queryParams: { hotelId: hotel.hotelId },
    });
  }

  toggleActif(hotel: Hotel): void {
    if (hotel.hotelId == null) {
      return;
    }
    const id = hotel.hotelId;
    const wasActif = hotel.actif !== false;
    const action$ = wasActif
      ? this.hotelsService.desactiver(id)
      : this.hotelsService.reactiver(id);

    const confirmKey = wasActif
      ? 'admin.hotels.messages.desactiverConfirm'
      : 'admin.hotels.messages.reactiverConfirm';
    const successKey = wasActif
      ? 'admin.hotels.messages.desactiverSuccess'
      : 'admin.hotels.messages.reactiverSuccess';
    const errorKey = wasActif
      ? 'admin.hotels.messages.desactiverError'
      : 'admin.hotels.messages.reactiverError';

    Swal.fire({
      title: this.i18n.translate(confirmKey),
      text: hotel.nom,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('admin.actions.confirm'),
      cancelButtonText: this.i18n.translate('admin.actions.cancel'),
      confirmButtonColor: wasActif ? '#d33' : undefined,
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.togglingId = id;
      action$
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.togglingId = null;
          }),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate(successKey),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate(errorKey),
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
