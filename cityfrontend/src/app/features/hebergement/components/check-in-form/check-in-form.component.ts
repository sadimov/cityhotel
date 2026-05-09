import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Reservation, StatutReservation } from '../../models/reservation.model';
import { ReservationsService } from '../../services/reservations.service';

type CheckInState = 'idle' | 'searching' | 'submitting' | 'error';

/**
 * Formulaire de check-in.
 *
 * Workflow :
 *  1. L'utilisateur saisit un terme de recherche (numéro de réservation ou nom client).
 *  2. Le composant interroge `ReservationsService.rechercher()` (debounce 300 ms).
 *  3. L'utilisateur sélectionne une réservation dans la liste de résultats.
 *  4. Validation déclenche `effectuerCheckIn(reservationId)` côté serveur.
 *
 * Le serveur, lui, libère la chambre attribuée, démarre la nuitée et passe la
 * réservation en statut `ARRIVEE` (cf. règle métier §6.4 du CLAUDE.md racine).
 */
@Component({
  selector: 'app-check-in-form',
  templateUrl: './check-in-form.component.html',
  styleUrls: ['./check-in-form.component.scss'],
  standalone: false,
})
export class CheckInFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: CheckInState = 'idle';
  results: Reservation[] = [];
  selected: Reservation | null = null;

  readonly StatutReservation = StatutReservation;

  private readonly destroy$ = new Subject<void>();
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly fb: FormBuilder,
    private readonly router: Router,
    private readonly reservationsService: ReservationsService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      terme: ['', [Validators.required, Validators.minLength(2)]],
    });
  }

  ngOnDestroy(): void {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Recherche serveur avec debounce 300 ms. */
  onTermeChange(value: string): void {
    this.form.get('terme')?.setValue(value, { emitEvent: false });
    this.selected = null;
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
    }
    if (!value || value.trim().length < 2) {
      this.results = [];
      this.state = 'idle';
      return;
    }
    this.searchTimer = setTimeout(() => this.runSearch(value.trim()), 300);
  }

  select(reservation: Reservation): void {
    this.selected = reservation;
  }

  goBack(): void {
    this.router.navigate(['/hebergement/reservations']);
  }

  /** Clé i18n du libellé statut. */
  statutKey(reservation: Reservation): string {
    const s = reservation.statut ?? StatutReservation.EN_ATTENTE;
    return 'hebergement.statut.' + s.toLowerCase();
  }

  isSelectable(reservation: Reservation): boolean {
    return (
      reservation.statut === StatutReservation.CONFIRMEE ||
      reservation.statut === StatutReservation.EN_ATTENTE
    );
  }

  confirm(): void {
    if (!this.selected || this.selected.reservationId == null) {
      return;
    }
    const id = this.selected.reservationId;
    Swal.fire({
      title: this.i18n.translate('hebergement.checkIn.confirmTitle'),
      text: this.selected.numeroReservation || '',
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('hebergement.actions.checkIn'),
      cancelButtonText: this.i18n.translate('hebergement.actions.close'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }
      this.state = 'submitting';
      this.reservationsService
        .checkIn(id)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => {
            if (this.state === 'submitting') {
              this.state = 'idle';
            }
          }),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('hebergement.checkIn.success'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.router.navigate(['/hebergement/reservations/list']);
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('hebergement.checkIn.error'),
            });
          },
        });
    });
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private runSearch(terme: string): void {
    this.state = 'searching';
    this.reservationsService
      .rechercher(terme, 0, 25)
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => {
          this.state = 'error';
          return of(null);
        }),
      )
      .subscribe((p) => {
        if (!p) {
          this.results = [];
          return;
        }
        this.results = p.content ?? [];
        this.state = 'idle';
      });
  }
}
