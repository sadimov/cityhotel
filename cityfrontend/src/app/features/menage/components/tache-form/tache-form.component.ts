import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Chambre } from '../../../hebergement/models/chambre.model';
import { ChambresService } from '../../../hebergement/services/chambres.service';
import { Personnel } from '../../models/personnel.model';
import {
  PRIORITES_TACHE,
  PrioriteTache,
  Tache,
  TYPES_NETTOYAGE,
  TypeNettoyage,
} from '../../models/tache.model';
import { PersonnelsService } from '../../services/personnels.service';
import { TachesService } from '../../services/taches.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'une tâche de ménage.
 *
 * Validators :
 *  - `chambreId` : required — select des chambres actives chargées via
 *    `ChambresService.findActives()`. Affiché « numéro - type » (consigne
 *    user 2026-05-17). Un input numérique libre laissait passer des IDs
 *    inexistants et provoquait une 404 côté backend.
 *  - `datePlanifiee` : required
 *  - `priorite` : required dans [1..3]
 *  - `typeNettoyage` : required dans l'enum
 *  - `personnelId` : optionnel (assignation peut être déférée)
 *  - `noteQualite` : optionnel mais bornée [1..5] côté backend
 */
@Component({
  selector: 'app-tache-form',
  templateUrl: './tache-form.component.html',
  styleUrls: ['./tache-form.component.scss'],
  standalone: false,
})
export class TacheFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  editingId: number | null = null;
  personnels: Personnel[] = [];
  chambres: Chambre[] = [];
  readonly typesNettoyage: ReadonlyArray<TypeNettoyage> = TYPES_NETTOYAGE;
  readonly priorites: ReadonlyArray<PrioriteTache> = PRIORITES_TACHE;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly tachesService: TachesService,
    private readonly personnelsService: PersonnelsService,
    private readonly chambresService: ChambresService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();
    this.loadPersonnels();
    this.loadChambres();

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam && idParam !== 'new') {
      const id = Number(idParam);
      if (!Number.isFinite(id)) {
        this.state = 'error';
        return;
      }
      this.editingId = id;
      this.loadExisting(id);
    }
  }

  /**
   * Libellé d'option chambre : « numéro - type » (consigne user 2026-05-17).
   * Utilise `nomTypeChambre` enrichi côté backend, fallback sur le numéro
   * seul si le type n'est pas résolu.
   */
  chambreLabel(c: Chambre): string {
    const num = c.numeroChambre ?? '';
    const type = c.nomTypeChambre ?? '';
    return type ? `${num} - ${type}` : num;
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

    const payload: Tache = {
      chambreId: Number(raw.chambreId),
      personnelId: raw.personnelId != null && raw.personnelId !== '' ? Number(raw.personnelId) : undefined,
      typeNettoyage: (raw.typeNettoyage || undefined) as TypeNettoyage | undefined,
      priorite: raw.priorite != null && raw.priorite !== '' ? (Number(raw.priorite) as PrioriteTache) : undefined,
      datePlanifiee: String(raw.datePlanifiee),
      heureDebutPrevue: raw.heureDebutPrevue || undefined,
      heureFinPrevue: raw.heureFinPrevue || undefined,
      commentaires: raw.commentaires || undefined,
      problemesDetectes: raw.problemesDetectes || undefined,
      materielUtilise: this.serializeMateriel(raw.materielUtilise),
    };

    const obs$ = this.editingId
      ? this.tachesService.update(this.editingId, payload)
      : this.tachesService.create(payload);

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
            ? 'menage.tache.messages.updateSuccess'
            : 'menage.tache.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/menage/taches']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('menage.tache.messages.saveError'),
          });
        },
      });
  }

  cancelEdit(): void {
    this.router.navigate(['/menage/taches']);
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private buildForm(): FormGroup {
    return this.fb.group({
      chambreId: [null, [Validators.required, Validators.min(1)]],
      personnelId: [null],
      typeNettoyage: ['QUOTIDIEN' as TypeNettoyage, [Validators.required]],
      priorite: [1 as PrioriteTache, [Validators.required, Validators.min(1), Validators.max(3)]],
      datePlanifiee: ['', [Validators.required]],
      heureDebutPrevue: [''],
      heureFinPrevue: [''],
      commentaires: ['', [Validators.maxLength(1000)]],
      problemesDetectes: ['', [Validators.maxLength(1000)]],
      materielUtilise: [''],
    });
  }

  private loadPersonnels(): void {
    this.personnelsService
      .findActifs()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of([] as Personnel[])),
      )
      .subscribe((list) => {
        this.personnels = list;
        if (this.state === 'loading' && !this.editingId) {
          this.state = 'ready';
        }
      });
  }

  private loadChambres(): void {
    this.chambresService
      .findActives()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of([] as Chambre[])),
      )
      .subscribe((list) => {
        // Tri stable : numéro de chambre ASC (le numéro est une string
        // type "101", "102", "201" → ordre lexicographique acceptable).
        this.chambres = [...list].sort((a, b) =>
          (a.numeroChambre ?? '').localeCompare(b.numeroChambre ?? ''),
        );
      });
  }

  private loadExisting(id: number): void {
    this.tachesService
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (t) => {
          this.hydrateForm(t);
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrateForm(t: Tache): void {
    this.form.patchValue({
      chambreId: t.chambreId ?? null,
      personnelId: t.personnelId ?? null,
      typeNettoyage: t.typeNettoyage ?? 'QUOTIDIEN',
      priorite: t.priorite ?? 1,
      datePlanifiee: t.datePlanifiee ?? '',
      heureDebutPrevue: t.heureDebutPrevue ?? '',
      heureFinPrevue: t.heureFinPrevue ?? '',
      commentaires: t.commentaires ?? '',
      problemesDetectes: t.problemesDetectes ?? '',
      materielUtilise: this.deserializeMateriel(t.materielUtilise),
    });
  }

  /** Sérialise « aspirateur, detergent » → `'["aspirateur","detergent"]'`. */
  private serializeMateriel(input: unknown): string | undefined {
    if (typeof input !== 'string' || !input.trim()) {
      return undefined;
    }
    const items = input
      .split(',')
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
    return items.length > 0 ? JSON.stringify(items) : undefined;
  }

  private deserializeMateriel(json: string | undefined): string {
    if (!json) {
      return '';
    }
    try {
      const parsed: unknown = JSON.parse(json);
      if (Array.isArray(parsed)) {
        return parsed.filter((x): x is string => typeof x === 'string').join(', ');
      }
    } catch {
      // valeur brute
    }
    return json;
  }
}
