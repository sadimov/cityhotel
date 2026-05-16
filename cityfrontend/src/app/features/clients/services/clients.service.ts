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
import { Societe, SocieteCreate } from '../models/societe.model';

/**
 * Service HTTP du module `clients`.
 *
 * Endpoints (backend Spring Boot, contexte `/citybackend`) :
 *   - `GET    /api/clients`           — liste paginée clients (filtre hôtel JWT)
 *   - `GET    /api/clients/{id}`      — détail
 *   - `POST   /api/clients`           — création
 *   - `PUT    /api/clients/{id}`      — mise à jour
 *   - `DELETE /api/clients/{id}`      — suppression
 *   - `GET    /api/societes`          — liste paginée sociétés
 *   - `GET    /api/societes/{id}`     — détail société
 *   - `POST   /api/societes`          — création société
 *   - `PUT    /api/societes/{id}`     — mise à jour société
 *   - `POST   /api/societes/{id}/deactivate` — désactivation soft
 *   - `POST   /api/societes/{id}/reactivate` — réactivation
 *   - `DELETE /api/societes/{id}`     — suppression hard
 *
 * ⚠️ Le `hotelId` n'est **JAMAIS** envoyé — backend l'extrait du JWT.
 */
@Injectable({ providedIn: 'root' })
export class ClientsService {
  private readonly base = `${environment.apiUrl}/api/clients`;
  private readonly baseSocietes = `${environment.apiUrl}/api/societes`;

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
  // Sociétés — base sur `/api/societes` (SocieteController)
  // ────────────────────────────────────────────────────────────────────────

  /** Liste paginée des sociétés de l'hôtel courant. */
  pageSocietes(req: PageRequest = {}): Observable<PageResponse<Societe>> {
    const params = this.toHttpParams(req);
    return this.http
      .get<ApiResponse<PageResponse<Societe>>>(this.baseSocietes, { params })
      .pipe(map((r) => r.data as PageResponse<Societe>));
  }

  /** Détail société par ID. */
  findSocieteById(id: number): Observable<Societe> {
    return this.http
      .get<ApiResponse<Societe>>(`${this.baseSocietes}/${id}`)
      .pipe(map((r) => r.data as Societe));
  }

  /** Création d'une société. */
  createSociete(dto: SocieteCreate): Observable<Societe> {
    return this.http
      .post<ApiResponse<Societe>>(this.baseSocietes, dto)
      .pipe(map((r) => r.data as Societe));
  }

  /** Mise à jour d'une société. */
  updateSociete(id: number, dto: SocieteCreate): Observable<Societe> {
    return this.http
      .put<ApiResponse<Societe>>(`${this.baseSocietes}/${id}`, dto)
      .pipe(map((r) => r.data as Societe));
  }

  /** Désactivation (soft delete) — pose actif=false. */
  deactivateSociete(id: number): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.baseSocietes}/${id}/deactivate`, {})
      .pipe(map(() => undefined));
  }

  /** Réactivation — pose actif=true. */
  reactivateSociete(id: number): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.baseSocietes}/${id}/reactivate`, {})
      .pipe(map(() => undefined));
  }

  /** Suppression définitive (hard delete) — refusée si des clients sont rattachés. */
  deleteSociete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.baseSocietes}/${id}`)
      .pipe(map(() => undefined));
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
