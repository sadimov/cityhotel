import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { Chambre, FiltresChambres, StatutChambre } from '../models/chambre.model';

/**
 * Service HTTP — Chambres.
 *
 * Spec API FROZEN (Tour audit hebergement B1+B2, 2026-05-07) :
 *   - `GET    /api/hebergement/chambres`                        — page + filtres
 *   - `GET    /api/hebergement/chambres/active`                 — liste actives (sans pagination)
 *   - `GET    /api/hebergement/chambres/{id}`                   — read
 *   - `GET    /api/hebergement/chambres/disponibles?dateDebut&dateFin`
 *   - `POST   /api/hebergement/chambres`
 *   - `PUT    /api/hebergement/chambres/{id}`
 *   - `PUT    /api/hebergement/chambres/{id}/statut?statut=...`
 *   - `POST   /api/hebergement/chambres/{id}/deactivate`
 *   - `POST   /api/hebergement/chambres/{id}/reactivate`
 *
 * ⚠️ `hotelId` n'est jamais transmis par le client (cf. CLAUDE.md racine §6.1).
 */
@Injectable({ providedIn: 'root' })
export class ChambresService {
  private readonly base = `${environment.apiUrl}/api/hebergement/chambres`;

  constructor(private readonly http: HttpClient) {}

  findById(chambreId: number): Observable<Chambre> {
    return this.http
      .get<ApiResponse<Chambre>>(`${this.base}/${chambreId}`)
      .pipe(map((r) => r.data as Chambre));
  }

  findActives(): Observable<Chambre[]> {
    return this.http
      .get<ApiResponse<Chambre[]>>(`${this.base}/active`)
      .pipe(map((r) => r.data ?? []));
  }

  /**
   * Liste paginée + filtres (filtre tenant côté serveur).
   */
  page(
    filtres: FiltresChambres = {},
    page = 0,
    size = 10,
    sortBy = 'numeroChambre',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<Chambre>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sort', `${sortBy},${sortDir}`);
    if (filtres.typeId != null) params = params.set('typeId', String(filtres.typeId));
    if (filtres.statut) params = params.set('statut', filtres.statut);
    if (filtres.etage != null) params = params.set('etage', String(filtres.etage));
    if (filtres.disponiblesSeulement) {
      params = params.set('disponiblesSeulement', 'true');
    }
    if (filtres.nbPersonnesMin != null) {
      params = params.set('nbPersonnesMin', String(filtres.nbPersonnesMin));
    }
    return this.http
      .get<ApiResponse<PageResponse<Chambre>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<Chambre>));
  }

  /**
   * Chambres disponibles pour une période donnée (clé pour le check-in /
   * recherche d'allocation). Spec FROZEN : `/disponibles?dateDebut&dateFin`.
   */
  disponibles(dateDebut: string, dateFin: string): Observable<Chambre[]> {
    const params = new HttpParams()
      .set('dateDebut', dateDebut)
      .set('dateFin', dateFin);
    return this.http
      .get<ApiResponse<Chambre[]>>(`${this.base}/disponibles`, { params })
      .pipe(map((r) => r.data ?? []));
  }

  create(dto: Chambre): Observable<Chambre> {
    return this.http
      .post<ApiResponse<Chambre>>(this.base, dto)
      .pipe(map((r) => r.data as Chambre));
  }

  update(chambreId: number, dto: Chambre): Observable<Chambre> {
    return this.http
      .put<ApiResponse<Chambre>>(`${this.base}/${chambreId}`, dto)
      .pipe(map((r) => r.data as Chambre));
  }

  changerStatut(chambreId: number, statut: StatutChambre): Observable<Chambre> {
    const params = new HttpParams().set('statut', statut);
    return this.http
      .put<ApiResponse<Chambre>>(`${this.base}/${chambreId}/statut`, {}, { params })
      .pipe(map((r) => r.data as Chambre));
  }

  /** Désactivation logique — POST /{id}/deactivate (spec FROZEN). */
  desactiver(chambreId: number): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/${chambreId}/deactivate`, {})
      .pipe(map(() => undefined));
  }

  /** Réactivation logique — POST /{id}/reactivate (spec FROZEN). */
  reactiver(chambreId: number): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/${chambreId}/reactivate`, {})
      .pipe(map(() => undefined));
  }
}
