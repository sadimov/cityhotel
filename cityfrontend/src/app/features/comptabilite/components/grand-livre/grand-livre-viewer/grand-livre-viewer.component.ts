import { Component, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../../services/translation.service';
import { FileDownloadUtil } from '../../../../../shared/utils/file-download.util';
import { GrandLivreDto } from '../../../models/etats.model';
import { EtatsService, GrandLivreFilter } from '../../../services/etats.service';

type ViewState = 'idle' | 'loading' | 'ready' | 'empty' | 'error';

interface AccordionState {
  [compteCode: string]: boolean;
}

/**
 * Visualiseur Grand Livre (B7) — accordéon par compte.
 */
@Component({
  selector: 'app-grand-livre-viewer',
  templateUrl: './grand-livre-viewer.component.html',
  standalone: false,
})
export class GrandLivreViewerComponent implements OnDestroy {
  state: ViewState = 'idle';
  grandLivre: GrandLivreDto | null = null;
  form: FormGroup;
  exporting = false;
  expanded: AccordionState = {};

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly service: EtatsService,
    private readonly i18n: TranslationService,
  ) {
    this.form = this.fb.group({
      compteCode: [''],
      exerciceId: [''],
      dateDebut: [''],
      dateFin: [''],
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private buildFilter(): GrandLivreFilter {
    const raw = this.form.value;
    return {
      compteCode: raw.compteCode || undefined,
      exerciceId: raw.exerciceId ? Number(raw.exerciceId) : undefined,
      dateDebut: raw.dateDebut || undefined,
      dateFin: raw.dateFin || undefined,
    };
  }

  generate(): void {
    this.state = 'loading';
    this.grandLivre = null;
    this.expanded = {};
    this.service
      .getGrandLivre(this.buildFilter())
      .pipe(
        takeUntil(this.destroy$),
        catchError((err) => {
          this.state = 'error';
          const key = err?.error?.message || 'error.etat.fetchFailed';
          Swal.fire({ icon: 'error', title: this.i18n.translate(key) });
          return of(null);
        }),
      )
      .subscribe((gl) => {
        if (!gl) return;
        this.grandLivre = gl;
        this.state = gl.comptes.length === 0 ? 'empty' : 'ready';
      });
  }

  toggle(compteCode: string): void {
    this.expanded[compteCode] = !this.expanded[compteCode];
  }

  isExpanded(compteCode: string): boolean {
    return !!this.expanded[compteCode];
  }

  exportXlsx(): void {
    this.exporting = true;
    this.service
      .exportGrandLivreXlsx(this.buildFilter())
      .pipe(takeUntil(this.destroy$), finalize(() => (this.exporting = false)))
      .subscribe({
        next: (blob) =>
          FileDownloadUtil.saveBlob(
            blob,
            FileDownloadUtil.buildFilename('grand-livre', new Date().toISOString().slice(0, 10), 'xlsx'),
          ),
        error: () =>
          Swal.fire({ icon: 'error', title: this.i18n.translate('error.etat.exportFailed') }),
      });
  }

  exportPdf(): void {
    this.exporting = true;
    this.service
      .exportGrandLivrePdf(this.buildFilter())
      .pipe(takeUntil(this.destroy$), finalize(() => (this.exporting = false)))
      .subscribe({
        next: (blob) =>
          FileDownloadUtil.saveBlob(
            blob,
            FileDownloadUtil.buildFilename('grand-livre', new Date().toISOString().slice(0, 10), 'pdf'),
          ),
        error: () =>
          Swal.fire({ icon: 'error', title: this.i18n.translate('error.etat.exportFailed') }),
      });
  }
}
