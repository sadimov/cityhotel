import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

interface ReportEntry {
  code: string;
  labelKey: string;
  endpoint: string;
}

const REPORTS_BY_MODULE: Record<string, ReportEntry[]> = {
  hebergement: [
    { code: 'R-HEB-001', labelKey: 'reporting.hebergement.r001', endpoint: '/api/reports/hebergement/occupation-quotidienne' },
    { code: 'R-HEB-002', labelKey: 'reporting.hebergement.r002', endpoint: '/api/reports/hebergement/revpar' },
    { code: 'R-HEB-003', labelKey: 'reporting.hebergement.r003', endpoint: '/api/reports/hebergement/adr' },
    { code: 'R-HEB-004', labelKey: 'reporting.hebergement.r004', endpoint: '/api/reports/hebergement/sources' },
    { code: 'R-HEB-005', labelKey: 'reporting.hebergement.r005', endpoint: '/api/reports/hebergement/alos' },
  ],
  finance: [
    { code: 'R-FIN-001', labelKey: 'reporting.finance.r001', endpoint: '/api/reports/finance/journal-ventes' },
    { code: 'R-FIN-002', labelKey: 'reporting.finance.r002', endpoint: '/api/reports/finance/encaissements' },
    { code: 'R-FIN-003', labelKey: 'reporting.finance.r003', endpoint: '/api/reports/finance/clients-debiteurs' },
    { code: 'R-FIN-004', labelKey: 'reporting.finance.r004', endpoint: '/api/reports/finance/balance-ages' },
  ],
  inventory: [
    { code: 'R-INV-001', labelKey: 'reporting.inventory.r001', endpoint: '/api/reports/inventory/stock-actuel' },
    { code: 'R-INV-002', labelKey: 'reporting.inventory.r002', endpoint: '/api/reports/inventory/valorisation' },
    { code: 'R-INV-003', labelKey: 'reporting.inventory.r003', endpoint: '/api/reports/inventory/rotation' },
  ],
  restaurant: [
    { code: 'R-RES-001', labelKey: 'reporting.restaurant.r001', endpoint: '/api/reports/restaurant/ventes' },
    { code: 'R-RES-002', labelKey: 'reporting.restaurant.r002', endpoint: '/api/reports/restaurant/top-articles' },
    { code: 'R-RES-003', labelKey: 'reporting.restaurant.r003', endpoint: '/api/reports/restaurant/services-pousses' },
  ],
  menage: [
    { code: 'R-MEN-001', labelKey: 'reporting.menage.r001', endpoint: '/api/reports/menage/productivite' },
    { code: 'R-MEN-002', labelKey: 'reporting.menage.r002', endpoint: '/api/reports/menage/retards' },
  ],
  direction: [
    { code: 'R-DIR-001', labelKey: 'reporting.direction.r001', endpoint: '/api/reports/direction/synthese' },
  ],
};

@Component({
  selector: 'app-reporting-home',
  templateUrl: './reporting-home.component.html',
  styleUrls: ['./reporting-home.component.scss'],
  standalone: false,
})
export class ReportingHomeComponent implements OnInit {
  module = '';
  reports: ReportEntry[] = [];

  constructor(private readonly route: ActivatedRoute) {}

  ngOnInit(): void {
    this.route.data.subscribe((data) => {
      this.module = (data['module'] as string) || 'hebergement';
      this.reports = REPORTS_BY_MODULE[this.module] || [];
    });
  }
}
