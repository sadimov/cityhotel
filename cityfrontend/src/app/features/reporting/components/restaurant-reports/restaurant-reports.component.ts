import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import {
  JournalCaisseDto,
  TicketMarginDto,
  TopArticleDto,
} from '../../models/restaurant-reports.model';
import { RestaurantReportsService } from '../../services/restaurant-reports.service';
import { ReportsDownloadService } from '../../services/reports-download.service';

type TabKey = 'journal' | 'topArticles' | 'ticket';
type SectionState = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Page reporting — Restaurant. 3 onglets pour R-RES-001..003.
 */
@Component({
  selector: 'app-restaurant-reports',
  templateUrl: './restaurant-reports.component.html',
  styleUrls: ['./restaurant-reports.component.scss'],
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RestaurantReportsComponent implements OnInit, OnDestroy {
  activeTab: TabKey = 'journal';

  filters = {
    journal: { date: '' },
    topArticles: { from: '', to: '', limit: 20 },
    ticket: { from: '', to: '' },
  };

  states: Record<TabKey, SectionState> = {
    journal: 'idle', topArticles: 'idle', ticket: 'idle',
  };
  exporting: Record<TabKey, boolean> = {
    journal: false, topArticles: false, ticket: false,
  };

  journal: JournalCaisseDto | null = null;
  topArticles: TopArticleDto | null = null;
  ticket: TicketMarginDto | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly api: RestaurantReportsService,
    private readonly downloader: ReportsDownloadService,
    private readonly i18n: TranslationService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const today = this.toIsoDate(new Date());
    const monthAgo = this.toIsoDate(new Date(Date.now() - 30 * 24 * 3600 * 1000));
    this.filters.journal.date = today;
    this.filters.topArticles.from = monthAgo;
    this.filters.topArticles.to = today;
    this.filters.ticket.from = monthAgo;
    this.filters.ticket.to = today;
    this.loadJournal();
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
      case 'journal':     return this.loadJournal();
      case 'topArticles': return this.loadTopArticles();
      case 'ticket':      return this.loadTicket();
    }
  }

  private loadJournal(): void {
    const { date } = this.filters.journal;
    if (!date) return;
    this.states.journal = 'loading';
    this.cdr.markForCheck();
    this.api.getJournalCaisse(date)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'journal')))
      .subscribe((dto) => {
        if (!dto) return;
        this.journal = dto;
        this.states.journal = 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadTopArticles(): void {
    const { from, to, limit } = this.filters.topArticles;
    if (!from || !to) return;
    this.states.topArticles = 'loading';
    this.cdr.markForCheck();
    this.api.getTopArticles(from, to, limit)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'topArticles')))
      .subscribe((dto) => {
        if (!dto) return;
        this.topArticles = dto;
        this.states.topArticles = 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadTicket(): void {
    const { from, to } = this.filters.ticket;
    if (!from || !to) return;
    this.states.ticket = 'loading';
    this.cdr.markForCheck();
    this.api.getTicketMoyen(from, to)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'ticket')))
      .subscribe((dto) => {
        if (!dto) return;
        this.ticket = dto;
        this.states.ticket = 'ready';
        this.cdr.markForCheck();
      });
  }

  // ── Exports Journal ──
  private journalQs(): Record<string, string> | null {
    return this.filters.journal.date ? { date: this.filters.journal.date } : null;
  }
  exportJournalXlsx(): void { const qs = this.journalQs(); if (qs) this.runExport('journal', '/api/reports/restaurant/journal-caisse/export.xlsx', 'journal-caisse.xlsx', qs); }
  exportJournalDocx(): void { const qs = this.journalQs(); if (qs) this.runExport('journal', '/api/reports/restaurant/journal-caisse/export.docx', 'journal-caisse.docx', qs); }
  exportJournalPdf(): void { const qs = this.journalQs(); if (qs) this.runExport('journal', '/api/reports/restaurant/journal-caisse/export.pdf', 'journal-caisse.pdf', qs); }

  // ── Exports Top articles ──
  private topQs(): Record<string, string> | null {
    const { from, to, limit } = this.filters.topArticles;
    if (!from || !to) return null;
    return { from, to, limit: String(limit) };
  }
  exportTopXlsx(): void { const qs = this.topQs(); if (qs) this.runExport('topArticles', '/api/reports/restaurant/top-articles/export.xlsx', 'top-articles.xlsx', qs); }
  exportTopDocx(): void { const qs = this.topQs(); if (qs) this.runExport('topArticles', '/api/reports/restaurant/top-articles/export.docx', 'top-articles.docx', qs); }
  exportTopPdf(): void { const qs = this.topQs(); if (qs) this.runExport('topArticles', '/api/reports/restaurant/top-articles/export.pdf', 'top-articles.pdf', qs); }

  // ── Exports Ticket moyen ──
  private ticketQs(): Record<string, string> | null {
    const { from, to } = this.filters.ticket;
    return from && to ? { from, to } : null;
  }
  exportTicketXlsx(): void { const qs = this.ticketQs(); if (qs) this.runExport('ticket', '/api/reports/restaurant/ticket-moyen/export.xlsx', 'ticket-moyen.xlsx', qs); }
  exportTicketDocx(): void { const qs = this.ticketQs(); if (qs) this.runExport('ticket', '/api/reports/restaurant/ticket-moyen/export.docx', 'ticket-moyen.docx', qs); }
  exportTicketPdf(): void { const qs = this.ticketQs(); if (qs) this.runExport('ticket', '/api/reports/restaurant/ticket-moyen/export.pdf', 'ticket-moyen.pdf', qs); }

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
