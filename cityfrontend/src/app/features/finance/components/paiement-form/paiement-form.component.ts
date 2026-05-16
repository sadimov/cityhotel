import { Component, OnDestroy, OnInit } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { FactureDto } from '../../models/facture.model';
import {
  ModePaiement,
  MODES_PAIEMENT,
  MODES_PAIEMENT_AVEC_REFERENCE,
  PaiementCreateDto,
} from '../../models/paiement.model';
import { FacturesService } from '../../services/factures.service';
import { PaiementsService } from '../../services/paiements.service';

type FormState = 'loading' | 'ready' | 'submitting' | 'error';

/**
 * Formulaire de création d'un paiement (encaissement) pour une facture
 * existante.
 *
 * Le composant attend `:id` dans l'URL = factureId. Il charge la facture,
 * affiche le `montantRestant` et propose un input `montant` validé `<=
 * montantRestant`.
 *
 * Modes de paiement supportés : enum `ModePaiement` complet aligné back
 * (cf. `entity/finance/ModePaiement.java`).
 *
 * Le numéro de référence externe (`referencePaiement`) est requis pour les
 * modes non-comptant : chèque, carte, wallets mobiles, virement.
 */
@Component({
  selector: 'app-paiement-form',
  templateUrl: './paiement-form.component.html',
  styleUrls: ['./paiement-form.component.scss'],
  standalone: false,
})
export class PaiementFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  state: FormState = 'loading';
  facture: FactureDto | null = null;
  factureId: number | null = null;

  readonly modes = MODES_PAIEMENT;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly facturesService: FacturesService,
    private readonly paiementsService: PaiementsService,
    private readonly i18n: TranslationService,
  ) {}

  /**
   * Montant pré-rempli depuis le query param `?montant=XXX` (paiement
   * individuel/partiel ciblé depuis facture-detail). `null` = aucun, on
   * pré-remplira avec `montantRestant` total.
   */
  private prefilledAmount: number | null = null;

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = Number(idParam);
    if (!idParam || !Number.isFinite(id)) {
      this.state = 'error';
      return;
    }
    this.factureId = id;

    const montantQp = this.route.snapshot.queryParamMap.get('montant');
    if (montantQp != null) {
      const v = Number(montantQp);
      if (Number.isFinite(v) && v > 0) {
        this.prefilledAmount = v;
      }
    }

    this.form = this.buildForm();
    this.loadFacture(id);
    this.subscribeModeChange();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  submit(): void {
    if (!this.facture || !this.factureId) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.state = 'submitting';
    const raw = this.form.getRawValue() as {
      modePaiement: ModePaiement;
      montant: number;
      datePaiement: string;
      referencePaiement: string;
      commentaires: string;
    };

    const payload: PaiementCreateDto = {
      compteId: this.facture.compteId ?? undefined,
      factureId: this.factureId,
      montantTotal: Number(raw.montant),
      devise: this.facture.devise || undefined,
      modePaiement: raw.modePaiement,
      referencePaiement: raw.referencePaiement?.trim() || undefined,
      datePaiement: raw.datePaiement || undefined,
      commentaires: raw.commentaires?.trim() || undefined,
    };

    this.paiementsService
      .create(payload)
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
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate('finance.paiement.messages.createSuccess'),
            timer: 1500,
            showConfirmButton: false,
          });
          this.router.navigate(['/finance/factures', this.factureId]);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('finance.paiement.messages.saveError'),
          });
        },
      });
  }

  cancel(): void {
    if (this.factureId) {
      this.router.navigate(['/finance/factures', this.factureId]);
    } else {
      this.router.navigate(['/finance/factures']);
    }
  }

  /** Vérifie si la `referencePaiement` est obligatoire pour le mode courant. */
  isReferenceRequired(): boolean {
    const mode = this.form?.get('modePaiement')?.value as ModePaiement | null;
    return mode != null && MODES_PAIEMENT_AVEC_REFERENCE.includes(mode);
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private buildForm(): FormGroup {
    const today = new Date().toISOString().slice(0, 10);
    return this.fb.group({
      modePaiement: [ModePaiement.ESPECES, [Validators.required]],
      montant: [
        0,
        [Validators.required, Validators.min(0.01), this.maxMontantValidator()],
      ],
      datePaiement: [today, [Validators.required]],
      referencePaiement: ['', [Validators.maxLength(100)]],
      commentaires: [''],
    });
  }

  /**
   * Validator dynamique : `montant` doit être <= `montantRestant` de la
   * facture courante.
   */
  private maxMontantValidator() {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!this.facture) return null;
      const v = Number(control.value);
      if (!Number.isFinite(v)) return null;
      if (v > Number(this.facture.montantRestant ?? 0)) {
        return { maxMontant: { max: this.facture.montantRestant } };
      }
      return null;
    };
  }

  private subscribeModeChange(): void {
    this.form
      .get('modePaiement')!
      .valueChanges.pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        const refCtrl = this.form.get('referencePaiement');
        if (!refCtrl) return;
        if (this.isReferenceRequired()) {
          refCtrl.setValidators([Validators.required, Validators.maxLength(100)]);
        } else {
          refCtrl.setValidators([Validators.maxLength(100)]);
        }
        refCtrl.updateValueAndValidity({ emitEvent: false });
      });
  }

  private loadFacture(id: number): void {
    this.facturesService
      .findById(id)
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of(null)),
      )
      .subscribe((f) => {
        if (!f) {
          this.state = 'error';
          return;
        }
        this.facture = f;
        // Pré-remplir avec le montant transmis (paiement ciblé depuis la
        // sélection de lignes), sinon avec le restant dû total. Plafonné au
        // restant pour éviter une saisie incohérente.
        const restant = Number(f.montantRestant ?? 0);
        const target =
          this.prefilledAmount != null
            ? Math.min(this.prefilledAmount, restant)
            : restant;
        this.form.patchValue({ montant: target });
        this.form.get('montant')?.updateValueAndValidity();
        this.state = 'ready';
      });
  }
}
