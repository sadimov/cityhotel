import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { distinctUntilChanged, filter, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { Commande, EncaissementCommandeRequest } from '../../models/commande.model';
import { PosStep, PosStore } from './state/pos.store';

/**
 * Point d'entrée POS Restaurant — layout 3 bandes horizontales empilées :
 *  A · `<app-pos-client-header>` — sélection client / réservation (sticky top).
 *  B · `<app-pos-article-grid>` — onglets Articles / Services + grille.
 *  C · `<app-pos-cart>` — panier (gauche) + récap + paiement (droite).
 *
 * Le composant orchestre :
 *  - l'ouverture de la modale de sélection client/réservation ;
 *  - la confirmation SweetAlert2 avant encaissement et report chambre ;
 *  - le toast Swal2 post-checkout avec actions impression / nouvelle commande
 *    (remplace l'écran « succès » plein-bloc historique du panier).
 *
 * `PosStore` est `provided` au niveau de ce composant (cycle de vie lié au
 * composant — détruit avec lui), pas globalement.
 */
@Component({
  selector: 'app-pos',
  templateUrl: './pos.component.html',
  styleUrls: ['./pos.component.scss'],
  standalone: false,
  providers: [PosStore],
})
export class PosComponent implements OnInit, OnDestroy {
  readonly PosStep = PosStep;

  /** Ouverture/fermeture de la modale de sélection client/réservation. */
  clientModalOpen = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly store: PosStore,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.store.loadCategories();

    this.store.error$
      .pipe(takeUntil(this.destroy$))
      .subscribe((errorKey) => {
        if (errorKey) {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate(errorKey),
            timer: 2500,
            showConfirmButton: false,
          });
          this.store.setError(null);
        }
      });

    this.store.lastSuccessMessage$
      .pipe(takeUntil(this.destroy$))
      .subscribe((messageKey) => {
        if (messageKey) {
          this.fireSuccessToast(this.i18n.translate(messageKey));
          this.store.setSuccess(null);
        }
      });

    // Toast post-checkout avec actions impression / nouvelle commande.
    // Déclenché sur transition d'une commande nouvellement encaissée.
    this.store.lastCommande$
      .pipe(
        distinctUntilChanged((a, b) => a?.commandeId === b?.commandeId),
        filter(
          (cmd): cmd is Commande & { commandeId: number } =>
            cmd != null && cmd.commandeId != null,
        ),
        takeUntil(this.destroy$),
      )
      .subscribe((cmd) => this.firePostCheckoutDialog(cmd));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ─────────────────────────────────────────────────────────────────────
  // Modale client / réservation
  // ─────────────────────────────────────────────────────────────────────

  openClientModal(): void {
    this.clientModalOpen = true;
  }

  closeClientModal(): void {
    this.clientModalOpen = false;
  }

  // ─────────────────────────────────────────────────────────────────────
  // Actions cart relayées
  // ─────────────────────────────────────────────────────────────────────

  onCheckoutComptant(payload: EncaissementCommandeRequest): void {
    const totalLabel = `${payload.montant.toFixed(2)} ${this.i18n.translate(
      'restaurant.pos.currency',
    )}`;
    const modeLabel = this.i18n.translate(
      'restaurant.pos.payment.modes.' + payload.modePaiement,
    );
    Swal.fire({
      icon: 'question',
      title: this.i18n.translate(
        'restaurant.pos.messages.checkoutConfirmTitle',
      ),
      html: `<div class="text-start">
        <div class="mb-1"><strong>${this.i18n.translate(
          'restaurant.pos.cart.total',
        )} :</strong> ${totalLabel}</div>
        <div><strong>${this.i18n.translate(
          'restaurant.pos.payment.modeLabel',
        )} :</strong> ${modeLabel}</div>
      </div>`,
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('restaurant.pos.cart.checkout'),
      cancelButtonText: this.i18n.translate('common.cancel'),
      reverseButtons: true,
    }).then((result) => {
      if (result.isConfirmed) {
        this.store.submitOrderComptant(payload);
      }
    });
  }

  onReportChambre(): void {
    Swal.fire({
      icon: 'question',
      title: this.i18n.translate('restaurant.pos.messages.reportConfirmTitle'),
      text: this.i18n.translate('restaurant.pos.messages.reportConfirmText'),
      showCancelButton: true,
      confirmButtonText: this.i18n.translate(
        'restaurant.pos.cart.reportChambre',
      ),
      cancelButtonText: this.i18n.translate('common.cancel'),
      reverseButtons: true,
    }).then((result) => {
      if (result.isConfirmed) {
        this.store.submitOrderReportChambre();
      }
    });
  }

  onClearCart(): void {
    Swal.fire({
      icon: 'warning',
      title: this.i18n.translate('restaurant.pos.messages.clearCartConfirmTitle'),
      text: this.i18n.translate('restaurant.pos.messages.clearCartConfirmText'),
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('restaurant.pos.cart.clear'),
      cancelButtonText: this.i18n.translate('common.cancel'),
      reverseButtons: true,
    }).then((result) => {
      if (result.isConfirmed) {
        this.store.clearCart();
      }
    });
  }

  onPushServices(): void {
    Swal.fire({
      icon: 'question',
      title: this.i18n.translate(
        'restaurant.pos.messages.pushServicesConfirmTitle',
      ),
      text: this.i18n.translate(
        'restaurant.pos.messages.pushServicesConfirmText',
      ),
      showCancelButton: true,
      confirmButtonText: this.i18n.translate(
        'restaurant.pos.cart.pushServicesToRoom',
      ),
      cancelButtonText: this.i18n.translate('common.cancel'),
      reverseButtons: true,
    }).then((result) => {
      if (result.isConfirmed) {
        this.store.pushServicesToReservation();
      }
    });
  }

  // ─────────────────────────────────────────────────────────────────────
  // Toasts & dialogs post-checkout
  // ─────────────────────────────────────────────────────────────────────

  /** Toast Swal2 court (3s, top-end, non bloquant). */
  private fireSuccessToast(message: string): void {
    Swal.fire({
      toast: true,
      position: 'top-end',
      icon: 'success',
      title: message,
      timer: 3000,
      showConfirmButton: false,
      timerProgressBar: true,
    });
  }

  /**
   * Dialog Swal2 affiché après un encaissement réussi (ou un report chambre).
   * Propose les actions impression caisse / impression cuisine / nouvelle
   * commande. Remplace l'écran « succès » plein-bloc historique du panier.
   */
  private firePostCheckoutDialog(cmd: Commande & { commandeId: number }): void {
    const numero = cmd.numeroCommande || `#${cmd.commandeId}`;
    const factureLine = cmd.numeroFacture
      ? `<div><span class="text-muted">${this.i18n.translate('restaurant.pos.cart.invoiceNumber')} :</span> <strong>${cmd.numeroFacture}</strong></div>`
      : '';
    const totalLine = cmd.montantTotal != null
      ? `<div><span class="text-muted">${this.i18n.translate('restaurant.pos.cart.total')} :</span> <strong>${cmd.montantTotal.toFixed(2)} ${this.i18n.translate('restaurant.pos.currency')}</strong></div>`
      : '';

    Swal.fire({
      icon: 'success',
      title: this.i18n.translate('restaurant.pos.cart.checkoutSuccess'),
      html: `<div class="text-start small">
        <div><span class="text-muted">${this.i18n.translate('restaurant.pos.cart.orderNumber')} :</span> <strong>${numero}</strong></div>
        ${factureLine}
        ${totalLine}
      </div>`,
      showConfirmButton: true,
      confirmButtonText: `<i class="bi bi-receipt me-1"></i>${this.i18n.translate('restaurant.pos.ticket.print')}`,
      showDenyButton: true,
      denyButtonText: `<i class="bi bi-fire me-1"></i>${this.i18n.translate('restaurant.pos.ticket.printKitchen')}`,
      showCancelButton: true,
      cancelButtonText: `<i class="bi bi-plus-lg me-1"></i>${this.i18n.translate('restaurant.pos.cart.newOrder')}`,
      reverseButtons: true,
      allowOutsideClick: false,
    }).then((result) => {
      if (result.isConfirmed) {
        this.store.imprimerTicketCaisse(cmd.commandeId);
      } else if (result.isDenied) {
        this.store.imprimerTicketCuisine(cmd.commandeId);
      } else {
        this.store.startNewOrder();
      }
    });
  }
}
