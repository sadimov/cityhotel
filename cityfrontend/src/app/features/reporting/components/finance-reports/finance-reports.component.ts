import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import {
  CARecapDto,
  EncoursClientDto,
  ReportPeriode,
  TopSocieteDto,
  TvaGroupBy,
  TvaRecapDto,
} from '../../models/finance-reports.model';
import { FinanceReportsService } from '../../services/finance-reports.service';
import { ReportsDownloadService } from '../../services/reports-download.service';

type TabKey = 'ca' | 'encours' | 'tva' | 'topSocietes';
type SectionState = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Page reporting — Finance. 4 onglets pour R-FIN-001..004.
 * Pattern aligné sur HebergementReportsComponent.
 */
@Component({
  selector: 'app-finance-reports',
  templateUrl: './finance-reports.component.html',
  styleUrls: ['./finance-reports.component.scss'],
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FinanceReportsComponent implements OnInit, OnDestroy {
  activeTab: TabKey = 'ca';

  filters = {
    ca: { periode: 'SEMAINE' as ReportPeriode, from: '', to: '' },
    encours: { reference: '' },
    tva: { from: '', to: '', groupBy: 'MOIS' as TvaGroupBy },
    topSocietes: { from: '', to: '', limit: 10 },
  };

  states: Record<TabKey, SectionState> = {
    ca: 'idle', encours: 'idle', tva: 'idle', topSocietes: 'idle',
  };
  exporting: Record<TabKey, boolean> = {
    ca: false, encours: false, tva: false, topSocietes: false,
  };

  ca: CARecapDto | null = null;
  encours: EncoursClientDto | null = null;
  tva: TvaRecapDto | null = null;
  topSocietes: TopSocieteDto[] = [];

  readonly periodes: ReportPeriode[] = ['JOUR', 'SEMAINE', 'MOIS', 'TRIMESTRE', 'ANNEE'];
  readonly tvaGroupOptions: TvaGroupBy[] = ['MOIS', 'TAUX'];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly api: FinanceReportsService,
    private readonly downloader: ReportsDownloadService,
    private readonly i18n: TranslationService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const today = this.toIsoDate(new Date());
    const monthAgo = this.toIsoDate(new Date(Date.now() - 30 * 24 * 3600 * 1000));
    this.filters.ca.from = monthAgo;
    this.filters.ca.to = today;
    this.filters.encours.reference = today;
    this.filters.tva.from = monthAgo;
    this.filters.tva.to = today;
    this.filters.topSocietes.from = monthAgo;
    this.filters.topSocietes.to = today;
    this.loadCa();
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
    switch (tab) {
      case 'ca':          return this.loadCa();
      case 'encours':     return this.loadEncours();
      case 'tva':         return this.loadTva();
      case 'topSocietes': return this.loadTopSocietes();
    }
  }

  private loadCa(): void {
    this.states.ca = 'loading';
    this.cdr.markForCheck();
    const { periode, from, to } = this.filters.ca;
    this.api.getCA(periode, from || undefined, to || undefined)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'ca')))
      .subscribe((dto) => {
        if (!dto) return;
        this.ca = dto;
        this.states.ca = 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadEncours(): void {
    this.states.encours = 'loading';
    this.cdr.markForCheck();
    const { reference } = this.filters.encours;
    this.api.getEncoursClients(reference || undefined)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'encours')))
      .subscribe((dto) => {
        if (!dto) return;
        this.encours = dto;
        this.states.encours = 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadTva(): void {
    const { from, to, groupBy } = this.filters.tva;
    if (!from || !to) return;
    this.states.tva = 'loading';
    this.cdr.markForCheck();
    this.api.getTvaRecap(from, to, groupBy)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'tva')))
      .subscribe((dto) => {
        if (!dto) return;
        this.tva = dto;
        this.states.tva = 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadTopSocietes(): void {
    const { from, to, limit } = this.filters.topSocietes;
    if (!from || !to) return;
    this.states.topSocietes = 'loading';
    this.cdr.markForCheck();
    this.api.getTopSocietes(from, to, limit)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'topSocietes')))
      .subscribe((data) => {
        this.topSocietes = data ?? [];
        this.states.topSocietes = 'ready';
        this.cdr.markForCheck();
      });
  }

  // ── Exports CA ──
  private caQs(): Record<string, string> {
    return {
      periode: this.filters.ca.periode,
      ...(this.filters.ca.from ? { from: this.filters.ca.from } : {}),
      ...(this.filters.ca.to ? { to: this.filters.ca.to } : {}),
    };
  }
  exportCaXlsx(): void { this.runExport('ca', '/api/reports/ca/export.xlsx', 'ca-recap.xlsx', this.caQs()); }
  exportCaDocx(): void { this.runExport('ca', '/api/reports/ca/export.docx', 'ca-recap.docx', this.caQs()); }
  exportCaPdf(): void { this.runExport('ca', '/api/reports/ca/export.pdf', 'ca-recap.pdf', this.caQs()); }

  // ── Exports Encours ──
  private encoursQs(): Record<string, string> {
    return this.filters.encours.reference ? { reference: this.filters.encours.reference } : {};
  }
  exportEncoursXlsx(): void { this.runExport('encours', '/api/reports/finance/encours-clients/export.xlsx', 'encours-clients.xlsx', this.encoursQs()); }
  exportEncoursDocx(): void { this.runExport('encours', '/api/reports/finance/encours-clients/export.docx', 'encours-clients.docx', this.encoursQs()); }
  exportEncoursPdf(): void { this.runExport('encours', '/api/reports/finance/encours-clients/export.pdf', 'encours-clients.pdf', this.encoursQs()); }

  // ── Exports TVA ──
  private tvaQs(): Record<string, string> | null {
    const { from, to, groupBy } = this.filters.tva;
    if (!from || !to) return null;
    return { from, to, groupBy };
  }
  exportTvaXlsx(): void { const qs = this.tvaQs(); if (qs) this.runExport('tva', '/api/reports/finance/tva-recap/export.xlsx', 'tva-recap.xlsx', qs); }
  exportTvaDocx(): void { const qs = this.tvaQs(); if (qs) this.runExport('tva', '/api/reports/finance/tva-recap/export.docx', 'tva-recap.docx', qs); }
  exportTvaPdf(): void { const qs = this.tvaQs(); if (qs) this.runExport('tva', '/api/reports/finance/tva-recap/export.pdf', 'tva-recap.pdf', qs); }

  // ── Exports Top sociétés ──
  private topQs(): Record<string, string> | null {
    const { from, to, limit } = this.filters.topSocietes;
    if (!from || !to) return null;
    return { from, to, limit: String(limit) };
  }
  exportTopXlsx(): void { const qs = this.topQs(); if (qs) this.runExport('topSocietes', '/api/reports/finance/top-societes/export.xlsx', 'top-societes.xlsx', qs); }
  exportTopDocx(): void { const qs = this.topQs(); if (qs) this.runExport('topSocietes', '/api/reports/finance/top-societes/export.docx', 'top-societes.docx', qs); }
  exportTopPdf(): void { const qs = this.topQs(); if (qs) this.runExport('topSocietes', '/api/reports/finance/top-societes/export.pdf', 'top-societes.pdf', qs); }

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
