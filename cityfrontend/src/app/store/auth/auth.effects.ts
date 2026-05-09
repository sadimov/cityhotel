import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';

import { AuthApi } from './auth.api';
import { AuthActions } from './auth.actions';
import {
  clearStoredSession,
  getStoredLoginResponse,
  getStoredToken,
  isTokenStillValid,
  persistSession,
} from './auth.storage';

/**
 * Effects du feature store `auth`.
 *
 * Responsabilités :
 * - Appeler `AuthApi` pour les actions HTTP (login / logout / refresh / me).
 * - Persister / nettoyer le JWT dans `localStorage` aux moments clés.
 * - Naviguer (login → dashboard, logout → /login).
 *
 * Aucun texte affiché ici n'est en clair — toute clé d'erreur est une **clé i18n**
 * que le composant consommera via ngx-translate.
 */
@Injectable()
export class AuthEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(AuthApi);
  private readonly router = inject(Router);

  // ────────────────────────────────────────────────────────────────────────
  // Bootstrap : au démarrage de l'app, réhydrater depuis localStorage
  // ────────────────────────────────────────────────────────────────────────
  bootstrap$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.bootstrap),
      map(() => {
        const token = getStoredToken();
        const stored = getStoredLoginResponse();
        if (token && stored && isTokenStillValid(token)) {
          // localStorage avait sérialisé un LoginResponse complet
          return AuthActions.bootstrapSuccess({ response: stored });
        }
        // Pas de session valide : on nettoie au cas où il restait un token expiré
        clearStoredSession();
        return AuthActions.bootstrapNoSession();
      }),
    ),
  );

  // ────────────────────────────────────────────────────────────────────────
  // Login
  // ────────────────────────────────────────────────────────────────────────
  login$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.login),
      switchMap(({ credentials, returnUrl }) =>
        this.api.login(credentials).pipe(
          map((response) => AuthActions.loginSuccess({ response, returnUrl })),
          catchError((err: Error) =>
            of(
              AuthActions.loginFailure({
                errorKey: err.message || 'error.auth.invalidCredentials',
              }),
            ),
          ),
        ),
      ),
    ),
  );

  loginSuccessPersist$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AuthActions.loginSuccess),
        tap(({ response }) => persistSession(response)),
      ),
    { dispatch: false },
  );

  loginSuccessRedirect$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AuthActions.loginSuccess),
        tap(({ response, returnUrl }) => {
          if (returnUrl && returnUrl !== '/login') {
            this.router.navigateByUrl(returnUrl);
            return;
          }
          this.router.navigate([this.defaultRouteForRole(response.roleCode)]);
        }),
      ),
    { dispatch: false },
  );

  // ────────────────────────────────────────────────────────────────────────
  // Logout
  // ────────────────────────────────────────────────────────────────────────
  logout$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.logout),
      switchMap(() =>
        this.api.logout().pipe(
          // Que le backend réussisse ou non, on déconnecte localement
          map(() => AuthActions.logoutSuccess()),
          catchError(() => of(AuthActions.logoutSuccess())),
        ),
      ),
    ),
  );

  logoutSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AuthActions.logoutSuccess),
        tap(() => {
          clearStoredSession();
          this.router.navigate(['/login']);
        }),
      ),
    { dispatch: false },
  );

  // ────────────────────────────────────────────────────────────────────────
  // Refresh token
  // ────────────────────────────────────────────────────────────────────────
  refreshToken$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.refreshToken),
      switchMap(() => {
        const token = getStoredToken();
        if (!token) {
          return of(
            AuthActions.refreshTokenFailure({
              errorKey: 'error.auth.noToken',
            }),
          );
        }
        return this.api.refresh(token).pipe(
          map((response) => AuthActions.refreshTokenSuccess({ response })),
          catchError((err: Error) =>
            of(
              AuthActions.refreshTokenFailure({
                errorKey: err.message || 'error.auth.refreshFailed',
              }),
            ),
          ),
        );
      }),
    ),
  );

  refreshTokenSuccessPersist$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AuthActions.refreshTokenSuccess),
        tap(({ response }) => persistSession(response)),
      ),
    { dispatch: false },
  );

  refreshTokenFailureClear$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AuthActions.refreshTokenFailure),
        tap(() => {
          clearStoredSession();
          this.router.navigate(['/login']);
        }),
      ),
    { dispatch: false },
  );

  // ────────────────────────────────────────────────────────────────────────
  // Load current user (/auth/me)
  // ────────────────────────────────────────────────────────────────────────
  loadCurrentUser$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.loadCurrentUser),
      switchMap(() =>
        this.api.me().pipe(
          map((response) =>
            AuthActions.loadCurrentUserSuccess({ response }),
          ),
          catchError((err: Error) =>
            of(
              AuthActions.loadCurrentUserFailure({
                errorKey: err.message || 'error.auth.loadCurrentUserFailed',
              }),
            ),
          ),
        ),
      ),
    ),
  );

  /**
   * Route par défaut après login en fonction du rôle.
   * Reproduit la logique de l'ancien `AuthService.redirectToRoleBasedDashboard`.
   */
  private defaultRouteForRole(roleCode: string | null | undefined): string {
    switch (roleCode) {
      case 'RECEPTION':
      case 'RESREC':
        return '/reservations';
      case 'RESTAURANT':
        return '/restaurant';
      case 'MENAGE':
        return '/housekeeping';
      case 'MAGASIN':
        return '/orders';
      case 'SUPERADMIN':
      case 'ADMIN':
      case 'GERANT':
        return '/dashboard';
      default:
        return '/profile';
    }
  }
}
