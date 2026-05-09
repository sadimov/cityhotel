import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { CategorieProduit } from '../models/categorie.model';

/**
 * Service HTTP — Catégories de produits (module inventory).
 *
 * Spec FROZEN :
 *   - GET    /api/inventory/categories            — page (search, sort)
 *   - GET    /api/inventory/categories/actives    — liste des catégories actives
 *   - GET    /api/inventory/categories/{id}       — read
 *   - POST   /api/inventory/categories            — create
 *   - PUT    /api/inventory/categories/{id}       — update
 *   - DELETE /api/inventory/categories/{id}       — delete (soft)
 *
 * ⚠️ `hotelId` jamais transmis (JWT côté serveur).
 */
@Injectable({ providedIn: 'root' })
export class CategoriesService {
  private readonly base = `${environment.apiUrl}/api/inventory/categories`;

  constructor(private readonly http: HttpClient) {}

  page(
    search?: string,
    page = 0,
    size = 10,
    sortBy = 'nomCategorie',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<CategorieProduit>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (search && search.trim()) {
      params = params.set('search', search.trim());
    }
    return this.http
      .get<ApiResponse<PageResponse<CategorieProduit>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<CategorieProduit>));
  }

  findActives(): Observable<CategorieProduit[]> {
    return this.http
      .get<ApiResponse<CategorieProduit[]>>(`${this.base}/actives`)
      .pipe(map((r) => r.data ?? []));
  }

  findById(id: number): Observable<CategorieProduit> {
    return this.http
      .get<ApiResponse<CategorieProduit>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as CategorieProduit));
  }

  create(dto: CategorieProduit): Observable<CategorieProduit> {
    return this.http
      .post<ApiResponse<CategorieProduit>>(this.base, dto)
      .pipe(map((r) => r.data as CategorieProduit));
  }

  update(id: number, dto: CategorieProduit): Observable<CategorieProduit> {
    return this.http
      .put<ApiResponse<CategorieProduit>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as CategorieProduit));
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }
}
