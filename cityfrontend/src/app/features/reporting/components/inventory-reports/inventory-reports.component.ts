import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, of } from 'rxjs';
import { catchError, finalize, takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import {
  BcPendantDto,
  MouvementValoriseDto,
  RotationProduitDto,
  StockAlertDto,
  TypeMouvementStock,
} from '../../models/inventory-reports.model';
import { InventoryReportsService } from '../../services/inventory-reports.service';
import { ReportsDownloadService } from '../../services/reports-download.service';

type TabKey = 'alerts' | 'mouvements' | 'bc' | 'rotation';
type SectionState = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Page reporting — Inventory. 4 onglets pour R-INV-001..003.
 */
@Component({
  selector: 'app-inventory-reports',
  templateUrl: './inventory-reports.component.html',
  styleUrls: ['./inventory-reports.component.scss'],
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryReportsComponent implements OnInit, OnDestroy {
  activeTab: TabKey = 'alerts';

  filters = {
    mouvements: { from: '', to: '', type: '' as TypeMouvementStock | '' },
    rotation: { from: '', to: '' },
  };

  states: Record<TabKey, SectionState> = {
    alerts: 'idle', mouvements: 'idle', bc: 'idle', rotation: 'idle',
  };
  exporting: Record<TabKey, boolean> = {
    alerts: false, mouvements: false, bc: false, rotation: false,
  };

  alerts: StockAlertDto[] = [];
  mouvements: MouvementValoriseDto | null = null;
  bc: BcPendantDto[] = [];
  rotation: RotationProduitDto[] = [];

  readonly typeOptions: TypeMouvementStock[] = ['ENTREE', 'SORTIE', 'PERTE', 'AJUSTEMENT'];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly api: InventoryReportsService,
    private readonly downloader: ReportsDownloadService,
    private readonly i18n: TranslationService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const today = this.toIsoDate(new Date());
    const monthAgo = this.toIsoDate(new Date(Date.now() - 30 * 24 * 3600 * 1000));
    this.filters.mouvements.from = monthAgo;
    this.filters.mouvements.to = today;
    this.filters.rotation.from = monthAgo;
    this.filters.rotation.to = today;
    this.loadAlerts();
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
      case 'alerts':     return this.loadAlerts();
      case 'mouvements': return this.loadMouvements();
      case 'bc':         return this.loadBc();
      case 'rotation':   return this.loadRotation();
    }
  }

  private loadAlerts(): void {
    this.states.alerts = 'loading';
    this.cdr.markForCheck();
    this.api.getStockAlerts()
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'alerts')))
      .subscribe((data) => {
        this.alerts = data ?? [];
        this.states.alerts = 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadMouvements(): void {
    const { from, to, type } = this.filters.mouvements;
    if (!from || !to) return;
    this.states.mouvements = 'loading';
    this.cdr.markForCheck();
    this.api.getMouvements(from, to, type || undefined)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'mouvements')))
      .subscribe((dto) => {
        if (!dto) return;
        this.mouvements = dto;
        this.states.mouvements = 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadBc(): void {
    this.states.bc = 'loading';
    this.cdr.markForCheck();
    this.api.getBcPendants()
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'bc')))
      .subscribe((data) => {
        this.bc = data ?? [];
        this.states.bc = 'ready';
        this.cdr.markForCheck();
      });
  }

  private loadRotation(): void {
    const { from, to } = this.filters.rotation;
    if (!from || !to) return;
    this.states.rotation = 'loading';
    this.cdr.markForCheck();
    this.api.getRotation(from, to)
      .pipe(takeUntil(this.destroy$), catchError((e) => this.handleError(e, 'rotation')))
      .subscribe((data) => {
        this.rotation = data ?? [];
        this.states.rotation = 'ready';
        this.cdr.markForCheck();
      });
  }

  // ── Exports Alerts ──
  exportAlertsXlsx(): void { this.runExport('alerts', '/api/reports/stock-alerts/export.xlsx', 'alertes-stock.xlsx', {}); }
  exportAlertsDocx(): void { this.runExport('alerts', '/api/reports/stock-alerts/export.docx', 'alertes-stock.docx', {}); }
  exportAlertsPdf(): void { this.runExport('alerts', '/api/reports/stock-alerts/export.pdf', 'alertes-stock.pdf', {}); }

  // ── Exports Mouvements ──
  private mouvementsQs(): Record<string, string> | null {
    const { from, to, type } = this.filters.mouvements;
    if (!from || !to) return null;
    return type ? { from, to, type } : { from, to };
  }
  exportMouvementsXlsx(): void { const qs = this.mouvementsQs(); if (qs) this.runExport('mouvements', '/api/reports/inventory/mouvements-valorises/export.xlsx', 'mouvements-valorises.xlsx', qs); }
  exportMouvementsDocx(): void { const qs = this.mouvementsQs(); if (qs) this.runExport('mouvements', '/api/reports/inventory/mouvements-valorises/export.docx', 'mouvements-valorises.docx', qs); }
  exportMouvementsPdf(): void { const qs = this.mouvementsQs(); if (qs) this.runExport('mouvements', '/api/reports/inventory/mouvements-valorises/export.pdf', 'mouvements-valorises.pdf', qs); }

  // ── Exports BC pendants ──
  exportBcXlsx(): void { this.runExport('bc', '/api/reports/inventory/bc-pendants/export.xlsx', 'bc-pendants.xlsx', {}); }
  exportBcDocx(): void { this.runExport('bc', '/api/reports/inventory/bc-pendants/export.docx', 'bc-pendants.docx', {}); }
  exportBcPdf(): void { this.runExport('bc', '/api/reports/inventory/bc-pendants/export.pdf', 'bc-pendants.pdf', {}); }

  // ── Exports Rotation ──
  private rotationQs(): Record<string, string> | null {
    const { from, to } = this.filters.rotation;
    return from && to ? { from, to } : null;
  }
  exportRotationXlsx(): void { const qs = this.rotationQs(); if (qs) this.runExport('rotation', '/api/reports/inventory/rotation-produits/export.xlsx', 'rotation-produits.xlsx', qs); }
  exportRotationDocx(): void { const qs = this.rotationQs(); if (qs) this.runExport('rotation', '/api/reports/inventory/rotation-produits/export.docx', 'rotation-produits.docx', qs); }
  exportRotationPdf(): void { const qs = this.rotationQs(); if (qs) this.runExport('rotation', '/api/reports/inventory/rotation-produits/export.pdf', 'rotation-produits.pdf', qs); }

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
