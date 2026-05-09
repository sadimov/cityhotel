import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import {
  ApiResponse,
  Client,
  ClientCreate,
  ClientUpdate,
  PageRequest,
  PageResponse,
  StatistiquesClient,
} from '../models/client.model';

/**
 * Service HTTP du module `clients`.
 *
 * Endpoints (backend Spring Boot, contexte `/citybackend`) :
 *   - `GET    /api/clients`           — liste paginée (filtre hôtel implicite via JWT)
 *   - `GET    /api/clients/{id}`      — détail
 *   - `POST   /api/clients`           — création
 *   - `PUT    /api/clients/{id}`      — mise à jour
 *   - `DELETE /api/clients/{id}`      — suppression / désactivation
 *
 * ⚠️ Le `hotelId` n'est **JAMAIS** envoyé par le client — le backend l'extrait
 *    du JWT (CLAUDE.md racine §6.1, cityfrontend/CLAUDE.md §4.1).
 */
@Injectable({ providedIn: 'root' })
export class ClientsService {
  private readonly base = `${environment.apiUrl}/api/clients`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Récupère un client par son identifiant.
   */
  findById(id: number): Observable<Client> {
    return this.http
      .get<ApiResponse<Client>>(`${this.base}/${id}`)
      .pipe(map((r) => r.data as Client));
  }

  /**
   * Liste paginée des clients de l'hôtel courant (filtre tenant côté serveur).
   */
  page(req: PageRequest = {}): Observable<PageResponse<Client>> {
    const params = this.toHttpParams(req);
    return this.http
      .get<ApiResponse<PageResponse<Client>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<Client>));
  }

  /**
   * Création d'un client.
   */
  create(dto: ClientCreate): Observable<Client> {
    return this.http
      .post<ApiResponse<Client>>(this.base, dto)
      .pipe(map((r) => r.data as Client));
  }

  /**
   * Mise à jour d'un client existant.
   */
  update(id: number, dto: ClientUpdate): Observable<Client> {
    return this.http
      .put<ApiResponse<Client>>(`${this.base}/${id}`, dto)
      .pipe(map((r) => r.data as Client));
  }

  /**
   * Suppression / désactivation d'un client.
   */
  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.base}/${id}`)
      .pipe(map(() => undefined));
  }

  /**
   * Statistiques agrégées clients/sociétés de l'hôtel courant.
   */
  statistiques(): Observable<StatistiquesClient> {
    return this.http
      .get<ApiResponse<StatistiquesClient>>(`${this.base}/statistiques`)
      .pipe(map((r) => r.data as StatistiquesClient));
  }

  // ────────────────────────────────────────────────────────────────────────
  // Helpers
  // ────────────────────────────────────────────────────────────────────────

  private toHttpParams(req: PageRequest): HttpParams {
    let params = new HttpParams()
      .set('page', String(req.page ?? 0))
      .set('size', String(req.size ?? 10))
      .set('sortBy', req.sortBy ?? 'nom')
      .set('sortDir', req.sortDir ?? 'asc');
    const recherche = req.recherche?.trim();
    if (recherche) {
      params = params.set('recherche', recherche);
    }
    return params;
  }
}
