import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api.model';
import { FiltresUsers, ResetPasswordResponse, User } from '../models/user.admin.model';

/**
 * Service HTTP — Administration des utilisateurs (SUPERADMIN uniquement).
 *
 * Endpoints (cf. consigne Tour 31) :
 *   - GET    /api/admin/users                                                     — page cross-hotel (filtres hotelId/roleCode/actif)
 *   - GET    /api/admin/hotels/{hotelId}/users                                    — page restreinte à un hôtel
 *   - GET    /api/admin/hotels/{hotelId}/users/{userId}                           — read
 *   - POST   /api/admin/hotels/{hotelId}/users                                    — create (hotelId path-positioned)
 *   - PUT    /api/admin/hotels/{hotelId}/users/{userId}                           — update
 *   - POST   /api/admin/hotels/{hotelId}/users/{userId}/verrouiller               — verrou
 *   - POST   /api/admin/hotels/{hotelId}/users/{userId}/deverrouiller             — déverrou
 *   - POST   /api/admin/hotels/{hotelId}/users/{userId}/reset-password            — génère un nouveau mdp serveur
 *   - POST   /api/admin/hotels/{hotelId}/users/{userId}/desactiver                — soft delete
 *
 * ⚠️ EXCEPTION SUPERADMIN sur `hotelId` path-positioned :
 *
 * Le principe général City Hotel veut que le client n'envoie JAMAIS de
 * `hotelId` (cf. CLAUDE.md racine §6.1 : tenant lu du JWT). Cette
 * exception est volontaire et limitée à la zone admin :
 *  - L'utilisateur de l'UI est forcément SUPERADMIN (cf. SuperAdminGuard)
 *    et son JWT autorise le bypass tenant côté serveur (sentinel ROOT).
 *  - Le `hotelId` dans l'URL identifie l'hôtel CIBLE de l'opération
 *    (à quel tenant rattacher le user créé / verrouiller le user
 *    appartenant), pas le tenant courant de l'opérateur.
 *  - Le contrôleur backend valide explicitement que `roleCode = SUPERADMIN`
 *    avant de traiter la requête (ceinture + bretelles).
 *
 * Cette exception ne doit JAMAIS être copiée dans un autre service
 * métier (clients, finance, etc.) — elle est strictement réservée
 * à la zone administration cross-tenant.
 */
@Injectable({ providedIn: 'root' })
export class UsersAdminService {
  private readonly base = `${environment.apiUrl}/api/admin`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Liste paginée cross-hotel (vue admin globale).
   * Filtres :
   *  - `hotelId` : pré-restreint à un hôtel (utilisé quand on arrive
   *    depuis l'écran hôtels via `?hotelId=`).
   *  - `roleCode` : filtre par rôle.
   *  - `actif`   : `true|false|undefined` (tous).
   */
  pageAll(
    filtres: FiltresUsers = {},
    page = 0,
    size = 10,
    sortBy = 'username',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<User>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (filtres.search && filtres.search.trim()) {
      params = params.set('search', filtres.search.trim());
    }
    if (filtres.hotelId != null) {
      params = params.set('hotelId', String(filtres.hotelId));
    }
    if (filtres.roleCode) {
      params = params.set('roleCode', filtres.roleCode);
    }
    if (filtres.actif !== undefined) {
      params = params.set('actif', String(filtres.actif));
    }
    return this.http
      .get<ApiResponse<PageResponse<User>>>(`${this.base}/users`, { params })
      .pipe(map((r) => r.data as PageResponse<User>));
  }

  /**
   * Liste paginée restreinte à un hôtel.
   * Utilisée quand on arrive depuis la fiche d'un hôtel.
   */
  pageByHotel(
    hotelId: number,
    filtres: FiltresUsers = {},
    page = 0,
    size = 10,
    sortBy = 'username',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<User>> {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortBy', sortBy)
      .set('sortDir', sortDir);
    if (filtres.search && filtres.search.trim()) {
      params = params.set('search', filtres.search.trim());
    }
    if (filtres.roleCode) {
      params = params.set('roleCode', filtres.roleCode);
    }
    if (filtres.actif !== undefined) {
      params = params.set('actif', String(filtres.actif));
    }
    return this.http
      .get<ApiResponse<PageResponse<User>>>(`${this.base}/hotels/${hotelId}/users`, { params })
      .pipe(map((r) => r.data as PageResponse<User>));
  }

  findById(hotelId: number, userId: number): Observable<User> {
    return this.http
      .get<ApiResponse<User>>(`${this.base}/hotels/${hotelId}/users/${userId}`)
      .pipe(map((r) => r.data as User));
  }

  create(hotelId: number, dto: User): Observable<User> {
    return this.http
      .post<ApiResponse<User>>(`${this.base}/hotels/${hotelId}/users`, dto)
      .pipe(map((r) => r.data as User));
  }

  update(hotelId: number, userId: number, dto: User): Observable<User> {
    return this.http
      .put<ApiResponse<User>>(`${this.base}/hotels/${hotelId}/users/${userId}`, dto)
      .pipe(map((r) => r.data as User));
  }

  verrouiller(hotelId: number, userId: number): Observable<User> {
    return this.http
      .post<ApiResponse<User>>(`${this.base}/hotels/${hotelId}/users/${userId}/verrouiller`, {})
      .pipe(map((r) => r.data as User));
  }

  deverrouiller(hotelId: number, userId: number): Observable<User> {
    return this.http
      .post<ApiResponse<User>>(`${this.base}/hotels/${hotelId}/users/${userId}/deverrouiller`, {})
      .pipe(map((r) => r.data as User));
  }

  /**
   * Réinitialise le mot de passe — le serveur génère un nouveau mdp
   * en clair, à afficher une seule fois côté UI puis jeté à la
   * fermeture du modal SweetAlert2.
   */
  resetPassword(hotelId: number, userId: number): Observable<ResetPasswordResponse> {
    return this.http
      .post<ApiResponse<ResetPasswordResponse>>(
        `${this.base}/hotels/${hotelId}/users/${userId}/reset-password`,
        {},
      )
      .pipe(map((r) => r.data as ResetPasswordResponse));
  }

  desactiver(hotelId: number, userId: number): Observable<User> {
    return this.http
      .post<ApiResponse<User>>(`${this.base}/hotels/${hotelId}/users/${userId}/desactiver`, {})
      .pipe(map((r) => r.data as User));
  }
}
