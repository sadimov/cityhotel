import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import {
  CategorieMenu,
  FiltresCategoriesMenus,
} from '../models/categorie-menu.model';

/**
 * Service HTTP — Catégories de menu (module restaurant, catalogue).
 *
 * Spec (alignée Tours 16/19) :
 *   - GET    /api/restaurant/categories             — page (search, sort)
 *   - GET    /api/restaurant/categories/actives     — liste actives (sélecteurs)
 *   - GET    /api/restaurant/categories/{id}        — read
 *   - POST   /api/restaurant/categories             — create
 *   - PUT    /api/restaurant/categories/{id}        — update
 *   - DELETE /api/restaurant/categories/{id}        — delete (soft)
 *   - PUT    /api/restaurant/categories/{id}/ordre  — modifier l'ordre d'affichage
 *
 * ⚠️ `hotelId` n'est JAMAIS transmis (JWT côté serveur).
 *
 * Note : la spec d'origine `restaurant-api.service.ts` exposait des
 * endpoints non paginés (List<CategorieMenuDto>). On aligne ici sur la
 * convention multi-tenant uniforme du projet (PageResponse + ApiResponse)
 * pour cohérence avec `inventory.categories` et `finance.factures`.
 */
@Injectable({ providedIn: 'root' })
export class CategoriesMenusService {
  private readonly base = `${environment.apiUrl}/api/restaurant/categories`;

  constructor(private readonly http: HttpClient) {}

  page(
    filtres: FiltresCategoriesMenus = {},
    page = 0,
    size = 10,
    sortBy = 'ordreAffichage',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<CategorieMenu>> {
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
      .get<ApiResponse<PageResponse<CategorieMenu>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<CategorieMenu>));
  }

  findActives(): Observable<CategorieMenu[]> {
    return this.http
      .get<ApiResponse<CategorieMenu[]>>(`${this.base}/actives`)
      .pipe(map((r) => r.data ?? []));
  }

  findById(id: number): Observable<CategorieMenu> {
    return this.http
      .get<ApiResponse<CategorieMenu>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as CategorieMenu));
  }

  create(dto: CategorieMenu): Observable<CategorieMenu> {
    return this.http
      .post<ApiResponse<CategorieMenu>>(this.base, dto)
      .pipe(map((r) => r.data as CategorieMenu));
  }

  update(id: number, dto: CategorieMenu): Observable<CategorieMenu> {
    return this.http
      .put<ApiResponse<CategorieMenu>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as CategorieMenu));
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }

  /**
   * Met à jour l'ordre d'affichage d'une catégorie. Préparé pour un
   * éventuel drag&drop côté UI (Tour 24+) — l'endpoint reste léger.
   */
  updateOrdre(id: number, ordreAffichage: number): Observable<CategorieMenu> {
    return this.http
      .put<ApiResponse<CategorieMenu>>(`${this.base}/${id}/ordre`, { ordreAffichage })
      .pipe(map((r) => r.data as CategorieMenu));
  }
}
