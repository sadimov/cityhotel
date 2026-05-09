import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { TypeChambre } from '../models/type-chambre.model';

/**
 * Service HTTP — Types de chambres.
 *
 * Spec API FROZEN (Tour audit hebergement B1+B2, 2026-05-07) :
 *   - `GET    /api/hebergement/types-chambres`             — liste paginée
 *   - `GET    /api/hebergement/types-chambres/active`      — liste des actifs
 *   - `GET    /api/hebergement/types-chambres/{id}`        — détail
 *   - `POST   /api/hebergement/types-chambres`             — création
 *   - `PUT    /api/hebergement/types-chambres/{id}`        — mise à jour
 *   - `POST   /api/hebergement/types-chambres/{id}/deactivate`
 *   - `POST   /api/hebergement/types-chambres/{id}/reactivate`
 *
 * ⚠️ Le `hotelId` n'est **JAMAIS** envoyé par le client — extraction JWT côté
 *    backend (CLAUDE.md racine §6.1).
 */
@Injectable({ providedIn: 'root' })
export class TypesChambreService {
  private readonly base = `${environment.apiUrl}/api/hebergement/types-chambres`;

  constructor(private readonly http: HttpClient) {}

  findById(typeId: number): Observable<TypeChambre> {
    return this.http
      .get<ApiResponse<TypeChambre>>(`${this.base}/${typeId}`)
      .pipe(map((r) => r.data as TypeChambre));
  }

  /** Liste des types de chambres actifs (sans pagination — usage select). */
  findActifs(): Observable<TypeChambre[]> {
    return this.http
      .get<ApiResponse<TypeChambre[]>>(`${this.base}/active`)
      .pipe(map((r) => r.data ?? []));
  }

  /**
   * Liste paginée des types de chambres.
   */
  page(
    page = 0,
    size = 10,
    actif?: boolean,
    sortBy = 'typeNom',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<TypeChambre>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sort', `${sortBy},${sortDir}`);
    if (actif !== undefined) {
      params = params.set('actif', String(actif));
    }
    return this.http
      .get<ApiResponse<PageResponse<TypeChambre>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<TypeChambre>));
  }

  create(dto: TypeChambre): Observable<TypeChambre> {
    return this.http
      .post<ApiResponse<TypeChambre>>(this.base, dto)
      .pipe(map((r) => r.data as TypeChambre));
  }

  update(typeId: number, dto: TypeChambre): Observable<TypeChambre> {
    return this.http
      .put<ApiResponse<TypeChambre>>(`${this.base}/${typeId}`, dto)
      .pipe(map((r) => r.data as TypeChambre));
  }

  /** Désactivation logique — POST /{id}/deactivate (spec FROZEN). */
  desactiver(typeId: number): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/${typeId}/deactivate`, {})
      .pipe(map(() => undefined));
  }

  /** Réactivation logique — POST /{id}/reactivate (spec FROZEN). */
  reactiver(typeId: number): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/${typeId}/reactivate`, {})
      .pipe(map(() => undefined));
  }
}
