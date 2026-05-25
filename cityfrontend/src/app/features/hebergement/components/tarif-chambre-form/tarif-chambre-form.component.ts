import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { TarifChambre, TarifChambreCreate } from '../../models/tarif-chambre.model';
import { TypeChambre } from '../../models/type-chambre.model';
import { TarifChambreService } from '../../services/tarif-chambre.service';
import { TypesChambreService } from '../../services/types-chambre.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'un tarif saisonnier.
 *
 * ⚠️ Le champ `prixWeekend` du backend N'EST PAS exposé dans le formulaire
 * (consigne user 2026-05-25 : majoration weekend désactivée). La clé n'est
 * volontairement pas envoyée dans le payload.
 */
@Component({
  selector: 'app-tarif-chambre-form',
  templateUrl: './tarif-chambre-form.component.html',
  standalone: false,
})
export class TarifChambreFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  editingId: number | null = null;
  types: TypeChambre[] = [];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly tarifsService: TarifChambreService,
    private readonly typesService: TypesChambreService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();

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
    // ⚠️ Pas de prixWeekend dans le payload (consigne user 2026-05-25).
    // ⚠️ Pas de hotelId — backend l'extrait du JWT.
    const payload: TarifChambreCreate = {
      typeId: Number(raw.typeId),
      nomTarif: raw.nomTarif,
      dateDebut: raw.dateDebut,
      dateFin: raw.dateFin ? raw.dateFin : null,
      prixNuit: Number(raw.prixNuit),
      priorite: raw.priorite != null && raw.priorite !== '' ? Number(raw.priorite) : 0,
      actif: raw.actif !== false,
    };
    const obs$ = this.editingId
      ? this.tarifsService.update(this.editingId, payload)
      : this.tarifsService.create(payload);

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
            ? 'hebergement.tarifsChambre.messages.updateSuccess'
            : 'hebergement.tarifsChambre.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(key),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/hebergement/tarifs-chambre']);
        },
        error: (err) => {
          const key = err?.error?.error || 'hebergement.tarifsChambre.messages.saveError';
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate(key),
          });
        },
      });
  }

  cancel(): void {
    this.router.navigate(['/hebergement/tarifs-chambre']);
  }

  private buildForm(): FormGroup {
    return this.fb.group({
      typeId: [null as number | null, [Validators.required]],
      nomTarif: ['', [Validators.required, Validators.maxLength(100)]],
      dateDebut: ['', [Validators.required]],
      dateFin: [''],
      prixNuit: [0, [Validators.required, Validators.min(0)]],
      priorite: [0, [Validators.min(0)]],
      actif: [true],
    });
  }

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
      // Pré-remplir le type depuis ?typeId=... si présent (cf. tarifs-chambre-list).
      const typeIdQuery = this.route.snapshot.queryParamMap.get('typeId');
      if (typeIdQuery) {
        const n = Number(typeIdQuery);
        if (Number.isFinite(n)) {
          this.form.patchValue({ typeId: n });
        }
      }
      this.state = 'ready';
    }
  }

  private loadExisting(id: number): void {
    this.tarifsService
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (t) => {
          this.hydrate(t);
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrate(t: TarifChambre): void {
    this.form.patchValue({
      typeId: t.typeId ?? null,
      nomTarif: t.nomTarif ?? '',
      dateDebut: t.dateDebut ?? '',
      dateFin: t.dateFin ?? '',
      prixNuit: t.prixNuit ?? 0,
      priorite: t.priorite ?? 0,
      actif: t.actif !== false,
    });
  }
}
