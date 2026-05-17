import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import Swal from 'sweetalert2';

import { TranslationService } from '../../../../services/translation.service';
import { ReportsDownloadService } from '../../services/reports-download.service';

/** Format d'export disponible côté backend pour un rapport donné. */
type ExportFormat = 'xlsx' | 'pdf';

interface ReportExport {
  format: ExportFormat;
  /** Path absolu API à passer à `ReportsDownloadService.download()`. */
  path: string;
  /** Nom de fichier proposé au navigateur. */
  filename: string;
}

interface ReportEntry {
  /** Code rapport (`R-HEB-001`, ...) — purement informatif côté UI. */
  code: string;
  /** Clé i18n du titre court (ex. `reporting.hebergement.r001`). */
  labelKey: string;
  /** Clé i18n d'une description courte (ex. `reporting.hebergement.r001Desc`). */
  descKey: string;
  /** Liste des exports disponibles. Vide = pas encore d'export téléchargeable. */
  exports: ReportExport[];
}

/**
 * Catalogue aligné sur les vrais endpoints backend (audit 2026-05-17) :
 *
 *   - `HebergementReportController`  : /alos, /no-show-rate, /sources, /kpi-reception
 *   - `FinanceReportController`      : /encours-clients, /tva-recap, /top-societes
 *   - `InventoryReportController`    : /mouvements-valorises, /bc-pendants, /rotation-produits
 *   - `RestaurantReportController`   : /journal-caisse, /top-articles, /ticket-moyen
 *   - `MenageReportController`       : /recap-taches, /charge-personnel
 *   - `DirectionReportController`    : /dashboard (JSON only — pas d'export)
 *   - `ReportController` (générique) : /occupation, /ca, /stock-alerts, /night-audit, /top-clients
 *
 * Les rapports sans `exports` affichent une carte informative sans bouton
 * téléchargement (vue détaillée à venir dans une itération ultérieure).
 */
const REPORTS_BY_MODULE: Record<string, ReportEntry[]> = {
  hebergement: [
    {
      code: 'R-HEB-001',
      labelKey: 'reporting.hebergement.r001',
      descKey: 'reporting.hebergement.r001Desc',
      exports: [{ format: 'pdf', path: '/api/reports/occupation/export.pdf', filename: 'occupation.pdf' }],
    },
    {
      code: 'R-HEB-002',
      labelKey: 'reporting.hebergement.r002',
      descKey: 'reporting.hebergement.r002Desc',
      exports: [{ format: 'xlsx', path: '/api/reports/hebergement/no-show-rate/export.xlsx', filename: 'no-show-rate.xlsx' }],
    },
    {
      code: 'R-HEB-003',
      labelKey: 'reporting.hebergement.r003',
      descKey: 'reporting.hebergement.r003Desc',
      exports: [{ format: 'pdf', path: '/api/reports/hebergement/kpi-reception/export.pdf', filename: 'kpi-reception.pdf' }],
    },
    {
      code: 'R-HEB-004',
      labelKey: 'reporting.hebergement.r004',
      descKey: 'reporting.hebergement.r004Desc',
      exports: [{ format: 'xlsx', path: '/api/reports/hebergement/sources/export.xlsx', filename: 'sources.xlsx' }],
    },
    {
      code: 'R-HEB-005',
      labelKey: 'reporting.hebergement.r005',
      descKey: 'reporting.hebergement.r005Desc',
      exports: [{ format: 'xlsx', path: '/api/reports/hebergement/alos/export.xlsx', filename: 'alos.xlsx' }],
    },
  ],
  finance: [
    {
      code: 'R-FIN-001',
      labelKey: 'reporting.finance.r001',
      descKey: 'reporting.finance.r001Desc',
      exports: [{ format: 'xlsx', path: '/api/reports/ca/export.xlsx', filename: 'chiffre-affaires.xlsx' }],
    },
    {
      code: 'R-FIN-002',
      labelKey: 'reporting.finance.r002',
      descKey: 'reporting.finance.r002Desc',
      exports: [{ format: 'xlsx', path: '/api/reports/finance/encours-clients/export.xlsx', filename: 'encours-clients.xlsx' }],
    },
    {
      code: 'R-FIN-003',
      labelKey: 'reporting.finance.r003',
      descKey: 'reporting.finance.r003Desc',
      exports: [{ format: 'xlsx', path: '/api/reports/finance/tva-recap/export.xlsx', filename: 'tva-recap.xlsx' }],
    },
    {
      code: 'R-FIN-004',
      labelKey: 'reporting.finance.r004',
      descKey: 'reporting.finance.r004Desc',
      exports: [{ format: 'xlsx', path: '/api/reports/finance/top-societes/export.xlsx', filename: 'top-societes.xlsx' }],
    },
  ],
  inventory: [
    {
      code: 'R-INV-001',
      labelKey: 'reporting.inventory.r001',
      descKey: 'reporting.inventory.r001Desc',
      exports: [{ format: 'xlsx', path: '/api/reports/stock-alerts/export.xlsx', filename: 'stock-alerts.xlsx' }],
    },
    {
      code: 'R-INV-002',
      labelKey: 'reporting.inventory.r002',
      descKey: 'reporting.inventory.r002Desc',
      exports: [{ format: 'xlsx', path: '/api/reports/inventory/mouvements-valorises/export.xlsx', filename: 'mouvements-valorises.xlsx' }],
    },
    {
      code: 'R-INV-003',
      labelKey: 'reporting.inventory.r003',
      descKey: 'reporting.inventory.r003Desc',
      exports: [{ format: 'xlsx', path: '/api/reports/inventory/rotation-produits/export.xlsx', filename: 'rotation-produits.xlsx' }],
    },
  ],
  restaurant: [
    {
      code: 'R-RES-001',
      labelKey: 'reporting.restaurant.r001',
      descKey: 'reporting.restaurant.r001Desc',
      exports: [
        { format: 'pdf', path: '/api/reports/restaurant/journal-caisse/export.pdf', filename: 'journal-caisse.pdf' },
        { format: 'xlsx', path: '/api/reports/restaurant/journal-caisse/export.xlsx', filename: 'journal-caisse.xlsx' },
      ],
    },
    {
      code: 'R-RES-002',
      labelKey: 'reporting.restaurant.r002',
      descKey: 'reporting.restaurant.r002Desc',
      exports: [{ format: 'xlsx', path: '/api/reports/restaurant/top-articles/export.xlsx', filename: 'top-articles.xlsx' }],
    },
    {
      code: 'R-RES-003',
      labelKey: 'reporting.restaurant.r003',
      descKey: 'reporting.restaurant.r003Desc',
      exports: [{ format: 'xlsx', path: '/api/reports/restaurant/ticket-moyen/export.xlsx', filename: 'ticket-moyen.xlsx' }],
    },
  ],
  menage: [
    {
      code: 'R-MEN-001',
      labelKey: 'reporting.menage.r001',
      descKey: 'reporting.menage.r001Desc',
      exports: [{ format: 'xlsx', path: '/api/reports/menage/recap-taches/export.xlsx', filename: 'recap-taches.xlsx' }],
    },
    {
      code: 'R-MEN-002',
      labelKey: 'reporting.menage.r002',
      descKey: 'reporting.menage.r002Desc',
      exports: [{ format: 'xlsx', path: '/api/reports/menage/charge-personnel/export.xlsx', filename: 'charge-personnel.xlsx' }],
    },
  ],
  direction: [
    {
      code: 'R-DIR-001',
      labelKey: 'reporting.direction.r001',
      descKey: 'reporting.direction.r001Desc',
      // Dashboard direction = vue JSON consommée par le dashboard accueil
      // (carte "Revenus du Jour"). Pas d'export téléchargeable dédié.
      exports: [],
    },
  ],
};

@Component({
  selector: 'app-reporting-home',
  templateUrl: './reporting-home.component.html',
  styleUrls: ['./reporting-home.component.scss'],
  standalone: false,
})
export class ReportingHomeComponent implements OnInit, OnDestroy {
  module = '';
  reports: ReportEntry[] = [];
  downloadingPath: string | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly downloadService: ReportsDownloadService,
    private readonly i18n: TranslationService,
  ) {}

  ngOnInit(): void {
    this.route.data
      .pipe(takeUntil(this.destroy$))
      .subscribe((data) => {
        this.module = (data['module'] as string) || 'hebergement';
        this.reports = REPORTS_BY_MODULE[this.module] || [];
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  download(report: ReportEntry, ex: ReportExport): void {
    if (this.downloadingPath) return;
    this.downloadingPath = ex.path;
    this.downloadService
      .download(ex.path, ex.filename)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.downloadingPath = null;
        },
        error: () => {
          this.downloadingPath = null;
          Swal.fire({
            icon: 'error',
            title: this.i18n.translate('reporting.downloadError'),
          });
        },
      });
  }

  trackByCode(_: number, r: ReportEntry): string {
    return r.code;
  }
}
