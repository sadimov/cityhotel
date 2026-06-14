import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import {
  ChargePersonnelDto,
  RecapTacheDto,
  TacheGroupBy,
} from '../../models/menage-reports.model';
import { MenageReportsService } from '../../services/menage-reports.service';
import { ReportsDownloadService } from '../../services/reports-download.service';

type TabKey = 'recap' | 'charge';
type SectionState = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Page reporting — Ménage. 2 onglets pour R-MEN-001..002.
 */
@Component({
  selector: 'app-menage-reports',
  templateUrl: './menage-reports.component.html',
  styleUrls: ['./menage-reports.component.scss'],
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MenageReportsComponent implements OnInit, OnDestroy {
  activeTab: TabKey = 'recap';

  filters = {
    recap: { from: '', to: '', groupBy: 'JOUR' as TacheGroupBy },
    charge: { from: '', to: '' },
  };

  states: Record<TabKey, SectionState> = { recap: 'idle', charge: 'idle' };
  exporting: Record<TabKey, boolean> = { recap: false, charge: false };

  recap: RecapTacheDto | null = null;
  charge: ChargePersonnelDto | null = null;

  readonly groupByOptions: TacheGroupBy[] = ['JOUR', 'TYPE_TACHE', 'STATUT'];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly api: MenageReportsService,
    private readonly downloader: ReportsDownloadService,
    private readonly i18n: TranslationService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const today = this.toIsoDate(new Date());
    const monthAgo = this.toIsoDate(new Date(Date.now() - 30 * 24 * 3600 * 1000));
    this.filters.recap.from = monthAgo;
    this.filters.recap.to = today;
    this.filters.charge.from = monthAgo;
    this.filters.charge.to = today;
    this.loadRecap();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  selectTab(tab: TabKey): void {
    this.activeTab = tab;
    if (this.states[tab] === 'idle') this.refresh(tab);
  }

  refresh(tab: TabKey = this.activeTab): void {
    if (tab === 'recap') return this.loadRecap();
    return this.loadCharge();
  }

  private loadRecap(): void {
    const { from, to, groupBy } = this.filters.recap;
    if (!from || !to) return;
    this.states.recap = 'loading';
    this.cdr.markForCheck();
    this.api.getRecapTaches(from, to, groupBy)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'recap')))
      .subscribe((dto) => {
        if (!dto) return;
        this.recap = dto;
        this.states.recap = 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadCharge(): void {
    const { from, to } = this.filters.charge;
    if (!from || !to) return;
    this.states.charge = 'loading';
    this.cdr.markForCheck();
    this.api.getChargePersonnel(from, to)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'charge')))
      .subscribe((dto) => {
        if (!dto) return;
        this.charge = dto;
        this.states.charge = 'ready';
        this.cdr.markForCheck();
      });
  }

  // ── Exports Recap ──
  private recapQs(): Record<string, string> | null {
    const { from, to, groupBy } = this.filters.recap;
    if (!from || !to) return null;
    return { from, to, groupBy };
  }
  exportRecapXlsx(): void { const qs = this.recapQs(); if (qs) this.runExport('recap', '/api/reports/menage/recap-taches/export.xlsx', 'recap-taches.xlsx', qs); }
  exportRecapDocx(): void { const qs = this.recapQs(); if (qs) this.runExport('recap', '/api/reports/menage/recap-taches/export.docx', 'recap-taches.docx', qs); }
  exportRecapPdf(): void { const qs = this.recapQs(); if (qs) this.runExport('recap', '/api/reports/menage/recap-taches/export.pdf', 'recap-taches.pdf', qs); }

  // ── Exports Charge ──
  private chargeQs(): Record<string, string> | null {
    const { from, to } = this.filters.charge;
    return from && to ? { from, to } : null;
  }
  exportChargeXlsx(): void { const qs = this.chargeQs(); if (qs) this.runExport('charge', '/api/reports/menage/charge-personnel/export.xlsx', 'charge-personnel.xlsx', qs); }
  exportChargeDocx(): void { const qs = this.chargeQs(); if (qs) this.runExport('charge', '/api/reports/menage/charge-personnel/export.docx', 'charge-personnel.docx', qs); }
  exportChargePdf(): void { const qs = this.chargeQs(); if (qs) this.runExport('charge', '/api/reports/menage/charge-personnel/export.pdf', 'charge-personnel.pdf', qs); }

  private runExport(tab: TabKey, path: string, filename: string, qs: Record<string, string>): void {
    this.exporting[tab] = true;
    this.cdr.markForCheck();
    this.downloader.download(path, filename, qs)
      .pipe(takeUntil(this.destroy$),
        finalize(() => { this.exporting[tab] = false; this.cdr.markForCheck(); }))
      .subscribe({
        next: () => undefined,
        error: (err) => Swal.fire({
          icon: 'error',
          title: this.i18n.translate('reporting.exportError', 'Erreur d\'export'),
          text: typeof err?.message === 'string' ? err.message : '',
        }),
      });
  }

  private handleError(err: unknown, tab: TabKey) {
    this.states[tab] = 'error';
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
