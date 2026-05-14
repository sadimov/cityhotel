import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../environments/environment';
import { ApiResponse } from '../shared/models/api.model';
import { ChangePasswordRequest, Profile, ProfileUpdate } from './profile.model';

/**
 * Service HTTP — Profil self-service de l'utilisateur courant.
 *
 * Endpoints (cf. Tour A backend, 2026-05-13) :
 *   - GET    /api/profile/me                    → ProfileDto
 *   - PUT    /api/profile/me                    → ProfileDto (body: ProfileUpdateDto)
 *   - POST   /api/profile/me/change-password    → void (body: ChangePasswordDto)
 *   - POST   /api/profile/me/avatar             → ProfileDto (multipart "file")
 *   - DELETE /api/profile/me/avatar             → ProfileDto
 *
 * Le backend résout TOUJOURS l'userId courant via SecurityContextHolder
 * (cf. `SecurityUtils.currentUserIdOrThrow()`). Aucun userId / hotelId
 * n'est envoyé par le client — la garde anti-spoof multi-tenant est
 * assurée côté serveur.
 */
@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly base = `${environment.apiUrl}/api/profile/me`;

  constructor(private readonly http: HttpClient) {}

  /** Charge le profil courant. */
  getProfile(): Observable<Profile> {
    return this.http
      .get<ApiResponse<Profile>>(this.base)
      .pipe(map((r) => r.data as Profile));
  }

  /** Met à jour les infos personnelles éditables (prenom, nom, telephone, poste). */
  updateProfile(dto: ProfileUpdate): Observable<Profile> {
    return this.http
      .put<ApiResponse<Profile>>(this.base, dto)
      .pipe(map((r) => r.data as Profile));
  }

  /**
   * Change le mot de passe. Le backend valide :
   *  - `ancienMotDePasse` correspond au hash en base.
   *  - `nouveauMotDePasse === confirmation`.
   *  - `nouveauMotDePasse !== ancienMotDePasse`.
   *  - `nouveauMotDePasse` respecte la policy (`PasswordUtil.validatePassword`).
   *
   * Clés d'erreur i18n possibles côté serveur :
   *   - `error.user.password.invalid` (ancien mdp incorrect)
   *   - `error.user.password.mismatch` (confirmation ≠ nouveau)
   *   - `error.user.password.unchanged` (nouveau identique à ancien)
   *   - `error.user.password.weak` (policy non respectée)
   */
  changePassword(dto: ChangePasswordRequest): Observable<void> {
    return this.http
      .post<ApiResponse<void>>(`${this.base}/change-password`, dto)
      .pipe(map(() => undefined));
  }

  /**
   * Upload de l'avatar (multipart). Backend valide :
   *  - taille max 2 MB
   *  - types autorisés : image/jpeg, image/png, image/webp
   *
   * Le composant valide AUSSI côté front avant l'appel pour une meilleure UX.
   */
  uploadAvatar(file: File): Observable<Profile> {
    const form = new FormData();
    form.append('file', file);
    return this.http
      .post<ApiResponse<Profile>>(`${this.base}/avatar`, form)
      .pipe(map((r) => r.data as Profile));
  }

  /** Supprime l'avatar et renvoie le profil mis à jour (avatarUrl = null). */
  deleteAvatar(): Observable<Profile> {
    return this.http
      .delete<ApiResponse<Profile>>(`${this.base}/avatar`)
      .pipe(map((r) => r.data as Profile));
  }

  /**
   * Construit l'URL absolue à utiliser dans un `<img [src]>` pour afficher
   * l'avatar. Retourne `null` si pas d'avatar (le composant affiche alors
   * un fallback initiales).
   *
   * Le backend sert les fichiers statiques sous le contexte `/citybackend`
   * (cf. `application.yml`), donc on concatène avec `environment.apiUrl`
   * qui inclut déjà ce préfixe.
   */
  buildAvatarUrl(profile: Profile | null | undefined): string | null {
    if (!profile?.avatarUrl) {
      return null;
    }
    const path = profile.avatarUrl.startsWith('/')
      ? profile.avatarUrl
      : `/${profile.avatarUrl}`;
    return `${environment.apiUrl}${path}`;
  }
}
