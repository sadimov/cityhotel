import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { CategorieEspace, TypeChambre } from '../../models/type-chambre.model';
import { TypesChambreService } from '../../services/types-chambre.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/** Formulaire création / édition d'un type de chambre. */
@Component({
  selector: 'app-types-chambre-form',
  templateUrl: './types-chambre-form.component.html',
  standalone: false,
})
export class TypesChambreFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  editingId: number | null = null;

  readonly categories: CategorieEspace[] = ['CHAMBRE', 'SALLE'];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly typesService: TypesChambreService,
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
    // ⚠️ Pas de hotelId — backend l'extrait du JWT.
    const payload: TypeChambre = {
      typeCode: raw.typeCode,
      typeNom: raw.typeNom,
      description: raw.description || undefined,
      categorie: raw.categorie,
      superficie: raw.superficie != null && raw.superficie !== '' ? Number(raw.superficie) : undefined,
      nbLitsMax: Number(raw.nbLitsMax),
      nbPersonnesMax: Number(raw.nbPersonnesMax),
      prixBase: Number(raw.prixBase),
    };
    const obs$ = this.editingId
      ? this.typesService.update(this.editingId, payload)
      : this.typesService.create(payload);

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
            ? 'hebergement.typesChambre.messages.updateSuccess'
            : 'hebergement.typesChambre.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(key),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/hebergement/types-chambre']);
        },
        error: (err) => {
          const key = err?.error?.error || 'hebergement.typesChambre.messages.saveError';
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate(key),
          });
        },
      });
  }

  cancel(): void {
    this.router.navigate(['/hebergement/types-chambre']);
  }

  private buildForm(): FormGroup {
    return this.fb.group({
      typeCode: ['', [Validators.required, Validators.maxLength(20)]],
      typeNom: ['', [Validators.required, Validators.maxLength(100)]],
      description: ['', [Validators.maxLength(1000)]],
      categorie: ['CHAMBRE' as CategorieEspace, [Validators.required]],
      superficie: [null as number | null, [Validators.min(0)]],
      nbLitsMax: [1, [Validators.required, Validators.min(1)]],
      nbPersonnesMax: [1, [Validators.required, Validators.min(1)]],
      prixBase: [0, [Validators.required, Validators.min(0)]],
    });
  }

  private loadExisting(id: number): void {
    this.typesService
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

  private hydrate(t: TypeChambre): void {
    this.form.patchValue({
      typeCode: t.typeCode ?? '',
      typeNom: t.typeNom ?? '',
      description: t.description ?? '',
      categorie: t.categorie ?? 'CHAMBRE',
      superficie: t.superficie ?? null,
      nbLitsMax: t.nbLitsMax ?? 1,
      nbPersonnesMax: t.nbPersonnesMax ?? 1,
      prixBase: t.prixBase ?? 0,
    });
  }
}
