import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { FiltresHotels, Hotel } from '../models/hotel.admin.model';

/**
 * Service HTTP — Administration des hôtels (SUPERADMIN uniquement).
 *
 * Endpoints (cf. consigne Tour 31) :
 *   - GET    /api/admin/hotels                       — page (search, filtre actif)
 *   - GET    /api/admin/hotels/{id}                  — read
 *   - POST   /api/admin/hotels                       — create
 *   - PUT    /api/admin/hotels/{id}                  — update (code immutable serveur)
 *   - POST   /api/admin/hotels/{id}/desactiver       — soft delete
 *   - POST   /api/admin/hotels/{id}/reactiver        — restauration
 *
 * ⚠️ Aucun `hotelId` envoyé dans le body : la route est SUPERADMIN-only,
 * gardée par `SuperAdminGuard` côté front + `@PreAuthorize("hasRole('SUPERADMIN')")`
 * côté back. Le tenant context côté serveur est mis en bypass (sentinel
 * ROOT) pour ces opérations cross-hotel.
 */
@Injectable({ providedIn: 'root' })
export class HotelsAdminService {
  private readonly base = `${environment.apiUrl}/api/admin/hotels`;

  constructor(private readonly http: HttpClient) {}

  page(
    filtres: FiltresHotels = {},
    page = 0,
    size = 10,
    sortBy = 'nom',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<Hotel>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (filtres.search && filtres.search.trim()) {
      params = params.set('search', filtres.search.trim());
    }
    if (filtres.actif !== undefined) {
      params = params.set('actif', String(filtres.actif));
    }
    return this.http
      .get<ApiResponse<PageResponse<Hotel>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<Hotel>));
  }

  findById(id: number): Observable<Hotel> {
    return this.http
      .get<ApiResponse<Hotel>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as Hotel));
  }

  create(dto: Hotel): Observable<Hotel> {
    return this.http
      .post<ApiResponse<Hotel>>(this.base, dto)
      .pipe(map((r) => r.data as Hotel));
  }

  update(id: number, dto: Hotel): Observable<Hotel> {
    return this.http
      .put<ApiResponse<Hotel>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as Hotel));
  }

  desactiver(id: number): Observable<Hotel> {
    return this.http
      .post<ApiResponse<Hotel>>(`${this.base}/${id}/desactiver`, {})
      .pipe(map((r) => r.data as Hotel));
  }

  reactiver(id: number): Observable<Hotel> {
    return this.http
      .post<ApiResponse<Hotel>>(`${this.base}/${id}/reactiver`, {})
      .pipe(map((r) => r.data as Hotel));
  }
}
