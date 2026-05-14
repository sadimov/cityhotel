import { Component, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../../services/translation.service';
import { FileDownloadUtil } from '../../../../../shared/utils/file-download.util';
import { BalanceComptableDto } from '../../../models/etats.model';
import { BalanceFilter, EtatsService } from '../../../services/etats.service';

type ViewState = 'idle' | 'loading' | 'ready' | 'empty' | 'error';

/**
 * Visualiseur Balance comptable (B7).
 *
 * Filtres : exerciceId (number) ou plage de dates (dateDebut/dateFin),
 * filtre optionnel par classe. Exports XLSX / PDF via téléchargement Blob.
 */
@Component({
  selector: 'app-balance-viewer',
  templateUrl: './balance-viewer.component.html',
  standalone: false,
})
export class BalanceViewerComponent implements OnDestroy {
  state: ViewState = 'idle';
  balance: BalanceComptableDto | null = null;
  form: FormGroup;
  exporting = false;
  readonly classes: ReadonlyArray<number> = [1, 2, 3, 4, 5, 6, 7];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly service: EtatsService,
    private readonly i18n: TranslationService,
  ) {
    this.form = this.fb.group({
      exerciceId: [''],
      dateDebut: [''],
      dateFin: [''],
      classe: [''],
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private buildFilter(): BalanceFilter {
    const raw = this.form.value;
    return {
      exerciceId: raw.exerciceId ? Number(raw.exerciceId) : undefined,
      dateDebut: raw.dateDebut || undefined,
      dateFin: raw.dateFin || undefined,
      classe: raw.classe ? Number(raw.classe) : undefined,
    };
  }

  generate(): void {
    this.state = 'loading';
    this.balance = null;
    this.service
      .getBalance(this.buildFilter())
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
        this.balance = b;
        this.state = b.lignes.length === 0 ? 'empty' : 'ready';
      });
  }

  exportXlsx(): void {
    this.exporting = true;
    this.service
      .exportBalanceXlsx(this.buildFilter())
      .pipe(takeUntil(this.destroy$), finalize(() => (this.exporting = false)))
      .subscribe({
        next: (blob) => {
          FileDownloadUtil.saveBlob(
            blob,
            FileDownloadUtil.buildFilename('balance', new Date().toISOString().slice(0, 10), 'xlsx'),
          );
        },
        error: () =>
          Swal.fire({ icon: 'error', title: this.i18n.translate('error.etat.exportFailed') }),
      });
  }

  exportPdf(): void {
    this.exporting = true;
    this.service
      .exportBalancePdf(this.buildFilter())
      .pipe(takeUntil(this.destroy$), finalize(() => (this.exporting = false)))
      .subscribe({
        next: (blob) => {
          FileDownloadUtil.saveBlob(
            blob,
            FileDownloadUtil.buildFilename('balance', new Date().toISOString().slice(0, 10), 'pdf'),
          );
        },
        error: () =>
          Swal.fire({ icon: 'error', title: this.i18n.translate('error.etat.exportFailed') }),
      });
  }
}
