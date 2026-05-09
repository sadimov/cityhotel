import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { PageResponse } from '../../models/api.model';
import {
  FiltresReservations,
  Reservation,
  StatutReservation,
} from '../../models/reservation.model';
import { ReservationsService } from '../../services/reservations.service';

type ListState = 'loading' | 'ready' | 'empty' | 'error';

interface ReservationsPageRequest {
  page: number;
  size: number;
  sortBy: string;
  sortDir: 'asc' | 'desc';
  filtres: FiltresReservations;
  recherche?: string;
}

/**
 * Liste paginée des réservations avec recherche serveur (debounce 300 ms),
 * cohérente avec `clients-list` (cf. Tour 8). DataTables n'est pas câblé ici :
 * un wrapper `<app-data-table>` viendra plus tard dans `shared/`.
 */
@Component({
  selector: 'app-reservations-list',
  templateUrl: './reservations-list.component.html',
  styleUrls: ['./reservations-list.component.scss'],
  standalone: false,
})
export class ReservationsListComponent implements OnInit, OnDestroy {
  state: ListState = 'loading';
  page: PageResponse<Reservation> | null = null;
  request: ReservationsPageRequest = {
    page: 0,
    size: 10,
    // TODO[B1B2] backend may only accept `createdAt` as sort key — to revisit
    // once backend tour audit hebergement B1+B2 lands and confirms whether
    // `dateCreation` is still exposed on the DTO.
    sortBy: 'dateCreation',
    sortDir: 'desc',
    filtres: {},
  };
  searchTerm = '';
  cancelling = false;

  /** Énum exposé au template pour color-coder le statut. */
  readonly StatutReservation = StatutReservation;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly reservationsService: ReservationsService,
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
    const obs$ = this.request.recherche
      ? this.reservationsService.rechercher(
          this.request.recherche,
          this.request.page,
          this.request.size,
        )
      : this.reservationsService.page(
          this.request.filtres,
          this.request.page,
          this.request.size,
          this.request.sortBy,
          this.request.sortDir,
        );

    obs$
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
        recherche: value.trim() || undefined,
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
    this.router.navigate(['/hebergement/reservations/new']);
  }

  edit(reservation: Reservation): void {
    if (reservation.reservationId == null) {
      return;
    }
    this.router.navigate(['/hebergement/reservations', reservation.reservationId]);
  }

  goCheckIn(): void {
    this.router.navigate(['/hebergement/check-in']);
  }

  cancel(reservation: Reservation): void {
    if (reservation.reservationId == null) {
      return;
    }
    const id = reservation.reservationId;
    Swal.fire({
      title: this.i18n.translate('hebergement.messages.cancelConfirm'),
      input: 'text',
      inputLabel: this.i18n.translate('hebergement.messages.cancelMotif'),
      inputValidator: (value) =>
        value ? null : this.i18n.translate('hebergement.messages.cancelMotifRequired'),
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('hebergement.actions.cancel'),
      cancelButtonText: this.i18n.translate('hebergement.actions.close'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed || !result.value) {
        return;
      }
      this.cancelling = true;
      this.reservationsService
        .annuler(id, String(result.value))
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            this.cancelling = false;
          }),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('hebergement.messages.cancelSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load();
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('hebergement.messages.cancelError'),
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

  /** Classe Bootstrap badge pour color-coder le statut. */
  badgeClass(statut: StatutReservation | undefined): string {
    switch (statut) {
      case StatutReservation.CONFIRMEE:
        return 'text-bg-info';
      case StatutReservation.ARRIVEE:
        return 'text-bg-success';
      case StatutReservation.PARTIE:
        return 'text-bg-secondary';
      case StatutReservation.ANNULEE:
        return 'text-bg-danger';
      case StatutReservation.EN_ATTENTE:
      default:
        return 'text-bg-warning';
    }
  }

  /** Clé i18n du libellé statut. */
  statutKey(statut: StatutReservation | undefined): string {
    if (!statut) {
      return 'hebergement.statut.en_attente';
    }
    return 'hebergement.statut.' + statut.toLowerCase();
  }
}
