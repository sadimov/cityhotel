import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { EncaissementCommandeRequest } from '../../models/commande.model';
import { PosStep, PosStore } from './state/pos.store';

/**
 * Point d'entrée POS Restaurant — vue 3 zones :
 *   - colonne gauche : recherche client + réservations actives (`<app-pos-client-search>`)
 *   - colonne centre : grille articles segmentée par catégorie
 *   - colonne droite : panier en cours + actions (`<app-pos-cart>`)
 *
 * Le composant orchestre uniquement la navigation entre étapes (sélection
 * client → articles → paiement) et relaie les événements enfants vers le
 * `PosStore`. Toute la logique métier vit dans le store.
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

  readonly step$ = this.store.step$;
  readonly selectedClient$ = this.store.selectedClient$;
  readonly cart$ = this.store.cart$;
  readonly total$ = this.store.total$;
  readonly submitting$ = this.store.submitting$;
  readonly canCheckoutComptant$ = this.store.canCheckoutComptant$;
  readonly canReportChambre$ = this.store.canReportChambre$;
  readonly lastCommande$ = this.store.lastCommande$;
  readonly printingTicket$ = this.store.printingTicket$;

  paymentModalOpen = false;
  paymentModalTotal = 0;

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
          // Clear error after showing it.
          this.store.setError(null);
        }
      });

    this.store.lastSuccessMessage$
      .pipe(takeUntil(this.destroy$))
      .subscribe((messageKey) => {
        if (messageKey) {
          Swal.fire({
            icon: 'success',
            title: this.i18n.translate(messageKey),
            timer: 2000,
            showConfirmButton: false,
          });
          this.store.setSuccess(null);
        }
      });

    // Synchronise paymentModalTotal avec le store quand le panier change
    this.store.total$
      .pipe(takeUntil(this.destroy$))
      .subscribe((total) => {
        this.paymentModalTotal = total;
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  openPaymentModal(): void {
    this.paymentModalOpen = true;
  }

  closePaymentModal(): void {
    this.paymentModalOpen = false;
  }

  onSubmitPayment(payload: EncaissementCommandeRequest): void {
    this.store.submitOrderComptant(payload);
    this.paymentModalOpen = false;
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

  onPrintCaisse(commandeId: number): void {
    this.store.imprimerTicketCaisse(commandeId);
  }

  onPrintCuisine(commandeId: number): void {
    this.store.imprimerTicketCuisine(commandeId);
  }

  onStartNewOrder(): void {
    this.store.startNewOrder();
  }
}
