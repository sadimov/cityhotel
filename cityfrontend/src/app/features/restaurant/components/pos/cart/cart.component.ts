import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject } from 'rxjs';

import {
  ModePaiement,
  MODES_PAIEMENT_AVEC_REFERENCE,
} from '../../../../finance/models/paiement.model';
import {
  EncaissementCommandeRequest,
  LigneCommande,
} from '../../../models/commande.model';
import { PosStore } from '../state/pos.store';

/**
 * Panier POS (Section C horizontale).
 *
 * Structure :
 *  - Zone gauche : lignes du panier (scroll vertical interne).
 *  - Zone droite : récap totaux + modes de paiement inline + boutons d'action.
 *
 * Le header client/réservation (Section A) est dans `<app-pos-client-header>`,
 * pas ici. L'écran « succès post-checkout » plein-bloc historique est remplacé
 * par un toast Swal2 + chip persistante orchestrés par `PosComponent`.
 */
@Component({
  selector: 'app-pos-cart',
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.scss'],
  standalone: false,
})
export class CartComponent implements OnInit, OnDestroy {
  @Output() readonly checkoutComptant =
    new EventEmitter<EncaissementCommandeRequest>();
  @Output() readonly reportChambre = new EventEmitter<void>();
  @Output() readonly clearCart = new EventEmitter<void>();
  /** Tour 55 — pousser les lignes SERVICE sur la facture chambre. */
  @Output() readonly pushServices = new EventEmitter<void>();

  readonly cart$ = this.store.cart$;
  readonly total$ = this.store.total$;
  readonly articleTotal$ = this.store.articleTotal$;
  readonly serviceTotal$ = this.store.serviceTotal$;
  readonly itemsCount$ = this.store.itemsCount$;
  readonly isCartEmpty$ = this.store.isCartEmpty$;
  readonly hasArticlesInCart$ = this.store.hasArticlesInCart$;
  readonly hasServicesInCart$ = this.store.hasServicesInCart$;
  readonly canCheckoutComptant$ = this.store.canCheckoutComptant$;
  readonly canReportChambre$ = this.store.canReportChambre$;
  readonly canPushServices$ = this.store.canPushServices$;
  readonly submitting$ = this.store.submitting$;
  readonly selectedClient$ = this.store.selectedClient$;
  readonly selectedReservation$ = this.store.selectedReservation$;

  /** Mode de paiement actuellement sélectionné (radio toggle visuel). */
  selectedMode: ModePaiement = ModePaiement.ESPECES;

  /** Modes mis en avant en boutons rapides. */
  readonly quickModes: ReadonlyArray<ModePaiement> = [
    ModePaiement.ESPECES,
    ModePaiement.BANKILY,
    ModePaiement.CARTE_BANCAIRE,
  ];

  /** Autres modes (select compact). */
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
  modeRequiresReference = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly store: PosStore,
    private readonly fb: FormBuilder,
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      referencePaiement: [''],
      commentaires: [''],
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ─────────────────────────────────────────────────────────────────────
  // Lignes panier
  // ─────────────────────────────────────────────────────────────────────

  onChangeQuantity(cartLineId: string, value: string): void {
    const quantite = Number(value);
    if (Number.isNaN(quantite)) {
      return;
    }
    this.store.updateLineQuantity({ cartLineId, quantite });
  }

  onIncrement(line: LigneCommande): void {
    this.store.updateLineQuantity({
      cartLineId: line.cartLineId,
      quantite: line.quantite + 1,
    });
  }

  onDecrement(line: LigneCommande): void {
    this.store.updateLineQuantity({
      cartLineId: line.cartLineId,
      quantite: line.quantite - 1,
    });
  }

  onRemove(cartLineId: string): void {
    this.store.removeLine(cartLineId);
  }

  // ─────────────────────────────────────────────────────────────────────
  // Paiement inline + actions principales
  // ─────────────────────────────────────────────────────────────────────

  selectMode(mode: ModePaiement): void {
    this.selectedMode = mode;
    this.modeRequiresReference =
      MODES_PAIEMENT_AVEC_REFERENCE.includes(mode);
    const refCtrl = this.form.get('referencePaiement');
    if (refCtrl) {
      refCtrl.setValidators(
        this.modeRequiresReference ? [Validators.required] : [],
      );
      refCtrl.updateValueAndValidity({ emitEvent: false });
    }
  }

  onChangeOtherMode(value: string): void {
    if (!value) {
      return;
    }
    this.selectMode(value as ModePaiement);
  }

  /**
   * Tour 55 — `articleTotal` (et non `total`) car les lignes SERVICE
   * sont facturées à part.
   */
  onCheckoutComptant(articleTotal: number): void {
    if (this.modeRequiresReference && this.form.get('referencePaiement')?.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const payload: EncaissementCommandeRequest = {
      modePaiement: this.selectedMode,
      montant: articleTotal,
      referencePaiement: raw.referencePaiement?.trim() || undefined,
      commentaires: raw.commentaires?.trim() || undefined,
    };
    this.checkoutComptant.emit(payload);
  }

  onReportChambre(): void {
    this.reportChambre.emit();
  }

  onPushServices(): void {
    this.pushServices.emit();
  }

  onClearCart(): void {
    this.clearCart.emit();
  }

  /** Reset du formulaire paiement après nouvelle commande (appelé par parent). */
  resetPaymentForm(): void {
    this.selectedMode = ModePaiement.ESPECES;
    this.modeRequiresReference = false;
    if (this.form) {
      this.form.reset({ referencePaiement: '', commentaires: '' });
    }
  }

  trackByCartLine(_index: number, line: LigneCommande): string {
    return line.cartLineId;
  }
}
