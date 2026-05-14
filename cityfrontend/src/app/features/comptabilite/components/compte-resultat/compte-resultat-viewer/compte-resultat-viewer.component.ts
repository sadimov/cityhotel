import { Component, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../../services/translation.service';
import { FileDownloadUtil } from '../../../../../shared/utils/file-download.util';
import { CompteResultatDto } from '../../../models/etats.model';
import { EtatsService } from '../../../services/etats.service';

type ViewState = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Visualiseur Compte de Résultat SYSCOHADA simplifié (B7).
 *
 * Layout deux colonnes Produits / Charges + marge brute + résultat net.
 */
@Component({
  selector: 'app-compte-resultat-viewer',
  templateUrl: './compte-resultat-viewer.component.html',
  standalone: false,
})
export class CompteResultatViewerComponent implements OnDestroy {
  state: ViewState = 'idle';
  resultat: CompteResultatDto | null = null;
  form: FormGroup;
  exporting = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly service: EtatsService,
    private readonly i18n: TranslationService,
  ) {
    this.form = this.fb.group({
      exerciceId: ['', [Validators.required, Validators.min(1)]],
      dateDebut: [''],
      dateFin: [''],
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private params(): { id: number; dd?: string; df?: string } {
    const raw = this.form.value;
    return {
      id: Number(raw.exerciceId),
      dd: raw.dateDebut || undefined,
      df: raw.dateFin || undefined,
    };
  }

  generate(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { id, dd, df } = this.params();
    this.state = 'loading';
    this.resultat = null;
    this.service
      .getCompteResultat(id, dd, df)
      .pipe(
        takeUntil(this.destroy$),
        catchError((err) => {
          this.state = 'error';
          const key = err?.error?.message || 'error.etat.fetchFailed';
          Swal.fire({ icon: 'error', title: this.i18n.translate(key) });
          return of(null);
        }),
      )
      .subscribe((r) => {
        if (!r) return;
        this.resultat = r;
        this.state = 'ready';
      });
  }

  exportXlsx(): void {
    if (this.form.invalid) return;
    const { id, dd, df } = this.params();
    this.exporting = true;
    this.service
      .exportCompteResultatXlsx(id, dd, df)
      .pipe(takeUntil(this.destroy$), finalize(() => (this.exporting = false)))
      .subscribe({
        next: (blob) =>
          FileDownloadUtil.saveBlob(
            blob,
            FileDownloadUtil.buildFilename('compte-resultat', new Date().toISOString().slice(0, 10), 'xlsx'),
          ),
        error: () =>
          Swal.fire({ icon: 'error', title: this.i18n.translate('error.etat.exportFailed') }),
      });
  }

  exportPdf(): void {
    if (this.form.invalid) return;
    const { id, dd, df } = this.params();
    this.exporting = true;
    this.service
      .exportCompteResultatPdf(id, dd, df)
      .pipe(takeUntil(this.destroy$), finalize(() => (this.exporting = false)))
      .subscribe({
        next: (blob) =>
          FileDownloadUtil.saveBlob(
            blob,
            FileDownloadUtil.buildFilename('compte-resultat', new Date().toISOString().slice(0, 10), 'pdf'),
          ),
        error: () =>
          Swal.fire({ icon: 'error', title: this.i18n.translate('error.etat.exportFailed') }),
      });
  }
}
