import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { FiltresMouvements, MouvementStock } from '../models/stock.model';

/**
 * Service HTTP — Mouvements de stock (lecture seule).
 *
 * Vu côté front "stocks" : journal des mouvements + vue par produit.
 * La photo instantanée du stock courant passe par `ProduitsService.page()`
 * (qui inclut `stockActuel` + `statutStock` calculé serveur).
 *
 * Spec FROZEN :
 *   - GET /api/inventory/mouvements                       — page + filtres
 *   - GET /api/inventory/mouvements/produit/{produitId}   — historique produit
 *
 * ⚠️ `hotelId` jamais transmis (JWT côté serveur).
 */
@Injectable({ providedIn: 'root' })
export class StocksService {
  private readonly base = `${environment.apiUrl}/api/inventory/mouvements`;

  constructor(private readonly http: HttpClient) {}

  pageMouvements(
    filtres: FiltresMouvements = {},
    page = 0,
    size = 20,
    sortBy = 'dateMouvement',
    sortDir: 'asc' | 'desc' = 'desc',
  ): Observable<PageResponse<MouvementStock>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (filtres.produitId != null) {
      params = params.set('produitId', String(filtres.produitId));
    }
    if (filtres.typeMouvement) params = params.set('typeMouvement', filtres.typeMouvement);
    if (filtres.dateDebut) params = params.set('dateDebut', filtres.dateDebut);
    if (filtres.dateFin) params = params.set('dateFin', filtres.dateFin);

    return this.http
      .get<ApiResponse<PageResponse<MouvementStock>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<MouvementStock>));
  }

  pageMouvementsProduit(
    produitId: number,
    page = 0,
    size = 20,
  ): Observable<PageResponse<MouvementStock>> {
    const params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));
    return this.http
      .get<ApiResponse<PageResponse<MouvementStock>>>(
        `${this.base}/produit/${produitId}`,
        { params },
      )
      .pipe(map((r) => r.data as PageResponse<MouvementStock>));
  }
}
