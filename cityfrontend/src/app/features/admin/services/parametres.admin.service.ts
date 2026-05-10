import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { FiltresParametres, Parametre } from '../models/parametre.admin.model';

/**
 * Service HTTP — Paramètres globaux (SUPERADMIN uniquement).
 *
 * Endpoints (cf. consigne Tour 31) :
 *   - GET    /api/admin/parametres                       — page (search, filtre catégorie/modifiable)
 *   - GET    /api/admin/parametres/{id}                  — read par id
 *   - GET    /api/admin/parametres/by-cle/{cle}          — read par clé
 *   - GET    /api/admin/parametres/by-categorie/{cat}    — liste d'une catégorie
 *   - POST   /api/admin/parametres                       — create
 *   - PUT    /api/admin/parametres/{id}                  — update (refus 400 si modifiable=false)
 *   - DELETE /api/admin/parametres/{id}                  — delete (uniquement si modifiable=true)
 */
@Injectable({ providedIn: 'root' })
export class ParametresAdminService {
  private readonly base = `${environment.apiUrl}/api/admin/parametres`;

  constructor(private readonly http: HttpClient) {}

  page(
    filtres: FiltresParametres = {},
    page = 0,
    size = 50,
    sortBy = 'cle',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<Parametre>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (filtres.search && filtres.search.trim()) {
      params = params.set('search', filtres.search.trim());
    }
    if (filtres.categorie) {
      params = params.set('categorie', filtres.categorie);
    }
    if (filtres.modifiable !== undefined) {
      params = params.set('modifiable', String(filtres.modifiable));
    }
    return this.http
      .get<ApiResponse<PageResponse<Parametre>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<Parametre>));
  }

  findById(id: number): Observable<Parametre> {
    return this.http
      .get<ApiResponse<Parametre>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as Parametre));
  }

  findByCle(cle: string): Observable<Parametre> {
    return this.http
      .get<ApiResponse<Parametre>>(`${this.base}/by-cle/${encodeURIComponent(cle)}`)
      .pipe(map((r) => r.data as Parametre));
  }

  findByCategorie(categorie: string): Observable<Parametre[]> {
    return this.http
      .get<ApiResponse<Parametre[]>>(`${this.base}/by-categorie/${encodeURIComponent(categorie)}`)
      .pipe(map((r) => r.data ?? []));
  }

  create(dto: Parametre): Observable<Parametre> {
    return this.http
      .post<ApiResponse<Parametre>>(this.base, dto)
      .pipe(map((r) => r.data as Parametre));
  }

  /**
   * Update — le serveur refuse avec 400 si le paramètre a `modifiable=false`.
   * Côté UI on désactive le formulaire en amont pour ne pas envoyer la requête.
   */
  update(id: number, dto: Parametre): Observable<Parametre> {
    return this.http
      .put<ApiResponse<Parametre>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as Parametre));
  }

  /**
   * Delete — accessible uniquement si `modifiable=true`. Le bouton est
   * masqué côté UI sinon, et le serveur refusera 400 par sécurité.
   */
  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }
}
