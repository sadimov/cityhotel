import { Component, OnDestroy, OnInit } from '@angular/core';
import {
  FormArray,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../../services/translation.service';
import {
  LigneEcritureCreateDto,
  SENS_LIGNE_OPTIONS,
  SensLigne,
} from '../../../models/ecriture.model';
import { JournalComptableDto } from '../../../models/journal.model';
import { EcrituresService } from '../../../services/ecritures.service';
import { JournauxService } from '../../../services/journaux.service';

/** Tolérance MRU pour l'équilibre Σ D / Σ C (alignée avec le back). */
const EQUILIBRE_TOLERANCE = 0.01;

/**
 * Formulaire de création d'une écriture comptable en partie double (B7).
 *
 * Garde-fous client-side (alignement avec validations back) :
 *  - au moins 2 lignes
 *  - Σ debit == Σ credit avec tolérance 0.01 MRU
 *  - compteCode + sens + montant > 0 obligatoires sur chaque ligne
 *  - journalCode + dateComptable + libelle obligatoires
 */
@Component({
  selector: 'app-ecriture-form',
  templateUrl: './ecriture-form.component.html',
  standalone: false,
})
export class EcritureFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  submitting = false;
  journauxActifs: JournalComptableDto[] = [];
  loadingJournaux = true;

  readonly sensOptions: ReadonlyArray<SensLigne> = SENS_LIGNE_OPTIONS;
  readonly SensLigne = SensLigne;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly service: EcrituresService,
    private readonly journauxService: JournauxService,
    private readonly router: Router,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    const today = new Date().toISOString().slice(0, 10);
    this.form = this.fb.group({
      dateComptable: [today, Validators.required],
      datePiece: [today],
      journalCode: ['', Validators.required],
      libelle: ['', [Validators.required, Validators.maxLength(500)]],
      reference: ['', Validators.maxLength(50)],
      lignes: this.fb.array([this.buildLigne(), this.buildLigne()]),
    });

    // Charge les journaux actifs (taille suffisante).
    this.journauxService
      .page({ page: 0, size: 100, sort: 'code,asc' })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (p) => {
          this.journauxActifs = p.content.filter((j) => j.actif);
          this.loadingJournaux = false;
        },
        error: () => {
          this.loadingJournaux = false;
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('comptabilite.journal.messages.loadError'),
          });
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private buildLigne(): FormGroup {
    return this.fb.group({
      compteCode: ['', [Validators.required, Validators.maxLength(10)]],
      libelle: ['', Validators.maxLength(500)],
      sens: [SensLigne.DEBIT, Validators.required],
      montant: [
        0,
        [Validators.required, Validators.min(0.01)],
      ],
      compteAuxiliaireRef: ['', Validators.maxLength(50)],
    });
  }

  get lignes(): FormArray {
    return this.form.get('lignes') as FormArray;
  }

  addLigne(): void {
    this.lignes.push(this.buildLigne());
  }

  removeLigne(index: number): void {
    if (this.lignes.length <= 2) return;
    this.lignes.removeAt(index);
  }

  get totalDebit(): number {
    return this.lignes.controls.reduce((acc, ctrl) => {
      const sens = ctrl.get('sens')?.value;
      const montant = Number(ctrl.get('montant')?.value || 0);
      return acc + (sens === SensLigne.DEBIT ? montant : 0);
    }, 0);
  }

  get totalCredit(): number {
    return this.lignes.controls.reduce((acc, ctrl) => {
      const sens = ctrl.get('sens')?.value;
      const montant = Number(ctrl.get('montant')?.value || 0);
      return acc + (sens === SensLigne.CREDIT ? montant : 0);
    }, 0);
  }

  get isEquilibree(): boolean {
    return Math.abs(this.totalDebit - this.totalCredit) <= EQUILIBRE_TOLERANCE
      && this.totalDebit > 0;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      Swal.fire({
        icon: 'warning',
        title: this.i18n.translate('error.ecriture.formInvalid'),
      });
      return;
    }
    if (this.lignes.length < 2) {
      Swal.fire({
        icon: 'warning',
        title: this.i18n.translate('error.ecriture.minLines'),
      });
      return;
    }
    if (!this.isEquilibree) {
      Swal.fire({
        icon: 'warning',
        title: this.i18n.translate('error.ecriture.unbalanced'),
        text: this.i18n.translate('error.ecriture.unbalancedDetail'),
      });
      return;
    }

    this.submitting = true;
    const raw = this.form.getRawValue();
    const lignes: LigneEcritureCreateDto[] = raw.lignes.map(
      (l: LigneEcritureCreateDto, idx: number) => ({
        ordre: idx + 1,
        compteCode: (l.compteCode || '').trim(),
        libelle: l.libelle || undefined,
        sens: l.sens,
        montant: Number(l.montant),
        compteAuxiliaireRef: l.compteAuxiliaireRef || undefined,
      }),
    );

    this.service
      .create({
        dateComptable: raw.dateComptable,
        datePiece: raw.datePiece || undefined,
        journalCode: raw.journalCode,
        libelle: raw.libelle,
        reference: raw.reference || undefined,
        lignes,
      })
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.submitting = false)),
      )
      .subscribe({
        next: (created) => {
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('comptabilite.ecriture.messages.createSuccess'),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/comptabilite/ecritures', created.id]);
        },
        error: (err) => {
          const key = err?.error?.message || 'error.ecriture.createFailed';
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate(key),
          });
        },
      });
  }

  cancel(): void {
    this.router.navigate(['/comptabilite/ecritures']);
  }

  sensKey(s: SensLigne): string {
    return `comptabilite.ecriture.sens.${s}`;
  }
}
