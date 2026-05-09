import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  LoginRequest,
  LoginResponse,
} from '../../services/auth.service';

/**
 * `AuthApi` — couche HTTP pure pour les effects auth.
 *
 * Ne fait QUE des appels HTTP : pas de localStorage, pas de routing, pas de state.
 * Les effects orchestrent localStorage / routing / store updates.
 *
 * Évite la dépendance circulaire `AuthEffects → AuthService → store.dispatch → AuthEffects`
 * qui apparaîtrait si on faisait dispatcher des actions à AuthService.
 */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/auth`;

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<ApiResponse<LoginResponse>>(`${this.base}/login`, credentials)
      .pipe(
        map((res) => this.unwrap(res, 'error.auth.invalidCredentials')),
        catchError((err) => this.toErrorKey(err, 'error.auth.invalidCredentials')),
      );
  }

  logout(): Observable<void> {
    return this.http
      .post<ApiResponse<unknown>>(`${this.base}/logout`, {})
      .pipe(
        map(() => undefined),
        catchError((err) => this.toErrorKey(err, 'error.auth.logout')),
      );
  }

  refresh(token: string): Observable<LoginResponse> {
    return this.http
      .post<ApiResponse<LoginResponse>>(`${this.base}/refresh`, { token })
      .pipe(
        map((res) => this.unwrap(res, 'error.auth.refreshFailed')),
        catchError((err) => this.toErrorKey(err, 'error.auth.refreshFailed')),
      );
  }

  me(): Observable<LoginResponse> {
    return this.http
      .get<ApiResponse<LoginResponse>>(`${this.base}/me`)
      .pipe(
        map((res) => this.unwrap(res, 'error.auth.loadCurrentUserFailed')),
        catchError((err) =>
          this.toErrorKey(err, 'error.auth.loadCurrentUserFailed'),
        ),
      );
  }

  /**
   * Démappe un `ApiResponse<T>` ; si `success === false`, lève une `Error`
   * dont le `message` est une clé i18n.
   */
  private unwrap<T>(res: ApiResponse<T>, fallbackKey: string): T {
    if (res.success && res.data) {
      return res.data;
    }
    throw new Error(fallbackKey);
  }

  /**
   * Convertit une erreur HTTP/inconnue en `Observable<never>` qui jette une
   * `Error` dont le `message` est une **clé i18n** (jamais un texte traduit).
   */
  private toErrorKey(
    err: unknown,
    fallbackKey: string,
  ): Observable<never> {
    let key = fallbackKey;
    if (err instanceof HttpErrorResponse) {
      if (err.status === 401) {
        key = 'error.auth.invalidCredentials';
      } else if (err.status === 403) {
        key = 'error.auth.forbidden';
      } else if (err.status === 0) {
        key = 'error.network.unreachable';
      } else if (err.status >= 500) {
        key = 'error.server.unavailable';
      } else if (
        typeof err.error === 'object' &&
        err.error !== null &&
        typeof (err.error as { error?: string }).error === 'string'
      ) {
        // Le backend retourne déjà une clé i18n dans `error.error`
        key = (err.error as { error: string }).error;
      }
    } else if (err instanceof Error && err.message) {
      key = err.message;
    }
    return throwError(() => new Error(key));
  }
}
