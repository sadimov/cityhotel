import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Chambre, StatutChambre } from '../../models/chambre.model';
import { TypeChambre } from '../../models/type-chambre.model';
import { ChambresService } from '../../services/chambres.service';
import { TypesChambreService } from '../../services/types-chambre.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/** Formulaire création / édition d'une chambre. */
@Component({
  selector: 'app-chambre-form',
  templateUrl: './chambre-form.component.html',
  standalone: false,
})
export class ChambreFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  editingId: number | null = null;

  types: TypeChambre[] = [];

  readonly statuts: StatutChambre[] = [
    StatutChambre.DISPONIBLE,
    StatutChambre.OCCUPEE,
    StatutChambre.NETTOYAGE,
    StatutChambre.MAINTENANCE,
    StatutChambre.HORS_SERVICE,
  ];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly chambresService: ChambresService,
    private readonly typesService: TypesChambreService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();

    // Chargement du select Type — pré-requis pour pouvoir éditer.
    this.typesService
      .findActifs()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (list) => {
          this.types = list;
          this.afterDepsLoaded();
        },
        error: () => {
          this.types = [];
          this.afterDepsLoaded();
        },
      });
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
    // ⚠️ Pas de hotelId — backend l'extrait du JWT.
    const payload: Chambre = {
      numeroChambre: raw.numeroChambre,
      typeId: Number(raw.typeId),
      etage: raw.etage != null && raw.etage !== '' ? Number(raw.etage) : undefined,
      statut: raw.statut,
      nbLits: Number(raw.nbLits),
      nbPersonnesMax: Number(raw.nbPersonnesMax),
      equipements: raw.equipements || undefined,
      description: raw.description || undefined,
    };
    const obs$ = this.editingId
      ? this.chambresService.update(this.editingId, payload)
      : this.chambresService.create(payload);

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
          const key = this.editingId
            ? 'hebergement.chambres.messages.updateSuccess'
            : 'hebergement.chambres.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(key),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/hebergement/chambres']);
        },
        error: (err) => {
          const key = err?.error?.error || 'hebergement.chambres.messages.saveError';
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate(key),
          });
        },
      });
  }

  cancel(): void {
    this.router.navigate(['/hebergement/chambres']);
  }

  private buildForm(): FormGroup {
    return this.fb.group({
      numeroChambre: ['', [Validators.required, Validators.maxLength(20)]],
      typeId: [null as number | null, [Validators.required]],
      etage: [null as number | null],
      statut: [StatutChambre.DISPONIBLE, [Validators.required]],
      nbLits: [1, [Validators.required, Validators.min(1)]],
      nbPersonnesMax: [1, [Validators.required, Validators.min(1)]],
      equipements: ['', [Validators.maxLength(2000)]],
      description: ['', [Validators.maxLength(1000)]],
    });
  }

  /**
   * Après chargement des types, on regarde si on est en mode édition pour
   * éventuellement charger l'entité. Sans types chargés, le select est vide.
   */
  private afterDepsLoaded(): void {
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

  private loadExisting(id: number): void {
    this.chambresService
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (c) => {
          this.hydrate(c);
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrate(c: Chambre): void {
    this.form.patchValue({
      numeroChambre: c.numeroChambre ?? '',
      typeId: c.typeId ?? null,
      etage: c.etage ?? null,
      statut: c.statut ?? StatutChambre.DISPONIBLE,
      nbLits: c.nbLits ?? 1,
      nbPersonnesMax: c.nbPersonnesMax ?? 1,
      equipements: c.equipements ?? '',
      description: c.description ?? '',
    });
  }
}
