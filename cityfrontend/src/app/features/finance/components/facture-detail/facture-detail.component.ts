import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { FileDownloadUtil } from '../../../../shared/utils/file-download.util';
import { FactureDto, StatutFacture } from '../../models/facture.model';
import { ModePaiement } from '../../models/paiement.model';
import { FacturesService } from '../../services/factures.service';

type DetailState = 'loading' | 'ready' | 'notfound' | 'error';

/**
 * Détail d'une facture : en-tête, lignes, paiements affectés (déduits de la
 * facture via `lignes` côté serveur ; les affectations paiement sont
 * intégrées au DTO Paiement et seraient à charger séparément si nécessaire).
 *
 * Actions backend disponibles :
 *  - POST `/factures/{id}/emettre`  → BROUILLON → EMISE
 *  - POST `/factures/{id}/annuler`  → BROUILLON | EMISE → ANNULEE
 *  - Encaisser → redirige vers `paiement-form` (POST /paiements avec
 *    `factureId`).
 *  - Édition non supportée côté back (cycle BROUILLON → EMISE → AVOIR pour
 *    correction).
 */
@Component({
  selector: 'app-facture-detail',
  templateUrl: './facture-detail.component.html',
  styleUrls: ['./facture-detail.component.scss'],
  standalone: false,
})
export class FactureDetailComponent implements OnInit, OnDestroy {
  state: DetailState = 'loading';
  facture: FactureDto | null = null;
  cancelling = false;
  emitting = false;
  downloadingPdf = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly facturesService: FacturesService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = Number(idParam);
    if (!idParam || !Number.isFinite(id)) {
      this.state = 'notfound';
      return;
    }
    this.load(id);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  load(id: number): void {
    this.state = 'loading';
    this.facturesService
      .findById(id)
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of(null)),
      )
      .subscribe((facture) => {
        if (!facture) {
          this.state = 'notfound';
          return;
        }
        this.facture = facture;
        this.state = 'ready';
      });
  }

  encaisser(): void {
    if (!this.facture?.factureId) return;
    this.router.navigate([
      '/finance/factures',
      this.facture.factureId,
      'paiement',
    ]);
  }

  emettre(): void {
    if (!this.facture?.factureId) return;
    const id = this.facture.factureId;
    Swal.fire({
      title: this.i18n.translate('finance.facture.messages.emettreConfirm'),
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('finance.actions.confirm'),
      cancelButtonText: this.i18n.translate('finance.actions.close'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) return;
      this.emitting = true;
      this.facturesService
        .emettre(id)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => (this.emitting = false)),
        )
        .subscribe({
          next: (f) => {
            this.facture = f;
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('finance.facture.messages.emettreSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('finance.facture.messages.emettreError'),
            });
          },
        });
    });
  }

  annuler(): void {
    if (!this.facture?.factureId) return;
    const id = this.facture.factureId;
    Swal.fire({
      title: this.i18n.translate('finance.facture.messages.cancelConfirm'),
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('finance.actions.confirm'),
      cancelButtonText: this.i18n.translate('finance.actions.close'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed) return;
      this.cancelling = true;
      this.facturesService
        .annuler(id)
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => (this.cancelling = false)),
        )
        .subscribe({
          next: (f) => {
            this.facture = f;
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('finance.facture.messages.cancelSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
          },
          error: () => {
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate('finance.facture.messages.cancelError'),
            });
          },
        });
    });
  }

  goBack(): void {
    this.router.navigate(['/finance/factures']);
  }

  /**
   * Télécharge la facture au format PDF (B6/B7).
   *
   * Le back retourne un `Content-Type: application/pdf`. Le helper
   * `FileDownloadUtil.saveBlob` orchestre le download navigateur via une
   * balise `<a download>` jetable + `URL.createObjectURL`.
   */
  downloadPdf(): void {
    if (!this.facture?.factureId) return;
    const id = this.facture.factureId;
    const numero = this.facture.numeroFacture;
    this.downloadingPdf = true;
    this.facturesService
      .downloadPdf(id)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => (this.downloadingPdf = false)),
      )
      .subscribe({
        next: (blob) => {
          FileDownloadUtil.saveBlob(blob, `FACTURE-${numero}.pdf`);
        },
        error: () => {
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('finance.facture.messages.pdfError'),
          });
        },
      });
  }

  badgeClass(statut: StatutFacture | undefined): string {
    switch (statut) {
      case StatutFacture.BROUILLON:
        return 'text-bg-secondary';
      case StatutFacture.EMISE:
        return 'text-bg-info';
      case StatutFacture.PARTIELLEMENT_PAYEE:
        return 'text-bg-warning';
      case StatutFacture.PAYEE:
        return 'text-bg-success';
      case StatutFacture.ANNULEE:
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }

  statutKey(statut: StatutFacture | undefined): string {
    if (!statut) return 'finance.facture.statut.BROUILLON';
    return 'finance.facture.statut.' + statut;
  }

  modeKey(mode: ModePaiement | string | undefined): string {
    if (!mode) return 'finance.paiement.mode.ESPECES';
    return 'finance.paiement.mode.' + mode;
  }

  get peutEmettre(): boolean {
    return this.facture?.statut === StatutFacture.BROUILLON;
  }

  get peutEncaisser(): boolean {
    if (!this.facture) return false;
    if (
      this.facture.statut !== StatutFacture.EMISE
      && this.facture.statut !== StatutFacture.PARTIELLEMENT_PAYEE
    ) {
      return false;
    }
    return Number(this.facture.montantRestant ?? 0) > 0;
  }

  get peutAnnuler(): boolean {
    if (!this.facture) return false;
    return (
      this.facture.statut === StatutFacture.BROUILLON
      || this.facture.statut === StatutFacture.EMISE
    );
  }
}
