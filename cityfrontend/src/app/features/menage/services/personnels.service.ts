import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { FiltresPersonnels, Personnel } from '../models/personnel.model';

/**
 * Service HTTP — Personnel de ménage.
 *
 * Spec (cf. `MENAGE/endpoints_module_menage.txt` §Personnel) :
 *   - GET    /api/menage/personnel                          — page (search, filtres)
 *   - GET    /api/menage/personnel/actifs                   — liste des actifs
 *   - GET    /api/menage/personnel/disponibles?date={iso}   — disponibles à une date
 *   - GET    /api/menage/personnel/specialite/{specialite}  — par spécialité
 *   - GET    /api/menage/personnel/rechercher?terme={t}     — recherche textuelle
 *   - GET    /api/menage/personnel/{id}                     — read
 *   - GET    /api/menage/personnel/numero/{numeroEmploye}   — read par n° employé
 *   - POST   /api/menage/personnel                          — create
 *   - PUT    /api/menage/personnel/{id}                     — update
 *   - PUT    /api/menage/personnel/{id}/desactiver          — soft delete
 *   - PUT    /api/menage/personnel/{id}/reactiver           — restauration
 *
 * ⚠️ `hotelId` n'est JAMAIS transmis (JWT côté serveur, cf. CLAUDE.md §6.1).
 *
 * ⚠️ Divergence potentielle backend (à vérifier avec l'agent backend
 * du tour parallèle) : `endpoints_module_menage.txt` documente les
 * routes sous `/menage/personnel` ; on suppose le préfixe global `/api`
 * appliqué par la config Spring (cohérent avec les autres modules :
 * `/api/clients`, `/api/restaurant/articles`, `/api/finance/factures`).
 */
@Injectable({ providedIn: 'root' })
export class PersonnelsService {
  private readonly base = `${environment.apiUrl}/api/menage/personnel`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Pagination du personnel via le format Spring standard `sort=field,dir`.
   *
   * <b>Sous-tour menage D2 (fix alignement backend) :</b>
   *  - L'endpoint backend `GET /api/menage/personnel` accepte le {@code Pageable}
   *    Spring standard (`page`, `size`, `sort=field,dir`) — pas de
   *    `sortBy`/`sortDir` séparés (qui étaient silencieusement ignorés).
   *  - Pour les filtres `search` et `specialite`, le backend expose des
   *    endpoints dédiés (`/rechercher?terme=`, `/specialite/{code}`).
   *    Ici on les passe en query params seulement si l'endpoint principal
   *    venait à les supporter à l'avenir ; en l'état actuel ils sont
   *    silencieusement ignorés côté backend (V1 acceptable). Pour un
   *    filtrage strict, utiliser `search()` / `findBySpecialite()`.
   */
  page(
    filtres: FiltresPersonnels = {},
    page = 0,
    size = 10,
    sortBy = 'nom',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<Personnel>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sort', `${sortBy},${sortDir}`); // Spring Pageable standard
    if (filtres.actif !== undefined) {
      params = params.set('actif', String(filtres.actif));
    }
    if (filtres.search && filtres.search.trim()) {
      params = params.set('search', filtres.search.trim());
    }
    if (filtres.specialite) {
      params = params.set('specialite', filtres.specialite);
    }
    return this.http
      .get<ApiResponse<PageResponse<Personnel>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<Personnel>));
  }

  /** Liste des personnels actifs (sélecteurs, dropdowns d'assignation). */
  findActifs(): Observable<Personnel[]> {
    return this.http
      .get<ApiResponse<Personnel[]>>(`${this.base}/actifs`)
      .pipe(map((r) => r.data ?? []));
  }

  /**
   * Personnels disponibles à une date donnée (croisement planning + actif).
   * Si `date` est omise, le backend retombe sur `aujourd'hui` (cf. spec).
   */
  findDisponibles(date?: string): Observable<Personnel[]> {
    let params = new HttpParams();
    if (date) {
      params = params.set('date', date);
    }
    return this.http
      .get<ApiResponse<Personnel[]>>(`${this.base}/disponibles`, { params })
      .pipe(map((r) => r.data ?? []));
  }

  findById(id: number): Observable<Personnel> {
    return this.http
      .get<ApiResponse<Personnel>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as Personnel));
  }

  findByNumero(numeroEmploye: string): Observable<Personnel> {
    return this.http
      .get<ApiResponse<Personnel>>(`${this.base}/numero/${encodeURIComponent(numeroEmploye)}`)
      .pipe(map((r) => r.data as Personnel));
  }

  create(dto: Personnel): Observable<Personnel> {
    return this.http
      .post<ApiResponse<Personnel>>(this.base, dto)
      .pipe(map((r) => r.data as Personnel));
  }

  update(id: number, dto: Personnel): Observable<Personnel> {
    return this.http
      .put<ApiResponse<Personnel>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as Personnel));
  }

  /**
   * Soft delete — `actif=false`. Pas de DELETE physique côté backend
   * pour préserver l'historique (cf. spec `endpoints_module_menage.txt`
   * §Personnel.Gestion du Statut).
   */
  desactiver(id: number): Observable<Personnel> {
    return this.http
      .put<ApiResponse<Personnel>>(`${this.base}/${id}/desactiver`, {})
      .pipe(map((r) => r.data as Personnel));
  }

  reactiver(id: number): Observable<Personnel> {
    return this.http
      .put<ApiResponse<Personnel>>(`${this.base}/${id}/reactiver`, {})
      .pipe(map((r) => r.data as Personnel));
  }
}
