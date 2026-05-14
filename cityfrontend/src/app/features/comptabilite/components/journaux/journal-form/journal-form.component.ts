import { Component, OnDestroy, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../../services/translation.service';
import {
  JOURNAL_CODE_PATTERN,
  TYPES_JOURNAL,
  TypeJournal,
} from '../../../models/journal.model';
import { JournauxService } from '../../../services/journaux.service';

/**
 * Formulaire de création / édition d'un journal comptable (B7).
 *
 * Mode édition : le code est non modifiable côté back (sert de discriminant
 * de numérotation). En édition, on n'envoie que `libelle` + `type`.
 */
@Component({
  selector: 'app-journal-form',
  templateUrl: './journal-form.component.html',
  standalone: false,
})
export class JournalFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  submitting = false;
  editing = false;
  journalId: number | null = null;

  readonly typesJournal: ReadonlyArray<TypeJournal> = TYPES_JOURNAL;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly service: JournauxService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    this.editing = !!idParam;
    this.journalId = idParam ? Number(idParam) : null;

    this.form = this.fb.group({
      code: [
        { value: '', disabled: this.editing },
        [
          Validators.required,
          Validators.maxLength(5),
          Validators.pattern(JOURNAL_CODE_PATTERN),
        ],
      ],
      libelle: ['', [Validators.required, Validators.maxLength(100)]],
      type: [TypeJournal.VENTE, Validators.required],
    });

    if (this.editing && this.journalId != null) {
      this.service
        .findById(this.journalId)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: (j) =>
            this.form.patchValue({
              code: j.code,
              libelle: j.libelle,
              type: j.type,
            }),
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('comptabilite.journal.messages.loadError'),
            });
            this.router.navigate(['/comptabilite/journaux']);
          },
        });
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting = true;

    const raw = this.form.getRawValue();
    const obs$ = this.editing && this.journalId != null
      ? this.service.update(this.journalId, {
          libelle: raw.libelle,
          type: raw.type,
        })
      : this.service.create({
          code: raw.code,
          libelle: raw.libelle,
          type: raw.type,
        });

    obs$
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.submitting = false)),
      )
      .subscribe({
        next: () => {
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(
              this.editing
                ? 'comptabilite.journal.messages.updateSuccess'
                : 'comptabilite.journal.messages.createSuccess',
            ),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/comptabilite/journaux']);
        },
        error: (err) => {
          const key = err?.error?.message
            || (this.editing
              ? 'error.journal.updateFailed'
              : 'error.journal.createFailed');
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate(key),
          });
        },
      });
  }

  cancel(): void {
    this.router.navigate(['/comptabilite/journaux']);
  }

  typeKey(t: TypeJournal): string {
    return `comptabilite.journal.type.${t}`;
  }
}
