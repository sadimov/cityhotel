import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../../services/translation.service';
import {
  EcritureComptableDto,
  SensLigne,
  STATUT_ECRITURE_BADGE_MAP,
  StatutEcriture,
} from '../../../models/ecriture.model';
import { EcrituresService } from '../../../services/ecritures.service';

type DetailState = 'loading' | 'ready' | 'notfound' | 'error';

/**
 * Détail d'une écriture comptable (B7).
 *
 * Affiche l'en-tête + les lignes en partie double + totaux.
 * Action : "Contre-passer" disponible si statut == VALIDEE.
 */
@Component({
  selector: 'app-ecriture-detail',
  templateUrl: './ecriture-detail.component.html',
  standalone: false,
})
export class EcritureDetailComponent implements OnInit, OnDestroy {
  state: DetailState = 'loading';
  ecriture: EcritureComptableDto | null = null;
  contrepassing = false;

  readonly SensLigne = SensLigne;
  readonly StatutEcriture = StatutEcriture;
  readonly badgeMap = STATUT_ECRITURE_BADGE_MAP;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly service: EcrituresService,
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
    this.service
      .findById(id)
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of(null)),
      )
      .subscribe((ec) => {
        if (!ec) {
          this.state = 'notfound';
          return;
        }
        this.ecriture = ec;
        this.state = 'ready';
      });
  }

  contrepasser(): void {
    if (!this.ecriture || this.ecriture.statut !== StatutEcriture.VALIDEE) return;
    const id = this.ecriture.id;
    Swal.fire({
      title: this.i18n.translate('comptabilite.ecriture.messages.contrepasserConfirm'),
      text: this.i18n.translate('comptabilite.ecriture.messages.contrepasserWarning'),
      icon: 'warning',
      input: 'textarea',
      inputLabel: this.i18n.translate('comptabilite.ecriture.fields.motif'),
      inputPlaceholder: this.i18n.translate('comptabilite.ecriture.fields.motifPlaceholder'),
      inputAttributes: { 'aria-label': 'motif' },
      inputValidator: (value) => {
        if (!value || value.trim().length < 3) {
          return this.i18n.translate('error.ecriture.motifRequired');
        }
        return null;
      },
      showCancelButton: true,
      confirmButtonText: this.i18n.translate('common.confirm'),
      cancelButtonText: this.i18n.translate('common.cancel'),
      reverseButtons: true,
    }).then((result) => {
      if (!result.isConfirmed || !result.value) return;
      this.contrepassing = true;
      this.service
        .contrePasser(id, { motif: result.value.trim() })
        .pipe(
          takeUntil(this.destroy$),
          finalize(() => (this.contrepassing = false)),
        )
        .subscribe({
          next: () => {
            Swal.fire({
              icon: 'success',
              title: this.i18n.translate('comptabilite.ecriture.messages.contrepasserSuccess'),
              timer: 1500,
              showConfirmButton: false,
            });
            this.load(id);
          },
          error: (err) => {
            const key = err?.error?.message || 'error.ecriture.contrepasserFailed';
            Swal.fire({
              icon: 'error',
              title: this.i18n.translate(key),
            });
          },
        });
    });
  }

  goBack(): void {
    this.router.navigate(['/comptabilite/ecritures']);
  }

  statutKey(s: StatutEcriture): string {
    return `comptabilite.ecriture.statut.${s}`;
  }

  get peutContrepasser(): boolean {
    return this.ecriture?.statut === StatutEcriture.VALIDEE;
  }
}
