import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../../../environments/environment';
import { ApiResponse, PageResponse } from '../../../shared/models/api.model';
import {
  HotelUser,
  HotelUserCreate,
  HotelUserResetPasswordResponse,
  HotelUserUpdate,
} from '../models/hotel-user.model';

/**
 * Service HTTP — Gestion des utilisateurs par un ADMIN d'hôtel.
 *
 * Endpoints (cf. Tour A backend, `HotelUserController`, 2026-05-13) :
 *   - GET    /api/hotel/users?page=&size=&sort=     → Page<DBUserAdminDto>
 *   - GET    /api/hotel/users/{userId}              → DBUserAdminDto
 *   - POST   /api/hotel/users                       → DBUserAdminDto (201)
 *   - PUT    /api/hotel/users/{userId}              → DBUserAdminDto
 *   - POST   /api/hotel/users/{userId}/verrouiller  → void
 *   - POST   /api/hotel/users/{userId}/deverrouiller → void
 *   - POST   /api/hotel/users/{userId}/reset-password → DBUserResetPasswordResponseDto
 *   - POST   /api/hotel/users/{userId}/desactiver   → void
 *
 * Sécurité multi-tenant :
 *  - Le tenant est résolu côté serveur via `TenantContext` (JWT) — l'ADMIN
 *    ne peut PAS viser un autre hôtel via URL.
 *  - Garde anti-escalation côté serveur : refus 400 si tentative de
 *    créer/promouvoir un SUPERADMIN ou ADMIN (`error.user.role.escalation.forbidden`).
 *  - Garde anti-suicide côté serveur : refus 400 si l'ADMIN tente de
 *    verrouiller/désactiver/reset son propre compte (`error.user.self.action.forbidden`).
 *
 * Le frontend reproduit ces gardes côté UI (filtre du sélecteur de rôle,
 * cacher les actions sur la propre ligne) — mais le serveur reste la source
 * d'autorité (ceinture + bretelles).
 */
@Injectable({ providedIn: 'root' })
export class HotelUsersService {
  private readonly base = `${environment.apiUrl}/api/hotel/users`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Liste paginée des utilisateurs du tenant courant.
   *
   * Le backend accepte les paramètres Spring standards : `page`, `size`, `sort`
   * (format `field,asc|desc`).
   */
  page(
    page = 0,
    size = 10,
    sortBy: string = 'username',
    sortDir: 'asc' | 'desc' = 'asc',
  ): Observable<PageResponse<HotelUser>> {
    const params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sort', `${sortBy},${sortDir}`);
    return this.http
      .get<ApiResponse<PageResponse<HotelUser>>>(this.base, { params })
      .pipe(map((r) => r.data as PageResponse<HotelUser>));
  }

  findById(userId: number): Observable<HotelUser> {
    return this.http
      .get<ApiResponse<HotelUser>>(`${this.base}/${userId}`)
      .pipe(map((r) => r.data as HotelUser));
  }

  create(dto: HotelUserCreate): Observable<HotelUser> {
    return this.http
      .post<ApiResponse<HotelUser>>(this.base, dto)
      .pipe(map((r) => r.data as HotelUser));
  }

  update(userId: number, dto: HotelUserUpdate): Observable<HotelUser> {
    return this.http
      .put<ApiResponse<HotelUser>>(`${this.base}/${userId}`, dto)
      .pipe(map((r) => r.data as HotelUser));
  }

  verrouiller(userId: number): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/${userId}/verrouiller`, {})
      .pipe(map(() => undefined));
  }

  deverrouiller(userId: number): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/${userId}/deverrouiller`, {})
      .pipe(map(() => undefined));
  }

  /**
   * Réinitialise le mot de passe — le serveur génère un nouveau mdp temporaire
   * en clair, à afficher une seule fois côté UI (SweetAlert2 copiable).
   */
  resetPassword(userId: number): Observable<HotelUserResetPasswordResponse> {
    return this.http
      .post<ApiResponse<HotelUserResetPasswordResponse>>(
        `${this.base}/${userId}/reset-password`,
        {},
      )
      .pipe(map((r) => r.data as HotelUserResetPasswordResponse));
  }

  desactiver(userId: number): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/${userId}/desactiver`, {})
      .pipe(map(() => undefined));
  }
}
