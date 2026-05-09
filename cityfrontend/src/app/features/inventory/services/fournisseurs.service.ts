import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { Fournisseur } from '../models/fournisseur.model';

/**
 * Service HTTP — Fournisseurs (module inventory).
 *
 * Spec FROZEN par INVENTORY/endpoints_module_inventory.txt :
 *   - GET    /api/inventory/fournisseurs               — page (search, sort)
 *   - GET    /api/inventory/fournisseurs/actifs        — liste des actifs (pour selects)
 *   - GET    /api/inventory/fournisseurs/{id}          — read
 *   - POST   /api/inventory/fournisseurs               — create
 *   - PUT    /api/inventory/fournisseurs/{id}          — update
 *   - DELETE /api/inventory/fournisseurs/{id}          — delete (soft)
 *
 * ⚠️ `hotelId`, `userId` jamais transmis — JWT côté serveur.
 */
@Injectable({ providedIn: 'root' })
export class FournisseursService {
  private readonly base = `${environment.apiUrl}/api/inventory/fournisseurs`;

  constructor(private readonly http: HttpClient) {}

  page(
    search?: string,
    page = 0,
    size = 10,
    sortBy = 'nomFournisseur',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<Fournisseur>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (search && search.trim()) {
      params = params.set('search', search.trim());
    }
    return this.http
      .get<ApiResponse<PageResponse<Fournisseur>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<Fournisseur>));
  }

  findActifs(): Observable<Fournisseur[]> {
    return this.http
      .get<ApiResponse<Fournisseur[]>>(`${this.base}/actifs`)
      .pipe(map((r) => r.data ?? []));
  }

  findById(id: number): Observable<Fournisseur> {
    return this.http
      .get<ApiResponse<Fournisseur>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as Fournisseur));
  }

  create(dto: Fournisseur): Observable<Fournisseur> {
    return this.http
      .post<ApiResponse<Fournisseur>>(this.base, dto)
      .pipe(map((r) => r.data as Fournisseur));
  }

  update(id: number, dto: Fournisseur): Observable<Fournisseur> {
    return this.http
      .put<ApiResponse<Fournisseur>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as Fournisseur));
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }
}
