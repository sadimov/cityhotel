import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
} from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  ModePaiement,
  MODES_PAIEMENT_AVEC_REFERENCE,
} from '../../../../finance/models/paiement.model';
import { EncaissementCommandeRequest } from '../../../models/commande.model';

/**
 * Modal d'encaissement comptant.
 *
 * Modes affichés (cf. `modes_paiements.txt` + arbitrage Tour 24) :
 *  - ESPECES, BANKILY, CARTE_BANCAIRE
 *  - les autres modes (CHEQUE, MASRIVI, SEDAD, ...) restent disponibles
 *    via un select étendu, masqué par défaut.
 *
 * Validation :
 *  - `montantPaye` > 0 (et ≥ `total` pour interdire les paiements partiels en V1).
 *  - `referencePaiement` requis si `modePaiement` ∈
 *    `MODES_PAIEMENT_AVEC_REFERENCE` (chèque, carte, mobile money, virement).
 *
 * Le composant n'effectue pas l'appel HTTP — il émet
 * `submitPayment` que `PosComponent` relaie au store.
 */
@Component({
  selector: 'app-pos-payment-modal',
  templateUrl: './payment-modal.component.html',
  styleUrls: ['./payment-modal.component.scss'],
  standalone: false,
})
export class PaymentModalComponent implements OnInit, OnChanges, OnDestroy {
  @Input() open = false;
  @Input() total = 0;
  @Input() submitting = false;
  /** Optionnellement préserver le formulaire entre ouvertures. */
  @Input() resetOnOpen = true;

  @Output() readonly close = new EventEmitter<void>();
  @Output() readonly submitPayment = new EventEmitter<EncaissementCommandeRequest>();

  /** Modes mis en avant en boutons rapides. */
  readonly quickModes: ReadonlyArray<ModePaiement> = [
    ModePaiement.ESPECES,
    ModePaiement.BANKILY,
    ModePaiement.CARTE_BANCAIRE,
  ];

  /** Tous les autres modes (sélecteur étendu). */
  readonly otherModes: ReadonlyArray<ModePaiement> = [
    ModePaiement.CHEQUE,
    ModePaiement.MASRIVI,
    ModePaiement.SEDAD,
    ModePaiement.CLICK,
    ModePaiement.AMANETY,
    ModePaiement.BFI_CASH,
    ModePaiement.MOOV_MONEY,
    ModePaiement.GAZAPAY,
    ModePaiement.VIREMENT,
  ];

  form!: FormGroup;
  modePaiementWithRefRequired = false;

  private readonly destroy$ = new Subject<void>();

  constructor(private readonly fb: FormBuilder) {}

  ngOnInit(): void {
    this.buildForm();
    this.form
      .get('modePaiement')!
      .valueChanges.pipe(takeUntil(this.destroy$))
      .subscribe((mode: ModePaiement) => this.applyReferenceValidation(mode));
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open) {
      if (!this.form) {
        this.buildForm();
      }
      if (this.resetOnOpen) {
        this.form.reset({
          modePaiement: ModePaiement.ESPECES,
          montantPaye: this.total,
          referencePaiement: '',
          commentaires: '',
        });
        this.applyReferenceValidation(ModePaiement.ESPECES);
      } else {
        this.form.patchValue({ montantPaye: this.total });
      }
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  selectQuickMode(mode: ModePaiement): void {
    this.form.patchValue({ modePaiement: mode });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const payload: EncaissementCommandeRequest = {
      modePaiement: raw.modePaiement,
      montantPaye: Number(raw.montantPaye),
      referencePaiement: raw.referencePaiement?.trim() || undefined,
      commentaires: raw.commentaires?.trim() || undefined,
    };
    this.submitPayment.emit(payload);
  }

  onCancel(): void {
    this.close.emit();
  }

  // ────────────────────────────────────────────────────────────────────────
  // Privé
  // ────────────────────────────────────────────────────────────────────────

  private buildForm(): void {
    this.form = this.fb.group({
      modePaiement: [ModePaiement.ESPECES, Validators.required],
      montantPaye: [
        this.total,
        [Validators.required, Validators.min(0.01)],
      ],
      referencePaiement: [''],
      commentaires: [''],
    });
  }

  private applyReferenceValidation(mode: ModePaiement): void {
    const ref = this.form.get('referencePaiement');
    if (!ref) {
      return;
    }
    const requireRef = MODES_PAIEMENT_AVEC_REFERENCE.includes(mode);
    this.modePaiementWithRefRequired = requireRef;
    ref.setValidators(requireRef ? [Validators.required] : []);
    ref.updateValueAndValidity({ emitEvent: false });
  }
}
