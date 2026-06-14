import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { DashboardDirectionDto } from '../../models/direction-reports.model';
import { DirectionReportsService } from '../../services/direction-reports.service';
import { ReportsDownloadService } from '../../services/reports-download.service';

type SectionState = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Page reporting — Direction. 1 dashboard agrégé (R-DIR-001).
 *
 * Pas de tabs : un seul rapport. Filtre date unique. 3 boutons d'export.
 */
@Component({
  selector: 'app-direction-reports',
  templateUrl: './direction-reports.component.html',
  styleUrls: ['./direction-reports.component.scss'],
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectionReportsComponent implements OnInit, OnDestroy {
  filters = { date: '' };

  state: SectionState = 'idle';
  exporting = false;

  dashboard: DashboardDirectionDto | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly api: DirectionReportsService,
    private readonly downloader: ReportsDownloadService,
    private readonly i18n: TranslationService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.filters.date = this.toIsoDate(new Date());
    this.refresh();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  refresh(): void {
    if (!this.filters.date) return;
    this.state = 'loading';
    this.cdr.markForCheck();
    this.api.getDashboard(this.filters.date)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e)))
      .subscribe((dto) => {
        if (!dto) return;
        this.dashboard = dto;
        this.state = 'ready';
        this.cdr.markForCheck();
      });
  }

  exportXlsx(): void { this.runExport('/api/reports/direction/dashboard/export.xlsx', 'dashboard-direction.xlsx'); }
  exportDocx(): void { this.runExport('/api/reports/direction/dashboard/export.docx', 'dashboard-direction.docx'); }
  exportPdf(): void { this.runExport('/api/reports/direction/dashboard/export.pdf', 'dashboard-direction.pdf'); }

  private runExport(path: string, filename: string): void {
    if (!this.filters.date) return;
    this.exporting = true;
    this.cdr.markForCheck();
    this.downloader.download(path, filename, { date: this.filters.date })
      .pipe(takeUntil(this.destroy$),
        finalize(() => { this.exporting = false; this.cdr.markForCheck(); }))
      .subscribe({
        next: () => undefined,
        error: (err) => Swal.fire({
          icon: 'error',
          title: this.i18n.translate('reporting.exportError', 'Erreur d\'export'),
          text: typeof err?.message === 'string' ? err.message : '',
        }),
      });
  }

  private handleError(err: unknown) {
    this.state = 'error';
    this.cdr.markForCheck();
    Swal.fire({
      icon: 'error',
      title: this.i18n.translate('reporting.loadError', 'Erreur de chargement'),
      text: this.extractMessage(err),
    });
    return of(null);
  }

  private extractMessage(err: unknown): string {
    if (err && typeof err === 'object') {
      const anyErr = err as Record<string, unknown>;
      if (typeof anyErr['message'] === 'string') return anyErr['message'] as string;
      const error = anyErr['error'] as Record<string, unknown> | undefined;
      if (error && typeof error['message'] === 'string') return error['message'] as string;
    }
    return '';
  }

  private toIsoDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
