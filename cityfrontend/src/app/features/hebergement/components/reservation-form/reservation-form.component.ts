import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import {
  CreerReservationRequest,
  ModifierReservationRequest,
  Reservation,
} from '../../models/reservation.model';
import { ReservationsService } from '../../services/reservations.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'une réservation.
 *
 * Périmètre Tour 11 : informations principales (client, dates, occupants).
 * La sélection multi-chambres + tarification dynamique sera traitée dans une
 * itération ultérieure (UX dédiée + endpoint `rechercher-disponibilite`).
 */
@Component({
  selector: 'app-reservation-form',
  templateUrl: './reservation-form.component.html',
  styleUrls: ['./reservation-form.component.scss'],
  standalone: false,
})
export class ReservationFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  editingId: number | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly reservationsService: ReservationsService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam && idParam !== 'new') {
      const id = Number(idParam);
      if (!Number.isFinite(id)) {
        this.state = 'error';
        return;
      }
      this.editingId = id;
      this.loadExisting(id);
    } else {
      this.state = 'ready';
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get isEditing(): boolean {
    return this.editingId !== null;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.state = 'submitting';
    const raw = this.form.getRawValue();

    const payload: CreerReservationRequest | ModifierReservationRequest = {
      clientPrincipalId: Number(raw.clientPrincipalId),
      societeId: raw.societeId ? Number(raw.societeId) : undefined,
      dateArrivee: raw.dateArrivee,
      dateDepart: raw.dateDepart,
      nbAdultes: Number(raw.nbAdultes ?? 1),
      nbEnfants: Number(raw.nbEnfants ?? 0),
      motifSejour: raw.motifSejour || undefined,
      commentaires: raw.commentaires || undefined,
      reductionPourcentage:
        raw.reductionPourcentage != null && raw.reductionPourcentage !== ''
          ? Number(raw.reductionPourcentage)
          : undefined,
      // Le tableau `chambres` reste vide en Tour 11 — l'UX d'allocation
      // multi-chambres viendra dans un tour dédié.
      chambres: [],
    };

    const obs$ = this.editingId
      ? this.reservationsService.update(this.editingId, payload as ModifierReservationRequest)
      : this.reservationsService.create(payload as CreerReservationRequest);

    obs$
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          if (this.state === 'submitting') {
            this.state = 'ready';
          }
        }),
      )
      .subscribe({
        next: () => {
          const successKey = this.editingId
            ? 'hebergement.messages.updateSuccess'
            : 'hebergement.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/hebergement/reservations/list']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('hebergement.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/hebergement/reservations/list']);
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private buildForm(): FormGroup {
    return this.fb.group({
      clientPrincipalId: [null, [Validators.required, Validators.min(1)]],
      societeId: [null],
      dateArrivee: ['', [Validators.required]],
      dateDepart: ['', [Validators.required]],
      nbAdultes: [1, [Validators.required, Validators.min(1)]],
      nbEnfants: [0, [Validators.min(0)]],
      motifSejour: [''],
      commentaires: [''],
      reductionPourcentage: [null, [Validators.min(0), Validators.max(100)]],
    });
  }

  private loadExisting(id: number): void {
    this.reservationsService
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (r) => {
          this.hydrateForm(r);
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrateForm(r: Reservation): void {
    this.form.patchValue({
      clientPrincipalId: r.clientPrincipalId ?? null,
      societeId: r.societeId ?? null,
      dateArrivee: r.dateArrivee ?? '',
      dateDepart: r.dateDepart ?? '',
      nbAdultes: r.nbAdultes ?? 1,
      nbEnfants: r.nbEnfants ?? 0,
      motifSejour: r.motifSejour ?? '',
      commentaires: r.commentaires ?? '',
      reductionPourcentage: r.reductionPourcentage ?? null,
    });
  }
}
