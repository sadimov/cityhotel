import { Component, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../../services/translation.service';
import { FileDownloadUtil } from '../../../../../shared/utils/file-download.util';
import { BilanDto } from '../../../models/etats.model';
import { EtatsService } from '../../../services/etats.service';

type ViewState = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Visualiseur Bilan SYSCOHADA simplifié (B7).
 *
 * Layout deux colonnes Actif / Passif, totaux, résultat net.
 */
@Component({
  selector: 'app-bilan-viewer',
  templateUrl: './bilan-viewer.component.html',
  standalone: false,
})
export class BilanViewerComponent implements OnDestroy {
  state: ViewState = 'idle';
  bilan: BilanDto | null = null;
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
      dateArrete: [''],
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  generate(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const exerciceId = Number(this.form.value.exerciceId);
    const dateArrete = this.form.value.dateArrete || undefined;
    this.state = 'loading';
    this.bilan = null;
    this.service
      .getBilan(exerciceId, dateArrete)
      .pipe(
        takeUntil(this.destroy$),
        catchError((err) => {
          this.state = 'error';
          const key = err?.error?.message || 'error.etat.fetchFailed';
          Swal.fire({ icon: 'error', title: this.i18n.translate(key) });
          return of(null);
        }),
      )
      .subscribe((b) => {
        if (!b) return;
        this.bilan = b;
        this.state = 'ready';
      });
  }

  exportXlsx(): void {
    if (this.form.invalid) return;
    const exerciceId = Number(this.form.value.exerciceId);
    const dateArrete = this.form.value.dateArrete || undefined;
    this.exporting = true;
    this.service
      .exportBilanXlsx(exerciceId, dateArrete)
      .pipe(takeUntil(this.destroy$), finalize(() => (this.exporting = false)))
      .subscribe({
        next: (blob) =>
          FileDownloadUtil.saveBlob(
            blob,
            FileDownloadUtil.buildFilename('bilan', new Date().toISOString().slice(0, 10), 'xlsx'),
          ),
        error: () =>
          Swal.fire({ icon: 'error', title: this.i18n.translate('error.etat.exportFailed') }),
      });
  }

  exportPdf(): void {
    if (this.form.invalid) return;
    const exerciceId = Number(this.form.value.exerciceId);
    const dateArrete = this.form.value.dateArrete || undefined;
    this.exporting = true;
    this.service
      .exportBilanPdf(exerciceId, dateArrete)
      .pipe(takeUntil(this.destroy$), finalize(() => (this.exporting = false)))
      .subscribe({
        next: (blob) =>
          FileDownloadUtil.saveBlob(
            blob,
            FileDownloadUtil.buildFilename('bilan', new Date().toISOString().slice(0, 10), 'pdf'),
          ),
        error: () =>
          Swal.fire({ icon: 'error', title: this.i18n.translate('error.etat.exportFailed') }),
      });
  }
}
