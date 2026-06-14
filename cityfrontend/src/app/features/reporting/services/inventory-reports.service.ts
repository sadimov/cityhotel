import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse } from '../../../shared/models/api.model';
import {
  BcPendantDto,
  MouvementValoriseDto,
  RotationProduitDto,
  StockAlertDto,
  TypeMouvementStock,
} from '../models/inventory-reports.model';

/**
 * Service HTTP — consultation JSON des rapports R-INV-001..003.
 *
 * Endpoints :
 *   - GET /api/reports/stock-alerts                           (R-INV-001)
 *   - GET /api/reports/inventory/mouvements-valorises         (R-INV-002)
 *   - GET /api/reports/inventory/bc-pendants                  (R-INV-003a)
 *   - GET /api/reports/inventory/rotation-produits            (R-INV-003b)
 */
@Injectable({ providedIn: 'root' })
export class InventoryReportsService {
  private readonly base = `${environment.apiUrl}/api/reports`;

  constructor(private readonly http: HttpClient) {}

  getStockAlerts(): Observable<StockAlertDto[]> {
    return this.http
      .get<ApiResponse<StockAlertDto[]>>(`${this.base}/stock-alerts`)
      .pipe(map((r) => r.data ?? []));
  }

  getMouvements(from: string, to: string, type?: TypeMouvementStock): Observable<MouvementValoriseDto> {
    let params = new HttpParams().set('from', from).set('to', to);
    if (type) params = params.set('type', type);
    return this.http
      .get<ApiResponse<MouvementValoriseDto>>(`${this.base}/inventory/mouvements-valorises`, { params })
      .pipe(map((r) => r.data as MouvementValoriseDto));
  }

  getBcPendants(): Observable<BcPendantDto[]> {
    return this.http
      .get<ApiResponse<BcPendantDto[]>>(`${this.base}/inventory/bc-pendants`)
      .pipe(map((r) => r.data ?? []));
  }

  getRotation(from: string, to: string): Observable<RotationProduitDto[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http
      .get<ApiResponse<RotationProduitDto[]>>(`${this.base}/inventory/rotation-produits`, { params })
      .pipe(map((r) => r.data ?? []));
  }
}
