import { Component, OnDestroy, OnInit } from '@angular/core';
import {
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Client } from '../../../clients/models/client.model';
import { Societe } from '../../../clients/models/societe.model';
import { ClientsService } from '../../../clients/services/clients.service';
import { Chambre } from '../../models/chambre.model';
import {
  CreerReservationChambreRequest,
  CreerReservationRequest,
  ModifierReservationRequest,
  Reservation,
} from '../../models/reservation.model';
import { ChambresService } from '../../services/chambres.service';
import { ReservationsService } from '../../services/reservations.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'une réservation.
 *
 * - Selects par nom pour `clientPrincipalId` et `societeId` (chargement
 *   ClientsService).
 * - FormArray `chambres` avec au moins une chambre obligatoire à la création
 *   (backend `ReservationCreateDto.chambres` est `@NotEmpty`).
 * - Les chambres sont sélectionnées par numéro (lookup ChambresService.findActives).
 * - En édition (`/:id`), les chambres sont laissées intactes côté backend si
 *   `chambres` n'est pas modifié (envoyé `undefined`).
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

  clients: Client[] = [];
  societes: Societe[] = [];
  chambresActives: Chambre[] = [];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly reservationsService: ReservationsService,
    private readonly clientsService: ClientsService,
    private readonly chambresService: ChambresService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();
    this.loadReferentiels();

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
      // Mode création : pré-initialise une ligne chambre vide (au moins 1
      // chambre obligatoire pour passer la validation backend).
      this.addChambre();
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

  get chambresArray(): FormArray<FormGroup> {
    return this.form.get('chambres') as FormArray<FormGroup>;
  }

  addChambre(): void {
    this.chambresArray.push(this.buildChambreLigne());
  }

  removeChambre(index: number): void {
    if (!this.isEditing && this.chambresArray.length <= 1) {
      return; // Au moins 1 chambre requise en création
    }
    this.chambresArray.removeAt(index);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.chambresArray.markAllAsTouched();
      return;
    }
    if (!this.isEditing && this.chambresArray.length === 0) {
      Swal.fire({
        icon: 'warning',
        title: this.i18n.translate('hebergement.errors.chambreRequired'),
      });
      return;
    }
    this.state = 'submitting';
    const raw = this.form.getRawValue();

    const chambres: CreerReservationChambreRequest[] = (raw.chambres || [])
      .filter((c: { chambreId: number | null }) => c.chambreId != null)
      .map((c: { chambreId: number; prixNuit: number; dateDebut?: string; dateFin?: string }) => ({
        chambreId: Number(c.chambreId),
        prixNuit: Number(c.prixNuit),
        dateDebut: c.dateDebut || undefined,
        dateFin: c.dateFin || undefined,
      }));

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
      chambres: this.isEditing
        ? (chambres.length > 0 ? chambres : undefined as unknown as CreerReservationChambreRequest[])
        : chambres,
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
        error: (err) => {
          const key = err?.error?.error || 'hebergement.messages.saveError';
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate(key),
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

  private loadReferentiels(): void {
    this.clientsService
      .page({ page: 0, size: 300, sortBy: 'nom', sortDir: 'asc' })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (p) => (this.clients = p.content || []),
        error: () => (this.clients = []),
      });
    this.clientsService
      .pageSocietes({ page: 0, size: 300, sortBy: 'societeNom', sortDir: 'asc' })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (p) => (this.societes = p.content || []),
        error: () => (this.societes = []),
      });
    this.chambresService
      .findActives()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (list) => (this.chambresActives = list || []),
        error: () => (this.chambresActives = []),
      });
  }

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
      chambres: this.fb.array<FormGroup>([]),
    });
  }

  private buildChambreLigne(seed?: { chambreId?: number; prixNuit?: number; dateDebut?: string; dateFin?: string }): FormGroup {
    return this.fb.group({
      chambreId: new FormControl<number | null>(seed?.chambreId ?? null, {
        validators: [Validators.required, Validators.min(1)],
      }),
      prixNuit: new FormControl<number>(seed?.prixNuit ?? 0, {
        validators: [Validators.required, Validators.min(0)],
        nonNullable: true,
      }),
      dateDebut: new FormControl<string | null>(seed?.dateDebut ?? null),
      dateFin: new FormControl<string | null>(seed?.dateFin ?? null),
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
    // Hydrate les chambres existantes (lecture seule en édition — les
    // modifications de chambres passent par d'autres flux dédiés).
    if (r.chambres && r.chambres.length > 0) {
      while (this.chambresArray.length > 0) {
        this.chambresArray.removeAt(0);
      }
      for (const c of r.chambres) {
        this.chambresArray.push(this.buildChambreLigne({
          chambreId: c.chambreId,
          prixNuit: c.prixNuit,
          dateDebut: c.dateDebut,
          dateFin: c.dateFin,
        }));
      }
    }
  }
}
