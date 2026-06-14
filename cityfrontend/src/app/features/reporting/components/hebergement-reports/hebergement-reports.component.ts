import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subject } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import { of } from 'rxjs';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import {
  AlosDto,
  AlosGroupBy,
  KpiReceptionDto,
  NoShowGroupBy,
  NoShowRateDto,
  OccupationDto,
  ReportPeriode,
  ReservationSourceDto,
} from '../../models/hebergement-reports.model';
import { HebergementReportsService } from '../../services/hebergement-reports.service';
import { ReportsDownloadService } from '../../services/reports-download.service';

type TabKey = 'occupation' | 'alos' | 'noshow' | 'sources' | 'kpi';
type SectionState = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Page reporting — Hébergement (pilote refonte consultation + export Tour 51ter).
 *
 * 5 onglets pour les rapports R-HEB-001..005. Chaque onglet expose :
 *  - une barre de filtres (dates, dimensions de groupage selon le rapport)
 *  - des boutons d'export (XLSX et/ou PDF) à droite des filtres
 *  - un tableau de consultation des données JSON retournées par le backend
 *
 * UX :
 *  - L'utilisateur charge un onglet → bouton "Actualiser" déclenche le GET
 *  - À chaque chargement réussi, les boutons d'export deviennent disponibles
 *  - Erreurs : SweetAlert avec le message backend (i18n côté serveur)
 */
@Component({
  selector: 'app-hebergement-reports',
  templateUrl: './hebergement-reports.component.html',
  styleUrls: ['./hebergement-reports.component.scss'],
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HebergementReportsComponent implements OnInit, OnDestroy {
  /** Onglet actif (clé locale, navigation via clic). */
  activeTab: TabKey = 'occupation';

  // ── Filtres ─────────────────────────────────────────────────────────────
  /** Date range par défaut : 30 derniers jours pour les rapports plage. */
  filters = {
    occupation: { periode: 'JOUR' as ReportPeriode, from: '', to: '' },
    alos: { from: '', to: '', groupBy: 'TYPE_CHAMBRE' as AlosGroupBy },
    noshow: { from: '', to: '', groupBy: 'JOUR' as NoShowGroupBy },
    sources: { from: '', to: '' },
    kpi: { date: '' },
  };

  // ── État + données ───────────────────────────────────────────────────────
  states: Record<TabKey, SectionState> = {
    occupation: 'idle',
    alos: 'idle',
    noshow: 'idle',
    sources: 'idle',
    kpi: 'idle',
  };
  exporting: Record<TabKey, boolean> = {
    occupation: false,
    alos: false,
    noshow: false,
    sources: false,
    kpi: false,
  };

  occupation: OccupationDto | null = null;
  alos: AlosDto | null = null;
  noshow: NoShowRateDto | null = null;
  sources: ReservationSourceDto | null = null;
  kpi: KpiReceptionDto | null = null;

  /** Options des selects (énum). */
  readonly periodes: ReportPeriode[] = ['JOUR', 'SEMAINE', 'MOIS', 'TRIMESTRE', 'ANNEE'];
  readonly alosGroupOptions: AlosGroupBy[] = ['TYPE_CHAMBRE', 'MOIS'];
  readonly noShowGroupOptions: NoShowGroupBy[] = ['JOUR', 'SEMAINE', 'MOIS'];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly api: HebergementReportsService,
    private readonly downloader: ReportsDownloadService,
    private readonly i18n: TranslationService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const today = this.toIsoDate(new Date());
    const monthAgo = this.toIsoDate(new Date(Date.now() - 30 * 24 * 3600 * 1000));
    this.filters.occupation.from = monthAgo;
    this.filters.occupation.to = today;
    this.filters.alos.from = monthAgo;
    this.filters.alos.to = today;
    this.filters.noshow.from = monthAgo;
    this.filters.noshow.to = today;
    this.filters.sources.from = monthAgo;
    this.filters.sources.to = today;
    this.filters.kpi.date = today;
    // Premier chargement automatique du tab par défaut
    this.loadOccupation();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ── Navigation tabs ─────────────────────────────────────────────────────

  selectTab(tab: TabKey): void {
    this.activeTab = tab;
    // Auto-charge si pas encore demandé
    if (this.states[tab] === 'idle') {
      this.refresh(tab);
    }
  }

  refresh(tab: TabKey = this.activeTab): void {
    switch (tab) {
      case 'occupation': return this.loadOccupation();
      case 'alos':       return this.loadAlos();
      case 'noshow':     return this.loadNoshow();
      case 'sources':    return this.loadSources();
      case 'kpi':        return this.loadKpi();
    }
  }

  // ── Chargements ─────────────────────────────────────────────────────────

  private loadOccupation(): void {
    this.states.occupation = 'loading';
    this.cdr.markForCheck();
    const { periode, from, to } = this.filters.occupation;
    this.api.getOccupation(periode, from || undefined, to || undefined)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'occupation')))
      .subscribe((dto) => {
        if (!dto) return;
        this.occupation = dto;
        this.states.occupation = 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadAlos(): void {
    const { from, to, groupBy } = this.filters.alos;
    if (!from || !to) return;
    this.states.alos = 'loading';
    this.cdr.markForCheck();
    this.api.getAlos(from, to, groupBy)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'alos')))
      .subscribe((dto) => {
        if (!dto) return;
        this.alos = dto;
        this.states.alos = 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadNoshow(): void {
    const { from, to, groupBy } = this.filters.noshow;
    if (!from || !to) return;
    this.states.noshow = 'loading';
    this.cdr.markForCheck();
    this.api.getNoShowRate(from, to, groupBy)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'noshow')))
      .subscribe((dto) => {
        if (!dto) return;
        this.noshow = dto;
        this.states.noshow = 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadSources(): void {
    const { from, to } = this.filters.sources;
    if (!from || !to) return;
    this.states.sources = 'loading';
    this.cdr.markForCheck();
    this.api.getSources(from, to)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'sources')))
      .subscribe((dto) => {
        if (!dto) return;
        this.sources = dto;
        this.states.sources = 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadKpi(): void {
    const { date } = this.filters.kpi;
    if (!date) return;
    this.states.kpi = 'loading';
    this.cdr.markForCheck();
    this.api.getKpiReception(date)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'kpi')))
      .subscribe((dto) => {
        if (!dto) return;
        this.kpi = dto;
        this.states.kpi = 'ready';
        this.cdr.markForCheck();
      });
  }

  // ── Exports ─────────────────────────────────────────────────────────────

  private occupationQs(): Record<string, string> {
    return {
      periode: this.filters.occupation.periode,
      ...(this.filters.occupation.from ? { from: this.filters.occupation.from } : {}),
      ...(this.filters.occupation.to ? { to: this.filters.occupation.to } : {}),
    };
  }
  exportOccupationXlsx(): void {
    this.runExport('occupation', '/api/reports/occupation/export.xlsx', 'occupation.xlsx', this.occupationQs());
  }
  exportOccupationDocx(): void {
    this.runExport('occupation', '/api/reports/occupation/export.docx', 'occupation.docx', this.occupationQs());
  }
  exportOccupationPdf(): void {
    this.runExport('occupation', '/api/reports/occupation/export.pdf', 'occupation.pdf', this.occupationQs());
  }

  exportAlosXlsx(): void {
    const { from, to, groupBy } = this.filters.alos;
    if (!from || !to) return;
    this.runExport('alos', '/api/reports/hebergement/alos/export.xlsx', 'alos.xlsx',
      { from, to, groupBy });
  }
  exportAlosDocx(): void {
    const { from, to, groupBy } = this.filters.alos;
    if (!from || !to) return;
    this.runExport('alos', '/api/reports/hebergement/alos/export.docx', 'alos.docx',
      { from, to, groupBy });
  }
  exportAlosPdf(): void {
    const { from, to, groupBy } = this.filters.alos;
    if (!from || !to) return;
    this.runExport('alos', '/api/reports/hebergement/alos/export.pdf', 'alos.pdf',
      { from, to, groupBy });
  }

  exportNoshowXlsx(): void {
    const { from, to, groupBy } = this.filters.noshow;
    if (!from || !to) return;
    this.runExport('noshow', '/api/reports/hebergement/no-show-rate/export.xlsx',
      'no-show-rate.xlsx', { from, to, groupBy });
  }
  exportNoshowDocx(): void {
    const { from, to, groupBy } = this.filters.noshow;
    if (!from || !to) return;
    this.runExport('noshow', '/api/reports/hebergement/no-show-rate/export.docx',
      'no-show-rate.docx', { from, to, groupBy });
  }
  exportNoshowPdf(): void {
    const { from, to, groupBy } = this.filters.noshow;
    if (!from || !to) return;
    this.runExport('noshow', '/api/reports/hebergement/no-show-rate/export.pdf',
      'no-show-rate.pdf', { from, to, groupBy });
  }

  exportSourcesXlsx(): void {
    const { from, to } = this.filters.sources;
    if (!from || !to) return;
    this.runExport('sources', '/api/reports/hebergement/sources/export.xlsx',
      'sources-reservations.xlsx', { from, to });
  }

  exportSourcesDocx(): void {
    const { from, to } = this.filters.sources;
    if (!from || !to) return;
    this.runExport('sources', '/api/reports/hebergement/sources/export.docx',
      'sources-reservations.docx', { from, to });
  }

  exportSourcesPdf(): void {
    const { from, to } = this.filters.sources;
    if (!from || !to) return;
    this.runExport('sources', '/api/reports/hebergement/sources/export.pdf',
      'sources-reservations.pdf', { from, to });
  }

  exportKpiXlsx(): void {
    const { date } = this.filters.kpi;
    if (!date) return;
    this.runExport('kpi', '/api/reports/hebergement/kpi-reception/export.xlsx',
      'kpi-reception.xlsx', { date });
  }
  exportKpiDocx(): void {
    const { date } = this.filters.kpi;
    if (!date) return;
    this.runExport('kpi', '/api/reports/hebergement/kpi-reception/export.docx',
      'kpi-reception.docx', { date });
  }
  exportKpiPdf(): void {
    const { date } = this.filters.kpi;
    if (!date) return;
    this.runExport('kpi', '/api/reports/hebergement/kpi-reception/export.pdf',
      'kpi-reception.pdf', { date });
  }

  private runExport(tab: TabKey, path: string, filename: string, qs: Record<string, string>): void {
    this.exporting[tab] = true;
    this.cdr.markForCheck();
    this.downloader.download(path, filename, qs)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.exporting[tab] = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: () => undefined,
        error: (err) => Swal.fire({
          icon: 'error',
          title: this.i18n.translate('reporting.exportError', 'Erreur d\'export'),
          text: typeof err?.message === 'string' ? err.message : '',
        }),
      });
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  private handleError(err: unknown, tab: TabKey) {
    this.states[tab] = 'error';
    this.cdr.markForCheck();
    Swal.fire({
      icon: 'error',
      title: this.i18n.translate('reporting.loadError', 'Erreur de chargement du rapport'),
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
