import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import {
  FiltresTypesServices,
  TypeServiceHotelier,
} from '../models/type-service-hotelier.model';

/**
 * Service HTTP — Types de services hôteliers (module inventory, Tour 51 Phase A).
 *
 * Spec :
 *   - GET    /api/inventory/types-services-hoteliers          — page (search, sort)
 *   - GET    /api/inventory/types-services-hoteliers/actifs   — liste pour selects
 *   - GET    /api/inventory/types-services-hoteliers/{id}     — read
 *   - POST   /api/inventory/types-services-hoteliers          — create
 *   - PUT    /api/inventory/types-services-hoteliers/{id}     — update
 *   - DELETE /api/inventory/types-services-hoteliers/{id}     — delete (soft)
 *
 * ⚠️ `hotelId` jamais transmis (JWT côté serveur).
 */
@Injectable({ providedIn: 'root' })
export class TypesServicesHoteliersService {
  private readonly base = `${environment.apiUrl}/api/inventory/types-services-hoteliers`;

  constructor(private readonly http: HttpClient) {}

  page(
    filtres: FiltresTypesServices = {},
    page = 0,
    size = 10,
    sortBy = 'nomType',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<TypeServiceHotelier>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (filtres.search && filtres.search.trim()) {
      params = params.set('search', filtres.search.trim());
    }
    if (filtres.actif != null) {
      params = params.set('actif', String(filtres.actif));
    }
    return this.http
      .get<ApiResponse<PageResponse<TypeServiceHotelier>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<TypeServiceHotelier>));
  }

  findActifs(): Observable<TypeServiceHotelier[]> {
    return this.http
      .get<ApiResponse<TypeServiceHotelier[]>>(`${this.base}/actifs`)
      .pipe(map((r) => r.data ?? []));
  }

  findById(id: number): Observable<TypeServiceHotelier> {
    return this.http
      .get<ApiResponse<TypeServiceHotelier>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as TypeServiceHotelier));
  }

  create(dto: TypeServiceHotelier): Observable<TypeServiceHotelier> {
    return this.http
      .post<ApiResponse<TypeServiceHotelier>>(this.base, dto)
      .pipe(map((r) => r.data as TypeServiceHotelier));
  }

  update(id: number, dto: TypeServiceHotelier): Observable<TypeServiceHotelier> {
    return this.http
      .put<ApiResponse<TypeServiceHotelier>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as TypeServiceHotelier));
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }
}
