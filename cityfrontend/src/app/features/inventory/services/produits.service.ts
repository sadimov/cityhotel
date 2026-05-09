import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import {
  AjustementStock,
  FiltresProduits,
  Produit,
} from '../models/produit.model';

/**
 * Service HTTP — Produits (module inventory).
 *
 * Spec FROZEN :
 *   - GET    /api/inventory/produits              — page + filtres
 *   - GET    /api/inventory/produits/actifs       — liste produits actifs (selects)
 *   - GET    /api/inventory/produits/alertes      — produits en alerte (stockActuel <= seuilAlerte)
 *   - GET    /api/inventory/produits/critiques    — produits en stock critique
 *   - GET    /api/inventory/produits/{id}         — read
 *   - POST   /api/inventory/produits              — create
 *   - PUT    /api/inventory/produits/{id}         — update
 *   - DELETE /api/inventory/produits/{id}         — delete (soft)
 *   - POST   /api/inventory/produits/{id}/ajuster-stock — ajustement manuel
 *
 * ⚠️ `hotelId` jamais transmis (JWT côté serveur).
 */
@Injectable({ providedIn: 'root' })
export class ProduitsService {
  private readonly base = `${environment.apiUrl}/api/inventory/produits`;

  constructor(private readonly http: HttpClient) {}

  page(
    filtres: FiltresProduits = {},
    page = 0,
    size = 10,
    sortBy = 'nomProduit',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<Produit>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (filtres.search && filtres.search.trim()) {
      params = params.set('search', filtres.search.trim());
    }
    if (filtres.categorieId != null) {
      params = params.set('categorieId', String(filtres.categorieId));
    }
    if (filtres.statutStock) {
      params = params.set('statutStock', filtres.statutStock);
    }
    return this.http
      .get<ApiResponse<PageResponse<Produit>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<Produit>));
  }

  findActifs(): Observable<Produit[]> {
    return this.http
      .get<ApiResponse<Produit[]>>(`${this.base}/actifs`)
      .pipe(map((r) => r.data ?? []));
  }

  findEnAlerte(): Observable<Produit[]> {
    return this.http
      .get<ApiResponse<Produit[]>>(`${this.base}/alertes`)
      .pipe(map((r) => r.data ?? []));
  }

  findEnStockCritique(): Observable<Produit[]> {
    return this.http
      .get<ApiResponse<Produit[]>>(`${this.base}/critiques`)
      .pipe(map((r) => r.data ?? []));
  }

  findById(id: number): Observable<Produit> {
    return this.http
      .get<ApiResponse<Produit>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as Produit));
  }

  create(dto: Produit): Observable<Produit> {
    return this.http
      .post<ApiResponse<Produit>>(this.base, dto)
      .pipe(map((r) => r.data as Produit));
  }

  update(id: number, dto: Produit): Observable<Produit> {
    return this.http
      .put<ApiResponse<Produit>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as Produit));
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }

  ajusterStock(id: number, dto: AjustementStock): Observable<Produit> {
    return this.http
      .post<ApiResponse<Produit>>(`${this.base}/${id}/ajuster-stock`, dto)
      .pipe(map((r) => r.data as Produit));
  }
}
