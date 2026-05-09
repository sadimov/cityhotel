import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import {
  BonSortie,
  FiltresBonsSortie,
  LigneBonSortie,
} from '../models/bon-sortie.model';

/**
 * Service HTTP — Bons de sortie (module inventory).
 *
 * Spec FROZEN :
 *   - GET    /api/inventory/bons-sortie                       — page + filtres
 *   - GET    /api/inventory/bons-sortie/en-attente-livraison  — vue pilotage
 *   - GET    /api/inventory/bons-sortie/{id}                  — read
 *   - POST   /api/inventory/bons-sortie                       — create
 *   - PUT    /api/inventory/bons-sortie/{id}                  — update
 *   - POST   /api/inventory/bons-sortie/{id}/lignes           — ajouter ligne
 *   - PUT    /api/inventory/bons-sortie/{id}/lignes/{ligneId} — modifier
 *   - DELETE /api/inventory/bons-sortie/{id}/lignes/{ligneId} — supprimer
 *   - POST   /api/inventory/bons-sortie/{id}/valider          — valider
 *   - POST   /api/inventory/bons-sortie/{id}/livrer           — livrer (lignes)
 *   - POST   /api/inventory/bons-sortie/{id}/annuler?motif=...— annuler
 *
 * ⚠️ `hotelId`, `userId` jamais transmis (JWT côté serveur).
 */
@Injectable({ providedIn: 'root' })
export class BonsSortieService {
  private readonly base = `${environment.apiUrl}/api/inventory/bons-sortie`;

  constructor(private readonly http: HttpClient) {}

  page(
    filtres: FiltresBonsSortie = {},
    page = 0,
    size = 10,
    sortBy = 'dateCreation',
    sortDir: 'asc' | 'desc' = 'desc',
  ): Observable<PageResponse<BonSortie>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (filtres.search && filtres.search.trim()) {
      params = params.set('search', filtres.search.trim());
    }
    if (filtres.statut) params = params.set('statut', filtres.statut);
    if (filtres.destination) params = params.set('destination', filtres.destination);
    if (filtres.dateDebut) params = params.set('dateDebut', filtres.dateDebut);
    if (filtres.dateFin) params = params.set('dateFin', filtres.dateFin);

    return this.http
      .get<ApiResponse<PageResponse<BonSortie>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<BonSortie>));
  }

  findEnAttenteLivraison(): Observable<BonSortie[]> {
    return this.http
      .get<ApiResponse<BonSortie[]>>(`${this.base}/en-attente-livraison`)
      .pipe(map((r) => r.data ?? []));
  }

  findById(id: number): Observable<BonSortie> {
    return this.http
      .get<ApiResponse<BonSortie>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as BonSortie));
  }

  create(dto: BonSortie): Observable<BonSortie> {
    return this.http
      .post<ApiResponse<BonSortie>>(this.base, dto)
      .pipe(map((r) => r.data as BonSortie));
  }

  update(id: number, dto: BonSortie): Observable<BonSortie> {
    return this.http
      .put<ApiResponse<BonSortie>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as BonSortie));
  }

  ajouterLigne(bonSortieId: number, ligne: LigneBonSortie): Observable<BonSortie> {
    return this.http
      .post<ApiResponse<BonSortie>>(`${this.base}/${bonSortieId}/lignes`, ligne)
      .pipe(map((r) => r.data as BonSortie));
  }

  modifierLigne(
    bonSortieId: number,
    ligneId: number,
    ligne: LigneBonSortie,
  ): Observable<BonSortie> {
    return this.http
      .put<ApiResponse<BonSortie>>(
        `${this.base}/${bonSortieId}/lignes/${ligneId}`,
        ligne,
      )
      .pipe(map((r) => r.data as BonSortie));
  }

  supprimerLigne(bonSortieId: number, ligneId: number): Observable<BonSortie> {
    return this.http
      .delete<ApiResponse<BonSortie>>(
        `${this.base}/${bonSortieId}/lignes/${ligneId}`,
      )
      .pipe(map((r) => r.data as BonSortie));
  }

  valider(id: number): Observable<BonSortie> {
    return this.http
      .post<ApiResponse<BonSortie>>(`${this.base}/${id}/valider`, {})
      .pipe(map((r) => r.data as BonSortie));
  }

  livrer(id: number, lignes: LigneBonSortie[]): Observable<BonSortie> {
    return this.http
      .post<ApiResponse<BonSortie>>(`${this.base}/${id}/livrer`, lignes)
      .pipe(map((r) => r.data as BonSortie));
  }

  annuler(id: number, motif: string): Observable<BonSortie> {
    const params = new HttpParams().set('motifAnnulation', motif);
    return this.http
      .post<ApiResponse<BonSortie>>(`${this.base}/${id}/annuler`, {}, { params })
      .pipe(map((r) => r.data as BonSortie));
  }
}
