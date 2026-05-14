import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import {
  FiltresServicesHoteliers,
  ServiceHotelier,
} from '../models/service-hotelier.model';

/**
 * Service HTTP — Services hôteliers (module inventory, Tour 51 Phase A).
 *
 * Spec :
 *   - GET    /api/inventory/services-hoteliers              — page + filtres
 *   - GET    /api/inventory/services-hoteliers/actifs       — liste pour selects
 *   - GET    /api/inventory/services-hoteliers/{id}         — read
 *   - POST   /api/inventory/services-hoteliers              — create
 *   - PUT    /api/inventory/services-hoteliers/{id}         — update
 *   - DELETE /api/inventory/services-hoteliers/{id}         — delete (soft)
 *
 * ⚠️ `hotelId` jamais transmis (JWT côté serveur).
 */
@Injectable({ providedIn: 'root' })
export class ServicesHoteliersService {
  private readonly base = `${environment.apiUrl}/api/inventory/services-hoteliers`;

  constructor(private readonly http: HttpClient) {}

  page(
    filtres: FiltresServicesHoteliers = {},
    page = 0,
    size = 10,
    sortBy = 'nomService',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<ServiceHotelier>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (filtres.search && filtres.search.trim()) {
      params = params.set('search', filtres.search.trim());
    }
    if (filtres.typeServiceId != null) {
      params = params.set('typeServiceId', String(filtres.typeServiceId));
    }
    if (filtres.actif != null) {
      params = params.set('actif', String(filtres.actif));
    }
    return this.http
      .get<ApiResponse<PageResponse<ServiceHotelier>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<ServiceHotelier>));
  }

  findActifs(): Observable<ServiceHotelier[]> {
    return this.http
      .get<ApiResponse<ServiceHotelier[]>>(`${this.base}/actifs`)
      .pipe(map((r) => r.data ?? []));
  }

  findById(id: number): Observable<ServiceHotelier> {
    return this.http
      .get<ApiResponse<ServiceHotelier>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as ServiceHotelier));
  }

  create(dto: ServiceHotelier): Observable<ServiceHotelier> {
    return this.http
      .post<ApiResponse<ServiceHotelier>>(this.base, dto)
      .pipe(map((r) => r.data as ServiceHotelier));
  }

  update(id: number, dto: ServiceHotelier): Observable<ServiceHotelier> {
    return this.http
      .put<ApiResponse<ServiceHotelier>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as ServiceHotelier));
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }
}
