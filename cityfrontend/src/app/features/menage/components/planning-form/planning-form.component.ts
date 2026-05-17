import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Personnel } from '../../models/personnel.model';
import { Planning, PlanningCreate } from '../../models/planning.model';
import { PersonnelsService } from '../../services/personnels.service';
import { PlanningService } from '../../services/planning.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire création / édition d'un créneau de planning de personnel ménage.
 *
 * Source de vérité backend : `PlanningCreateDto` (Tour 27) — champs requis :
 *   personnelId, dateTravail, heureDebut, heureFin.
 * Optionnels : disponible (default true), commentaires.
 *
 * Composant créé au Tour 56 (différé Tour 28-29 originellement — cf.
 * `menage-routing.module.ts`). Auparavant, le bouton « Nouveau planning »
 * de la liste naviguait vers une route inexistante → fallback dashboard.
 */
@Component({
  selector: 'app-planning-form',
  templateUrl: './planning-form.component.html',
  standalone: false,
})
export class PlanningFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  editingId: number | null = null;
  personnels: Personnel[] = [];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly planningService: PlanningService,
    private readonly personnelsService: PersonnelsService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.form = this.buildForm();
    this.loadPersonnels();

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

    const payload: PlanningCreate = {
      personnelId: Number(raw.personnelId),
      dateTravail: String(raw.dateTravail),
      heureDebut: String(raw.heureDebut),
      heureFin: String(raw.heureFin),
      disponible: raw.disponible !== false,
      commentaires: raw.commentaires || undefined,
    };

    const obs$ = this.editingId
      ? this.planningService.update(this.editingId, payload)
      : this.planningService.create(payload);

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
            ? 'menage.planning.messages.updateSuccess'
            : 'menage.planning.messages.createSuccess';
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(successKey),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/menage/planning']);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('menage.planning.messages.saveError'),
          });
        },
      });
  }

  cancel(): void {
    this.router.navigate(['/menage/planning']);
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private buildForm(): FormGroup {
    return this.fb.group({
      personnelId: [null, [Validators.required]],
      dateTravail: ['', [Validators.required]],
      heureDebut: ['08:00', [Validators.required]],
      heureFin: ['17:00', [Validators.required]],
      disponible: [true],
      commentaires: ['', [Validators.maxLength(1000)]],
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

  private loadExisting(id: number): void {
    this.planningService
      .findById(id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (p) => {
          this.hydrateForm(p);
          this.state = 'ready';
        },
        error: () => {
          this.state = 'error';
        },
      });
  }

  private hydrateForm(p: Planning): void {
    this.form.patchValue({
      personnelId: p.personnelId ?? null,
      dateTravail: p.dateTravail ?? '',
      // Backend retourne possiblement `HH:mm:ss` — on garde tel quel,
      // les `<input type="time">` tolèrent les 2 formats.
      heureDebut: p.heureDebut ?? '08:00',
      heureFin: p.heureFin ?? '17:00',
      disponible: p.disponible !== false,
      commentaires: p.commentaires ?? '',
    });
  }
}
