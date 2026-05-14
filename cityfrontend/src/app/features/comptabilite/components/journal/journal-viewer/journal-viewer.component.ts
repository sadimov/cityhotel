import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { of, Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../../services/translation.service';
import { FileDownloadUtil } from '../../../../../shared/utils/file-download.util';
import { JournalEditionDto } from '../../../models/etats.model';
import { JournalComptableDto } from '../../../models/journal.model';
import { EtatsService, JournalFilter } from '../../../services/etats.service';
import { JournauxService } from '../../../services/journaux.service';

type ViewState = 'idle' | 'loading' | 'ready' | 'empty' | 'error';

/**
 * Visualiseur Journal édité (B7).
 */
@Component({
  selector: 'app-journal-viewer',
  templateUrl: './journal-viewer.component.html',
  standalone: false,
})
export class JournalViewerComponent implements OnInit, OnDestroy {
  state: ViewState = 'idle';
  edition: JournalEditionDto | null = null;
  form: FormGroup;
  exporting = false;
  journaux: JournalComptableDto[] = [];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly service: EtatsService,
    private readonly journauxService: JournauxService,
    private readonly i18n: TranslationService,
  ) {
    this.form = this.fb.group({
      journalId: ['', Validators.required],
      dateDebut: [''],
      dateFin: [''],
    });
  }

  ngOnInit(): void {
    this.journauxService
      .page({ page: 0, size: 100, sort: 'code,asc' })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (p) => (this.journaux = p.content),
        error: () => {
          // pas bloquant — l'utilisateur peut saisir manuellement l'ID
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private buildFilter(): JournalFilter {
    const raw = this.form.value;
    return {
      journalId: Number(raw.journalId),
      dateDebut: raw.dateDebut || undefined,
      dateFin: raw.dateFin || undefined,
    };
  }

  generate(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.state = 'loading';
    this.edition = null;
    this.service
      .getJournal(this.buildFilter())
      .pipe(
        takeUntil(this.destroy$),
        catchError((err) => {
          this.state = 'error';
          const key = err?.error?.message || 'error.etat.fetchFailed';
          Swal.fire({ icon: 'error', title: this.i18n.translate(key) });
          return of(null);
        }),
      )
      .subscribe((j) => {
        if (!j) return;
        this.edition = j;
        this.state = j.ecritures.length === 0 ? 'empty' : 'ready';
      });
  }

  exportXlsx(): void {
    if (this.form.invalid) return;
    this.exporting = true;
    this.service
      .exportJournalXlsx(this.buildFilter())
      .pipe(takeUntil(this.destroy$), finalize(() => (this.exporting = false)))
      .subscribe({
        next: (blob) =>
          FileDownloadUtil.saveBlob(
            blob,
            FileDownloadUtil.buildFilename('journal', new Date().toISOString().slice(0, 10), 'xlsx'),
          ),
        error: () =>
          Swal.fire({ icon: 'error', title: this.i18n.translate('error.etat.exportFailed') }),
      });
  }

  exportPdf(): void {
    if (this.form.invalid) return;
    this.exporting = true;
    this.service
      .exportJournalPdf(this.buildFilter())
      .pipe(takeUntil(this.destroy$), finalize(() => (this.exporting = false)))
      .subscribe({
        next: (blob) =>
          FileDownloadUtil.saveBlob(
            blob,
            FileDownloadUtil.buildFilename('journal', new Date().toISOString().slice(0, 10), 'pdf'),
          ),
        error: () =>
          Swal.fire({ icon: 'error', title: this.i18n.translate('error.etat.exportFailed') }),
      });
  }
}
