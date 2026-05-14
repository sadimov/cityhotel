import { Component, EventEmitter, Output } from '@angular/core';

import { LigneCommande } from '../../../models/commande.model';
import { PosStore } from '../state/pos.store';

/** Type interne — événement émis pour déclencher une impression ticket. */
export interface PrintTicketEvent {
  commandeId: number;
}

/**
 * Panier en cours d'édition (colonne droite POS).
 *
 * Affiche les lignes du panier, permet l'ajustement de quantité, la
 * suppression de ligne, et déclenche les actions parent (encaisser /
 * reporter chambre) via `EventEmitter`. Le composant parent (`PosComponent`)
 * relaie vers le `PaymentModalComponent` ou directement le store.
 */
@Component({
  selector: 'app-pos-cart',
  templateUrl: './cart.component.html',
  styleUrls: ['./cart.component.scss'],
  standalone: false,
})
export class CartComponent {
  @Output() readonly checkoutComptant = new EventEmitter<void>();
  @Output() readonly reportChambre = new EventEmitter<void>();
  @Output() readonly printCaisse = new EventEmitter<PrintTicketEvent>();
  @Output() readonly printCuisine = new EventEmitter<PrintTicketEvent>();
  @Output() readonly startNewOrder = new EventEmitter<void>();

  readonly cart$ = this.store.cart$;
  readonly total$ = this.store.total$;
  readonly itemsCount$ = this.store.itemsCount$;
  readonly isCartEmpty$ = this.store.isCartEmpty$;
  readonly canCheckoutComptant$ = this.store.canCheckoutComptant$;
  readonly canReportChambre$ = this.store.canReportChambre$;
  readonly submitting$ = this.store.submitting$;
  readonly lastCommande$ = this.store.lastCommande$;
  readonly printingTicket$ = this.store.printingTicket$;

  constructor(private readonly store: PosStore) {}

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

  onClearCart(): void {
    this.store.clearCart();
  }

  onCheckoutComptant(): void {
    this.checkoutComptant.emit();
  }

  onReportChambre(): void {
    this.reportChambre.emit();
  }

  onPrintCaisse(commandeId: number): void {
    this.printCaisse.emit({ commandeId });
  }

  onPrintCuisine(commandeId: number): void {
    this.printCuisine.emit({ commandeId });
  }

  onStartNewOrder(): void {
    this.startNewOrder.emit();
  }

  trackByCartLine(_index: number, line: LigneCommande): string {
    return line.cartLineId;
  }
}
